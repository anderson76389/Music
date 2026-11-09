package fr.makoff.music.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundProofPolicyTest {
    @Test fun normalizesArtistAndTopicChannelCredits() {
        assertEquals("joji", SoundProofPolicy.normalize("  Joji - Topic "))
        assertEquals("honey-&-buck", SoundProofPolicy.normalize("Honey & Buck"))
        assertEquals("test-artist", SoundProofPolicy.normalize(" TEST   ARTIST "))
    }

    @Test fun insufficientOrHumanMajorityIsNeverSkipped() {
        assertFalse(SoundProofPolicy.isReportedAi(0, 0))
        assertFalse(SoundProofPolicy.isReportedAi(4, 1))
        assertFalse(SoundProofPolicy.isReportedAi(5, 0))
        assertTrue(SoundProofPolicy.isReportedAi(1, 1))
        assertTrue(SoundProofPolicy.isReportedAi(0, 3))
    }

    @Test fun preventsInfiniteSkipLoopWhenEveryItemIsReportedAi() {
        assertFalse(SoundProofPolicy.fullQueueAlreadySkipped(0, 3))
        assertFalse(SoundProofPolicy.fullQueueAlreadySkipped(2, 3))
        assertTrue(SoundProofPolicy.fullQueueAlreadySkipped(3, 3))
        assertTrue(SoundProofPolicy.fullQueueAlreadySkipped(7, 3))
        assertFalse(SoundProofPolicy.fullQueueAlreadySkipped(0, 0))
    }

    @Test fun unknownOrUnvotedArtistsAreNeverAssumedAi() {
        val votes = mapOf(
            "unvoted" to (0 to 0),
            "community-ai" to (1 to 6),
            "human" to (40 to 0),
        )
        assertFalse(SoundProofPolicy.isAnyArtistReportedAi(listOf("Unvoted"), votes))
        assertFalse(SoundProofPolicy.isAnyArtistReportedAi(listOf("Unlisted"), votes))
        assertTrue(SoundProofPolicy.isAnyArtistReportedAi(listOf("Community AI - Topic"), votes))
        assertFalse(SoundProofPolicy.isAnyArtistReportedAi(listOf("Human"), votes))
    }

    @Test fun matchesCreditsAndCollaborationsWithoutGuessingUnknownArtists() {
        val votes = mapOf(
            "ai-performer" to (0 to 2),
            "real-artist" to (6 to 0),
            "honey-&-buck" to (10 to 0),
        )
        assertTrue(SoundProofPolicy.isAnyArtistReportedAi(listOf("Real Artist feat. AI Performer"), votes))
        assertFalse(SoundProofPolicy.isAnyArtistReportedAi(listOf("Real Artist"), votes))
        assertFalse(SoundProofPolicy.isAnyArtistReportedAi(listOf("Unlisted Artist"), votes))
        assertFalse(SoundProofPolicy.isAnyArtistReportedAi(listOf("Honey & Buck"), votes))
        assertFalse(SoundProofPolicy.isAnyArtistReportedAi(emptyList(), votes))
    }
}
