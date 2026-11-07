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
}
