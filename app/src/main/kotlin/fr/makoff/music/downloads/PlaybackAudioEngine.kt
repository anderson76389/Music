package fr.makoff.music.downloads

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.ConnectivityManager
import androidx.core.content.getSystemService
import fr.makoff.music.constants.AudioQuality
import fr.makoff.music.innertube.YouTube
import fr.makoff.music.innertubex.extraction.ContentHints
import fr.makoff.music.utils.InnerTubeXPlayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Resolve with the player session first; keep the independent extractor as a fallback. */
internal object PlaybackAudioEngine {
    suspend fun download(
        context: Context, videoId: String, directory: File, processId: String,
        cookie: String, format: DownloadAudioFormat,
        isExplicit: Boolean? = null, isUploaded: Boolean? = null,
        audioQuality: AudioQuality = AudioQuality.HIGH,
        progress: (Float) -> Unit,
    ): File {
        require(videoId.matches(Regex("[A-Za-z0-9_-]{11}")))
        val resolved = File(directory, "resolved")
        try {
            val stream = InnerTubeXPlayer.playerResponseForPlayback(
                videoId, audioQuality = audioQuality,
                connectivityManager = requireNotNull(context.getSystemService<ConnectivityManager>()),
                contentHints = ContentHints(isExplicit = isExplicit, isUploaded = isUploaded),
                allowBoundedRange = true,
                preferM4a = format == DownloadAudioFormat.M4A,
            ).getOrThrow()
            val mime = stream.format.mimeType.substringBefore(';').trim()
            val extension = when (mime) {
                "audio/mp4" -> "m4a"
                "audio/webm" -> "webm"
                "audio/ogg" -> "ogg"
                else -> throw IOException("Unsupported resolved audio container")
            }
            // A requested M4A must remain AAC from the source, never re-encoded Opus.
            if (format == DownloadAudioFormat.M4A && extension != "m4a")
                throw IOException("Resolved source is not M4A")
            val file = File(resolved, "audio.$extension")
            downloadResolvedAudio(
                client, stream.streamUrl, stream.streamHeaders,
                stream.format.contentLength?.takeIf { it > 0L }, file,
                stream.rangeChunkSizeBytes.takeIf { it > 0 }?.coerceAtMost(1024L * 1024) ?: (1024L * 1024),
                progress,
            )
            withContext(Dispatchers.IO) {
                val extractor = MediaExtractor()
                try {
                    extractor.setDataSource(file.absolutePath)
                    check((0 until extractor.trackCount).any {
                        extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
                    }) { "Resolved file has no audio track" }
                } finally { extractor.release() }
            }
            return requireNotNull(YtDlpAudioEngine.reuse(context, file, format, directory, processId))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            withContext(Dispatchers.IO) { resolved.deleteRecursively() }
        }
        return YtDlpAudioEngine.download(context, "https://www.youtube.com/watch?v=$videoId",
            directory, processId, cookie, format = format, progress = progress)
    }

    private val client by lazy {
        OkHttpClient.Builder()
            .proxy(YouTube.proxy)
            .proxyAuthenticator { _, response ->
                YouTube.proxyAuth?.let { response.request.newBuilder().header("Proxy-Authorization", it).build() }
            }
            .connectTimeout(30, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    }
}

/** Copy every bounded range, validate offsets/lengths and publish only the completed file. */
internal suspend fun downloadResolvedAudio(
    client: OkHttpClient, url: String, headers: Map<String, String>,
    expectedLength: Long?, destination: File, chunkSize: Long = 1024L * 1024,
    progress: (Float) -> Unit = {},
): File = withContext(Dispatchers.IO) {
    require(chunkSize in 1..(1024L * 1024))
    check(destination.parentFile?.let { it.isDirectory || it.mkdirs() } == true)
    val partial = File(destination.parentFile, destination.name + ".part")
    var total = expectedLength?.takeIf { it > 0 }
    var offset = 0L
    try {
        partial.outputStream().use { output ->
            do {
                currentCoroutineContext().ensureActive()
                val end = total?.let { minOf(it - 1, offset + chunkSize - 1) } ?: (offset + chunkSize - 1)
                val request = Request.Builder().url(url).apply {
                    headers.forEach { (name, value) -> header(name, value) }
                    header("Range", "bytes=$offset-$end")
                }.build()
                client.newCall(request).execute().use { response ->
                    if (response.code != 200 && response.code != 206)
                        throw IOException("Audio HTTP status ${response.code}")
                    val range = if (response.code == 206)
                        Regex("bytes (\\d+)-(\\d+)/(\\d+)", RegexOption.IGNORE_CASE)
                            .matchEntire(response.header("Content-Range").orEmpty().trim())
                            ?: throw IOException("Missing audio content range")
                        else null
                    var responseBytes: Long? = null
                    if (range != null) {
                        val start = range.groupValues[1].toLong()
                        val last = range.groupValues[2].toLong()
                        val declaredTotal = range.groupValues[3].toLong()
                        check(start == offset && last >= start && last < declaredTotal && last <= end) {
                            "Unexpected audio range"
                        }
                        check(total == null || total == declaredTotal) { "Audio length changed" }
                        total = declaredTotal
                        responseBytes = last - start + 1
                    } else {
                        check(offset == 0L) { "Server ignored audio continuation range" }
                    }
                    val body = response.body ?: throw IOException("Missing audio body")
                    val declaredBody = body.contentLength().takeIf { it >= 0 }
                    val before = offset
                    body.byteStream().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            offset += count
                            check(total == null || offset <= total!!) { "Audio exceeds declared length" }
                            output.write(buffer, 0, count)
                            progress(total?.let { (offset * 100.0 / it).toFloat().coerceAtMost(100f) } ?: 0f)
                        }
                    }
                    check(offset > before) { "Empty audio response" }
                    check(responseBytes == null || offset - before == responseBytes) { "Incomplete audio range" }
                    check(declaredBody == null || offset - before == declaredBody) { "Incomplete audio body" }
                    if (response.code == 200) {
                        check(total == null || total == offset) { "Incomplete audio file" }
                        total = offset
                    }
                }
            } while (total == null || offset < total!!)
            check(offset > 0 && offset == total) { "Incomplete audio file" }
        }
        check(partial.renameTo(destination)) { "Cannot finalize audio file" }
        progress(100f)
        destination
    } catch (error: Throwable) {
        partial.delete()
        throw error
    }
}
