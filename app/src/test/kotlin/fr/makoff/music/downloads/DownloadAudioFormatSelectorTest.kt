package fr.makoff.music.downloads

import org.junit.Assert.assertEquals
import org.junit.Test

/** Only original AAC-in-M4A is selected; raw AAC is a container remux. */
class DownloadAudioFormatSelectorTest {
    @Test fun defaultIsHighestCompatibleM4aAndOldPreferencesAreMigrated() {
        assertEquals(DownloadAudioFormat.M4A, DownloadAudioFormat.parse(null))
        assertEquals(DownloadAudioFormat.M4A, DownloadAudioFormat.parse("BEST"))
        assertEquals(DownloadAudioFormat.M4A, DownloadAudioFormat.parse("FLAC"))
        assertEquals(DownloadAudioFormat.M4A, DownloadAudioFormat.parse("OPUS"))
        assertEquals(DownloadAudioFormat.AAC, DownloadAudioFormat.parse("AAC"))
        assertEquals(listOf(DownloadAudioFormat.M4A, DownloadAudioFormat.AAC), DownloadAudioFormat.portableChoices)
    }

    @Test fun bothFormatsRequestBestOriginalAacSourceEvenWithOldLowQualityFlag() {
        for (format in DownloadAudioFormat.entries) {
            assertEquals("bestaudio[ext=m4a]",
                selectYtDlpAudioFormat(format, lowQuality = false, isLocalSource = false))
            assertEquals("bestaudio[ext=m4a]",
                selectYtDlpAudioFormat(format, lowQuality = true, isLocalSource = false))
        }
    }

    @Test fun localFixtureInputRemainsReadableByYtDlp() {
        assertEquals("best", selectYtDlpAudioFormat(DownloadAudioFormat.M4A, false, true))
        assertEquals("best", selectYtDlpAudioFormat(DownloadAudioFormat.AAC, false, true))
    }
}
