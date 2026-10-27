package fr.makoff.music.downloads

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import fr.makoff.music.constants.AutoDownloadOnLikeKey
import fr.makoff.music.constants.FolderAudioTreeKey
import fr.makoff.music.models.toMediaMetadata
import fr.makoff.music.playback.DownloadUtil
import fr.makoff.music.utils.dataStore
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import timber.log.Timber

internal object AutoLikedDownloads {
    const val FOLDER = "J'aime"
    val SKIPPED = stringSetPreferencesKey("clearedLikedDownloads")
    fun documentKey(tree: String, id: String) =
        stringPreferencesKey("likedAudio:" + destinationId(tree) + ":" + id)

    suspend fun observe(context: Context, downloads: DownloadUtil) {
        val handled = mutableSetOf<String>()
        var destination: String? = null
        combine(context.dataStore.data, downloads.database.likedSongsByRowIdAsc()) { prefs, songs -> prefs to songs }
            .collect { (prefs, songs) ->
                val eligible = songs.filter { !it.song.isLocal && !it.song.isEpisode && it.id.matches(Regex("[A-Za-z0-9_-]{11}")) }
                val eligibleIds = eligible.map { it.id }.toSet()
                val skipped = prefs[SKIPPED].orEmpty().intersect(eligibleIds)
                if (skipped != prefs[SKIPPED].orEmpty()) context.dataStore.edit { it[SKIPPED] = skipped }
                if (prefs[AutoDownloadOnLikeKey] != true) {
                    handled.clear()
                    destination = null
                    return@collect
                }
                val tree = prefs[FolderAudioTreeKey].orEmpty()
                if (destination != tree) { handled.clear(); destination = tree }
                handled.retainAll(eligibleIds)
                for (song in eligible) {
                    if (song.id in handled || song.id in skipped) continue
                    try {
                        val previous = prefs[documentKey(tree, song.id)]?.let(Uri::parse)
                        val size = previous?.let { uri ->
                            runCatching { context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } }.getOrNull()
                        }
                        if (previous != null && size != null && size > 0) {
                            if (downloads.externalAudioUri(song.id) != previous) {
                                downloads.registerExternalAudio(song.toMediaMetadata(), previous, size)
                            }
                        } else if (tree.isNotBlank()) {
                            FolderAudioDownloads.enqueue(context, song.toMediaMetadata(), Uri.parse(tree), listOf(FOLDER))
                        } else {
                            // Preserve offline downloading until the user authorizes a public folder.
                            downloads.download(song)
                        }
                        handled += song.id
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        Timber.w("Automatic liked download could not be queued")
                    }
                }
            }
    }
}
