/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package fr.makoff.music.playback

import android.content.Context
import android.net.ConnectivityManager
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.media3.database.DatabaseProvider
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertubex.extraction.ContentHints
import fr.makoff.music.constants.AudioQuality
import fr.makoff.music.constants.InternalDownloadFormatKey
import fr.makoff.music.downloads.DownloadAudioFormat
import fr.makoff.music.db.MusicDatabase
import fr.makoff.music.db.entities.AlbumEntity
import fr.makoff.music.db.entities.FormatEntity
import fr.makoff.music.db.entities.Song
import fr.makoff.music.db.entities.SongEntity
import fr.makoff.music.di.DownloadCache
import fr.makoff.music.di.PlayerCache
import fr.makoff.music.models.MediaMetadata
import fr.makoff.music.models.toMediaMetadata
import fr.makoff.music.utils.InnerTubeXPlayer
import fr.makoff.music.utils.enumPreference
import fr.makoff.music.utils.dataStore
import fr.makoff.music.utils.get
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import timber.log.Timber
import java.io.IOException
import java.time.LocalDateTime
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadUtil
@Inject
constructor(
    @ApplicationContext private val context: Context,
    val database: MusicDatabase,
    val databaseProvider: DatabaseProvider,
    @DownloadCache val downloadCache: Cache,
    @PlayerCache val playerCache: Cache,
) {
    private val TAG = "DownloadUtil"
    private val connectivityManager = context.getSystemService<ConnectivityManager>()!!
    private val downloadFormat by enumPreference(context, InternalDownloadFormatKey, DownloadAudioFormat.BEST)
    private val songUrlCache = StreamUrlCache()
    private val streamHttpClient =
        OkHttpClient.Builder()
            .proxy(YouTube.proxy)
            .proxyAuthenticator { _, response ->
                YouTube.proxyAuth?.let { auth ->
                    response.request.newBuilder()
                        .header("Proxy-Authorization", auth)
                        .build()
                } ?: response.request
            }
            .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val downloadPreparations = Semaphore(3)

    val downloads = MutableStateFlow<Map<String, Download>>(emptyMap())

    private val dataSourceFactory =
        ResolvingDataSource.Factory(
            androidx.media3.datasource.DefaultDataSource.Factory(context, OkHttpDataSource.Factory(streamHttpClient)),
        ) { dataSpec ->
            val mediaId = dataSpec.key ?: error("No media id")
            val formatChoice = DownloadAudioFormat.parse(
                downloadManager.downloadIndex.getDownload(mediaId)?.request?.uri?.getQueryParameter("downloadFormat"))
            if (formatChoice != DownloadAudioFormat.BEST) {
                return@Factory dataSpec.withUri(android.net.Uri.fromFile(prepareFormattedAudio(mediaId, formatChoice)))
            }

            songUrlCache[mediaId]?.let { cachedStream ->
                return@Factory dataSpec.withResolvedStream(cachedStream)
            }
            val cacheGeneration = songUrlCache.generation(mediaId)

            val playbackData = runBlocking(Dispatchers.IO) {
                val song = database.songEntity(mediaId)
                InnerTubeXPlayer.playerResponseForPlayback(
                    mediaId,
                    audioQuality = AudioQuality.HIGH,
                    connectivityManager = connectivityManager,
                    contentHints = ContentHints(
                        isExplicit = song?.explicit,
                        isUploaded = song?.isUploaded,
                    ),
                    allowBoundedRange = false,
                )
            }.getOrElse { error ->
                val cookie = context.dataStore.get(fr.makoff.music.constants.InnerTubeCookieKey, "")
                if (!mediaId.matches(Regex("[A-Za-z0-9_-]{11}")) || !canUseAuthenticatedDownloadFallback(error, cookie)) throw error
                return@Factory dataSpec.withUri(android.net.Uri.fromFile(prepareFormattedAudio(mediaId, DownloadAudioFormat.BEST)))
            }
            val format = playbackData.format

            val actualContentLength =
                format.contentLength?.takeIf { it > 0L } ?: run {
                    val request = okhttp3.Request.Builder()
                        .get()
                        .url(playbackData.streamUrl)
                        .apply {
                            playbackData.streamHeaders.forEach { (name, value) ->
                                header(name, value)
                            }
                        }
                        .header("Range", "bytes=0-0")
                        .build()
                    try {
                        streamHttpClient.newCall(request).execute().use { response ->
                            downloadContentLength(
                                statusCode = response.code,
                                contentRange = response.header("Content-Range"),
                                contentLength = response.header("Content-Length"),
                            )
                        }
                    } catch (_: IOException) {
                        null
                    }
                }

            database.query {
                if (actualContentLength != null) {
                    upsert(
                        FormatEntity(
                            id = mediaId,
                            itag = format.itag,
                            mimeType = format.mimeType.substringBefore(";"),
                            codecs =
                                format.mimeType
                                    .substringAfter("codecs=", missingDelimiterValue = "")
                                    .substringBefore(";")
                                    .trim()
                                    .removeSurrounding("\""),
                            bitrate = format.bitrate,
                            sampleRate = format.audioSampleRate,
                            contentLength = actualContentLength,
                            loudnessDb = playbackData.audioConfig?.loudnessDb,
                            perceptualLoudnessDb = playbackData.audioConfig?.perceptualLoudnessDb,
                            playbackUrl = playbackData.playbackTracking?.videostatsPlaybackUrl?.baseUrl
                        ),
                    )
                } else {
                    deleteFormat(mediaId)
                }

                // Metadata registration only — dateDownload is intentionally NOT set here.
                // It belongs solely to onDownloadChanged()'s STATE_COMPLETED branch below,
                // which only fires once the download has actually finished. Setting it here
                // (at URL-resolve time, i.e. the moment the download merely *starts*) would
                // mark the song as "cached" before a single byte is written.
                val existing = getSongByIdBlocking(mediaId)?.song
                val updatedSong = existing ?: SongEntity(
                    id = mediaId,
                    title = playbackData.videoDetails?.title ?: "Unknown",
                    duration = playbackData.videoDetails?.lengthSeconds?.toIntOrNull() ?: 0,
                    thumbnailUrl = playbackData.videoDetails?.thumbnail?.thumbnails?.lastOrNull()?.url,
                    dateDownload = null,
                    isDownloaded = false
                )

                upsert(updatedSong)
            }

            // googlevideo throttles unbounded requests to roughly playback speed; a range param covering
            // the whole file lets the download run at full speed.
            val streamUrl =
                if (actualContentLength != null && "&range=" !in playbackData.streamUrl) {
                    "${playbackData.streamUrl}&range=0-${actualContentLength - 1}"
                } else {
                    playbackData.streamUrl
                }

            songUrlCache.put(
                mediaId = mediaId,
                url = streamUrl,
                requestHeaders = playbackData.streamHeaders,
                clientName = playbackData.streamClient,
                expiresInSeconds = playbackData.streamExpiresInSeconds,
                requireBoundedRange = playbackData.requireBoundedRange,
                rangeChunkSizeBytes = playbackData.rangeChunkSizeBytes,
                useRangeChunks = playbackData.useRangeChunks,
                expectedGeneration = cacheGeneration,
            )
            dataSpec.withResolvedStream(
                CachedStreamUrl(
                    url = streamUrl,
                    requestHeaders = playbackData.streamHeaders,
                    clientName = playbackData.streamClient,
                    requireBoundedRange = playbackData.requireBoundedRange,
                    rangeChunkSizeBytes = playbackData.rangeChunkSizeBytes,
                    useRangeChunks = playbackData.useRangeChunks,
                ),
            )
        }

    val downloadNotificationHelper =
        DownloadNotificationHelper(context, ExoDownloadService.CHANNEL_ID)

    @OptIn(DelicateCoroutinesApi::class)
    val downloadManager: DownloadManager =
        DownloadManager(
            context,
            databaseProvider,
            downloadCache,
            dataSourceFactory,
            Executor(Runnable::run)
        ).apply {
            maxParallelDownloads = 3
            addListener(
                object : DownloadManager.Listener {
                    override fun onDownloadChanged(
                        downloadManager: DownloadManager,
                        download: Download,
                        finalException: Exception?,
                    ) {
                        if (download.state == Download.STATE_FAILED && finalException.isExpiredStreamError()) {
                            songUrlCache.invalidate(download.request.id)
                        }

                        downloads.update { map ->
                            map.toMutableMap().apply {
                                set(download.request.id, download)
                            }
                        }

                        scope.launch {
                            when (download.state) {
                                Download.STATE_COMPLETED -> {
                                    java.io.File(context.cacheDir, "authenticated-download/${download.request.id}").deleteRecursively()
                                    removeFromPlayerCache(download.request.id)
                                    database.updateDownloadedInfo(download.request.id, true, LocalDateTime.now())
                                }
                                Download.STATE_FAILED,
                                Download.STATE_STOPPED,
                                Download.STATE_REMOVING -> {
                                    java.io.File(context.cacheDir, "authenticated-download/${download.request.id}").deleteRecursively()
                                    database.updateDownloadedInfo(download.request.id, false, null)
                                }
                                else -> {
                                }
                            }
                        }
                    }

                    override fun onDownloadRemoved(
                        downloadManager: DownloadManager,
                        download: Download,
                    ) {
                        val downloadId = download.request.id
                        songUrlCache.invalidate(downloadId)

                        scope.launch {
                            runCatching {
                                database.updateDownloadedInfo(downloadId, false, null)
                            }.onSuccess {
                                downloads.update { map ->
                                    map.toMutableMap().apply {
                                        remove(downloadId)
                                    }
                                }
                                Timber.tag(TAG).d("Successfully removed download $downloadId from in-memory map")
                            }.onFailure { error ->
                                Timber.tag(TAG).e(error, "Failed to update database for removed download $downloadId, keeping in-memory entry")
                            }
                        }
                    }
                }
            )
        }

    init {
        val result = mutableMapOf<String, Download>()
        downloadManager.downloadIndex.getDownloads().use { cursor ->
            while (cursor.moveToNext()) {
                result[cursor.download.request.id] = cursor.download
            }
        }
        downloads.value = result
        scope.launch {
            result.values
                .filter { it.state == Download.STATE_COMPLETED }
                .forEach { removeFromPlayerCache(it.request.id) }
        }
    }

    /** Returns only a still-readable completed document. Lost files can be downloaded again. */
    fun externalAudioUri(songId: String): android.net.Uri? {
        val download = downloads.value[songId] ?: return null
        if (download.state != Download.STATE_COMPLETED || download.request.uri.scheme != "content") return null
        val uri = download.request.uri
        val readable = runCatching { context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length != 0L } == true }.getOrDefault(false)
        if (readable) return uri
        (downloadManager.downloadIndex as androidx.media3.exoplayer.offline.WritableDownloadIndex).removeDownload(songId)
        downloads.update { it - songId }
        database.updateDownloadedInfo(songId, false, null)
        return null
    }

    /** Commit the document reference before removing private bytes; never delete the public document. */
    fun registerExternalAudio(media: MediaMetadata, uri: android.net.Uri, size: Long) {
        require(uri.scheme == "content" && size > 0)
        check(context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length != 0L } == true)
        val previous = downloadManager.downloadIndex.getDownload(media.id)
        check(previous == null || previous.isTerminalState) { "An internal download is still active" }
        if (database.getSongByIdBlocking(media.id) == null) database.insert(media)
        val extractor = android.media.MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            val audio = (0 until extractor.trackCount).map(extractor::getTrackFormat)
                .first { it.getString(android.media.MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
            database.upsert(FormatEntity(media.id, -1, audio.getString(android.media.MediaFormat.KEY_MIME)!!,
                "", 0, if (audio.containsKey(android.media.MediaFormat.KEY_SAMPLE_RATE)) audio.getInteger(android.media.MediaFormat.KEY_SAMPLE_RATE) else null,
                size, null, playbackUrl = null))
        } finally { extractor.release() }
        val request = DownloadRequest.Builder(media.id, uri).setCustomCacheKey(media.id).setData(media.title.toByteArray()).build()
        val now = System.currentTimeMillis()
        val progress = androidx.media3.exoplayer.offline.DownloadProgress().apply { bytesDownloaded = size; percentDownloaded = 100f }
        val completed = Download(request, Download.STATE_COMPLETED, now, now, size, 0, 0, progress)
        (downloadManager.downloadIndex as androidx.media3.exoplayer.offline.WritableDownloadIndex).putDownload(completed)
        downloads.update { it + (media.id to completed) }
        database.updateDownloadedInfo(media.id, true, LocalDateTime.now())
        songUrlCache.invalidate(media.id)
        downloadCache.removeResource(media.id)
        removeFromPlayerCache(media.id)
    }

    /** Copy a completed local source to temporary staging without accessing YouTube. */
    fun stageExistingAudio(songId: String, directory: java.io.File): java.io.File? {
        val download = downloads.value[songId] ?: return null
        if (download.state != Download.STATE_COMPLETED) return null
        val uri = externalAudioUri(songId)
        val size = download.contentLength.takeIf { it > 0 }
            ?: androidx.media3.datasource.cache.ContentMetadata.getContentLength(downloadCache.getContentMetadata(songId))
        if (uri == null && (size <= 0 || !downloadCache.isCached(songId, 0, size))) return null
        directory.mkdirs()
        val staging = java.io.File(directory, "existing.audio")
        try {
            if (uri != null) {
                requireNotNull(context.contentResolver.openInputStream(uri)).use { input -> staging.outputStream().use { input.copyTo(it) } }
            } else {
                val source = CacheDataSource.Factory().setCache(downloadCache).setUpstreamDataSourceFactory(null).createDataSource()
                val spec = androidx.media3.datasource.DataSpec.Builder().setUri(songId).setKey(songId).setLength(size).build()
                androidx.media3.datasource.DataSourceInputStream(source, spec).use { input -> staging.outputStream().use { input.copyTo(it) } }
            }
            check(staging.length() > 0 && (size <= 0 || staging.length() == size))
            val extractor = android.media.MediaExtractor()
            val extension = try {
                extractor.setDataSource(staging.absolutePath)
                val mime = (0 until extractor.trackCount).map(extractor::getTrackFormat)
                    .mapNotNull { it.getString(android.media.MediaFormat.KEY_MIME) }.first { it.startsWith("audio/") }
                val header = ByteArray(12).also { bytes -> staging.inputStream().use { it.read(bytes) } }
                when {
                    header.take(4) == listOf(0x1a.toByte(), 0x45.toByte(), 0xdf.toByte(), 0xa3.toByte()) -> "webm"
                    header.take(4).toByteArray().toString(Charsets.US_ASCII) == "OggS" -> "ogg"
                    header.drop(4).take(4).toByteArray().toString(Charsets.US_ASCII) == "ftyp" -> "m4a"
                    mime == "audio/mpeg" -> "mp3"
                    mime == "audio/flac" -> "flac"
                    else -> error("Unsupported cached audio container")
                }
            } finally { extractor.release() }
            return java.io.File(directory, "existing.$extension").also { check(staging.renameTo(it)) }
        } catch (error: Exception) { staging.delete(); throw error }
    }

    private fun prepareFormattedAudio(mediaId: String, format: DownloadAudioFormat): java.io.File {
        require(mediaId.matches(Regex("[A-Za-z0-9_-]{11}")))
        val directory = java.io.File(context.cacheDir, "authenticated-download/$mediaId/${format.name}")
        val marker = java.io.File(directory, "complete.txt")
        val file = runBlocking(Dispatchers.IO) {
            val saved = marker.takeIf { it.exists() }?.readText()?.takeIf { it.matches(Regex("audio\\.[a-z0-9]{1,8}")) }
                ?.let { java.io.File(directory, it) }?.takeIf { it.isFile && it.length() > 0 }
            saved ?: try {
                directory.deleteRecursively()
                val result = fr.makoff.music.downloads.YtDlpAudioEngine.download(context,
                    "https://www.youtube.com/watch?v=$mediaId", directory, "internal-$mediaId",
                    context.dataStore.get(fr.makoff.music.constants.InnerTubeCookieKey, ""), format = format) { }
                marker.writeText(result.name)
                result
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                throw IOException("Requested audio format unavailable; check your YouTube session and retry")
            }
        }
        val extractor = android.media.MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            val audio = (0 until extractor.trackCount).map(extractor::getTrackFormat)
                .first { it.getString(android.media.MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
            database.upsert(FormatEntity(mediaId, -1, audio.getString(android.media.MediaFormat.KEY_MIME)!!,
                "", 0, if (audio.containsKey(android.media.MediaFormat.KEY_SAMPLE_RATE)) audio.getInteger(android.media.MediaFormat.KEY_SAMPLE_RATE) else null,
                file.length(), null, playbackUrl = null))
        } finally { extractor.release() }
        return file
    }

    fun getDownload(songId: String): Flow<Download?> = downloads.map { it[songId] }

    fun download(song: Song) = download(song.toMediaMetadata())

    fun download(song: SongItem) = download(song.toMediaMetadata())

    fun download(mediaMetadata: MediaMetadata) {
        scope.launch {
            downloadPreparations.withPermit {
                // Revalidate document permission/existence before treating an external copy as complete.
                externalAudioUri(mediaMetadata.id)
                if (!shouldPrepareDownload(downloads.value[mediaMetadata.id]?.state)) return@withPermit

                mediaMetadata.album?.let { album ->
                    if (database.albumEntity(album.id) == null) {
                        database.insert(
                            AlbumEntity(
                                id = album.id,
                                title = album.title,
                                thumbnailUrl = mediaMetadata.thumbnailUrl,
                                songCount = 0,
                                duration = 0,
                            ),
                        )
                    }
                }

                val existing = database.getSongByIdBlocking(mediaMetadata.id)
                if (existing == null) {
                    database.insert(mediaMetadata)
                } else {
                    database.update(
                        existing,
                        mediaMetadata,
                        overwriteTitle = false,
                        overwriteArtists = false,
                    )
                }

                if (!shouldPrepareDownload(downloadManager.downloadIndex.getDownload(mediaMetadata.id)?.state)) {
                    return@withPermit
                }

                // New/retried downloads must not mix bytes from a previous quality or container.
                downloadCache.removeResource(mediaMetadata.id)
                songUrlCache.invalidate(mediaMetadata.id)
                val selectedFormat = downloadFormat
                val request =
                    DownloadRequest
                        .Builder(mediaMetadata.id, mediaMetadata.id.toUri().buildUpon()
                            .appendQueryParameter("downloadFormat", selectedFormat.name).build())
                        .setCustomCacheKey(mediaMetadata.id)
                        .setData(mediaMetadata.title.toByteArray())
                        .build()
                DownloadService.sendAddDownload(
                    context,
                    ExoDownloadService::class.java,
                    request,
                    false,
                )

                val albumArtwork = database.getSongByIdBlocking(mediaMetadata.id)?.album?.thumbnailUrl
                downloadArtworkUrls(mediaMetadata.thumbnailUrl, albumArtwork).forEach { artworkUrl ->
                    runCatching {
                        context.imageLoader.execute(
                            ImageRequest
                                .Builder(context)
                                .data(artworkUrl)
                                .memoryCachePolicy(CachePolicy.DISABLED)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .networkCachePolicy(CachePolicy.ENABLED)
                                .build(),
                        )
                    }
                }
            }
        }
    }

    fun download(songId: String) {
        scope.launch {
            database.getSongByIdBlocking(songId)?.let { song ->
                download(song)
            }
        }
    }

    fun release() {
        scope.cancel()
    }

    private fun removeFromPlayerCache(songId: String) {
        runCatching { playerCache.removeResource(songId) }
            .onFailure { Timber.tag(TAG).w(it, "Failed to remove downloaded song $songId from player cache") }
    }

    private fun Throwable?.isExpiredStreamError(): Boolean {
        var current = this
        while (current != null) {
            if (current is HttpDataSource.InvalidResponseCodeException &&
                (current.responseCode == 403 || current.responseCode == 410 || current.responseCode == 416)
            ) {
                return true
            }
            current = current.cause
        }
        return false
    }
}

