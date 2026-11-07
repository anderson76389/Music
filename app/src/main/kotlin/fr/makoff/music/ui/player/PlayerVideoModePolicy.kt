package fr.makoff.music.ui.player

/**
 * The mode header and classic "Now Playing" header are mutually exclusive.
 * A mode selector must never be shown for local tracks or unavailable clips.
 */
internal object PlayerVideoModePolicy {
    fun showSelector(
        enabled: Boolean,
        expanded: Boolean,
        hasPlayableClip: Boolean,
    ): Boolean = enabled && expanded && hasPlayableClip

    /**
     * A known official pairing may show Son/Vidéo while its URL is loading;
     * unverified direct video fallback is shown only after stream extraction.
     * Never advertise a clip that already failed to play.
     */
    fun clipSelectable(
        youtubeTrack: Boolean,
        clipId: String?,
        unavailableClipId: String?,
        hasResolvedStream: Boolean,
        hasOfficialPair: Boolean,
        streamLookupFinished: Boolean,
    ): Boolean = youtubeTrack && clipId != null && clipId != unavailableClipId &&
        (hasResolvedStream || (hasOfficialPair && !streamLookupFinished))

    /** Wake and permit sensor rotation only while the selected video is on
     * screen. Audio-only playback and Cast must not acquire these flags.
     */
    fun videoModeActive(
        videoSelected: Boolean,
        modeEnabled: Boolean,
        expanded: Boolean,
        casting: Boolean,
    ): Boolean = videoSelected && modeEnabled && expanded && !casting

    /** Prefer YouTube Music's actual paired clip; otherwise probe the same
     * item's video stream, but never an arbitrary unrelated video.
     */
    fun candidateVideoId(
        originalId: String?,
        isYouTubeTrack: Boolean,
        isLocalOrUploaded: Boolean,
        isVideoSong: Boolean,
        pairedId: String?,
        counterpartLookupDone: Boolean,
    ): String? = when {
        !isYouTubeTrack || isLocalOrUploaded -> null
        isVideoSong -> originalId
        pairedId != null -> pairedId
        counterpartLookupDone -> originalId
        else -> null
    }
}
