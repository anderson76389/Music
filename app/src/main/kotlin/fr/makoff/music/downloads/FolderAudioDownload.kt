package fr.makoff.music.downloads

import androidx.datastore.preferences.core.edit
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import fr.makoff.music.R
import fr.makoff.music.models.MediaMetadata
import fr.makoff.music.models.toMediaMetadata
import fr.makoff.music.utils.dataStore
import fr.makoff.music.utils.get
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

object FolderAudioDownloads {
    const val TAG = "folder-audio"
    const val TITLE_TAG = "audio-title:"
    const val SONG_TAG = "audio-song:"
    internal fun downloadUtil(context: Context) = dagger.hilt.android.EntryPointAccessors.fromApplication(context.applicationContext,
        FolderDownloadDependencies::class.java).downloadUtil()
    private val videoId = Regex("[A-Za-z0-9_-]{11}")

    fun enqueue(context: Context, media: MediaMetadata, tree: Uri, folders: List<String> = emptyList(), track: Int = 0) {
        require(videoId.matches(media.id)) { "Invalid YouTube video identifier" }
        val downloads = downloadUtil(context)
        val format = context.selectedDownloadFormat()
        downloads.download(media, requestedFormat = format)
        val request = OneTimeWorkRequestBuilder<FolderAudioWorker>()
            .setInputData(workDataOf(
                "videoId" to media.id,
                "format" to format.name,
                "title" to media.title.take(200),
                "artist" to media.artists.joinToString { it.name }.take(200),
                "artistNames" to media.artists.take(20).map { it.name.take(100) }.toTypedArray(),
                "artistIds" to media.artists.take(20).map { it.id.orEmpty().take(100) }.toTypedArray(),
                "duration" to media.duration,
                "thumbnail" to media.thumbnailUrl.orEmpty().take(1500),
                "albumId" to media.album?.id.orEmpty().take(100),
                "albumTitle" to media.album?.title.orEmpty().take(200),
                "explicit" to media.explicit,
                "tree" to tree.toString(),
                "folders" to folders.toTypedArray(),
                "track" to track,
            ))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(if (downloadUtil(context).downloads.value[media.id]?.state == androidx.media3.exoplayer.offline.Download.STATE_COMPLETED) NetworkType.NOT_REQUIRED else NetworkType.CONNECTED).build())
            .addTag(TAG)
            .addTag(SONG_TAG + media.id)
            .addTag("audio-created:${System.currentTimeMillis()}")
            .addTag(TITLE_TAG + media.title.take(100))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("folder-audio-${media.id}-${destinationId(tree.toString() + folders.joinToString("/") + track)}", ExistingWorkPolicy.KEEP, request)
    }
}

/** The executable, Python and QuickJS are packaged in the APK; no companion app is launched. */
internal object YtDlpAudioEngine {
    const val VERSION = "2026.08.19"
    private const val ENGINE_SHA256 = "1fa6733c37ea6fb51c99ad8fe785e7b7e5f3246c9b980230329d4fb72ed8d4d6"
    private var verified = false

