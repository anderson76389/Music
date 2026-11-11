package fr.makoff.music.ui.player

import androidx.media3.common.Player

/** Owns the muted video clock independently of portrait/landscape surfaces. */
internal class PlayerVideoSynchronizer(
    private val audio: Player,
    private val video: Player,
    private val audioId: String,
    private val nowMs: () -> Long,
) : Player.Listener {
    private var active = false
    private var lastSeekMs: Long? = null

    fun attach() {
        audio.addListener(this)
        video.volume = 0f
        video.playWhenReady = false
    }

    fun setActive(value: Boolean) {
        if (active == value) return
        active = value
        synchronize(alignPosition = value)
    }

    fun tick() = synchronize(alignPosition = false)

    override fun onEvents(player: Player, events: Player.Events) {
        synchronize(alignPosition = false)
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int,
    ) {
        // Covers explicit seeks and repeat-one returning to the same media ID.
        if (active && audio.currentMediaItem?.mediaId == audioId &&
            PlayerVideoModePolicy.shouldSeekOnVideoSelection(
                newPosition.positionMs, video.currentPosition, toleranceMs = 500L,
            )
        ) {
            seek(newPosition.positionMs)
        }
    }

    private fun synchronize(alignPosition: Boolean) {
        if (!active || audio.currentMediaItem?.mediaId != audioId) {
            video.playWhenReady = false
            return
        }
        if (video.playbackParameters != audio.playbackParameters) {
            video.playbackParameters = audio.playbackParameters
        }
        val now = nowMs()
        val shouldSeek = if (alignPosition) {
            PlayerVideoModePolicy.shouldSeekOnVideoSelection(audio.currentPosition, video.currentPosition)
        } else {
            PlayerVideoModePolicy.shouldCorrectVideoDrift(
                audioPositionMs = audio.currentPosition,
                videoPositionMs = video.currentPosition,
                videoReady = video.playbackState == Player.STATE_READY,
                timeSinceLastSeekMs = lastSeekMs?.let { now - it } ?: 5_000L,
            )
        }
        if (shouldSeek) seek(audio.currentPosition)
        // playWhenReady alone stays true during audio buffering or focus loss.
        // Following isPlaying prevents the picture running ahead of the sound.
        video.playWhenReady = audio.isPlaying
    }

    private fun seek(positionMs: Long) {
        video.seekTo(positionMs.coerceAtLeast(0L))
        lastSeekMs = nowMs()
    }

    fun detach() {
        audio.removeListener(this)
        active = false
        video.playWhenReady = false
    }
}
