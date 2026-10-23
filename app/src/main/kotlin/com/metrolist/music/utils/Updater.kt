/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package fr.makoff.music.utils

import fr.makoff.music.BuildConfig
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.CancellationException
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class ReleaseInfo(
    val tagName: String,
    val versionName: String,
    val description: String,
    val releaseDate: String,
    val assets: List<ReleaseAsset>
)

data class ReleaseAsset(
    val name: String,
    val downloadUrl: String,
    val size: Long,
    val architecture: String,
    val variant: String // "foss" or "gms"
)

object Updater {
    private val client = HttpClient {
        install(HttpTimeout) { requestTimeoutMillis = 15_000 }
    }
    var lastCheckTime = -1L
        private set
    
    private var cachedReleaseInfo: ReleaseInfo? = null
    private var cachedAllReleases: List<ReleaseInfo> = emptyList()
    
    private const val CHECK_INTERVAL_MILLIS = 5 * 60 * 1000L // Automatic checks also run when returning to the app.
    internal const val GITHUB_API_BASE = "https://api.github.com/repos/anderson76389/Music"

    /**
     * Compares two version strings.
     * Returns: 1 if v1 > v2, -1 if v1 < v2, 0 if equal
     */
    fun compareVersions(v1: String, v2: String): Int {
        val v1Parts = v1.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
        val v2Parts = v2.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
        val maxLength = maxOf(v1Parts.size, v2Parts.size)
        
        for (i in 0 until maxLength) {
            val part1 = v1Parts.getOrNull(i) ?: 0
            val part2 = v2Parts.getOrNull(i) ?: 0
            when {
                part1 > part2 -> return 1
                part1 < part2 -> return -1
            }
        }
        return 0
    }

    /**
     * Checks if the latest version is newer than the current version.
     * Returns true if an update is available (latestVersion > currentVersion)
     */
    fun isUpdateAvailable(currentVersion: String, latestVersion: String): Boolean {
        return compareVersions(latestVersion, currentVersion) > 0
    }

    /**
     * Get the current app's architecture and variant
     */
    private fun getCurrentAppVariant(): Pair<String, String> {
        val architecture = BuildConfig.ARCHITECTURE
        val variant = if (BuildConfig.CAST_AVAILABLE) "gms" else "foss"
        return architecture to variant
    }

    internal fun isOwnApkUrl(url: String): Boolean = runCatching {
        val uri = java.net.URI(url)
        uri.scheme == "https" && uri.host == "github.com" && uri.userInfo == null && uri.port == -1 &&
            uri.rawPath.startsWith("/anderson76389/Music/releases/download/") && uri.rawPath.endsWith(".apk")
    }.getOrDefault(false)

    /**
     * Parse release assets from GitHub API response
     */
    internal fun parseAssets(assetsArray: JSONArray): List<ReleaseAsset> {
        val assets = mutableListOf<ReleaseAsset>()
        
        for (i in 0 until assetsArray.length()) {
            val asset = assetsArray.getJSONObject(i)
            val name = asset.getString("name")
            
            // Skip non-APK files
            if (!name.endsWith(".apk")) continue
            
            val downloadUrl = asset.getString("browser_download_url")
            val size = asset.getLong("size")
            if (!isOwnApkUrl(downloadUrl) || size <= 0) continue
            
            // Parse architecture and variant from filename
            val (arch, variant) = when {
                name == "Metrolist.apk" || name == "MAKOFF-Music-universal.apk" -> "universal" to "gms"
                name == "MAKOFF-Music.apk" -> "universal" to "foss"
                name == "MAKOFF-Music-Google-Cast.apk" -> "universal" to "gms"
                name.startsWith("app-") && name.endsWith("-release.apk") -> {
                    val arch = name.removePrefix("app-").removeSuffix("-release.apk")
                    arch to "foss"
                }
                name.startsWith("app-") && name.endsWith("-with-Google-Cast.apk") -> {
                    val arch = name.removePrefix("app-").removeSuffix("-with-Google-Cast.apk")
                    arch to "gms"
                }
                else -> null to null
            }
            
            if (arch != null && variant != null) {
                assets.add(ReleaseAsset(name, downloadUrl, size, arch, variant))
            }
        }
        
        return assets
    }

