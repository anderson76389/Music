package fr.makoff.music.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerLayoutPolicyTest {
    @Test fun foldedCoverAndPortraitRemainVertical() {
        assertFalse(PlayerLayoutPolicy.useTwoPane(false, 400, 900))
        assertFalse(PlayerLayoutPolicy.useTwoPane(false, 599, 900))
        assertFalse(PlayerLayoutPolicy.useTwoPane(false, 700, 550))
        assertEquals(96, PlayerLayoutPolicy.artworkBlurRadiusDp(400, 900))
    }

    @Test fun unfoldedFoldablesAndTabletsUseWideLayout() {
        assertTrue(PlayerLayoutPolicy.useTwoPane(false, 730, 670))
        assertTrue(PlayerLayoutPolicy.useTwoPane(false, 900, 1200))
        assertEquals(64, PlayerLayoutPolicy.artworkBlurRadiusDp(730, 670))
    }

    @Test fun landscapePhonesPreserveExistingLayout() {
        assertTrue(PlayerLayoutPolicy.useTwoPane(true, 900, 420))
        assertFalse(PlayerLayoutPolicy.useTwoPane(false, 420, 900))
    }
}
