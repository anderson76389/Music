package fr.makoff.music.innertube.pages

import fr.makoff.music.innertube.models.SongItem

data class PlaylistContinuationPage(
    val songs: List<SongItem>,
    val continuation: String?,
)
