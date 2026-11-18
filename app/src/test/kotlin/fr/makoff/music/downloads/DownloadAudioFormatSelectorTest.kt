package fr.makoff.music.downloads

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadAudioFormatSelectorTest {
    @Test fun onlyM4aAndAacAreSelectable() {
        assertEquals(listOf(DownloadAudioFormat.M4A, DownloadAudioFormat.AAC), DownloadAudioFormat.available)
        assertEquals(DownloadAudioFormat.M4A, DownloadAudioFormat.parse(null))
        assertEquals(DownloadAudioFormat.M4A, DownloadAudioFormat.parse("BEST"))
        assertEquals(DownloadAudioFormat.M4A, DownloadAudioFormat.parse("FLAC"))
        assertEquals(DownloadAudioFormat.AAC, DownloadAudioFormat.parse("AAC"))
    }

    @Test fun maximumNativeAacQualityForBothFormats() {
        for (format in DownloadAudioFormat.available) {
            assertEquals("bestaudio[ext=m4a]", selectYtDlpAudioFormat(format, lowQuality = false, isLocalSource = false))
            // A reduced streaming bitrate must NEVER downgrade exported files.
            assertEquals("bestaudio[ext=m4a]", selectYtDlpAudioFormat(format, lowQuality = true, isLocalSource = false))
        }
    }

    @Test fun oldQueuedExportFormatsAreMigratedToNativeAac() {
        assertEquals("bestaudio[ext=m4a]", selectYtDlpAudioFormat(DownloadAudioFormat.BEST, false, false))
        assertEquals("bestaudio[ext=m4a]", selectYtDlpAudioFormat(DownloadAudioFormat.FLAC, false, false))
    }

    @Test fun conversionOfLocalStagedM4aUsesLocalInput() {
        assertEquals("best", selectYtDlpAudioFormat(DownloadAudioFormat.AAC, false, true))
    }
}
