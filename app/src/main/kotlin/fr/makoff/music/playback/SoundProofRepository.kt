package fr.makoff.music.playback

import android.content.Context
import android.util.JsonReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale

/**
 * Opt-in, on-device lookup of community reports from the public SoundProof
 * dump. No per-song requests, identity, account or listening history are sent.
 *
 * Source: NuclearBlox/SoundProof-Dumps. A community vote is an allegation,
 * NOT a technical proof that a musician uses generative AI.
 */
internal class SoundProofRepository(private val context: Context) {
    private data class Votes(val human: Int, val ai: Int)

    private val mutex = Mutex()
    private val directory = context.filesDir.resolve("soundproof")
    private val cacheFile = File(directory, "artists.json")
    private var artists: Map<String, Votes>? = null
    private var lastAttemptMs = 0L

    suspend fun refresh() = withContext(Dispatchers.IO) {
        mutex.withLock { loadAndRefreshLocked() }
    }

    suspend fun shouldSkipArtist(credits: List<String>): Boolean = withContext(Dispatchers.IO) {
        if (credits.isEmpty()) return@withContext false
        val known = mutex.withLock {
            loadAndRefreshLocked()
            artists.orEmpty()
        }
        SoundProofPolicy.isAnyArtistReportedAi(credits, known.mapValues { it.value.human to it.value.ai })
    }

    private fun loadAndRefreshLocked() {
        if (artists == null) {
            artists = runCatching { if (cacheFile.isFile) parseDump(cacheFile) else emptyMap() }
                .onFailure { Timber.w(it, "SoundProof cache unreadable") }
                .getOrDefault(emptyMap())
        }
        val now = System.currentTimeMillis()
        val stale = !cacheFile.isFile || now - cacheFile.lastModified() >= REFRESH_AFTER_MS
        if (!stale || now - lastAttemptMs < RETRY_AFTER_MS) return
        lastAttemptMs = now

        runCatching {
            directory.mkdirs()
            val temporary = File(directory, "artists.json.download")
            try {
                fetchDump(temporary)
                val parsed = parseDump(temporary)
                require(parsed.isNotEmpty()) { "SoundProof dump contained no artists" }
                Files.move(
                    temporary.toPath(), cacheFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
                artists = parsed
            } finally {
                temporary.delete()
            }
        }.onFailure {
            // Never replace a previous valid cache with a failed download.
            Timber.w(it, "SoundProof update unavailable: using saved community database")
        }
    }

    private fun fetchDump(target: File) {
        val connection = URL(DUMP_URL).openConnection() as HttpURLConnection
        connection.connectTimeout = 12_000
        connection.readTimeout = 15_000
        connection.instanceFollowRedirects = true
        try {
            check(connection.responseCode == HttpURLConnection.HTTP_OK) {
                "SoundProof HTTP ${connection.responseCode}"
            }
            connection.inputStream.use { input ->
                FileOutputStream(target).use { output ->
                    val buf = ByteArray(16 * 1024)
                    var total = 0L
                    while (true) {
                        val count = input.read(buf)
                        if (count < 0) break
                        total += count
                        require(total <= MAX_DOWNLOAD_BYTES) { "SoundProof dump exceeds size limit" }
                        output.write(buf, 0, count)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun parseDump(file: File): Map<String, Votes> {
        require(file.length() <= MAX_DOWNLOAD_BYTES) { "SoundProof cache too large" }
        val result = HashMap<String, Votes>()
        InputStreamReader(FileInputStream(file), Charsets.UTF_8).use { input ->
            JsonReader(input).use { json ->
                json.beginArray()
                while (json.hasNext()) {
                    var name: String? = null
                    var id: String? = null
                    var human = -1
                    var ai = -1
                    json.beginObject()
                    while (json.hasNext()) {
                        when (json.nextName()) {
                            "id" -> id = if (json.peek() == android.util.JsonToken.NULL) { json.nextNull(); null } else json.nextString()
                            "name" -> name = if (json.peek() == android.util.JsonToken.NULL) { json.nextNull(); null } else json.nextString()
                            "human_votes" -> human = if (json.peek() == android.util.JsonToken.NULL) { json.nextNull(); -1 } else json.nextInt()
                            "ai_votes" -> ai = if (json.peek() == android.util.JsonToken.NULL) { json.nextNull(); -1 } else json.nextInt()
                            else -> json.skipValue()
                        }
                    }
                    json.endObject()
                    if (!name.isNullOrBlank() && human >= 0 && ai >= 0) {
                        val key = SoundProofPolicy.normalize(id?.takeIf { it.isNotBlank() } ?: name)
                        if (key.isNotBlank()) result[key] = Votes(human, ai)
                    }
                }
                json.endArray()
            }
        }
        return result
    }

    private companion object {
        const val DUMP_URL = "https://raw.githubusercontent.com/NuclearBlox/SoundProof-Dumps/main/data/artists.json"
        const val REFRESH_AFTER_MS = 24L * 60 * 60 * 1000
        const val RETRY_AFTER_MS = 60L * 60 * 1000
        const val MAX_DOWNLOAD_BYTES = 8L * 1024 * 1024
    }
}

/** Pure policy to unit-test without making network requests. */
internal object SoundProofPolicy {
    private val topicSuffix = Regex("(?i)\\s*-\\s*topic$")
    private val creditSeparator = Regex("(?i)\\s*[,&]\\s*|\\s+(?:x|×|feat\\.?|ft\\.?|featuring|vs\\.?)\\s+")

    fun normalize(name: String): String = name.trim()
        .replace(topicSuffix, "")
        .trim()
        .replace(Regex("\\s+"), " ")
        .lowercase(Locale.ROOT)
        .replace(' ', '-')

    fun isReportedAi(human: Int, ai: Int): Boolean =
        human >= 0 && ai > 0 && ai.toLong() * 100 >= (human.toLong() + ai) * 50

    fun isAnyArtistReportedAi(credits: List<String>, votesByName: Map<String, Pair<Int, Int>>): Boolean =
        credits.any { credit ->
            val whole = votesByName[normalize(credit)]
            if (whole != null) return@any isReportedAi(whole.first, whole.second)
            creditSeparator.split(credit)
                .filter { it.isNotBlank() }
                .any { name ->
                    val votes = votesByName[normalize(name)]
                    votes != null && isReportedAi(votes.first, votes.second)
                }
        }
}
