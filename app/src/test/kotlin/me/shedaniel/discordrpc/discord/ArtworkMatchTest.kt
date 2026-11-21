package me.shedaniel.discordrpc.discord

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ArtworkMatchTest {
    @Test
    fun normalization() {
        assertEquals("yellow", ArtworkMatch.normalize("Yellow (Remastered 2011)"))
        assertEquals("yellow", ArtworkMatch.normalize("Yellow - Remastered 2011"))
        assertEquals("beyonce", ArtworkMatch.normalize("Beyoncé"))
        assertEquals("song", ArtworkMatch.normalize("Song feat. Someone"))
        assertEquals("song", ArtworkMatch.normalize("Song [Live]"))
        assertEquals("rock n roll", ArtworkMatch.normalize("Rock 'n' Roll!"))
        assertEquals("artist", ArtworkMatch.normalize("Artist - Topic"))
        // A hyphen that isn't a version suffix stays part of the title.
        assertEquals("anti hero", ArtworkMatch.normalize("Anti-Hero"))
        assertTrue(ArtworkMatch.normalize("夜に駆ける").isNotEmpty())
    }

    @Test
    fun artistMatching() {
        assertTrue(ArtworkMatch.artistMatches("Coldplay", "Coldplay"))
        assertTrue(ArtworkMatch.artistMatches("Daft Punk, Pharrell Williams", "Daft Punk & Pharrell Williams"))
        assertTrue(ArtworkMatch.artistMatches("Calvin Harris", "Calvin Harris feat. Rihanna"))
        assertFalse(ArtworkMatch.artistMatches("Adele", "Madeleine"))
        assertFalse(ArtworkMatch.artistMatches(null, "Adele"))
    }

    @Test
    fun picksExactMatchAndRejectsWrongArt() {
        val c = listOf(
            ArtworkCandidate("Yellow (Karaoke Version)", "Karaoke Hits", "Karaoke", "https://k"),
            ArtworkCandidate("Yellow", "Coldplay", "Parachutes", "https://right"),
        )
        assertEquals("https://right", ArtworkMatch.pick(c, "Yellow", "Coldplay", null)?.artworkUrl)
        // Title matches but artist doesn't: skip rather than show wrong art.
        assertNull(ArtworkMatch.pick(listOf(ArtworkCandidate("Yellow", "Someone Else", "X", "https://wrong")), "Yellow", "Coldplay", null))
        // Localized artist name, but title + album agree: accept.
        assertEquals(
            "https://jp",
            ArtworkMatch.pick(listOf(ArtworkCandidate("Yellow", "コールドプレイ", "Parachutes", "https://jp")), "Yellow", "Coldplay", "Parachutes")?.artworkUrl,
        )
        // Prefer the album match.
        val two = listOf(ArtworkCandidate("Song", "A", "Single", "https://single"), ArtworkCandidate("Song", "A", "Album", "https://album"))
        assertEquals("https://album", ArtworkMatch.pick(two, "Song", "A", "Album")?.artworkUrl)
    }

    @Test
    fun parsesCatalogResponses() {
        val itunes = """{"resultCount":1,"results":[{"trackName":"Yellow","artistName":"Coldplay","collectionName":"Parachutes",
            "artworkUrl100":"https://is1-ssl.mzstatic.com/image/thumb/a.jpg/100x100bb.jpg","extra":1}]}"""
        val i = ArtworkMatch.parseItunes(itunes).single()
        assertEquals("https://is1-ssl.mzstatic.com/image/thumb/a.jpg/600x600bb.jpg", i.artworkUrl)
        val deezer = """{"data":[{"title":"Yellow","artist":{"name":"Coldplay"},"album":{"title":"Parachutes","cover_xl":"https://cdn/xl.jpg"}}],"total":1}"""
        assertEquals("https://cdn/xl.jpg", ArtworkMatch.parseDeezer(deezer).single().artworkUrl)
        assertEquals("Coldplay Yellow", ArtworkMatch.searchTerm("Yellow (Remastered)", "Coldplay"))
    }

    @Test
    fun playIconExtraction() {
        val html = """<meta name="x"><meta property="og:image" content="https://play-lh.googleusercontent.com/AbC_d-1=w526-h296-rw"><meta>"""
        assertEquals("https://play-lh.googleusercontent.com/AbC_d-1=s512", ArtworkMatch.playIconFrom(html))
        assertNull(ArtworkMatch.playIconFrom("""<meta property="og:image" content="https://www.gstatic.com/android/market_images/web/play_prism_hlock_2x.png">"""))
        assertNull(ArtworkMatch.playIconFrom("<html>"))
    }

    @Test
    fun cacheIsLruAndPersists() {
        val c = AssetCache(maxEntries = 3)
        c["a"] = CachedAsset("1")
        c["b"] = CachedAsset("2")
        c["c"] = CachedAsset("3")
        c["a"] // touch
        c["d"] = CachedAsset("4")
        assertNull(c["b"])
        assertEquals(setOf("a", "c", "d"), c.keys().toSet())
        val f = File.createTempFile("cache", ".json")
        try {
            c["e"] = CachedAsset(null, exp = 0) // expired negative: not persisted
            c.save(f)
            val c2 = AssetCache(3).apply { load(f) }
            assertEquals("4", c2["d"]?.v)
            assertNull(c2["e"])
        } finally {
            f.delete()
        }
    }
}
