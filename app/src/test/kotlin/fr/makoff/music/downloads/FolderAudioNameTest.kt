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