internal fun shouldPrepareDownload(downloadState: Int?): Boolean = downloadState != Download.STATE_COMPLETED

internal fun downloadArtworkUrls(
    songArtwork: String?,
    albumArtwork: String?,
): List<String> = listOfNotNull(songArtwork, albumArtwork).filter(String::isNotBlank).distinct()

internal fun downloadContentLength(
    statusCode: Int,
    contentRange: String?,
    contentLength: String?,
): Long? {
    val rangePattern =
        when (statusCode) {
            206 -> PARTIAL_CONTENT_RANGE
            416 -> UNSATISFIED_CONTENT_RANGE
            else -> null
        }
    if (rangePattern != null) {
        return contentRange
            ?.trim()
            ?.let(rangePattern::matchEntire)
            ?.groupValues
            ?.get(1)
            ?.toLongOrNull()
            ?.takeIf { it > 0L }
    }
    return if (statusCode == 200) contentLength?.toLongOrNull()?.takeIf { it > 0L } else null
}

private val PARTIAL_CONTENT_RANGE = Regex("""bytes\s+0-0/(\d+)""", RegexOption.IGNORE_CASE)
private val UNSATISFIED_CONTENT_RANGE = Regex("""bytes\s+\*/(\d+)""", RegexOption.IGNORE_CASE)

internal fun canUseAuthenticatedDownloadFallback(error: Throwable, cookie: String): Boolean {
    val authenticated = cookie.split(';').any { it.trim().substringBefore('=') in setOf("SAPISID", "__Secure-3PAPISID", "__Secure-1PAPISID") && it.substringAfter('=', "").isNotBlank() }
    return authenticated && error is com.metrolist.innertubex.extraction.StreamResolveException &&
        error.reason in setOf(com.metrolist.innertubex.extraction.StreamResolveException.Reason.AGE_RESTRICTED,
            com.metrolist.innertubex.extraction.StreamResolveException.Reason.EXPLICIT_UNSUPPORTED)
}
