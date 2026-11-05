package fr.makoff.music.playback

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeListeningHistoryReporterTest {
    private val tracking = YouTubeListeningHistoryReporter.Tracking(
        playbackUrl = "https://s.youtube.com/api/stats/playback?test=1",
        watchtimeUrl = "https://s.youtube.com/api/stats/watchtime?test=1",
        format = 251,
    )
    private val playCalls = mutableListOf<Pair<String, String>>()
    private val watchCalls = mutableListOf<Watch>()
    private data class Watch(
        val cpn: String, val start: Double, val end: Double, val final: Boolean,
    )
    private val reporter = YouTubeListeningHistoryReporter(
        sendPlayback = { url, cpn, _ -> playCalls += url to cpn; true },
        sendWatchtime = { _, cpn, start, end, _, _, final, _ ->
            watchCalls += Watch(cpn, start, end, final)
            true
        },
    )
    private fun sample(
        pos: Long,
        at: Long = pos,
        song: String? = "abcdefghijk",
        playing: Boolean = true,
        allowed: Boolean = true,
        account: String = "accountA",
        urls: YouTubeListeningHistoryReporter.Tracking? = tracking,
    ) = YouTubeListeningHistoryReporter.Sample(
        mediaId = song, account = account, positionMs = pos,
        elapsedRealtimeMs = at, isPlaying = playing,
        canSync = allowed, tracking = urls,
    )

    @Test fun shortSongsAndPausedHistoryDoNotReport() = runBlocking {
        reporter.observe(sample(0))
        reporter.observe(sample(5_000))
        reporter.observe(sample(5_000, 6_000, playing = false))
        assertTrue(playCalls.isEmpty())
        assertTrue(watchCalls.isEmpty())

        reporter.observe(sample(0, 10_000, allowed = false))
        reporter.observe(sample(30_000, 40_000, allowed = false))
        assertTrue(playCalls.isEmpty())
    }

    @Test fun sendsPlaybackOnceThenIncrementalWatchtimeWithSameNonce() = runBlocking {
        for (sec in 0L..30L step 5L) {
            reporter.observe(sample(sec * 1_000))
        }
        assertEquals(1, playCalls.size)
        assertTrue(watchCalls.isNotEmpty())
        assertTrue(watchCalls.all { it.cpn == playCalls.single().second })
        assertTrue(watchCalls.first().end > watchCalls.first().start)

        reporter.observe(sample(30_000, 31_000, playing = false))
        assertTrue(watchCalls.last().final)
        assertTrue(watchCalls.last().end <= 30.0)
    }

    @Test fun switchingTracksAndAccountsNeverAttributesToWrongAccount() = runBlocking {
        reporter.observe(sample(0))
        reporter.observe(sample(10_000))
        reporter.observe(sample(20_000))
        reporter.observe(sample(0, 25_000, song = "lmnopqrstuv"))
        assertEquals(1, playCalls.size)
        assertTrue(watchCalls.any { it.final })

        // The service samples every five seconds; a stalled ten-second sample is capped.
        reporter.observe(sample(5_000, 30_000, song = "lmnopqrstuv"))
        reporter.observe(sample(10_000, 35_000, song = "lmnopqrstuv"))
        assertEquals(2, playCalls.size)
        assertFalse(playCalls[0].second == playCalls[1].second)

        val before = watchCalls.size
        reporter.observe(sample(0, 40_000, song = "abcdefghijk", account = "accountB"))
        assertEquals(before, watchCalls.size)
    }

    @Test fun seekDoesNotClaimSkippedAudioAsListeningTime() = runBlocking {
        reporter.observe(sample(0))
        reporter.observe(sample(5_000))
        reporter.observe(sample(90_000, 10_000))
        reporter.observe(sample(95_000, 15_000))
        assertEquals(1, playCalls.size)
        // The jump from 5s to 90s is not an 85-second listening session.
        assertTrue(watchCalls.all { it.end - it.start < 20.0 })
    }

    @Test fun noTrackingUrlNeverInventsAHistoryEvent() = runBlocking {
        reporter.observe(sample(0, urls = null))
        reporter.observe(sample(15_000, urls = null))
        reporter.observe(sample(30_000, urls = null))
        assertTrue(playCalls.isEmpty())
        assertTrue(watchCalls.isEmpty())
    }
}
