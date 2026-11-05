package fr.makoff.music.innertube.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class PlaylistPanelRenderer(
    val title: String?,
    val titleText: Runs?,
    val shortBylineText: Runs?,
    val contents: List<Content>,
    val isInfinite: Boolean?,
    val numItemsToShow: Int?,
    val playlistId: String?,
    val continuations: List<Continuation>?,
) {
    @Serializable
    data class Content(
        val playlistPanelVideoRenderer: PlaylistPanelVideoRenderer? = null,
        val automixPreviewVideoRenderer: AutomixPreviewVideoRenderer? = null,
        // YouTube Music's authenticated next response exposes the official
        // song/video companion in this wrapper. Keep the raw shape because
        // additional, nonessential endpoint fields change frequently.
        val playlistPanelVideoWrapperRenderer: JsonObject? = null,
    )
}
