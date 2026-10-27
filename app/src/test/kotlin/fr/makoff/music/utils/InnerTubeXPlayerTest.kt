package fr.makoff.music.utils

import fr.makoff.music.innertubex.extraction.PlayerConfig
import fr.makoff.music.innertubex.extraction.YtConfigParser
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InnerTubeXPlayerTest {
    @Test
    fun `high quality never downgrades on a metered connection`() {
        val high = fr.makoff.music.constants.AudioQuality.HIGH
        val low = fr.makoff.music.constants.AudioQuality.LOW
        val auto = fr.makoff.music.constants.AudioQuality.AUTO
        InnerTubeXPlayer.run {
            for (metered in listOf(false, true)) {
                assertEquals(fr.makoff.music.innertubex.extraction.AudioQuality.HIGH, high.toInnerTubeX(metered))
                assertEquals(fr.makoff.music.innertubex.extraction.AudioQuality.LOW, low.toInnerTubeX(metered))
            }
            assertEquals(fr.makoff.music.innertubex.extraction.AudioQuality.LOW, auto.toInnerTubeX(true))
            assertEquals(fr.makoff.music.innertubex.extraction.AudioQuality.AUTO, auto.toInnerTubeX(false))
        }
    }

    @After
    fun tearDown() {
        InnerTubeXPlayer.clearStreamClientFailures()
    }

    @Test
    fun `failed stream clients accumulate per video and expire`() {
        InnerTubeXPlayer.markStreamClientFailed("song", "VISIONOS", nowMs = 1_000L)
        InnerTubeXPlayer.markStreamClientFailed("song", "WEB_REMIX", nowMs = 2_000L)

        assertEquals(
            setOf("VISIONOS", "WEB_REMIX"),
            InnerTubeXPlayer.failedStreamClients("song", nowMs = 2_000L),
        )
        assertTrue(InnerTubeXPlayer.failedStreamClients("other", nowMs = 2_000L).isEmpty())
        assertTrue(InnerTubeXPlayer.failedStreamClients("song", nowMs = 302_000L).isEmpty())
    }

    @Test
    fun `failed watch page falls back to anonymous embedded config`() =
        runBlocking {
            val expected = PlayerConfig("embedded", 123, null, null)
            var embeddedUsesLogin = true
            val parser =
                object : YtConfigParser {
                    override suspend fun fetchConfig(videoId: String, useLoginCookies: Boolean): PlayerConfig =
                        error("HTTP 302")

                    override suspend fun fetchEmbeddedConfig(videoId: String, useLoginCookies: Boolean): PlayerConfig {
                        embeddedUsesLogin = useLoginCookies
                        return expected
                    }
                }

            assertEquals(expected, InnerTubeXPlayer.run { parser.withEmbeddedConfigFallback() }.fetchConfig("song", true))
            assertEquals(false, embeddedUsesLogin)
        }
}
