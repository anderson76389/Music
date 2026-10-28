package fr.makoff.music.downloads

import org.junit.Assert.*
import org.junit.Test

class FolderAudioNameTest {
    @Test fun likedDownloadsKeepTheirOwnFolderEvenWhenTheSongHasAnAlbum() {
        assertEquals(listOf("J'aime"), audioDestinationFolders(emptyList(), liked = true))
        assertEquals(listOf("J'aime"), audioDestinationFolders(listOf("Sélections", "Titres"), liked = true))
        assertEquals(listOf("J'aime"), audioDestinationFolders(listOf("J'aime"), liked = false))
        assertEquals(listOf("Audio"), audioDestinationFolders(emptyList(), liked = false))
        assertEquals(listOf("Albums", "Explicit album"), audioDestinationFolders(listOf("Albums", "Explicit album"), liked = true))
        for (id in listOf("liked", "LM", "VLLM")) {
            assertEquals(listOf("J'aime"), collectionAudioFolders("Playlists", "Localized likes title", id))
        }
        assertEquals(listOf("Playlists", "My playlist"), collectionAudioFolders("Playlists", "My playlist", "private-custom"))
    }

    @org.junit.Test fun exportArtworkUsesUncroppedSourcesWithoutChangingOtherHosts() {
        org.junit.Assert.assertEquals("https://lh3.googleusercontent.com/image=w1024",
            exportArtworkUrls("https://lh3.googleusercontent.com/image=w544-h544-l90-rj", "BaW_jenozKc").first())
        org.junit.Assert.assertEquals("https://i.ytimg.com/vi/BaW_jenozKc/maxresdefault.jpg",
            exportArtworkUrls("https://i.ytimg.com/vi/BaW_jenozKc/hq720.jpg?sqp=cropped", "BaW_jenozKc").first())
        org.junit.Assert.assertEquals("https://other.example/picture?size=512&token=abc",
            exportArtworkUrls("https://other.example/picture?size=512&token=abc", "BaW_jenozKc").first())
    }

    @Test fun namesCannotEscapeTheChosenFolder() {
        val name = audioFileName("../../a/b\n:c*?", "webm")
        assertFalse(name.contains('/'))
        assertFalse(name.contains('\\'))
        assertFalse(name.contains('\n'))
        assertTrue(name.endsWith(".webm"))
        assertFalse(name.contains("BaW_jenozKc"))
    }
    @Test fun collectionNamesAreReadableWithoutHashOrCombiningStacks() {
        assertEquals("See", collectionFolder("S\u030d\u0351e\u0334e\u036f", "album-id"))
        assertEquals("Album été", collectionFolder("𝔄𝔩𝔟𝔲𝔪 été", "album-id"))
        assertEquals("Mix été 2026", collectionFolder("Mix / été : \"2026\"", "album-id"))
        assertEquals("Album", collectionFolder("\u202eAlbum\u200b", "album-id"))
        assertFalse(collectionFolder("..", "album-id").contains('/'))
    }
    @Test fun unifiedFormatPreservesAnExplicitLegacyChoice() {
        val prefs = androidx.datastore.preferences.core.mutablePreferencesOf()
        prefs[fr.makoff.music.constants.InternalDownloadFormatKey] = "FLAC"
        prefs[fr.makoff.music.constants.ExternalDownloadFormatKey] = "BEST"
        assertEquals(DownloadAudioFormat.FLAC, prefs.selectedDownloadFormat())
        prefs[fr.makoff.music.constants.ExternalDownloadFormatKey] = "M4A"
        assertEquals(DownloadAudioFormat.M4A, prefs.selectedDownloadFormat())
        prefs[fr.makoff.music.constants.UnifiedDownloadFormatKey] = "BEST"
        assertEquals(DownloadAudioFormat.BEST, prefs.selectedDownloadFormat())
        for (format in DownloadAudioFormat.entries) {
            prefs[fr.makoff.music.constants.UnifiedDownloadFormatKey] = format.name
            assertEquals(format, prefs.selectedDownloadFormat())
        }
    }
    @Test fun readableFileNamesKeepLongTitlesAndNoTechnicalIdentifier() {
        assertEquals("W.opus", audioFileName("W", "opus"))
        val title = "You Are Working In Silence With The Full Original Title"
        assertEquals("$title.m4a", audioFileName(title, "m4a"))
    }
    @Test fun cookiesAreDomainScopedAndCannotInjectJarRows() {
        val jar = youtubeCookieJar("SID=secret; invalid=bad\n.evil.com; SAPISID=ok")
        assertTrue(jar.contains(".youtube.com\tTRUE\t/\tTRUE\t0\tSID\tsecret"))
        assertFalse(jar.contains("evil.com"))
        assertTrue(jar.contains("SAPISID\tok"))
    }
    @Test fun utf8NamesStayWithinFilesystemLimits() {
        val name = audioFileName("🎵".repeat(200), "m4a")
        assertTrue(name.toByteArray(Charsets.UTF_8).size < 255)
        assertEquals("audio/mp4", audioMime("m4a"))
        assertEquals("audio/webm", audioMime("webm"))
    }
}