    @Synchronized
    private fun initialize(context: Context) {
        if (verified) return
        val engine = YoutubeDL.getInstance()
        engine.init(context.applicationContext)
        val installed = File(File(File(context.noBackupFilesDir, YoutubeDL.baseName), YoutubeDL.ytdlpDirName), YoutubeDL.ytdlpBin)
        fun digest(file: File): String {
            val hash = java.security.MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    hash.update(buffer, 0, count)
                }
            }
            return hash.digest().joinToString("") { "%02x".format(it) }
        }
        if (!installed.exists() || digest(installed) != ENGINE_SHA256) {
            val replacement = File(installed.parentFile, "yt-dlp.new")
            try {
                context.resources.openRawResource(R.raw.ytdlp).use { input -> replacement.outputStream().use { input.copyTo(it) } }
                check(digest(replacement) == ENGINE_SHA256) { "Bundled yt-dlp checksum mismatch" }
                java.nio.file.Files.move(replacement.toPath(), installed.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE)
            } finally { replacement.delete() }
        }
        verified = true
    }

    suspend fun download(context: Context, url: String, directory: File, processId: String, cookie: String = "", lowQuality: Boolean = false, format: DownloadAudioFormat = DownloadAudioFormat.BEST, progress: (Float) -> Unit): File =
        runInterruptible(Dispatchers.IO) {
            try {
                initialize(context)
                if (format != DownloadAudioFormat.M4A) com.yausername.ffmpeg.FFmpeg.getInstance().init(context.applicationContext)
            } catch (failure: LinkageError) {
                throw IOException("Download engine initialization failed", failure)
            }
            check(directory.isDirectory || directory.mkdirs())
            val request = YoutubeDLRequest(url)
                .addOption("--ignore-config")
                .addOption("--no-playlist")
                .addOption("--no-mtime")
                .addOption("--no-cache-dir")
                .addOption("--socket-timeout", 30)
                .addOption("--retries", 3)
                .addOption("--fixup", "never")
                .addOption("-f", if (url.startsWith("file:")) "best" else if (lowQuality && format == DownloadAudioFormat.BEST) "worstaudio" else format.selector)
                .addOption("-o", File(directory, "audio.%(ext)s").absolutePath)
            if (format != DownloadAudioFormat.M4A) {
                request.addOption("--extract-audio").addOption("--audio-format", if (format == DownloadAudioFormat.FLAC) "flac" else "best")
            }
            // Only our staged private files use file URLs; public download entry points accept video IDs.
            if (url.startsWith("file:")) request.addOption("--enable-file-urls")
            if (cookie.isNotBlank()) {
                val jar = File(directory, "youtube-cookies.txt")
                jar.writeText(youtubeCookieJar(cookie))
                request.addOption("--cookies", jar.absolutePath)
            }
            YoutubeDL.getInstance().execute(request, processId) { percent, _, _ -> progress(percent) }
            val output = directory.listFiles().orEmpty().singleOrNull {
                it.isFile && it.name.startsWith("audio.") && it.extension !in setOf("part", "ytdl", "temp") && it.length() > 0
            } ?: throw IOException("No completed audio file")
            check(format != DownloadAudioFormat.FLAC || output.extension == "flac") { "FLAC conversion failed" }
            check(format != DownloadAudioFormat.M4A || output.extension == "m4a") { "M4A unavailable" }
            output
        }

    suspend fun reuse(context: Context, source: File?, format: DownloadAudioFormat, directory: File, processId: String): File? {
        source ?: return null
        return when {
            format == DownloadAudioFormat.BEST && source.extension.lowercase() == "webm" ->
                download(context, source.toURI().toString(), File(directory, "remuxed"), processId,
                    format = DownloadAudioFormat.BEST) { }
            format == DownloadAudioFormat.BEST -> source
            format == DownloadAudioFormat.M4A && source.extension == "m4a" -> source
            format == DownloadAudioFormat.M4A -> { source.delete(); null }
            source.extension == "flac" -> source
            else -> download(context, source.toURI().toString(), File(directory, "converted"), processId,
                format = DownloadAudioFormat.FLAC) { }
        }
    }
}

internal fun youtubeCookieJar(cookie: String): String = buildString {
    append("# Netscape HTTP Cookie File\n")
    cookie.split(';').forEach { item ->
        val parts = item.trim().split('=', limit = 2)
        if (parts.size == 2 && parts[0].matches(Regex("[A-Za-z0-9_-]+")) && parts[1].none { it.isISOControl() }) {
            append(".youtube.com\tTRUE\t/\tTRUE\t0\t${parts[0]}\t${parts[1]}\n")
        }
    }
}

internal fun audioFileName(title: String, artist: String, id: String, extension: String): String {
    val ext = extension.lowercase().takeIf { it.matches(Regex("[a-z0-9]{1,8}")) } ?: "audio"
    val stem = readableAudioName("$artist - $title").ifBlank { "Audio" }
    return "$stem [$id].$ext"
}

internal fun audioMime(extension: String): String = when (extension.lowercase()) {
    "m4a", "mp4" -> "audio/mp4"
    "webm" -> "audio/webm"
    "opus", "ogg" -> "audio/ogg"
    "mp3" -> "audio/mpeg"
    "wav" -> "audio/wav"
    "flac" -> "audio/flac"
    else -> "application/octet-stream"
}

