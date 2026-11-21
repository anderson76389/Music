package fr.makoff.music.playback.addons

import fr.makoff.music.models.MediaMetadata
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioAddonsTest {
    @Test
    fun builtInSourceIsOptionalAndExistingPrioritySurvivesUpgrade() {
        val preset = AudioAddons.decode(null).single()
        assertEquals(AudioAddons.AUDIUS, preset.manifest)
        assertEquals(false, preset.enabled)
        val custom = AudioAddon("https://example.test/manifest.json", "Local", "LOSSLESS")
        assertEquals(listOf(custom, preset), AudioAddons.decode(Json.encodeToString(listOf(custom))))
        val reordered = listOf(preset.copy(enabled = true), custom)
        assertEquals(reordered, AudioAddons.decode(Json.encodeToString(reordered)))
    }

    @Test
    fun manifestKeepsPersonalPathAndQuery() {
        assertEquals(
            "https://example.test/personal/manifest.json?key=value",
            AudioAddons.manifestUrl("https://example.test/personal/?key=value").toString(),
        )
        assertEquals(
            "https://example.test/manifest.json",
            AudioAddons.manifestUrl("https://example.test/").toString(),
        )
        assertNull(AudioAddons.manifestUrl("http://example.test/"))
        assertNull(AudioAddons.manifestUrl("https://user:password@example.test/"))
    }

    @Test
    fun selectsTheRecordingByTitleArtistAndDuration() {
        val song = MediaMetadata(
            id = "sample",
            title = "Étoile",
            artists = listOf(MediaMetadata.Artist(null, "Example")),
            duration = 240,
        )
        val rows = Json.parseToJsonElement(
            """[
                {"id":"cover","title":"Étoile","artist":"Other","duration":240},
                {"id":"live","title":"Étoile (Live)","artist":"Example","duration":241},
                {"id":"preview","title":"Étoile","artist":"Example","duration":30},
                {"id":"studio","title":"Etoile","artist":"Example","duration":239.5}
            ]""",
        ).jsonArray
        assertEquals(rows[3], AudioAddons.matchingTrack(rows, song))
        assertNull(AudioAddons.matchingTrack(rows, song.copy(duration = 180)))
    }

    @Test
    fun onlyUsesUnencryptedDirectHttpsStreams() {
        fun url(body: String) = AudioAddons.progressiveUrl(Json.parseToJsonElement(body).jsonObject, "url")

        assertEquals("https://example.test/track.flac", url("""{"url":"https://example.test/track.flac"}""").toString())
        assertNull(url("""{"url":"https://example.test/track.flac","encrypted":true}"""))
        assertNull(url("""{"url":"https://example.test/track","manifest":"dash"}"""))
        assertNull(url("""{"url":"https://example.test/track.m3u8?token=123"}"""))
        assertNull(url("""{"url":"file:///storage/emulated/0/track.flac"}"""))
    }
}
