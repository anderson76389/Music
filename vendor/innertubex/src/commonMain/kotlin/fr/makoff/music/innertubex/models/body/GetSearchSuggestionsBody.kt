package fr.makoff.music.innertubex.models.body

import fr.makoff.music.innertubex.models.Context
import kotlinx.serialization.Serializable

@Serializable
internal data class GetSearchSuggestionsBody(
    val context: Context,
    val input: String,
)
