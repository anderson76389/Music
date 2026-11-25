package fr.makoff.music.playback

import androidx.media3.common.MediaItem
import fr.makoff.music.extensions.toMediaItem
import fr.makoff.music.innertube.YouTube
import fr.makoff.music.innertube.models.WatchEndpoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * YouTube Music suggestions for the current video. Unlike a one-song manual
 * Next request, this prepopulates the queue while the current song is playing.
 * No recommendation endpoint should block the initial song's audio.
 */
internal object PlaybackRecommendations {
    suspend fun fetch(seedId: String): List<MediaItem> = withContext(Dispatchers.IO) {
        if (!NextTrackPolicy.canRequestNextSong(seedId)) return@withContext emptyList()
        val response = withTimeoutOrNull(8_000L) { YouTube.next(WatchEndpoint(videoId = seedId)).getOrNull() }
        val fromNext = response?.items.orEmpty().map { it.toMediaItem() }
        val related = if (distinctCandidates(seedId, fromNext).size < 6) {
            response?.relatedEndpoint?.let { endpoint ->
                withTimeoutOrNull(6_000L) { YouTube.related(endpoint).getOrNull() }?.songs.orEmpty().map { it.toMediaItem() }
            }.orEmpty()
        } else emptyList()
        val radioResponse = if (distinctCandidates(seedId, fromNext + related).size < 6) {
            withTimeoutOrNull(8_000L) { YouTube.next(WatchEndpoint(videoId = seedId, playlistId = "RDAMVM$seedId")).getOrNull() }
        } else null
        val radio = radioResponse?.items.orEmpty().map { it.toMediaItem() }
        val radioRelated = if (distinctCandidates(seedId, fromNext + related + radio).size < 6) {
            radioResponse?.relatedEndpoint?.let { endpoint ->
                withTimeoutOrNull(6_000L) { YouTube.related(endpoint).getOrNull() }?.songs.orEmpty().map { it.toMediaItem() }
            }.orEmpty()
        } else emptyList()
        distinctCandidates(seedId, fromNext + related + radio + radioRelated)
    }

    /** Keep a substantial queue ready, reserving at least one suggestion for Similar when possible. */
    fun splitSuggestions(suggestions: List<MediaItem>): Pair<List<MediaItem>, List<MediaItem>> {
        val queuedCount = when {
            suggestions.size <= 1 -> suggestions.size
            else -> minOf(12, suggestions.size - 1)
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
