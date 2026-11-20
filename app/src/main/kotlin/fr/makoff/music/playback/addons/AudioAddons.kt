package fr.makoff.music.playback.addons

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import fr.makoff.music.models.MediaMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.text.Normalizer
import java.util.concurrent.TimeUnit
import kotlin.math.abs

@Serializable
internal data class AudioAddon(
    val manifest: String,
    val name: String,
    val quality: String,
    val enabled: Boolean = true,
)

internal object AudioAddons {
    const val MAX_SOURCES = 12
    private const val MAX_JSON_BYTES = 256 * 1024L
    private val sourcesKey = stringPreferencesKey("sources")
    private var store: DataStore<Preferences>? = null

    @Synchronized
    private fun store(context: Context) = store ?: PreferenceDataStoreFactory.create(
        produceFile = { File(context.applicationContext.noBackupFilesDir, "audio-addons.preferences_pb") },
    ).also { store = it }

    private fun decode(serialized: String?): List<AudioAddon> =
        serialized?.let { runCatching { Json.decodeFromString<List<AudioAddon>>(it) }.getOrNull() }.orEmpty()

    fun sources(context: Context) = store(context).data
        .map { decode(it[sourcesKey]) }
        .catch { error -> if (error is IOException) emit(emptyList()) else throw error }

    suspend fun update(context: Context, transform: (List<AudioAddon>) -> List<AudioAddon>): Boolean =
        try {
            store(context).edit { it[sourcesKey] = Json.encodeToString(transform(decode(it[sourcesKey]))) }
            true
        } catch (_: IOException) {
            false
        }

    private val api = OkHttpClient.Builder()
        .followRedirects(false)
        .retryOnConnectionFailure(false)
        .callTimeout(1800, TimeUnit.MILLISECONDS)
        .build()

    // Use a separate client from YouTube and refuse HTTPS-to-HTTP redirects.
    val mediaClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .followSslRedirects(false)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    enum class TestStatus { READY, INVALID, RESTRICTED, UNAVAILABLE }
    data class TestResult(val status: TestStatus, val source: AudioAddon? = null)
    data class Stream(val url: String, val name: String)

    internal fun manifestUrl(input: String): HttpUrl? {
        val url = httpsUrl(input.trim()) ?: return null
        val converted = url.newBuilder()
        if (url.pathSegments.last() != "manifest.json") {
            if (url.pathSegments.last().isEmpty()) converted.removePathSegment(url.pathSize - 1)
            converted.addPathSegment("manifest.json")
        }
        return converted.build()
    }

    private fun httpsUrl(value: String): HttpUrl? = value.toHttpUrlOrNull()?.takeIf {
        it.isHttps && it.username.isEmpty() && it.password.isEmpty() && it.fragment == null
    }

    private fun endpoint(manifest: HttpUrl, route: String) =
        manifest.newBuilder().removePathSegment(manifest.pathSize - 1).addPathSegment(route)

