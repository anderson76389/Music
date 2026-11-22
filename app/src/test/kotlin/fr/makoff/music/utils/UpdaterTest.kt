package fr.makoff.music.utils

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UpdaterTest {
    @Test fun androidVersionCodeWinsOverManuallyChangedVersionNames() {
        val newer = ReleaseInfo("v13.7.0.12", "13.7.0.12", "", "", emptyList(), 10012)
        assertTrue(Updater.isNewerRelease(10011, "99.0.0", newer))
        assertFalse(Updater.isNewerRelease(10012, "1.0", newer))
        assertFalse(Updater.isNewerRelease(10013, "1.0", newer))
        assertEquals(10012L, Updater.parsePublishedVersionCode("MUSIC_VERSION_CODE=10012\nMUSIC_VERSION_NAME=13.7.0.12\n"))
        assertTrue(runCatching { Updater.parsePublishedVersionCode("MUSIC_VERSION_CODE=bad") }.isFailure)
    }


    @Test fun githubRepositoryRenameKeepsTrustedReleasesWorking() {
        val old = "https://github.com/anderson76389/Music/releases/download/v13.7.0.135/Apple.Music.apk"
        val renamed = "https://github.com/anderson76389/Apple-Music/releases/download/v13.7.0.136/Apple.Music-arm64-v8a.apk"
        val metadata = "https://github.com/anderson76389/Apple-Music/releases/download/v13.7.0.136/BUILD.txt"
        assertTrue(Updater.isOwnApkUrl(old))
        assertTrue(Updater.isOwnApkUrl(renamed))
        assertTrue(Updater.isOwnBuildInfoUrl(metadata))
        assertFalse(Updater.isOwnApkUrl("https://github.com/anderson76389/Apple-Music-evil/releases/download/v1/Apple.Music.apk"))
        assertFalse(Updater.isOwnApkUrl("https://github.com.evil.test/anderson76389/Apple-Music/releases/download/v1/Apple.Music.apk"))
        assertFalse(Updater.isOwnApkUrl("http://github.com/anderson76389/Apple-Music/releases/download/v1/Apple.Music.apk"))
        assertFalse(Updater.isOwnApkUrl("https://github.com/other/Apple-Music/releases/download/v1/Apple.Music.apk"))
        assertFalse(Updater.isOwnApkUrl("https://github.com/anderson76389/Apple-Music/releases/download/v1/subfolder/Apple.Music.apk"))
        assertFalse(Updater.isOwnBuildInfoUrl("https://github.com/anderson76389/Apple-Music/releases/download/v1/other.txt"))
        assertFalse(Updater.isOwnBuildInfoUrl("https://github.com/anderson76389/Apple-Music/releases/download/v1/BUILD.txt?x=1"))
        val parsed = Updater.parseAssets(JSONArray("""[
          {"name":"Apple.Music-arm64-v8a.apk","browser_download_url":"$renamed","size":1000},
          {"name":"Apple.Music.apk","browser_download_url":"https://example.com/foreign.apk","size":999}
        ]"""))
        assertEquals(1, parsed.size)
        assertEquals(renamed, Updater.selectUpdateAsset(parsed, "arm64-v8a", "gms")?.downloadUrl)
    }

    @Test fun renamedRepositoryApiIsUsedWhenOldPathIsGone() = runBlocking {
        val requests = mutableListOf<String>()
        val httpClient = HttpClient(MockEngine { request ->
            requests.add(request.url.toString())
            when {
                request.url.encodedPath.endsWith("/releases/latest") &&
                    request.url.encodedPath.contains("/Apple-Music/") ->
                    respond("""{
                      "tag_name":"v13.7.0.136","published_at":"2026-11-22T00:00:00Z","body":"Correctifs",
                      "assets":[
                        {"name":"Apple.Music.apk",
                         "browser_download_url":"https://github.com/anderson76389/Apple-Music/releases/download/v13.7.0.136/Apple.Music.apk",
                         "size":120},
                        {"name":"BUILD.txt",
                         "browser_download_url":"https://github.com/anderson76389/Apple-Music/releases/download/v13.7.0.136/BUILD.txt",
                         "size":90}
                      ]}""", HttpStatusCode.OK)
                request.url.encodedPath.endsWith("/releases/latest") ->
                    respond("not found", HttpStatusCode.NotFound)
                else -> respond("MUSIC_VERSION_CODE=100136\n", HttpStatusCode.OK)
            }
        })
        try {
            val release = Updater.fetchLatestRelease(httpClient)
            assertEquals("v13.7.0.136", release.tagName)
            assertEquals(100136L, release.versionCode)
            assertEquals(1, release.assets.size)
            assertEquals("Apple.Music.apk", release.assets.single().name)
            assertTrue(requests.any { it.contains("/anderson76389/Music/releases/latest") })
            assertTrue(requests.any { it.contains("/anderson76389/Apple-Music/releases/latest") })
        } finally { httpClient.close() }
    }

    @Test fun githubNormalizesSpacesToDotsAndLegacyClientsStillFindAnApk() {
        // GitHub Releases publishes "Apple Music.apk" as "Apple.Music.apk".
        // The historical filename is also published so apps before this fix
        // can continue updating in place with their existing signing key.
        val newUrl = "https://github.com/anderson76389/Music/releases/download/v13.7.0.108/Apple.Music.apk"
        val legacyUrl = "https://github.com/anderson76389/Music/releases/download/v13.7.0.108/Metrolist.apk"
        val assets = Updater.parseAssets(JSONArray("""
          [
            {"name":"Metrolist.apk","browser_download_url":"$legacyUrl","size":120},
            {"name":"Apple.Music.apk","browser_download_url":"$newUrl","size":120}
          ]
        """))
        assertEquals(2, assets.size)
        assertEquals("universal", assets.single { it.name == "Apple.Music.apk" }.architecture)
        assertEquals("gms", assets.single { it.name == "Apple.Music.apk" }.variant)
        assertTrue(Updater.isOwnApkUrl(newUrl))
        assertEquals(newUrl, Updater.selectUpdateAsset(assets, "arm64-v8a", "foss")?.downloadUrl)
        assertEquals(newUrl, Updater.selectUpdateAsset(assets, "universal", "gms")?.downloadUrl)
        assertEquals(legacyUrl, Updater.selectUpdateAsset(listOf(assets.first()), "universal", "gms")?.downloadUrl)
        assertFalse(Updater.isOwnApkUrl("https://github.com.evil.invalid/anderson76389/Music/releases/download/v1/Apple.Music.apk"))
        assertFalse(Updater.isOwnApkUrl("http://github.com/anderson76389/Music/releases/download/v1/Apple.Music.apk"))
    }

    @Test fun deviceAbiGetsSmallerSignedPackageAndOthersKeepUniversalFallback() {
        val prefix = "https://github.com/anderson76389/Music/releases/download/v13.7.0.123/"
        val assets = Updater.parseAssets(JSONArray("""
            [
                {"name":"Apple.Music.apk","browser_download_url":"${prefix}Apple.Music.apk","size":224000000},
                {"name":"Apple.Music-arm64-v8a.apk","browser_download_url":"${prefix}Apple.Music-arm64-v8a.apk","size":98000000},
                {"name":"Apple.Music-armeabi-v7a.apk","browser_download_url":"${prefix}Apple.Music-armeabi-v7a.apk","size":88000000},
                {"name":"Apple.Music-x86_64.apk","browser_download_url":"${prefix}Apple.Music-x86_64.apk","size":100000000},
                {"name":"Apple.Music-x86.apk","browser_download_url":"${prefix}Apple.Music-x86.apk","size":94000000},
                {"name":"Apple.Music-unknown.apk","browser_download_url":"${prefix}Apple.Music-unknown.apk","size":100000},
                {"name":"Apple.Music-arm64-v8a.apk","browser_download_url":"https://example.com/foreign.apk","size":10}
            ]
        """))
        assertEquals(5, assets.size)
        assertEquals("Apple.Music-arm64-v8a.apk", Updater.selectUpdateAsset(assets, "arm64-v8a", "gms")?.name)
        assertEquals("Apple.Music-armeabi-v7a.apk", Updater.selectUpdateAsset(assets, "armeabi-v7a", "gms")?.name)
        assertEquals("Apple.Music-x86_64.apk", Updater.selectUpdateAsset(assets, "x86_64", "gms")?.name)
        assertEquals("Apple.Music-x86.apk", Updater.selectUpdateAsset(assets, "x86", "gms")?.name)
        assertEquals("Apple.Music.apk", Updater.selectUpdateAsset(assets, "mips64", "gms")?.name)
        assertEquals("Apple.Music.apk", Updater.selectUpdateAsset(assets, "arm64-v8a", "foss")?.name)
        assertEquals("universal", assets.first().architecture)
        assertEquals("gms", assets[1].variant)
    }

    @Test fun latestReleaseReadsPublishedAndroidCodeAndApk() = runBlocking {
        val client = HttpClient(MockEngine { request ->
            if (request.url.encodedPath.endsWith("/releases/latest")) respond("""{
              "tag_name":"v13.7.0.12","published_at":"2026-10-23T00:00:00Z","body":"Mise à jour",
              "assets":[
                {"name":"Metrolist.apk","browser_download_url":"https://github.com/anderson76389/Music/releases/download/v13.7.0.12/Metrolist.apk","size":100},
                {"name":"BUILD.txt","browser_download_url":"https://github.com/anderson76389/Music/releases/download/v13.7.0.12/BUILD.txt","size":80}
              ]} """, HttpStatusCode.OK)
            else respond("MUSIC_VERSION_CODE=10012\n", HttpStatusCode.OK)
        })
        try {
            val release = Updater.fetchLatestRelease(client)
            assertEquals(10012L, release.versionCode)
            assertEquals("Metrolist.apk", Updater.selectUpdateAsset(release.assets, "universal", "foss")?.name)
        } finally { client.close() }
    }

    @Test fun ownUniversalApkUpdatesBothPreviouslyPublishedVariants() {
        val url = "https://github.com/anderson76389/Music/releases/download/v13.7.0.12/Metrolist.apk"
        val assets = Updater.parseAssets(JSONArray("""[{"name":"Metrolist.apk","browser_download_url":"$url","size":100}]"""))
        assertEquals(url, Updater.selectUpdateAsset(assets, "arm64-v8a", "foss")?.downloadUrl)
        assertEquals(url, Updater.selectUpdateAsset(assets, "universal", "gms")?.downloadUrl)
        assertFalse(Updater.isOwnApkUrl("https://github.com.evil.test/anderson76389/Music/releases/download/v1/Metrolist.apk"))
        assertFalse(Updater.isOwnApkUrl("http://github.com/anderson76389/Music/releases/download/v1/Metrolist.apk"))
        assertFalse(Updater.isOwnApkUrl("https://github.com/other/project/releases/download/v1/Metrolist.apk"))
        assertTrue(Updater.isUpdateAvailable("13.7.0.11", "13.7.0.12"))
        assertFalse(Updater.isUpdateAvailable("13.7.0.12", "13.7.0.11"))
    }

    @Test fun selectsOwnVariantAndRejectsForeignDownloads() {
        val assets = Updater.parseAssets(JSONArray("""[
          {"name":"MAKOFF-Music.apk","browser_download_url":"https://github.com/anderson76389/Music/releases/download/v13.7.0/MAKOFF-Music.apk","size":42},
          {"name":"MAKOFF-Music-Google-Cast.apk","browser_download_url":"https://github.com/anderson76389/Music/releases/download/v13.7.0/MAKOFF-Music-Google-Cast.apk","size":43},
          {"name":"MAKOFF-Music.apk","browser_download_url":"https://example.com/foreign.apk","size":44},
          {"name":"MAKOFF-Music-universal.apk","browser_download_url":"https://github.com/anderson76389/Music/releases/download/v13.7.0/MAKOFF-Music-universal.apk","size":45},
          {"name":"Metrolist.apk","browser_download_url":"https://github.com/anderson76389/Music/releases/download/v13.7.0/Metrolist.apk","size":46}
        ]"""))
        assertEquals(4, assets.size)
        assertEquals("gms", assets[3].variant)
        assertEquals("universal", assets[3].architecture)
        assertEquals("gms", assets[2].variant)
        assertEquals("universal", assets[2].architecture)
        assertEquals("foss", assets[0].variant)
        assertEquals("gms", assets[1].variant)
        assertEquals("universal", assets[0].architecture)
        assertTrue(Updater.isUpdateAvailable("13.7.0", "13.7.1"))
        assertFalse(Updater.isUpdateAvailable("13.7.0", "13.7.0"))
        assertEquals("https://api.github.com/repos/anderson76389/Music", Updater.GITHUB_API_BASE)
    }
    @Test fun historyIncludesTestAndFutureReleasesOnDebugBuild() = runBlocking {
        val requested = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            requested.add(request.url.toString())
            respond("""[
              {"tag_name":"v13.7.0","published_at":"2026-10-01T00:00:00Z","body":"Ancien","assets":[]},
              {"tag_name":"test-20-abc1234","prerelease":true,"published_at":"2026-10-17T00:00:00Z","body":"Mes changements","assets":[]},
              {"tag_name":"v99.0.0","published_at":"2026-10-18T00:00:00Z","body":null,"assets":[]},
              {"tag_name":"draft","draft":true,"published_at":"2026-10-19T00:00:00Z","assets":[]}
            ]""", HttpStatusCode.OK)
        })
        try {
            val releases = Updater.fetchReleaseHistory(client)
            assertEquals(listOf("v99.0.0", "test-20-abc1234", "v13.7.0"), releases.map { it.tagName })
            assertEquals("Mes changements", releases[1].description)
            assertEquals("", releases[0].description)
            assertEquals(listOf("${Updater.GITHUB_API_BASE}/releases?page=1&per_page=30"), requested)
        } finally { client.close() }
    }

    @Test fun historyRejectsGithubRateLimitInsteadOfShowingAnEmptyHistory() = runBlocking {
        val client = HttpClient(MockEngine { respond("{\"message\":\"rate limit\"}", HttpStatusCode.Forbidden) })
        try {
            val result = runCatching { Updater.fetchReleaseHistory(client) }
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("403"))
        } finally { client.close() }
    }
}
