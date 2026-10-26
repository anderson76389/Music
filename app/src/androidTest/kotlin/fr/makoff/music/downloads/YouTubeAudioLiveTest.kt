package fr.makoff.music.downloads

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class YouTubeAudioLiveTest {
    @Test(timeout = 120000) fun downloadPublicTestVideoAudio() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "live-audio-${UUID.randomUUID()}")
        try {
            for (format in listOf(DownloadAudioFormat.BEST, DownloadAudioFormat.M4A)) {
            val file = try {
                PlaybackAudioEngine.download(context, "jNQXAC9IVRw", File(directory, format.name),
                    "live-${UUID.randomUUID()}", "", format) { }
            } catch (error: Exception) {
                // Suppress stderr and signed URLs in public CI logs.
                val errors = error.message.orEmpty().lineSequence().filter { it.startsWith("ERROR:") }.joinToString(" ")
                val diagnostic = listOf(
                    "HTTP Error 403" to "HTTP_403",
                    "HTTP Error 429" to "HTTP_429",
                    "CERTIFICATE_VERIFY_FAILED" to "TLS_CERTIFICATE",
                    "Sign in" to "YOUTUBE_AUTH_REQUIRED",
                    "JavaScript runtime" to "JS_RUNTIME",
                    "ffmpeg" to "FFMPEG_REQUIRED",
                    "not available" to "VIDEO_UNAVAILABLE",
                ).firstOrNull { errors.contains(it.first, ignoreCase = true) }?.second ?: error.javaClass.simpleName
                throw AssertionError("Téléchargement YouTube non réussi depuis ce réseau ($diagnostic).")
            }
            assertTrue(file.length() > 1000)
            assertTrue(file.extension in setOf("m4a", "webm", "opus", "ogg", "mp4"))
            if (format == DownloadAudioFormat.M4A) assertTrue(file.extension == "m4a")
            println("YOUTUBE_DOWNLOAD_OK: format=$format; native=${file.parentFile?.name == "resolved"}; bytes=${file.length()}")
            }
        } finally { directory.deleteRecursively() }
    }
}
