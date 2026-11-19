package fr.makoff.music.downloads

import org.junit.Assert.assertEquals
import org.junit.Test

/** Only native M4A (AAC) and Ogg Opus are offered to new downloads. */
class DownloadAudioFormatSelectorTest {
    @Test fun defaultIsBestM4aAndLegacyPreferencesMigrate() {
        assertEquals(DownloadAudioFormat.M4A, DownloadAudioFormat.parse(null))
        assertEquals(DownloadAudioFormat.M4A, DownloadAudioFormat.parse("BEST"))
        assertEquals(DownloadAudioFormat.M4A, DownloadAudioFormat.parse("FLAC"))
        assertEquals(DownloadAudioFormat.M4A, DownloadAudioFormat.parse("AAC"))
        assertEquals(DownloadAudioFormat.M4A, DownloadAudioFormat.parse("unknown"))
        assertEquals(DownloadAudioFormat.OPUS, DownloadAudioFormat.parse("OPUS"))
        assertEquals(listOf(DownloadAudioFormat.M4A, DownloadAudioFormat.OPUS), DownloadAudioFormat.portableChoices)
    }

    @Test fun originalBestCodecIsRequestedRegardlessOfLegacyLowQuality() {
        for (low in listOf(false, true)) {
            assertEquals("bestaudio[ext=m4a]",
                selectYtDlpAudioFormat(DownloadAudioFormat.M4A, low, false))
            assertEquals("bestaudio[acodec=opus]",
                selectYtDlpAudioFormat(DownloadAudioFormat.OPUS, low, false))
        }
    }

    @Test fun localFixtureInputRemainsReadableByYtDlp() {
        assertEquals("best", selectYtDlpAudioFormat(DownloadAudioFormat.M4A, false, true))
        assertEquals("best", selectYtDlpAudioFormat(DownloadAudioFormat.OPUS, false, true))
    }
}
