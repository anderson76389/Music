package fr.makoff.music.innertube

import fr.makoff.music.innertube.models.Artist
import fr.makoff.music.innertube.models.SongItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoCounterpartParserTest {
    private val songId = "ZrOKjDZOtkA"
    private val clipId = "bx1Bh8ZvH84"

    private fun wrappedQueue(primary: String, companion: String) = """
        {
          "primaryRenderer": { "playlistPanelVideoRenderer": { "videoId": "$primary" } },
          "counterpart": [
            {
              "counterpartRenderer": {
                "playlistPanelVideoRenderer": {
                  "videoId": "$companion",
                  "navigationEndpoint": {
                    "watchEndpoint": {
                      "watchEndpointMusicSupportedConfigs": {
                        "watchEndpointMusicConfig": { "musicVideoType": "MUSIC_VIDEO_TYPE_OMV" }
                      }
                    }
                  }
                }
              }
            }
          ]
        }
    """.trimIndent()

    @Test fun nextResponseFindsExplicitlyPairedClip() {
        val response = """
            {
              "contents": {
                "singleColumnMusicWatchNextResultsRenderer": {
                  "tabbedRenderer": { "watchNextTabbedResultsRenderer": {
                    "tabs": [ { "tabRenderer": {
                      "content": { "musicQueueRenderer": {
                        "content": { "playlistPanelRenderer": { "contents": [
                          { "playlistPanelVideoWrapperRenderer": ${wrappedQueue(songId, clipId)} }
                        ] } }
                      } }
                    } } ]
                  } }
                }
              }
            }
        """.trimIndent()
        assertEquals(clipId, VideoCounterpartParser.fromResponse(response, songId))
    }

    @Test fun getQueueResponseAlsoFindsExplicitPair() {
        val response = """
            { "queueDatas": [ { "content": {
              "playlistPanelVideoWrapperRenderer": ${wrappedQueue(songId, clipId)}
            } } ] }
        """.trimIndent()
        assertEquals(clipId, VideoCounterpartParser.fromResponse(response, songId))
        assertNull(VideoCounterpartParser.fromResponse(response, "abcdefghijk"))
    }

    @Test fun doesNotTreatAudioAsOfficialVideo() {
        val response = """
            { "queueDatas": [ { "content": {
              "playlistPanelVideoWrapperRenderer": {
                "primaryRenderer": {"playlistPanelVideoRenderer":{"videoId":"$songId"}},
                "counterpart": [{
                  "counterpartRenderer": {"playlistPanelVideoRenderer": {
                    "videoId":"$clipId",
                    "navigationEndpoint":{"watchEndpoint":{"watchEndpointMusicSupportedConfigs":{
                      "watchEndpointMusicConfig":{"musicVideoType":"MUSIC_VIDEO_TYPE_ATV"}
                    }}}
                  }}
                }]
              }
            } } ] }
        """.trimIndent()
        assertNull(VideoCounterpartParser.fromResponse(response, songId))
    }

    @Test fun searchFallbackRejectsCoverAndWrongArtist() {
        val artist = Artist("Oasis", null)
        fun song(id: String, title: String, name: String, type: String?) = SongItem(
            id = id,
            title = title,
            artists = listOf(Artist(name, null)),
            musicVideoType = type,
            thumbnail = "",
        )
        val candidates = listOf(
            song("abcdefghijk", "Wonderwall (Acoustic Cover)", "Oasis", "MUSIC_VIDEO_TYPE_OMV"),
            song("lmnopqrstuv", "Wonderwall (Official Music Video)", "Another Artist", "MUSIC_VIDEO_TYPE_OMV"),
            song(clipId, "Oasis - Wonderwall (Official Music Video)", artist.name, "MUSIC_VIDEO_TYPE_OMV"),
        )
        assertEquals(
            clipId,
            VideoCounterpartParser.verifiedSearchVideo(candidates, songId, "Wonderwall", artist.name),
        )
        assertNull(
            VideoCounterpartParser.verifiedSearchVideo(candidates.take(2), songId, "Wonderwall", artist.name),
        )
    }
}
