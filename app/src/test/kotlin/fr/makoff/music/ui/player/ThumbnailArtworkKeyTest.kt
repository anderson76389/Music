package fr.makoff.music.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ThumbnailArtworkKeyTest {
    @Test fun changingTrackInvalidatesEveryOldArtworkSlot() {
        val old = thumbnailArtworkKey("oldTrack", 1, "oldTrack")
        val previousAsNeighbor = thumbnailArtworkKey("newTrack", 0, "oldTrack")
        val newCurrent = thumbnailArtworkKey("newTrack", 1, "newTrack")
        assertNotEquals(old, previousAsNeighbor)
        assertNotEquals(old, newCurrent)
        assertEquals(newCurrent, thumbnailArtworkKey("newTrack", 1, "newTrack"))
    }

    @Test fun repeatedQueueEntriesKeepSeparateLazyGridIdentities() {
        assertNotEquals(
            thumbnailArtworkKey("current", 0, "sameTrack"),
            thumbnailArtworkKey("current", 1, "sameTrack"),
        )
    }
}
