package fr.makoff.music.innertubex.models.body

import fr.makoff.music.innertubex.models.Context
import kotlinx.serialization.Serializable

@Serializable
internal data class PlaylistDeleteBody(
    val context: Context,
    val playlistId: String,
)
