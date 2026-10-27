package fr.makoff.music.downloads

import android.content.Context
import android.graphics.Bitmap
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
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
internal suspend fun tagAudioFile(context: Context, source: File, media: MediaMetadata, track: Int, directory: File): File {
    val thumbnailUrls = buildList {
        media.thumbnailUrl?.takeIf { it.isNotBlank() }?.let(::add)
        if (media.id.matches(Regex("[A-Za-z0-9_-]{11}"))) add("https://i.ytimg.com/vi/${media.id}/hqdefault.jpg")
    }.distinct()
    var cover: Cover? = null
    for (url in thumbnailUrls) {
        try {
            val result = context.imageLoader.execute(ImageRequest.Builder(context).data(url)
                .size(512).allowHardware(false).build()) as? SuccessResult ?: continue
            var bitmap = result.image.toBitmap()
            while (true) {
                val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
                if (bytes.size in 1..64000) {
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
        args += listOf("-map", "0:a:0", "-c:a", "copy", "-map_metadata", "-1")
        if (artwork != null && !ogg) args += listOf("-map", "1:v:0", "-c:v", "copy", "-disposition:v:0", "attached_pic")
        fun tag(key: String, value: String?) {
            value?.replace("\u0000", "")?.take(4096)?.takeIf { it.isNotBlank() }?.let { args += listOf("-metadata", "$key=$it") }
        }
        tag("title", media.title)
        tag("artist", media.artists.joinToString { it.name })
        tag("album", media.album?.title)
        if (track > 0) tag("track", track.toString())
        if (artwork != null && ogg) {
            val picture = ByteArrayOutputStream()
            DataOutputStream(picture).use { data ->
                data.writeInt(3)
                val mime = "image/jpeg".toByteArray(Charsets.US_ASCII)
                data.writeInt(mime.size); data.write(mime); data.writeInt(0)
                data.writeInt(artwork.width); data.writeInt(artwork.height); data.writeInt(24); data.writeInt(0)
                data.writeInt(artwork.bytes.size); data.write(artwork.bytes)
            }
            // Binary artwork is already bounded above; the text-tag limit would corrupt its Base64.
            args += listOf("-metadata", "METADATA_BLOCK_PICTURE=" +
                android.util.Base64.encodeToString(picture.toByteArray(), android.util.Base64.NO_WRAP))
        }
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
