package fr.makoff.music.downloads

import android.content.Context
import android.graphics.Bitmap
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.CachePolicy
import coil3.request.allowHardware
import coil3.toBitmap
import fr.makoff.music.models.MediaMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Write portable tags and cover art without decoding/re-encoding the audio packets. */
internal suspend fun tagAudioFile(context: Context, source: File, media: MediaMetadata, track: Int, directory: File,
    releaseYear: Int? = media.album?.year): File {
    val thumbnailUrls = exportArtworkUrls(media.thumbnailUrl, media.id)
    var cover: Cover? = null
    for (url in thumbnailUrls) {
        try {
            val result = context.imageLoader.execute(ImageRequest.Builder(context).data(url)
                .size(1024).scale(coil3.size.Scale.FIT).precision(coil3.size.Precision.EXACT)
                .memoryCachePolicy(CachePolicy.DISABLED).allowHardware(false).build()) as? SuccessResult ?: continue
            val original = result.image.toBitmap()
            val side = maxOf(original.width, original.height)
            var bitmap = if (original.width == original.height) original else {
                Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888).also { square ->
                    android.graphics.Canvas(square).apply {
                        drawColor(android.graphics.Color.BLACK)
                        drawBitmap(original, (side - original.width) / 2f, (side - original.height) / 2f, null)
                    }
                }
            }
            while (true) {
                val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }.toByteArray()
                if (bytes.size in 1..MAX_COVER_BYTES) {
                    cover = Cover(bytes, bitmap.width, bitmap.height)
                    break
                }
                if (maxOf(bitmap.width, bitmap.height) <= 64) break
                bitmap = Bitmap.createScaledBitmap(bitmap, (bitmap.width / 2).coerceAtLeast(1),
                    (bitmap.height / 2).coerceAtLeast(1), true)
            }
            if (cover != null) break
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) { /* Artwork failure must not discard completed audio. */ }
    }
    val artwork = cover
    return runInterruptible(Dispatchers.IO) {
        try {
            YtDlpAudioEngine.initialize(context)
            com.yausername.ffmpeg.FFmpeg.getInstance().init(context.applicationContext)
        } catch (error: LinkageError) { throw IOException("Audio metadata tools unavailable", error) }
        val outputDir = File(directory, "tagged").apply { check(isDirectory || mkdirs()) }
        val output = File(outputDir, "audio.${source.extension}")
        val ogg = source.extension.lowercase() in setOf("ogg", "opus")
        val args = mutableListOf(File(context.applicationInfo.nativeLibraryDir, "libffmpeg.so").absolutePath,
            "-nostdin", "-y", "-loglevel", "error", "-i", source.absolutePath)
        if (artwork != null && !ogg) {
            val image = File(outputDir, "cover.jpg").apply { writeBytes(artwork.bytes) }
            args += listOf("-i", image.absolutePath)
        }
        if (artwork != null && ogg) {
            val picture = ByteArrayOutputStream()
            DataOutputStream(picture).use { data ->
                data.writeInt(3)
                val mime = "image/jpeg".toByteArray(Charsets.US_ASCII)
                data.writeInt(mime.size); data.write(mime); data.writeInt(0)
                data.writeInt(artwork.width); data.writeInt(artwork.height); data.writeInt(24); data.writeInt(0)
                data.writeInt(artwork.bytes.size); data.write(artwork.bytes)
            }
            // A metadata file keeps full-size artwork outside Android's per-argument size limit.
            val metadata = File(outputDir, "cover.ffmetadata").apply {
                writeText(";FFMETADATA1\nMETADATA_BLOCK_PICTURE=" +
                    android.util.Base64.encodeToString(picture.toByteArray(), android.util.Base64.NO_WRAP) + "\n")
            }
            args += listOf("-f", "ffmetadata", "-i", metadata.absolutePath)
        }
        args += listOf("-map", "0:a:0", "-c:a", "copy", "-map_metadata", if (artwork != null && ogg) "1" else "-1")
        if (artwork != null && !ogg) args += listOf("-map", "1:v:0", "-c:v", "copy", "-disposition:v:0", "attached_pic")
        fun tag(key: String, value: String?) {
            value?.replace("\u0000", "")?.take(4096)?.takeIf { it.isNotBlank() }?.let { args += listOf("-metadata", "$key=$it") }
        }
        tag("title", media.title)
        tag("artist", media.artists.joinToString { it.name })
        tag("album", media.album?.title)
        releaseYear?.takeIf { it in 1..9999 }?.let { tag("date", it.toString().padStart(4, '0')) }
        if (track > 0) tag("track", track.toString())
        args += output.absolutePath
        val runtime = File(context.noBackupFilesDir, "youtubedl-android/packages")
        val builder = ProcessBuilder(args).redirectErrorStream(true).redirectOutput(File(outputDir, "ffmpeg.log"))
        builder.environment()["LD_LIBRARY_PATH"] = listOf("python", "ffmpeg", "aria2c")
            .joinToString(":") { File(runtime, "$it/usr/lib").absolutePath }
        val process = builder.start()
        try {
            check(process.waitFor(120, TimeUnit.SECONDS)) { "Audio metadata timed out" }
            if (process.exitValue() != 0 || !output.isFile || output.length() == 0L)
                throw IOException("Audio metadata could not be written")
            output
        } catch (error: Exception) {
            output.delete()
            throw error
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
    }
}

private data class Cover(val bytes: ByteArray, val width: Int, val height: Int)
private const val MAX_COVER_BYTES = 2 * 1024 * 1024

internal fun exportArtworkUrls(thumbnail: String?, videoId: String): List<String> = buildList {
    thumbnail?.takeIf { it.isNotBlank() }?.let { url ->
        val host = runCatching { java.net.URI(url).host?.lowercase() }.getOrNull().orEmpty()
        if (host == "googleusercontent.com" || host.endsWith(".googleusercontent.com") ||
            host == "ggpht.com" || host.endsWith(".ggpht.com")) {
            add(url.substringBefore('=') + "=w1024")
        } else if ((host == "i.ytimg.com" || host == "img.youtube.com") && videoId.matches(Regex("[A-Za-z0-9_-]{11}"))) {
            add("https://i.ytimg.com/vi/$videoId/maxresdefault.jpg")
            add("https://i.ytimg.com/vi/$videoId/hqdefault.jpg")
        }
        add(url)
    }
    if (videoId.matches(Regex("[A-Za-z0-9_-]{11}"))) add("https://i.ytimg.com/vi/$videoId/hqdefault.jpg")
}.distinct()
