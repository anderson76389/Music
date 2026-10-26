package fr.makoff.music.utils

import org.junit.Assert.*
import org.junit.Test

class PlaylistImportTest {
    @Test fun quotedCsvPreservesCommasQuotesAndMultilineTitles() {
        val rows = PlaylistImport.csv("\uFEFFArtist,Title,URL\r\n\"A, B\",\"A \"\"quote\"\"\nsecond line\",https://youtu.be/abcdefghijk\r\n")
        assertEquals("A, B", rows[1][0])
        assertEquals("A \"quote\"\nsecond line", rows[1][1])
        assertEquals(2, PlaylistImport.headerIndex(rows[0], "url"))
        assertEquals("abcdefghijk", PlaylistImport.youtubeId(rows[1][2]))
    }
    @Test fun frenchSemicolonCsvAndHeaderlessFilesAreRecognized() {
        val rows = PlaylistImport.csv("Artiste;Titre;Lien\nA;B;https://music.youtube.com/watch?v=abcdefghijk")
        assertEquals(listOf("A", "B", "https://music.youtube.com/watch?v=abcdefghijk"), rows[1])
        assertEquals(0, PlaylistImport.headerIndex(rows[0], "artist"))
        assertEquals(-1, PlaylistImport.headerIndex(listOf("A", "B"), "title"))
    }
    @Test fun m3uKeepsExactYouTubeIdsAndMetadata() {
        val rows = PlaylistImport.m3u("\uFEFF#EXTM3U\n#EXTINF:90,Artist - Title\nhttps://music.youtube.com/watch?v=abcdefghijk\nhttps://youtu.be/lmnopqrstuv\n")
        assertEquals(listOf("abcdefghijk", "lmnopqrstuv"), rows.map { it.id })
        assertEquals("Title", rows[0].title)
        assertEquals(listOf("Artist"), rows[0].artists)
        assertEquals(90, rows[0].duration)
    }
    @Test fun foreignUrlsDoNotMasqueradeAsYouTubeIds() {
        assertNull(PlaylistImport.youtubeId("https://youtube.com.evil.test/watch?v=abcdefghijk"))
        assertNull(PlaylistImport.youtubeId("https://example.com/abcdefghijk"))
        assertEquals("abcdefghijk", PlaylistImport.youtubeId("https://www.youtube.com/shorts/abcdefghijk"))
    }
}
