package fr.makoff.music.ui.player

/**
 * Folded phones keep the familiar compact vertical player. Unfolded foldables
 * and tablets with two usable dimensions receive the existing two-pane
 * player, even if Android reports the near-square internal display as PORTRAIT.
 * No manufacturer- or model-specific checks and no Activity recreation.
 */
internal object PlayerLayoutPolicy {
    fun useTwoPane(
        isLandscape: Boolean,
        widthDp: Int,
        heightDp: Int,
    ): Boolean = isLandscape || (widthDp >= 600 && heightDp >= 560)

    fun artworkBlurRadiusDp(widthDp: Int, heightDp: Int): Int =
        if (widthDp >= 600 && heightDp >= 560) 64 else 96
}
