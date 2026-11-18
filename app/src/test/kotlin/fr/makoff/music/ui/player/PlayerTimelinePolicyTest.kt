package fr.makoff.music.ui.player

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Regression tests for 0:00 jumps, stale last-track timers and Son/Vidéo switches. */
class PlayerTimelinePolicyTest {
    @Test fun timerNeverDisplaysTheOutgoingSongPosition() {
        assertEquals(0L, PlayerTimelinePolicy.positionMs("new-song", "old-song", 170_000L))
        assertEquals(0L, PlayerTimelinePolicy.positionMs("new-song", null, 170_000L))
        assertEquals(15_000L, PlayerTimelinePolicy.positionMs("new-song", "new-song", 15_000L))
    }

    @Test fun durationDoesNotLeakBetweenSongs() {
        assertEquals(181_000L,
            PlayerTimelinePolicy.durationMs("next", "previous", 320_000L, 181L))
        assertEquals(0L,
            PlayerTimelinePolicy.durationMs("next", "previous", 320_000L, null))
        assertEquals(222_123L,
            PlayerTimelinePolicy.durationMs("next", "next", 222_123L, 181L))
    }

    @Test fun unknownDurationNeverCreatesNegativeSliderRangeOrTimer() {
        assertEquals(0L, PlayerTimelinePolicy.durationMs("id", "id", C.TIME_UNSET, null))
        assertEquals(0L, PlayerTimelinePolicy.durationMs("id", "id", -1L, 0L))
        assertEquals(0L, PlayerTimelinePolicy.displayPosition(-9L, 0L))
        assertNull(PlayerTimelinePolicy.seekPosition(1_000L, 0L))
    }

    @Test fun metadataUsesSecondsButPlayerUsesMilliseconds() {
        assertEquals(243_000L, PlayerTimelinePolicy.durationMs("song", null, C.TIME_UNSET, 243L))
        assertEquals(243_500L, PlayerTimelinePolicy.durationMs("song", "song", 243_500L, 243L))
    }

    @Test fun seeksAndRenderedTimeAreBoundedToKnownSongDuration() {
        assertEquals(90_000L, PlayerTimelinePolicy.displayPosition(123_000L, 90_000L))
        assertEquals(90_000L, PlayerTimelinePolicy.seekPosition(123_000L, 90_000L))
        assertEquals(0L, PlayerTimelinePolicy.seekPosition(-2_000L, 90_000L))
        assertEquals(0L, PlayerTimelinePolicy.positionMs("a", "a", -10L))
    }

    @Test fun unavailableDurationCannotOverflowWhenConvertedToMilliseconds() {
        assertEquals(0L, PlayerTimelinePolicy.durationMs("id", "id", C.TIME_UNSET, Long.MAX_VALUE))
    }
}