    /**
     * Fetch latest release from GitHub API
     */
    suspend fun getLatestRelease(forceRefresh: Boolean = false): Result<ReleaseInfo> =
        withContext(Dispatchers.IO) {
            runCatching {
                // Return cached if available and not forcing refresh
                if (cachedReleaseInfo != null && !forceRefresh) {
                    return@runCatching cachedReleaseInfo!!
                }
                
                check(BuildConfig.UPDATER_AVAILABLE) { "Mises à jour non configurées pour Metrolist" }
                val response = client.get("$GITHUB_API_BASE/releases/latest")
                check(response.status.value in 200..299) { "GitHub HTTP ${response.status.value}" }
                val json = JSONObject(response.bodyAsText())
                
                val releaseInfo = ReleaseInfo(
                    tagName = json.getString("tag_name"),
                    versionName = json.getString("tag_name").removePrefix("v"),
                    description = json.optString("body").takeUnless { json.isNull("body") }.orEmpty(),
                    releaseDate = json.getString("published_at"),
                    assets = parseAssets(json.getJSONArray("assets"))
                )
                
                cachedReleaseInfo = releaseInfo
                lastCheckTime = System.currentTimeMillis()
                releaseInfo
            }
        }

    /**
     * Fetch all releases from GitHub API (paginated)
     */
    suspend fun getAllReleases(forceRefresh: Boolean = false): Result<List<ReleaseInfo>> =
        withContext(Dispatchers.IO) {
            try {
                val releases = if (cachedAllReleases.isNotEmpty() && !forceRefresh) {
                    cachedAllReleases
                } else {
                    fetchReleaseHistory(client).also { cachedAllReleases = it }
                }
                Result.success(releases)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    // Reading release notes is available even when APK auto-updates are disabled.
    internal suspend fun fetchReleaseHistory(httpClient: HttpClient): List<ReleaseInfo> {
        val releases = mutableListOf<ReleaseInfo>()
        for (page in 1..10) {
            val response = httpClient.get("$GITHUB_API_BASE/releases?page=$page&per_page=30")
            check(response.status.value in 200..299) { "GitHub HTTP ${response.status.value}" }
            val json = JSONArray(response.bodyAsText())
            for (i in 0 until json.length()) {
                val release = json.getJSONObject(i)
                if (release.optBoolean("draft")) continue
                releases.add(ReleaseInfo(
                    tagName = release.getString("tag_name"),
                    versionName = release.getString("tag_name").removePrefix("v"),
                    description = release.optString("body").takeUnless { release.isNull("body") }.orEmpty(),
                    releaseDate = release.getString("published_at"),
                    assets = parseAssets(release.getJSONArray("assets"))
                ))
            }
            if (json.length() < 30) break
        }
        return releases.sortedByDescending { it.releaseDate }
    }

    /**
     * Get the download URL for the correct app variant
     */
    fun getDownloadUrlForCurrentVariant(releaseInfo: ReleaseInfo): String? {
        val (currentArch, currentVariant) = getCurrentAppVariant()
        
        return selectUpdateAsset(releaseInfo.assets, currentArch, currentVariant)?.downloadUrl
    }

    internal fun selectUpdateAsset(assets: List<ReleaseAsset>, architecture: String, variant: String): ReleaseAsset? =
        assets.firstOrNull { it.name == "Metrolist.apk" && it.architecture == "universal" }
            ?: assets.firstOrNull { it.architecture == architecture && it.variant == variant }
            ?: assets.firstOrNull { it.architecture == "universal" && it.variant == variant }

    /**
     * Check if update is needed (respects 2-hour cache)
     */
    suspend fun checkForUpdate(forceRefresh: Boolean = false): Result<Pair<ReleaseInfo?, Boolean>> =
        withContext(Dispatchers.IO) {
            runCatching {
                // Check if we should fetch (2 hour interval)
                val shouldFetch = forceRefresh || 
                    (System.currentTimeMillis() - lastCheckTime) > CHECK_INTERVAL_MILLIS
                
                if (!shouldFetch && cachedReleaseInfo != null) {
                    val hasUpdate = isUpdateAvailable(
                        BuildConfig.BASE_VERSION_NAME,
                        cachedReleaseInfo!!.versionName
                    )
                    return@runCatching cachedReleaseInfo!! to hasUpdate
                }
                
                val result = getLatestRelease(forceRefresh = true)
                if (result.isSuccess) {
                    val releaseInfo = result.getOrThrow()
                    val hasUpdate = isUpdateAvailable(
                        BuildConfig.BASE_VERSION_NAME,
                        releaseInfo.versionName
                    )
                    releaseInfo to hasUpdate
                } else {
                    throw result.exceptionOrNull() ?: Exception("Unknown error")
                }
            }
        }

    /**
     * Get the latest release info (cached)
     */
    fun getCachedLatestRelease(): ReleaseInfo? = cachedReleaseInfo
}
