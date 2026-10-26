package fr.makoff.music.innertubex.models.body

import fr.makoff.music.innertubex.models.Context
import kotlinx.serialization.Serializable

@Serializable
internal data class SubscribeBody(
    val context: Context,
    val channelIds: List<String>,
    val params: String? = null,
)
