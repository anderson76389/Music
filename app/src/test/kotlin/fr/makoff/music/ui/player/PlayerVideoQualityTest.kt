package fr.makoff.music.ui.player

import fr.makoff.music.constants.PlayerVideoQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionParameters
import org.junit.Test

class PlayerVideoQualityTest {
    @Test fun availableVideoResolutionsAreOrderedAndCapped() {
        assertEquals(2160, PlayerVideoQuality.BEST.maxHeight)
        assertEquals(1080, PlayerVideoQuality.FULL_HD.maxHeight)
        assertEquals(720, PlayerVideoQuality.HD.maxHeight)
        assertEquals(480, PlayerVideoQuality.ECONOMY.maxHeight)
    }

    @Test fun bestVideoQualityDoesNotGetCappedBySmallPrebufferViewport() {
        val quality = highestAvailableVideoTracks(
            TrackSelectionParameters.DEFAULT,
            PlayerVideoQuality.BEST.maxHeight,
        )
        assertEquals(2160, quality.maxVideoHeight)
        assertEquals(Int.MAX_VALUE, quality.maxVideoWidth)
        assertEquals(Int.MAX_VALUE, quality.viewportWidth)
        assertEquals(Int.MAX_VALUE, quality.viewportHeight)
        assertTrue(quality.forceHighestSupportedBitrate)
        assertTrue(C.TRACK_TYPE_AUDIO in quality.disabledTrackTypes)
    }

    @Test fun chosenQualityCapsOnlyVideoWithoutChangingAudioSettings() {
        for (option in PlayerVideoQuality.entries) {
            val params = highestAvailableVideoTracks(TrackSelectionParameters.DEFAULT, option.maxHeight)
            assertEquals(option.maxHeight, params.maxVideoHeight)
            assertTrue(params.forceHighestSupportedBitrate)
        }
    }
}
