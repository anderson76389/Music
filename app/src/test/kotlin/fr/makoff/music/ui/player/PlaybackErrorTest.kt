/*
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package fr.makoff.music.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.EOFException
import java.io.IOException

class PlaybackErrorTest {
    @Test
    fun `cause chain keeps each nested playback failure`() {
        val rootCause = EOFException("stream ended")
        val sourceCause = IOException("read failed", rootCause)
        val error = IllegalStateException("playback failed", sourceCause)

        assertEquals(
            listOf(error, sourceCause, rootCause),
            error.causeChain(),
        )
    }

    @Test fun `local parser failures do not require internet`() {
        org.junit.Assert.assertFalse(needsNetworkConnection(
            androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED, emptyList()))
        org.junit.Assert.assertTrue(needsNetworkConnection(
            androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            listOf(java.net.UnknownHostException())))
    }

    @Test
    fun `offline warning replaces misleading stream errors`() {
        assertEquals(
            listOf("Connect to the internet"),
            playbackErrorMessages(
                isOnline = false,
                offlineMessage = "Connect to the internet",
                guidance = null,
                rawErrorMessages = listOf("This track is unavailable"),
            ),
        )
    }
}
