package fr.makoff.music.playback

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SoundProofInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun cachedRealSchemaMatchesYouTubeMusicDisplayNames() = runBlocking {
        val directory = context.filesDir.resolve("soundproof")
        directory.mkdirs()
        val cache = File(directory, "artists.json")
        val saved = if (cache.isFile) cache.readBytes() else null
        try {
            // Same fields as NuclearBlox/SoundProof-Dumps/data/artists.json.
            // The ID differs from the display name on purpose: the previous
            // implementation indexed the slug only, missing these credits.
            cache.writeText("""
                [
                  {"id":"test-ai-slug","name":"Artist with AI","human_votes":1,"ai_votes":11,"verified":true},
                  {"id":"test-human-slug","name":"Real Human","human_votes":20,"ai_votes":0,"verified":true},
                  {"id":"no-votes-slug","name":"Unknown Votes","human_votes":0,"ai_votes":0}
                ]
            """.trimIndent())
            val repository = SoundProofRepository(context)
            assertTrue(repository.shouldSkipArtist(listOf("Artist with AI")))
            assertTrue(repository.shouldSkipArtist(listOf("Artist with AI - Topic")))
            assertTrue(repository.shouldSkipArtist(listOf("Real Human feat. Artist with AI")))
            assertTrue(repository.shouldSkipArtist(listOf("test-ai-slug")))
            assertFalse(repository.shouldSkipArtist(listOf("Real Human")))
            assertFalse(repository.shouldSkipArtist(listOf("Unknown Votes")))
            assertFalse(repository.shouldSkipArtist(listOf("An Unknown YouTube Artist")))
            assertTrue(repository.cachedArtistCount() >= 3)
            println("SOUNDPROOF_CACHE_OK: actual parser, display names, slugs and votes")
        } finally {
            if (saved == null) cache.delete() else cache.writeBytes(saved)
        }
    }

    @Test fun manualRefreshRechecksChangedVotesEvenWithSameFileTimestamp() = runBlocking {
        val directory = context.filesDir.resolve("soundproof").apply { mkdirs() }
        val cache = File(directory, "artists.json")
        val before = cache.takeIf { it.isFile }?.readBytes()
        val originalModified = cache.lastModified()
        try {
            val first = """[{"id":"changed-artist","name":"Changed Artist","human_votes":0,"ai_votes":9}]"""
            val second = """[{"id":"changed-artist","name":"Changed Artist","human_votes":9,"ai_votes":0}]"""
            cache.writeText(first)
            val repository = SoundProofRepository(context)
            assertTrue(repository.shouldSkipArtist(listOf("Changed Artist")))
            val stamp = cache.lastModified()
            cache.writeText(second)
            // Android filesystem timestamps can have coarse granularity.
            cache.setLastModified(stamp)
            repository.invalidateCachedIndex()
            assertFalse(repository.shouldSkipArtist(listOf("Changed Artist")))
            println("SOUNDPROOF_MANUAL_REFRESH_RECHECK_OK")
        } finally {
            if (before == null) cache.delete()
            else {
                cache.writeBytes(before)
                cache.setLastModified(originalModified)
            }
        }
    }

    /**
     * Live smoke check. Run separately from deterministic CI: the network
     * may block raw.githubusercontent.com even with correct app behavior.
     */
    @Test fun downloadsCommunityDumpAndParsesArtistVotes() = runBlocking {
        val repository = SoundProofRepository(context)
        assertTrue("Could not download or parse the actual SoundProof data", repository.refreshNow())
        val count = repository.cachedArtistCount()
        assertTrue("SoundProof dump unexpectedly small: $count", count >= 100)
        println("SOUNDPROOF_LIVE_OK: parsed $count artist lookup keys from real GitHub dataset")
    }
}
