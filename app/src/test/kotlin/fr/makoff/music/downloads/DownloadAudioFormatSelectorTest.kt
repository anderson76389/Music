package fr.makoff.music.downloads

import org.junit.Assert.assertEquals
import org.junit.Test

/** Source selection is independent of the player's streaming-quality setting. */
class DownloadAudioFormatSelectorTest {
    @Test fun defaultAndBestAlwaysRequestHighestAvailableYouTubeAudio() {
        assertEquals(DownloadAudioFormat.BEST, DownloadAudioFormat.parse(null))
        assertEquals("bestaudio", selectYtDlpAudioFormat(DownloadAudioFormat.BEST, lowQuality = false, isLocalSource = false))
        assertEquals("bestaudio", selectYtDlpAudioFormat(DownloadAudioFormat.FLAC, lowQuality = false, isLocalSource = false))
    }

    @Test fun originalM4aIsRequiredInsteadOfLossyTranscoding() {
        assertEquals("bestaudio[ext=m4a]", selectYtDlpAudioFormat(DownloadAudioFormat.M4A, lowQuality = false, isLocalSource = false))
        assertEquals("bestaudio[ext=m4a]", selectYtDlpAudioFormat(DownloadAudioFormat.M4A, lowQuality = true, isLocalSource = false))
    }

    @Test fun lowQualityMustBeRequestedExplicitly() {
        assertEquals("worstaudio", selectYtDlpAudioFormat(DownloadAudioFormat.BEST, lowQuality = true, isLocalSource = false))
        assertEquals("best", selectYtDlpAudioFormat(DownloadAudioFormat.BEST, lowQuality = true, isLocalSource = true))
        assertEquals("best", selectYtDlpAudioFormat(DownloadAudioFormat.FLAC, lowQuality = false, isLocalSource = true))
    }
}
