package fr.makoff.music.ui.player

import androidx.media3.common.C

/**
 * The audio service owns the song clock, even when a muted video decoder is
 * visible. A stale ExoPlayer duration/position from the outgoing track must
 * never be shown under the incoming track's title and artwork.
 */
internal object PlayerTimelinePolicy {
    fun positionMs(
        currentId: String?,
        playerId: String?,
        playerPositionMs: Long,
    ): Long =
        if (currentId != null && currentId == playerId) {
            playerPositionMs.coerceAtLeast(0L)
        } else 0L

    fun durationMs(
        currentId: String?,
        playerId: String?,
        playerDurationMs: Long,
        metadataDurationSeconds: Long?,
    ): Long {
        val actual = playerDurationMs.takeIf {
            currentId != null && currentId == playerId && it != C.TIME_UNSET && it > 0L
        }
        // MediaMetadata.duration is in seconds. The extractor/player reports
        // milliseconds. Never mix units or turn TIME_UNSET into a negative range.
        val metadata = metadataDurationSeconds?.takeIf { it > 0L && it <= Long.MAX_VALUE / 1_000L }
            ?.times(1_000L)
        return actual ?: metadata ?: 0L
    }

    fun displayPosition(positionMs: Long, durationMs: Long): Long =
        if (durationMs > 0L) positionMs.coerceIn(0L, durationMs)
        else positionMs.coerceAtLeast(0L)

    fun seekPosition(positionMs: Long, durationMs: Long): Long? =
        durationMs.takeIf { it > 0L }?.let { positionMs.coerceIn(0L, it) }
}
