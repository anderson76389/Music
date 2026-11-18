package fr.makoff.music.downloads

import org.junit.Assert.assertEquals
import org.junit.Test

/** Source selection is independent of the player's streaming-quality setting. */
class DownloadAudioFormatSelectorTest {
    @Test fun defaultAndBestAlwaysRequestHighestAvailableYouTubeAudio() {
        assertEquals(DownloadAudioFormat.M4A, DownloadAudioFormat.parse(null))
        assertEquals(listOf(DownloadAudioFormat.M4A, DownloadAudioFormat.AAC), DownloadAudioFormat.portableChoices)
        assertEquals("bestaudio", selectYtDlpAudioFormat(DownloadAudioFormat.BEST, lowQuality = false, isLocalSource = false))
        assertEquals("bestaudio", selectYtDlpAudioFormat(DownloadAudioFormat.FLAC, lowQuality = false, isLocalSource = false))
    }

    @Test fun originalM4aIsRequiredInsteadOfLossyTranscoding() {
        assertEquals("bestaudio[ext=m4a]/bestaudio", selectYtDlpAudioFormat(DownloadAudioFormat.M4A, lowQuality = false, isLocalSource = false))
        assertEquals("bestaudio[ext=m4a]/bestaudio", selectYtDlpAudioFormat(DownloadAudioFormat.M4A, lowQuality = true, isLocalSource = false))
    }

    @Test fun portableAacAlwaysPrioritizesNativeM4aAndCanUseHighestSourceIfNeeded() {
        assertEquals("bestaudio[ext=m4a]/bestaudio",
            selectYtDlpAudioFormat(DownloadAudioFormat.AAC, lowQuality = false, isLocalSource = false))
        assertEquals("bestaudio[ext=m4a]/bestaudio",
            selectYtDlpAudioFormat(DownloadAudioFormat.AAC, lowQuality = true, isLocalSource = false))
        assertEquals("best", selectYtDlpAudioFormat(DownloadAudioFormat.AAC, false, true))
        assertEquals(DownloadAudioFormat.BEST, DownloadAudioFormat.parse("BEST"))
        assertEquals(DownloadAudioFormat.FLAC, DownloadAudioFormat.parse("FLAC"))
    }

    @Test fun lowQualityMustBeRequestedExplicitly() {
        assertEquals("worstaudio", selectYtDlpAudioFormat(DownloadAudioFormat.BEST, lowQuality = true, isLocalSource = false))
        assertEquals("best", selectYtDlpAudioFormat(DownloadAudioFormat.BEST, lowQuality = true, isLocalSource = true))
        assertEquals("best", selectYtDlpAudioFormat(DownloadAudioFormat.FLAC, lowQuality = false, isLocalSource = true))
    }
}
