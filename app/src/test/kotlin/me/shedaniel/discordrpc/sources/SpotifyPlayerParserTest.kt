package me.shedaniel.discordrpc.sources

import me.shedaniel.discordrpc.core.model.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyPlayerParserTest {
    private val track = """
        {
          "device": {"id": "d1", "is_active": true, "is_private_session": false, "is_restricted": false,
                     "name": "Living Room", "type": "Speaker", "volume_percent": 40, "supports_volume": true},
          "shuffle_state": false, "repeat_state": "off", "timestamp": 1700000000000,
          "context": {"type": "album", "href": "x", "external_urls": {"spotify": "x"}, "uri": "spotify:album:1"},
          "progress_ms": 61000, "is_playing": true,
          "item": {
            "album": {"name": "Whenever You Need Somebody", "album_type": "album",
              "images": [{"url": "https://i.scdn.co/640", "height": 640, "width": 640},
                         {"url": "https://i.scdn.co/300", "height": 300, "width": 300},
                         {"url": "https://i.scdn.co/64", "height": 64, "width": 64}]},
            "artists": [{"name": "Rick Astley", "id": "a"}, {"name": "Someone Else", "id": "b"}],
            "duration_ms": 213573, "explicit": false,
            "external_urls": {"spotify": "https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT"},
            "id": "4cOdK2wGLETKBW3PvgPWqT", "name": "Never Gonna Give You Up", "type": "track",
            "uri": "spotify:track:4cOdK2wGLETKBW3PvgPWqT", "is_local": false
          },
          "currently_playing_type": "track",
          "actions": {"disallows": {"resuming": true}}
        }
    """.trimIndent()

    @Test fun parsesTrack() {
        val p = SpotifyPlayerParser.parse(track)!!
        assertEquals("Never Gonna Give You Up", p.title)
        assertEquals("Rick Astley, Someone Else", p.artist)
        assertEquals("Whenever You Need Somebody", p.album)
        assertEquals("https://i.scdn.co/300", p.imageUrl)
        assertEquals("https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT", p.link)
        assertEquals(213573L, p.durationMs)
        assertEquals(61000L, p.progressMs)
        assertTrue(p.isPlaying)
        assertEquals("Living Room", p.deviceName)
        assertEquals(MediaKind.MUSIC, p.kind)
    }

    @Test fun parsesEpisode() {
        val json = """
            {"device": {"name": "PC", "type": "Computer"}, "progress_ms": 1000, "is_playing": false,
             "currently_playing_type": "episode",
             "item": {"name": "Ep 1", "type": "episode", "id": "e1", "duration_ms": 3600000,
                      "images": [{"url": "https://i/ep640", "width": 640, "height": 640}],
                      "external_urls": {"spotify": "https://open.spotify.com/episode/e1"},
                      "show": {"name": "The Show", "publisher": "Pub", "images": []}}}
        """.trimIndent()
        val p = SpotifyPlayerParser.parse(json)!!
        assertEquals(MediaKind.PODCAST, p.kind)
        assertEquals("The Show", p.artist)
        assertEquals("https://i/ep640", p.imageUrl)
        assertEquals(false, p.isPlaying)
    }

    @Test fun nothingIdentifiable() {
        assertNull(SpotifyPlayerParser.parse("""{"is_playing": true, "item": null, "currently_playing_type": "ad"}"""))
        assertNull(SpotifyPlayerParser.parse("""{"device": {"is_private_session": true}, "item": {"name": "x"}}"""))
        assertNull(SpotifyPlayerParser.parse("not json"))
    }

    @Test fun pollsRightAfterTrackEnd() {
        val p = SpotifyPlayerParser.parse(track)!!
        // 152.5s left: the regular cadence wins.
        assertEquals(20_000L, SpotifyPlayerParser.nextPollDelayMs(p, 20_000, 120_000))
        val nearEnd = p.copy(progressMs = 205_000)
        assertEquals(213573L - 205_000 + 1500, SpotifyPlayerParser.nextPollDelayMs(nearEnd, 20_000, 120_000))
        assertEquals(3000L, SpotifyPlayerParser.nextPollDelayMs(p.copy(progressMs = 213_573), 20_000, 120_000))
        assertEquals(120_000L, SpotifyPlayerParser.nextPollDelayMs(p.copy(isPlaying = false), 20_000, 120_000))
        assertEquals(120_000L, SpotifyPlayerParser.nextPollDelayMs(null, 20_000, 120_000))
    }
}
