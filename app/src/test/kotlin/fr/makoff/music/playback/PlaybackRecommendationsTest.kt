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

    @Test fun suggestionsAreSplitBetweenAutoplayAndVisibleSimilarSection() {
        val singles = listOf(item("TRACK000001"))
        val (singleQueued, singleSimilar) = PlaybackRecommendations.splitSuggestions(singles)
        assertEquals(1, singleQueued.size)
        assertEquals(0, singleSimilar.size)
        val many = (1..7).map { item("MUSIC%06d".format(it)) }
        val (queued, visible) = PlaybackRecommendations.splitSuggestions(many)
        assertEquals(6, queued.size)
        assertEquals(1, visible.size)
        assertEquals(emptySet<String>(), queued.map { it.mediaId }.toSet().intersect(visible.map { it.mediaId }.toSet()))
    }

    @Test fun largerRecommendationPoolKeepsTwelveReadyWithoutDuplicates() {
        val many = (1..30).map { item("MUSIC%06d".format(it)) }
        val (queued, visible) = PlaybackRecommendations.splitSuggestions(many)
        assertEquals(12, queued.size)
        assertEquals(18, visible.size)
        assertEquals(many, queued + visible)
    }

    @Test fun noSuggestionsDoesNotReplaceTheCurrentQueue() {
        assertEquals(emptyList<MediaItem>(), PlaybackRecommendations.distinctCandidates("CURRENT1234", emptyList()))
    }
}
