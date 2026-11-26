package fr.makoff.music.playback

import androidx.media3.common.MediaItem
import fr.makoff.music.extensions.toMediaItem
import fr.makoff.music.innertube.YouTube
import fr.makoff.music.innertube.models.WatchEndpoint
import fr.makoff.music.innertube.models.SongItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * YouTube Music suggestions for the current video. Unlike a one-song manual
 * Next request, this prepopulates the queue while the current song is playing.
 * No recommendation endpoint should block the initial song's audio.
 */
internal object PlaybackRecommendations {
    suspend fun fetch(seedId: String, existing: Set<String> = emptySet()): List<MediaItem> = withContext(Dispatchers.IO) {
        if (!NextTrackPolicy.canRequestNextSong(seedId)) return@withContext emptyList()
        val response = withTimeoutOrNull(8_000L) { YouTube.next(WatchEndpoint(videoId = seedId)).getOrNull() }
        val fromNext = response?.items.orEmpty().map { it.toMediaItem() }
        val related = if (distinctCandidates(seedId, fromNext, existing).size < 12) {
            response?.relatedEndpoint?.let { endpoint ->
                withTimeoutOrNull(6_000L) { YouTube.related(endpoint).getOrNull() }?.songs.orEmpty().map { it.toMediaItem() }
            }.orEmpty()
        } else emptyList()
        val radioResponse = if (distinctCandidates(seedId, fromNext + related, existing).size < 12) {
            withTimeoutOrNull(8_000L) { YouTube.next(WatchEndpoint(videoId = seedId, playlistId = "RDAMVM$seedId")).getOrNull() }
        } else null
        val radio = radioResponse?.items.orEmpty().map { it.toMediaItem() }
        val radioRelated = if (distinctCandidates(seedId, fromNext + related + radio, existing).size < 12) {
            radioResponse?.relatedEndpoint?.let { endpoint ->
                withTimeoutOrNull(6_000L) { YouTube.related(endpoint).getOrNull() }?.songs.orEmpty().map { it.toMediaItem() }
            }.orEmpty()
        } else emptyList()
        val candidates = (fromNext + related + radio + radioRelated).toMutableList()
        // The first /next page can contain only tracks already in the queue.
        // Continue the actual YouTube Music mix rather than returning empty.
        var page = radioResponse ?: response
        repeat(2) {
            if (distinctCandidates(seedId, candidates, existing).size < 12) {
                val currentPage = page
                val token = currentPage?.continuation
                if (currentPage != null && token != null) {
                    page = withTimeoutOrNull(6_000L) {
                        YouTube.next(currentPage.endpoint, token).getOrNull()
                    }
                    candidates += page?.items.orEmpty().map { it.toMediaItem() }
                }
            }
        }
        // Last online fallback is personalized Music home, never a made-up ID.
        if (distinctCandidates(seedId, candidates, existing).size < 12) {
            val home = withTimeoutOrNull(8_000L) { YouTube.home().getOrNull() }
            candidates += home?.sections.orEmpty().flatMap { it.items }
                .filterIsInstance<SongItem>().map { it.toMediaItem() }
        }
        distinctCandidates(seedId, candidates, existing)
    }

    // In ordinary playback, never replay a recent title or any upcoming
    // title. Older played titles may reappear when the pool is exhausted,
    // so a days-long listening session is not permanently blacklisted.
    // Shuffle keeps the full queue excluded since physical order is different.
    fun excludedIds(ids: List<String>, currentIndex: Int, shuffle: Boolean): Set<String> =
        if (shuffle || currentIndex < 0) ids.toSet()
        else ids.drop((currentIndex - 100).coerceAtLeast(0)).toSet()

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
