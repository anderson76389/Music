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
