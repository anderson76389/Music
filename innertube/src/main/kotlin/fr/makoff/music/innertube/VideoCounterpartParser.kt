package fr.makoff.music.innertube

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Find an official Song/Video pairing in either /next or /music/get_queue.
 * The two response shapes differ; the explicit current video ID makes it
 * safe to traverse queue wrappers without accidentally picking another song.
 */
internal object VideoCounterpartParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun fromResponse(body: String, currentVideoId: String): String? =
        runCatching { fromElement(json.parseToJsonElement(body), currentVideoId) }.getOrNull()

    fun fromElement(root: JsonElement, currentVideoId: String): String? {
        if (!isYouTubeId(currentVideoId)) return null
        return findWrappers(root).firstNotNullOfOrNull { wrapper ->
            val primary = wrapper.child("primaryRenderer")
                ?.child("playlistPanelVideoRenderer")
                ?: return@firstNotNullOfOrNull null
            if (primary.string("videoId") != currentVideoId) return@firstNotNullOfOrNull null

            val counterparts = wrapper["counterpart"] as? JsonArray
                ?: return@firstNotNullOfOrNull null
            counterparts.firstNotNullOfOrNull { item ->
                val renderer = (item as? JsonObject)
                    ?.child("counterpartRenderer")
                    ?.child("playlistPanelVideoRenderer")
                    ?: return@firstNotNullOfOrNull null
                val pairedId = renderer.string("videoId")
                val pairedType = renderer.child("navigationEndpoint")
                    ?.child("watchEndpoint")
                    ?.child("watchEndpointMusicSupportedConfigs")
                    ?.child("watchEndpointMusicConfig")
                    ?.string("musicVideoType")
                pairedId?.takeIf {
                    isYouTubeId(it) && it != currentVideoId &&
                        pairedType != "MUSIC_VIDEO_TYPE_ATV"
                }
            }
        }
    }

    private fun findWrappers(element: JsonElement): Sequence<JsonObject> = sequence {
        when (element) {
            is JsonObject -> {
                element.child("playlistPanelVideoWrapperRenderer")?.let { yield(it) }
                for (child in element.values) yieldAll(findWrappers(child))
            }
            is JsonArray -> for (child in element) yieldAll(findWrappers(child))
            else -> Unit
        }
    }

    private fun JsonObject.child(name: String) = this[name] as? JsonObject
    private fun JsonObject.string(name: String) = (this[name] as? JsonPrimitive)?.content

    fun isYouTubeId(id: String): Boolean =
        id.length == 11 && id.all { it.isLetterOrDigit() || it == '_' || it == '-' }
}
