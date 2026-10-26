package fr.makoff.music.innertube.pages

import fr.makoff.music.innertube.models.YTItem

data class LibraryContinuationPage(
    val items: List<YTItem>,
    val continuation: String?,
)
