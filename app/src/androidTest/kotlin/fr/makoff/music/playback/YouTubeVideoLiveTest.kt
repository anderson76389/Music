package fr.makoff.music.playback

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fr.makoff.music.innertube.YouTube
import fr.makoff.music.utils.InnerTubeXPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Online diagnostic: actually resolves a YouTube Music song/clip pair and the
 * signed video URL through the same independent engine used by the player.
 * YouTube availability varies by region/account and CI networking, therefore
 * the script records failures instead of treating an external outage as an APK
 * regression. The parser and player code are covered by deterministic tests.
 */
@RunWith(AndroidJUnit4::class)
class YouTubeVideoLiveTest {
    @Test(timeout = 120_000L)
    fun probeRealMusicVideoStream() = runBlocking(Dispatchers.IO) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        InnerTubeXPlayer.initialize(context)

        val audioId = "ZrOKjDZOtkA" // Oasis - Wonderwall, public YT Music track
        val fallbackOfficialVideoId = "bx1Bh8ZvH84"
        val result = withTimeoutOrNull(50_000L) {
            YouTube.videoCounterpart(
                videoId = audioId,
                title = "Wonderwall",
                artists = listOf("Oasis"),
            ).getOrNull()
        }
        println("VIDEO_PAIR_PROBE: " + (result ?: "not_returned_by_youtube"))
        val resolved = withTimeoutOrNull(60_000L) {
            InnerTubeXPlayer.resolveVideoStream(result ?: fallbackOfficialVideoId)
        }
        if (resolved != null) {
            println("VIDEO_STREAM_PROBE_OK: mime=" + resolved.mimeType +
                " signedUrl=" + resolved.url.startsWith("https://"))
        } else {
            println("VIDEO_STREAM_PROBE_UNAVAILABLE: video URL blocked or missing in CI")
        }
    }
}
