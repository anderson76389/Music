package fr.makoff.music.ui.player

/**
 * The mode header and classic "Now Playing" header are mutually exclusive.
 * A mode selector must never be shown for local tracks or unavailable clips.
 */
internal object PlayerVideoModePolicy {
    enum class HeaderMode { SELECTOR, CLASSIC, LOADING }

    /** Exactly one header is rendered; never combine En cours de lecture
     * with Son/Vidéo. Don't flash the old header during video discovery.
     */
    fun headerMode(selectorVisible: Boolean, waitingForClip: Boolean, modeEnabled: Boolean = false): HeaderMode = when {
        selectorVisible -> HeaderMode.SELECTOR
        // Once enabled, the classic header must NEVER flash even before the
        // metadata is ready or after a video stream has failed.
        modeEnabled || waitingForClip -> HeaderMode.LOADING
        else -> HeaderMode.CLASSIC
    }

    /** The cover's independent legacy header must not leak through while
     * the full-screen sheet is opening or closing with Son/Vidéo enabled.
     */
    fun suppressLegacyThumbnailHeader(modeEnabled: Boolean, expanded: Boolean): Boolean =
        modeEnabled || expanded

    fun showSelector(
        enabled: Boolean,
        expanded: Boolean,
        hasPlayableClip: Boolean,
    ): Boolean = enabled && expanded && hasPlayableClip

    /** Do not request video for a local/uploaded track, Cast, or malformed ID. */
    fun canProbeVideo(videoId: String?, enabled: Boolean, localOrUploaded: Boolean, casting: Boolean): Boolean =
        enabled && !localOrUploaded && !casting &&
            videoId != null && videoId.length == 11 &&
            videoId.all { it.isLetterOrDigit() || it == '_' || it == '-' }

    /**
     * Only a confirmed usable stream may enable the Son/Vidéo tabs.
     * An official counterpart alone is not proof the clip can be played.
     */
    fun clipSelectable(
        youtubeTrack: Boolean,
        clipId: String?,
        unavailableClipId: String?,
        hasResolvedStream: Boolean,
    ): Boolean = youtubeTrack && clipId != null && clipId != unavailableClipId && hasResolvedStream

    /** Do not keep retrying a failed clip. For a valid YouTube song with an
     * unavailable paired video, the original item may itself have a video stream.
     */
    fun bestClipId(
        originalId: String?,
        preferredId: String?,
        youtubeTrack: Boolean,
        localOrUploaded: Boolean,
        counterpartLookupDone: Boolean,
        failedIds: Set<String>,
    ): String? = when {
        !youtubeTrack || localOrUploaded -> null
        preferredId != null && preferredId !in failedIds -> preferredId
        counterpartLookupDone && originalId != null && originalId !in failedIds -> originalId
        else -> null
    }

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
