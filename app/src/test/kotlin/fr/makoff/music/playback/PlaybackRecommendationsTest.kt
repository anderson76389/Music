package fr.makoff.music.playback

import androidx.media3.common.MediaItem
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackRecommendationsTest {
    private fun item(id: String) = MediaItem.Builder().setMediaId(id).build()

    @Test fun youtubeMusicSuggestionsRemoveTheCurrentSongAndDuplicates() {
        val input = listOf(item("CURRENT1234"), item("NEXTSONG001"), item("NEXTSONG001"), item("NEXTSONG002"))
        assertEquals(
            listOf("NEXTSONG001", "NEXTSONG002"),
            PlaybackRecommendations.distinctCandidates("CURRENT1234", input).map { it.mediaId },
        )
    }

    @Test fun alreadyQueuedSongsAreNeverAppendedTwice() {
        val input = listOf(item("QUEUED00001"), item("SUGGEST0002"), item("SUGGEST0002"))
        assertEquals(
            listOf("SUGGEST0002"),
            PlaybackRecommendations.distinctCandidates("CURRENT1234", input, setOf("QUEUED00001")).map { it.mediaId },
        )
    }

    @Test fun noSuggestionsDoesNotReplaceTheCurrentQueue() {
        assertEquals(emptyList<MediaItem>(), PlaybackRecommendations.distinctCandidates("CURRENT1234", emptyList()))
    }
}
