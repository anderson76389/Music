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
    // Network I/O must never hold the cache lock: otherwise the first queued
    // song continues playing for up to 27 seconds before SoundProof can skip it.
    private val cacheMutex = Mutex()
    private val refreshMutex = Mutex()
    private val directory = context.filesDir.resolve("soundproof")
    private val cacheFile = File(directory, "artists.json")
    private var artists: Map<String, Pair<Int, Int>>? = null
    private var loadedCacheModifiedMs = 0L
    private var lastAttemptMs = 0L

    suspend fun refresh() = withContext(Dispatchers.IO) {
        refreshMutex.withLock { refreshIfNeeded(force = false) }
    }

    /** Force a real download and report whether its JSON was accepted. */
    suspend fun refreshNow(): Boolean = withContext(Dispatchers.IO) {
        refreshMutex.withLock { refreshIfNeeded(force = true) }
    }

    /** Read local votes only, without any network wait in the player hot path. */
    suspend fun shouldSkipArtist(credits: List<String>): Boolean = withContext(Dispatchers.IO) {
        if (credits.isEmpty()) return@withContext false
        val known = cacheMutex.withLock {
            loadSavedCacheLocked()
            artists.orEmpty()
        }
        SoundProofPolicy.isAnyArtistReportedAi(credits, known)
    }

    /** Used by a real Android test to validate the decoded community download. */
    internal suspend fun cachedArtistCount(): Int = withContext(Dispatchers.IO) {
        cacheMutex.withLock {
            loadSavedCacheLocked()
            artists.orEmpty().size
        }
    }

    private fun loadSavedCacheLocked() {
        val lastModified = if (cacheFile.isFile) cacheFile.lastModified() else 0L
        if (artists != null && loadedCacheModifiedMs == lastModified) return
        artists = runCatching { if (cacheFile.isFile) parseDump(cacheFile) else emptyMap() }
            .onFailure { Timber.w(it, "SoundProof cache unreadable") }
            .getOrDefault(emptyMap())
        loadedCacheModifiedMs = lastModified
    }

    /** Caller holds refreshMutex, but not cacheMutex across HTTP operations. */
    private suspend fun refreshIfNeeded(force: Boolean): Boolean {
        val now = System.currentTimeMillis()
        val stale = cacheMutex.withLock {
            loadSavedCacheLocked()
            !cacheFile.isFile || now - cacheFile.lastModified() >= REFRESH_AFTER_MS
        }
        if (!force && (!stale || now - lastAttemptMs < RETRY_AFTER_MS)) return false
        lastAttemptMs = now

        return runCatching {
            directory.mkdirs()
            // Settings and the playback service have independent repositories.
            // Unique temp paths prevent their simultaneous refreshes colliding.
            val temporary = File.createTempFile("artists.", ".download", directory)
            try {
                fetchDump(temporary)
                val parsed = parseDump(temporary)
                require(parsed.isNotEmpty()) { "SoundProof dump contained no artists" }
                cacheMutex.withLock {
                    Files.move(
                        temporary.toPath(), cacheFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                    )
                    artists = parsed
                    loadedCacheModifiedMs = cacheFile.lastModified()
                }
            } finally {
                temporary.delete()
            }
        }.onFailure {
            // A network failure must not destroy the previous votes.
            Timber.w(it, "SoundProof refresh unavailable: retaining saved database")
        }.isSuccess
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

    private fun parseDump(file: File): Map<String, Pair<Int, Int>> {
        require(file.length() <= MAX_DOWNLOAD_BYTES) { "SoundProof cache too large" }
        val result = HashMap<String, Pair<Int, Int>>()
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
                        val vote = human to ai
                        // IDs in the upstream dump are slugs, whereas YouTube
                        // Music credits contain display names. Index BOTH:
                        // keeping only the ID silently missed real artists.
                        val nameKey = SoundProofPolicy.normalize(name)
                        if (nameKey.isNotBlank()) result[nameKey] = vote
                        val idKey = id?.let(SoundProofPolicy::normalize).orEmpty()
                        if (idKey.isNotBlank()) result.putIfAbsent(idKey, vote)
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

    /** Stop autoplay loops after checking a complete queue of reported artists. */
    fun fullQueueAlreadySkipped(consecutiveSkips: Int, queueLength: Int): Boolean =
        queueLength > 0 && consecutiveSkips >= queueLength

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
