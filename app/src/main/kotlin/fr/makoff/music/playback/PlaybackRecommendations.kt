package fr.makoff.music.playback

import androidx.media3.common.MediaItem
import fr.makoff.music.extensions.toMediaItem
import fr.makoff.music.innertube.YouTube
import fr.makoff.music.innertube.models.WatchEndpoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * YouTube Music suggestions for the current video. Unlike a one-song manual
 * Next request, this prepopulates the queue while the current song is playing.
 * No recommendation endpoint should block the initial song's audio.
 */
internal object PlaybackRecommendations {
    suspend fun fetch(seedId: String): List<MediaItem> = withContext(Dispatchers.IO) {
        if (!NextTrackPolicy.canRequestNextSong(seedId)) return@withContext emptyList()
        val response = YouTube.next(WatchEndpoint(videoId = seedId)).getOrNull()
        val fromNext = response?.items.orEmpty().map { it.toMediaItem() }
        val related = if (fromNext.size < 6) {
            response?.relatedEndpoint?.let { endpoint ->
                YouTube.related(endpoint).getOrNull()?.songs.orEmpty().map { it.toMediaItem() }
            }.orEmpty()
        } else emptyList()
        val radioResponse = if (fromNext.size + related.size <= 1) {
            YouTube.next(WatchEndpoint(videoId = seedId, playlistId = "RDAMVM$seedId")).getOrNull()
        } else null
        val radio = radioResponse?.items.orEmpty().map { it.toMediaItem() }
        val radioRelated = if (fromNext.size + related.size + radio.size <= 1) {
            radioResponse?.relatedEndpoint?.let { endpoint ->
                YouTube.related(endpoint).getOrNull()?.songs.orEmpty().map { it.toMediaItem() }
            }.orEmpty()
        } else emptyList()
        distinctCandidates(seedId, fromNext + related + radio + radioRelated)
    }

    /** Keep at least one track ready to play and expose the others as Similar. */
    fun splitSuggestions(suggestions: List<MediaItem>): Pair<List<MediaItem>, List<MediaItem>> {
        val queuedCount = when {
            suggestions.size <= 1 -> suggestions.size
            else -> minOf(2, suggestions.size - 1)
        }
        return suggestions.take(queuedCount) to suggestions.drop(queuedCount)
    }

    fun distinctCandidates(seedId: String, suggestions: List<MediaItem>, existing: Set<String> = emptySet()): List<MediaItem> {
        val seen = existing.toMutableSet().apply { add(seedId) }
        return suggestions.filter { candidate ->
            val id = candidate.mediaId
            id.isNotBlank() && id != seedId && seen.add(id)
        }
    }
}
