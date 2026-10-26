package fr.makoff.music.innertubex.models.body

import fr.makoff.music.innertubex.models.Context
import kotlinx.serialization.Serializable

@Serializable
internal data class SearchBody(
    val context: Context,
    val query: String? = null,
    val params: String? = null,
)
