package fr.makoff.music.playback

/**
 * The old radio launcher used to populate a queue implicitly. Now that radio
 * modes are gone, a standalone song still needs an explicit next-track action.
 * Requesting one next song is a user action, not automatic radio playback.
 */
internal object NextTrackPolicy {
    private val youtubeId = Regex("[A-Za-z0-9_-]{11}")

    fun canRequestNextSong(mediaId: String?): Boolean =
        mediaId != null && youtubeId.matches(mediaId)

    fun canAdvance(hasQueuedNext: Boolean, mediaId: String?): Boolean =
        hasQueuedNext || canRequestNextSong(mediaId)
}
