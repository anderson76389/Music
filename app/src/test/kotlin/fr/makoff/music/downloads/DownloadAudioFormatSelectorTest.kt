package fr.makoff.music.downloads

import org.junit.Assert.assertEquals
import org.junit.Test

/** Download format: portable, highest-quality original AAC in an M4A container. */
class DownloadAudioFormatSelectorTest {
    @Test fun defaultAndLegacyFormatsMigrateToM4a() {
        for (value in listOf(null, "M4A", "OPUS", "AAC", "")) {
            assertEquals(DownloadAudioFormat.M4A, DownloadAudioFormat.parse(value))
        }
        // An existing offline request stores its original codec/format
        // explicitly; it must keep playing without migration or network.
        assertEquals(DownloadAudioFormat.BEST, DownloadAudioFormat.parse("BEST"))
        assertEquals(DownloadAudioFormat.FLAC, DownloadAudioFormat.parse("FLAC"))
        assertEquals(listOf(DownloadAudioFormat.M4A), DownloadAudioFormat.userChoices)
    }

    @Test fun highestNativeM4aIsAlwaysSelectedNotOpus() {
        assertEquals(
            "bestaudio[ext=m4a]",
            selectYtDlpAudioFormat(DownloadAudioFormat.M4A, lowQuality = false, isLocalSource = false),
        )
        // Old "low quality" flags must never downgrade a portable download.
        assertEquals(
            "bestaudio[ext=m4a]",
            selectYtDlpAudioFormat(DownloadAudioFormat.M4A, lowQuality = true, isLocalSource = false),
        )
    }

    @Test fun localFixtureDoesNotRequireYoutubeFormatMetadata() {
        assertEquals("best", selectYtDlpAudioFormat(
            DownloadAudioFormat.M4A, lowQuality = false, isLocalSource = true,
        ))
    }
}
