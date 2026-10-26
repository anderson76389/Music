package fr.makoff.music.playback

import androidx.media3.exoplayer.offline.Download
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadUtilTest {
    @Test
    fun `restricted fallback requires a real saved session and a relevant failure`() {
        val age = fr.makoff.music.innertubex.extraction.StreamResolveException(
            fr.makoff.music.innertubex.extraction.StreamResolveException.Reason.AGE_RESTRICTED, "restricted")
        assertFalse(canUseAuthenticatedDownloadFallback(age, ""))
        assertFalse(canUseAuthenticatedDownloadFallback(age, "SAPISID="))
        assertFalse(canUseAuthenticatedDownloadFallback(age, "VISITOR_INFO1_LIVE=visitor"))
        assertTrue(canUseAuthenticatedDownloadFallback(age, "SAPISID=test-session"))
        assertTrue(canUseAuthenticatedDownloadFallback(age, "__Secure-3PAPISID=test-session"))
        assertFalse(canUseAuthenticatedDownloadFallback(java.io.IOException("offline"), "SAPISID=test-session"))
    }

    @Test
    fun `completed downloads are not prepared again`() {
        assertFalse(shouldPrepareDownload(Download.STATE_COMPLETED))
        assertFalse(shouldPrepareDownload(Download.STATE_QUEUED))
        assertFalse(shouldPrepareDownload(Download.STATE_DOWNLOADING))
        assertFalse(shouldPrepareDownload(Download.STATE_RESTARTING))
        assertTrue(shouldPrepareDownload(Download.STATE_FAILED))
        assertTrue(shouldPrepareDownload(null))
    }

    @Test
    fun `downloadArtworkUrls keeps distinct song and album covers`() {
        assertEquals(
            listOf("song-cover", "album-cover"),
            downloadArtworkUrls("song-cover", "album-cover"),
        )
        assertEquals(
            listOf("song-cover"),
            downloadArtworkUrls("song-cover", "song-cover"),
        )
        assertEquals(emptyList<String>(), downloadArtworkUrls("", null))
    }
}
