package fr.makoff.music.innertubex.models.body

import fr.makoff.music.innertubex.models.Context
import kotlinx.serialization.Serializable

@Serializable
internal data class BrowseBody(
    val context: Context,
    val browseId: String? = null,
    val params: String? = null,
    val continuation: String? = null,
)
