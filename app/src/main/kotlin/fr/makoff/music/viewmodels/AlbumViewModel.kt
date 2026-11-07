/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package fr.makoff.music.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.makoff.music.innertube.YouTube
import fr.makoff.music.innertube.models.AlbumItem
import fr.makoff.music.db.MusicDatabase
import fr.makoff.music.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AlbumViewModel
@Inject
constructor(
    database: MusicDatabase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val albumId = savedStateHandle.get<String>("albumId")!!
    val playlistId = MutableStateFlow("")
    val albumWithSongs =
        database
            .albumWithSongs(albumId)
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    var otherVersions = MutableStateFlow<List<AlbumItem>>(emptyList())

    init {
        viewModelScope.launch {
            val album = database.album(albumId).first()
            YouTube
                .album(albumId)
                .onSuccess {
                    playlistId.value = it.album.playlistId
                    otherVersions.value = it.otherVersions
                    // Read the latest row inside the transaction. Using the album
                    // snapshot obtained BEFORE the network request can overwrite a
                    // user's new bookmarkedAt if they liked the album meanwhile.
                    database.withTransaction {
                        val current = albumEntity(albumId)
                        if (current == null) {
                            insert(it)
                        } else {
                            update(current, it)
                        }
                    }
                }.onFailure {
                    reportException(it)
                    if (it.message?.contains("NOT_FOUND") == true) {
                        // A transiently unavailable YouTube page is not a reason
                        // to erase a locally saved album. Check the latest row,
                        // not the pre-request snapshot.
                        database.withTransaction {
                            albumEntity(albumId)?.takeIf {
                                it.bookmarkedAt == null && !it.isLocal
                            }?.let(::delete)
                        }
                    }
                }
        }
    }
}
