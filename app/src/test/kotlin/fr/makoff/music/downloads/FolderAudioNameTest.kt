package fr.makoff.music.downloads

import org.junit.Assert.*
import org.junit.Test

class FolderAudioNameTest {
    @Test fun namesCannotEscapeTheChosenFolder() {
        val name = audioFileName("../../a/b\n:c*?", "../artiste", "BaW_jenozKc", "webm")
        assertFalse(name.contains('/'))
        assertFalse(name.contains('\\'))
        assertFalse(name.contains('\n'))
        assertTrue(name.endsWith("[BaW_jenozKc].webm"))
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
    @Test fun cookiesAreDomainScopedAndCannotInjectJarRows() {
        val jar = youtubeCookieJar("SID=secret; invalid=bad\n.evil.com; SAPISID=ok")
        assertTrue(jar.contains(".youtube.com\tTRUE\t/\tTRUE\t0\tSID\tsecret"))
        assertFalse(jar.contains("evil.com"))
        assertTrue(jar.contains("SAPISID\tok"))
    }
    @Test fun utf8NamesStayWithinFilesystemLimits() {
        val name = audioFileName("🎵".repeat(200), "artiste", "BaW_jenozKc", "m4a")
        assertTrue(name.toByteArray(Charsets.UTF_8).size < 255)
        assertEquals("audio/mp4", audioMime("m4a"))
        assertEquals("audio/webm", audioMime("webm"))
    }
}