internal suspend fun saveAudioToTree(context: Context, tree: Uri, source: File, name: String, folders: List<String> = emptyList()): Uri = withContext(Dispatchers.IO) {
    var parent = DocumentFile.fromTreeUri(context, tree) ?: throw IOException("Invalid document tree")
    if (!parent.canWrite()) throw IOException("Document tree is not writable")
    require(folders.size <= 3)
    for (segment in folders) {
        require(segment.isNotBlank() && segment != "." && segment != ".." && segment.none { it == '/' || it == '\\' || it.isISOControl() })
        val child = parent.findFile(segment) ?: parent.createDirectory(segment) ?: throw IOException("Cannot create folder")
        if (!child.isDirectory || !child.canWrite()) throw IOException("Destination is not a writable folder")
        parent = child
    }
    // Let the provider choose a unique name rather than overwrite an existing file.
    val finalName = if (parent.findFile(name) == null) name else
        name.substringBeforeLast('.') + " (" + java.util.UUID.randomUUID().toString().take(8) + ")." + source.extension
    val document = parent.createFile(audioMime(source.extension), "$finalName.part") ?: throw IOException("Cannot create document")
    try {
        context.contentResolver.openOutputStream(document.uri, "w")?.use { output ->
            source.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                    total += count
                }
                if (total != source.length() || total == 0L) throw IOException("Incomplete document")
            }
        } ?: throw IOException("Cannot open document")
        currentCoroutineContext().ensureActive()
        if (!document.renameTo(finalName)) throw IOException("Document provider cannot finalize file")
        document.uri
    } catch (error: Throwable) {
        runCatching { document.delete() }
        throw error
    }
}

class FolderAudioWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val tree = inputData.getString("tree")?.let(Uri::parse) ?: return Result.failure()
        val videoId = inputData.getString("videoId") ?: return Result.failure()
        if (!videoId.matches(Regex("[A-Za-z0-9_-]{11}"))) return Result.failure()
        val title = inputData.getString("title").orEmpty()
        val directory = File(applicationContext.cacheDir, "folder-audio/$id")
        var exporting = false
        val downloads = FolderAudioDownloads.downloadUtil(applicationContext)
        val media = downloads.database.getSongByIdBlocking(videoId)?.let { it.toMediaMetadata() }
            ?: MediaMetadata(videoId, title,
                inputData.getStringArray("artistNames").orEmpty().mapIndexed { index, name ->
                    MediaMetadata.Artist(inputData.getStringArray("artistIds")?.getOrNull(index)?.ifBlank { null }, name)
                }, inputData.getInt("duration", -1), inputData.getString("thumbnail")?.ifBlank { null },
                inputData.getString("albumId")?.takeIf { it.isNotBlank() }?.let { MediaMetadata.Album(it, inputData.getString("albumTitle").orEmpty()) },
                explicit = inputData.getBoolean("explicit", false))
        try {
            setProgress(workDataOf("waiting" to true))
            setForeground(notification(title, -1))
            return slots.withPermit {
                setProgress(workDataOf("percent" to 0))
                setForeground(notification(title, 0))
                awaitOfflineAudio({ downloads.downloadManager.downloadIndex.getDownload(videoId) })
                val format = DownloadAudioFormat.parse(inputData.getString("format"))
                val staged = downloads.stageExistingAudio(videoId, directory)
                    ?: throw IOException("Completed offline audio is unavailable")
                val source = YtDlpAudioEngine.reuse(applicationContext, staged, format, directory, id.toString())
                    ?: throw IOException("Requested audio format is unavailable")
                exporting = true
                setProgress(workDataOf("copying" to true))
                val track = inputData.getInt("track", 0)
                val name = (if (track > 0) "%03d - ".format(java.util.Locale.ROOT, track) else "") + audioFileName(title, inputData.getString("artist").orEmpty(), videoId, source.extension)
                val uri = saveAudioToTree(applicationContext, tree, source, name, inputData.getStringArray("folders")?.toList().orEmpty())
                currentCoroutineContext().ensureActive()
                // Removing a completed native download during export must not restore it.
                if (downloads.downloadManager.downloadIndex.getDownload(videoId)?.state != androidx.media3.exoplayer.offline.Download.STATE_COMPLETED)
                    return@withPermit Result.failure(workDataOf("error" to "cancelled"))
                downloads.registerExternalAudio(media, uri, source.length())
                if (inputData.getStringArray("folders")?.toList() == listOf(AutoLikedDownloads.FOLDER)) {
                    applicationContext.dataStore.edit {
                        it[AutoLikedDownloads.documentKey(tree.toString(), videoId)] = uri.toString()
                    }
                }
                val mime = audioMime(source.extension)
                notifyCompleted(title, uri, mime)
                Result.success(workDataOf("uri" to uri.toString(), "mime" to mime, "name" to name))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Do not expose yt-dlp stderr: it may include signed media URLs or account data.
            val error = if (exporting) "storage" else "download"
            return Result.failure(workDataOf("error" to error))
        } finally {
            withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { directory.deleteRecursively() }
        }
    }

    private fun channel(): NotificationManager {
        val manager = requireNotNull(applicationContext.getSystemService(NotificationManager::class.java))
        manager.createNotificationChannel(NotificationChannel(CHANNEL, applicationContext.getString(R.string.folder_audio_settings), NotificationManager.IMPORTANCE_LOW))
        return manager
    }

    private fun notification(title: String, percent: Int): ForegroundInfo {
        channel()
        val cancel = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
        val builder = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(R.drawable.download)
            .setContentTitle(applicationContext.getString(if (percent < 0) R.string.folder_audio_waiting else R.string.folder_audio_running))
            .setContentText(title)
            .setProgress(100, percent.coerceAtLeast(0), percent <= 0)
            .setOngoing(true)
            .addAction(0, applicationContext.getString(R.string.folder_audio_cancel), cancel)
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(id.hashCode(), builder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(id.hashCode(), builder.build())
    }

    private fun notifyCompleted(title: String, uri: Uri, mime: String) {
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val open = PendingIntent.getActivity(applicationContext, id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(R.drawable.download)
            .setContentTitle(applicationContext.getString(R.string.folder_audio_completed))
            .setContentText(title)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        // Notification permission can be denied without preventing the file from being saved.
        runCatching { channel().notify(id.hashCode() xor 0x40000000, notification) }
    }

    private companion object {
        const val CHANNEL = "folder-audio-downloads"
        val slots = Semaphore(1)
    }
}

internal fun destinationId(value: String): String = java.security.MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray()).take(8).joinToString("") { "%02x".format(it) }

internal fun collectionFolder(title: String, id: String): String {
    return readableAudioName(title).ifBlank { "Collection ${destinationId(id)}" }
}

/** Normalize stylized letters and strip combining stacks/invisible direction controls. */
internal fun readableAudioName(value: String): String {
    val normalized = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFKC)
        .replace(Regex("[\\p{M}\\p{Cf}]"), "")
        .replace(Regex("[\\p{Cntrl}/\\\\:*?\"<>|]"), " ")
        .replace(Regex("\\s+"), " ").trim(' ', '.')
    return normalized.codePoints().limit(50).toArray().let { String(it, 0, it.size) }.trimEnd(' ', '.')
}

/** Export follows the native download; cancellation/failure must never start a second transfer. */
internal suspend fun awaitOfflineAudio(read: () -> androidx.media3.exoplayer.offline.Download?) {
    var seen = false
    val started = android.os.SystemClock.elapsedRealtime()
    while (true) {
        currentCoroutineContext().ensureActive()
        val download = withContext(Dispatchers.IO) { read() }
        if (download != null) seen = true
        when (download?.state) {
            androidx.media3.exoplayer.offline.Download.STATE_COMPLETED -> return
            androidx.media3.exoplayer.offline.Download.STATE_FAILED,
            androidx.media3.exoplayer.offline.Download.STATE_STOPPED,
            androidx.media3.exoplayer.offline.Download.STATE_REMOVING -> throw IOException("Native download did not complete")
            null -> if (seen || android.os.SystemClock.elapsedRealtime() - started > 60000)
                throw IOException("Native download was cancelled or not queued")
        }
        kotlinx.coroutines.delay(200)
    }
}
