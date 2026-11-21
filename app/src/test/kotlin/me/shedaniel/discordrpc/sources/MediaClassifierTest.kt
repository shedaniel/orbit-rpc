package me.shedaniel.discordrpc.sources

import me.shedaniel.discordrpc.core.model.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaClassifierTest {
    private fun kind(pkg: String, title: String = "t", block: MediaFacts.() -> MediaFacts = { this }) =
        MediaClassifier.classify(MediaFacts(pkg, title).block())

    @Test fun knownApps() {
        assertEquals(MediaKind.MUSIC, kind(KnownApps.SPOTIFY))
        assertEquals(MediaKind.MUSIC, kind(KnownApps.YOUTUBE_MUSIC))
        assertEquals(MediaKind.VIDEO, kind(KnownApps.YOUTUBE))
        assertEquals(MediaKind.VIDEO, kind("app.revanced.android.youtube"))
        assertEquals(MediaKind.MUSIC, kind("app.revanced.android.apps.youtube.music"))
        assertEquals(MediaKind.PODCAST, kind("au.com.shiftyjelly.pocketcasts"))
        assertEquals(MediaKind.AUDIOBOOK, kind("com.audible.application"))
        assertEquals(MediaKind.VIDEO, kind("com.netflix.mediaclient"))
    }

    @Test fun youTubeMusicIsNotYouTube() {
        assertTrue(KnownApps.isYouTubeMusic(KnownApps.YOUTUBE_MUSIC))
        assertFalse(KnownApps.isYouTube(KnownApps.YOUTUBE_MUSIC))
        assertTrue(KnownApps.isYouTube(KnownApps.YOUTUBE))
        assertTrue(KnownApps.isYouTube("app.rvx.android.youtube"))
    }

    @Test fun spotifyPodcastsAndAudiobooks() {
        assertEquals(MediaKind.PODCAST, kind(KnownApps.SPOTIFY) { copy(mediaId = "spotify:episode:4rOoJ6Egrf8K2IrywzwOMk") })
        assertEquals(MediaKind.AUDIOBOOK, kind(KnownApps.SPOTIFY) { copy(mediaId = "spotify:audiobook:abc") })
        assertEquals(MediaKind.MUSIC, kind(KnownApps.SPOTIFY) { copy(mediaId = "spotify:track:4cOdK2wGLETKBW3PvgPWqT") })
        assertEquals(MediaKind.PODCAST, kind(KnownApps.YOUTUBE_MUSIC) { copy(genre = "Podcast") })
    }

    @Test fun browsers() {
        assertEquals(MediaKind.VIDEO, kind("com.android.chrome") { copy(artist = "Some Channel", durationMs = 600_000) })
        assertEquals(MediaKind.MUSIC, kind("org.mozilla.firefox") { copy(artist = "Artist", album = "Album") })
        assertEquals(MediaKind.VIDEO, kind("com.brave.browser"))
    }

    @Test fun categoryFromCatalog() {
        assertEquals(MediaKind.MUSIC, kind("x.y") { copy(appCategory = AppCategory.MUSIC) })
        assertEquals(MediaKind.VIDEO, kind("x.y") { copy(appCategory = AppCategory.VIDEO, album = "A") })
        // Explicit podcast hints win over the app category.
        assertEquals(MediaKind.PODCAST, kind("x.y") { copy(appCategory = AppCategory.MUSIC, genre = "Podcasts") })
    }

    @Test fun heuristicsForUnknownApps() {
        assertEquals(MediaKind.MUSIC, kind("x.y") { copy(album = "Album") })
        assertEquals(MediaKind.MUSIC, kind("x.y") { copy(trackNumber = 3) })
        assertEquals(MediaKind.MUSIC, kind("x.y") { copy(mediaId = "content://media/external/audio/media/12") })
        assertEquals(MediaKind.PODCAST, kind("x.y") { copy(genre = "Podcast") })
        assertEquals(MediaKind.AUDIOBOOK, kind("x.y") { copy(genre = "Audiobook") })
        assertEquals(MediaKind.PODCAST, kind("x.y") { copy(artist = "Show", durationMs = 45 * 60_000L) })
        assertEquals(MediaKind.VIDEO, kind("x.y") { copy(durationMs = 90 * 60_000L) })
        assertEquals(MediaKind.MUSIC, kind("x.y") { copy(artist = "Artist", durationMs = 200_000) })
        assertEquals(MediaKind.UNKNOWN, kind("x.y"))
    }

    @Test fun links() {
        assertEquals("https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT", MediaLinks.find(KnownApps.SPOTIFY, "spotify:track:4cOdK2wGLETKBW3PvgPWqT"))
        assertEquals("https://open.spotify.com/episode/4rOoJ6Egrf8K2IrywzwOMk", MediaLinks.find("x", null, listOf("https://open.spotify.com/episode/4rOoJ6Egrf8K2IrywzwOMk?si=1")))
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", MediaLinks.find(KnownApps.YOUTUBE, "dQw4w9WgXcQ"))
        assertEquals("https://music.youtube.com/watch?v=dQw4w9WgXcQ", MediaLinks.find(KnownApps.YOUTUBE_MUSIC, "dQw4w9WgXcQ"))
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", MediaLinks.find("com.android.chrome", null, listOf("https://youtu.be/dQw4w9WgXcQ")))
        // An 11-char id from an unrelated app is not a YouTube id.
        assertNull(MediaLinks.find("x.y", "abcdefghijk"))
    }
}
