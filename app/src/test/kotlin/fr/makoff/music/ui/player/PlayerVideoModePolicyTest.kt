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

    @Test fun collapsedPlayerNeverShowsVideoModeControls() {
        assertFalse(PlayerVideoModePolicy.showSelector(true, false, true))
    }
}
