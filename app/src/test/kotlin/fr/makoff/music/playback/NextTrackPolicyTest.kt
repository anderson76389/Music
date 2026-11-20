package fr.makoff.music.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NextTrackPolicyTest {
    @Test fun explicitSuccessorAlwaysAllowsNext() {
        assertTrue(NextTrackPolicy.canAdvance(true, null))
        assertTrue(NextTrackPolicy.canAdvance(true, "not-a-video-id"))
    }

    @Test fun oneSongQueueCanRequestItsNextTrackOnDemand() {
        assertTrue(NextTrackPolicy.canAdvance(false, "BaW_jenozKc"))
        assertTrue(NextTrackPolicy.canRequestNextSong("BaW_jenozKc"))
    }

    @Test fun unavailableOrNonYouTubeEntriesCannotInventANextTrack() {
        assertFalse(NextTrackPolicy.canAdvance(false, null))
        assertFalse(NextTrackPolicy.canAdvance(false, ""))
        assertFalse(NextTrackPolicy.canAdvance(false, "not-a-video-id"))
        assertFalse(NextTrackPolicy.canRequestNextSong("../some/local/path.mp3"))
    }
}
