package fr.makoff.music.downloads

import org.junit.Assert.assertEquals
import org.junit.Test

/** Native AAC/M4A and native Opus remain separate, lossless-in-container exports. */
class DownloadAudioFormatSelectorTest {
    @Test fun m4aRemainsDefaultWhileOpusIsExplicitAndOldPreferencesMigrate() {
        assertEquals(DownloadAudioFormat.M4A, DownloadAudioFormat.parse(null))
        assertEquals(DownloadAudioFormat.M4A, DownloadAudioFormat.parse("BEST"))
        assertEquals(DownloadAudioFormat.M4A, DownloadAudioFormat.parse("FLAC"))
        assertEquals(DownloadAudioFormat.M4A, DownloadAudioFormat.parse("AAC"))
        assertEquals(DownloadAudioFormat.OPUS, DownloadAudioFormat.parse("OPUS"))
        assertEquals(listOf(DownloadAudioFormat.M4A, DownloadAudioFormat.OPUS), DownloadAudioFormat.portableChoices)
    }

    @Test fun eachFormatRequestsItsBestNativeCodecWithoutLowerQualityFallback() {
        for (lowQuality in listOf(false, true)) {
            assertEquals("bestaudio[ext=m4a]",
                selectYtDlpAudioFormat(DownloadAudioFormat.M4A, lowQuality, false))
            assertEquals("bestaudio[acodec=opus]",
                selectYtDlpAudioFormat(DownloadAudioFormat.OPUS, lowQuality, false))
        }
    }

    @Test fun existingLocalAudioFixturesRemainReadableByYtDlp() {
        assertEquals("best", selectYtDlpAudioFormat(DownloadAudioFormat.M4A, false, true))
        assertEquals("best", selectYtDlpAudioFormat(DownloadAudioFormat.OPUS, false, true))
    }
}