    suspend fun test(input: String): TestResult = withContext(Dispatchers.IO) {
        val manifest = manifestUrl(input) ?: return@withContext TestResult(TestStatus.INVALID)
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4)
        try {
            val document = get(manifest, deadline) ?: return@withContext TestResult(TestStatus.INVALID)
            val id = document.text("id").takeIf { it.isNotBlank() }
                ?: return@withContext TestResult(TestStatus.INVALID)
            if (document.text("bitchordAuth").lowercase() in setOf("true", "1")) {
                return@withContext TestResult(TestStatus.RESTRICTED)
            }
            val quality = (document["settings"] as? JsonArray)?.filterIsInstance<JsonObject>()
                ?.firstOrNull { it.text("key") == "quality" }
                ?.let { it["options"] as? JsonArray }
                ?.filterIsInstance<JsonObject>()
                ?.firstNotNullOfOrNull { it.text("value").takeIf(String::isNotBlank) }
                ?: "LOSSLESS"
            val search = get(searchUrl(manifest, "music", quality), deadline)
            if (search?.get("tracks") !is JsonArray) return@withContext TestResult(TestStatus.INVALID)
            val name = document.text("name").ifBlank { id }.filterNot(Char::isISOControl).take(120)
            TestResult(TestStatus.READY, AudioAddon(manifest.toString(), name.ifBlank { manifest.host }, quality))
        } catch (failure: HttpFailure) {
            val status = when (failure.status) {
                401, 403 -> TestStatus.RESTRICTED
                404 -> TestStatus.INVALID
                else -> TestStatus.UNAVAILABLE
            }
            TestResult(status)
        } catch (_: IOException) {
            TestResult(TestStatus.UNAVAILABLE)
        }
    }

    fun resolve(sources: List<AudioAddon>, metadata: MediaMetadata): Stream? {
        val artist = metadata.artists.firstOrNull()?.name?.takeIf(String::isNotBlank) ?: return null
        if (metadata.title.isBlank() || metadata.isEpisode || metadata.uploadEntityId != null) return null
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
        for (source in sources) {
            if (!source.enabled) continue
            if (System.nanoTime() >= deadline) break
            val manifest = manifestUrl(source.manifest) ?: continue
            try {
                val search = get(searchUrl(manifest, "${metadata.title} $artist", source.quality), deadline)
                val match = matchingTrack(search?.get("tracks") as? JsonArray ?: continue, metadata) ?: continue
                val url = endpoint(manifest, "stream").addPathSegment(match.text("id"))
                    .addQueryParameter("quality", source.quality).build()
                val response = try {
                    get(url, deadline)
                } catch (failure: HttpFailure) {
                    if (failure.status != 404) throw failure
                    null
                }
                val media = if (response == null) {
                    progressiveUrl(match, "streamURL")
                } else {
                    progressiveUrl(response, "url")
                }
                if (media != null) return Stream(media.toString(), source.name)
            } catch (_: IOException) {
                continue
            }
        }
        return null
    }

    private fun searchUrl(manifest: HttpUrl, query: String, quality: String) =
        endpoint(manifest, "search").addQueryParameter("q", query)
            .addQueryParameter("quality", quality).build()

    private class HttpFailure(val status: Int) : IOException()

    private fun get(url: HttpUrl, deadline: Long): JsonObject? {
        val timeLeft = deadline - System.nanoTime()
        if (timeLeft <= 0) throw IOException()
        val call = api.newCall(Request.Builder().url(url).header("Accept", "application/json").build())
        call.timeout().timeout(minOf(timeLeft, TimeUnit.MILLISECONDS.toNanos(1800)), TimeUnit.NANOSECONDS)
        return call.execute().use { response ->
            if (!response.isSuccessful) throw HttpFailure(response.code)
            val body = response.body?.source() ?: return@use null
            if (body.request(MAX_JSON_BYTES + 1)) return@use null
            runCatching { Json.parseToJsonElement(body.readUtf8()) as? JsonObject }.getOrNull()
        }
    }

    internal fun matchingTrack(rows: JsonArray, song: MediaMetadata): JsonObject? {
        val title = normalize(song.title)
        val artist = normalize(song.artists.firstOrNull()?.name.orEmpty())
        val allArtists = normalize(song.artists.joinToString(", ") { it.name })
        val album = normalize(song.album?.title.orEmpty())
        if (title.isEmpty() || artist.isEmpty()) return null
        return rows.asSequence().take(60).filterIsInstance<JsonObject>()
            .filter { track ->
                val candidateArtist = normalize(track.text("artist"))
                val duration = (track["duration"] as? JsonPrimitive)?.doubleOrNull
                    ?.takeIf { it.isFinite() && it > 0 }
                val durationMatches = song.duration > 0 && duration != null && abs(song.duration - duration) <= 4
                val albumMatches = album.isNotEmpty() && album == normalize(track.text("album"))
                track.text("id").isNotBlank() && normalize(track.text("title")) == title &&
                    (candidateArtist == artist || candidateArtist == allArtists) &&
                    (durationMatches || (albumMatches && (song.duration <= 0 || duration == null)))
            }
            .firstOrNull()
    }

    private fun normalize(value: String) = Normalizer.normalize(value, Normalizer.Form.NFKD)
        .lowercase().replace(Regex("\\p{M}"), "").replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    internal fun progressiveUrl(track: JsonObject, field: String): HttpUrl? {
        val url = httpsUrl(track.text(field)) ?: return null
        if (track.text("encrypted").lowercase() in setOf("true", "1")) return null
        if (track.text("manifest").lowercase() !in setOf("", "none")) return null
        if (track.text("format").lowercase() in setOf("hls", "dash")) return null
        return url.takeUnless { it.encodedPath.lowercase().let { path -> path.endsWith(".m3u8") || path.endsWith(".mpd") } }
    }

    private fun JsonObject.text(key: String): String = (this[key] as? JsonPrimitive)
        ?.takeUnless { it is JsonNull }?.content.orEmpty()
}
