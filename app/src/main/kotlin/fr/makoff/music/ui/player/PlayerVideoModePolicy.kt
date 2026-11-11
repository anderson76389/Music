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
        // When enabled, the classic header must NEVER appear even if the
        // current item is audio-only or video lookup fails. This invariant is
        // also covered by Android 11's real player UI instrumentation.
        modeEnabled -> HeaderMode.LOADING
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

    /** Avoid decoder-flushing seeks for tiny drift when changing Son/Vidéo.
     * Never seek to invalid negative positions (unprepared/unknown clock).
     */
    fun shouldSeekOnVideoSelection(audioPositionMs: Long, videoPositionMs: Long, toleranceMs: Long = 1_800L): Boolean =
        audioPositionMs >= 0L && videoPositionMs >= 0L && toleranceMs >= 0L &&
            kotlin.math.abs(audioPositionMs - videoPositionMs) > toleranceMs

    /** Large drift is corrected only after the decoder is ready and the
     * preceding seek had time to rebuffer. No repeated stall every tick.
     */
    fun shouldCorrectVideoDrift(
        audioPositionMs: Long, videoPositionMs: Long,
        videoReady: Boolean, timeSinceLastSeekMs: Long,
    ): Boolean = videoReady && timeSinceLastSeekMs >= 5_000L &&
        shouldSeekOnVideoSelection(audioPositionMs, videoPositionMs, toleranceMs = 2_800L)

    /** Exclusive rendering: Son always owns the cover; Vidéo never
     * composes the cover behind a native TextureView.
     */
    fun showSongArtwork(videoSelected: Boolean): Boolean = !videoSelected

    /** Collapse and temporary layout detach do not invalidate a user's
     * selected video. Only disabling, Cast, or proven clip failure does.
     */
    fun shouldResetVideoSelection(
        modeEnabled: Boolean, casting: Boolean,
        lookupFinished: Boolean, hasPlayableClip: Boolean,
    ): Boolean = !modeEnabled || casting || (lookupFinished && !hasPlayableClip)

    /** Landscape video replaces the entire music player canvas, never a
     * two-pane artwork slot. The audio and normal landscape player are unchanged.
     */
    fun fullscreenLandscapeVideo(videoActive: Boolean, isLandscape: Boolean): Boolean =
        videoActive && isLandscape

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
