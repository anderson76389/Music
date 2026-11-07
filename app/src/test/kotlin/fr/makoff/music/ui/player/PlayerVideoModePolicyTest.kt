package fr.makoff.music.ui.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerVideoModePolicyTest {
    @Test fun modeIsEnabledByDefaultOnlyForPlayableVideos() {
        assertTrue(PlayerVideoModePolicy.showSelector(true, true, true))
        assertFalse(PlayerVideoModePolicy.showSelector(true, true, false))
    }

    @Test fun disablingModeAlwaysRestoresTheClassicHeader() {
        assertFalse(PlayerVideoModePolicy.showSelector(false, true, true))
        assertFalse(PlayerVideoModePolicy.showSelector(false, true, false))
    }

    @Test fun missingPairFallsBackToTheSameYouTubeItemOnlyAfterLookup() {
        val id = "abcdefghijk"
        assertTrue(PlayerVideoModePolicy.candidateVideoId(id, true, false, false, null, true) == id)
        assertFalse(PlayerVideoModePolicy.candidateVideoId(id, true, false, false, null, false) != null)
        assertTrue(PlayerVideoModePolicy.candidateVideoId(id, true, false, true, null, false) == id)
        assertTrue(PlayerVideoModePolicy.candidateVideoId(id, true, false, false, "lmnopqrstuv", true) == "lmnopqrstuv")
        assertTrue(PlayerVideoModePolicy.candidateVideoId(id, true, true, true, null, true) == null)
        assertTrue(PlayerVideoModePolicy.candidateVideoId(null, false, false, false, null, true) == null)
    }

    @Test fun selectorNoLongerRequiresPrebufferingOnceStreamIsAvailable() {
        assertTrue(PlayerVideoModePolicy.showSelector(enabled = true, expanded = true, hasPlayableClip = true))
    }

    @Test fun officialClipShowsButtonsBeforeItsStreamFinishesLoading() {
        assertTrue(PlayerVideoModePolicy.clipSelectable(true, "abcdefghijk", null, false, true, false))
        assertTrue(PlayerVideoModePolicy.clipSelectable(true, "abcdefghijk", null, true, true, true))
    }

    @Test fun missingOrUnplayableClipsDoNotDisplayFakeButtons() {
        assertFalse(PlayerVideoModePolicy.clipSelectable(true, null, null, false, false, false))
        assertFalse(PlayerVideoModePolicy.clipSelectable(true, "abcdefghijk", null, false, false, false))
        assertFalse(PlayerVideoModePolicy.clipSelectable(true, "abcdefghijk", null, false, true, true))
        assertFalse(PlayerVideoModePolicy.clipSelectable(true, "abcdefghijk", "abcdefghijk", true, true, true))
        assertFalse(PlayerVideoModePolicy.clipSelectable(false, "abcdefghijk", null, true, true, true))
    }

    @Test fun screenStaysAwakeOnlyForExpandedVideoNotAudioOrCast() {
        assertTrue(PlayerVideoModePolicy.videoModeActive(true, true, true, false))
        assertFalse(PlayerVideoModePolicy.videoModeActive(false, true, true, false))
        assertFalse(PlayerVideoModePolicy.videoModeActive(true, false, true, false))
        assertFalse(PlayerVideoModePolicy.videoModeActive(true, true, false, false))
        assertFalse(PlayerVideoModePolicy.videoModeActive(true, true, true, true))
    }

    @Test fun collapsedPlayerNeverShowsVideoModeControls() {
        assertFalse(PlayerVideoModePolicy.showSelector(true, false, true))
    }
}
