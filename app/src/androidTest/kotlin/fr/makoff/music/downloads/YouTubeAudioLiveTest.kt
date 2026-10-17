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
            val file = try {
                YtDlpAudioEngine.download(context, "https://www.youtube.com/watch?v=BaW_jenozKc", directory, "live-${UUID.randomUUID()}") { }
            } catch (error: Exception) {
                // Suppress stderr and signed URLs in public CI logs.
                val diagnostic = listOf(
                    "HTTP Error 403" to "HTTP_403",
                    "HTTP Error 429" to "HTTP_429",
                    "CERTIFICATE_VERIFY_FAILED" to "TLS_CERTIFICATE",
                    "Sign in" to "YOUTUBE_AUTH_REQUIRED",
                    "JavaScript runtime" to "JS_RUNTIME",
                    "ffmpeg" to "FFMPEG_REQUIRED",
                    "not available" to "VIDEO_UNAVAILABLE",
                ).firstOrNull { error.message.orEmpty().contains(it.first, ignoreCase = true) }?.second ?: error.javaClass.simpleName
                throw AssertionError("Téléchargement YouTube non réussi depuis ce réseau ($diagnostic).")
            }
            assertTrue(file.length() > 1000)
            assertTrue(file.extension in setOf("m4a", "webm", "opus", "ogg", "mp4"))
        } finally { directory.deleteRecursively() }
    }
}
