package fr.makoff.music.ui.player

import fr.makoff.music.constants.PlayerVideoQuality
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerVideoQualityTest {
    @Test fun availableVideoResolutionsAreOrderedAndCapped() {
        assertEquals(2160, PlayerVideoQuality.BEST.maxHeight)
        assertEquals(1080, PlayerVideoQuality.FULL_HD.maxHeight)
        assertEquals(720, PlayerVideoQuality.HD.maxHeight)
        assertEquals(480, PlayerVideoQuality.ECONOMY.maxHeight)
    }
}
