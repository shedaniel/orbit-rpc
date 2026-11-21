package me.shedaniel.discordrpc.discord

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.Normalizer

/** A search hit from a public catalog. */
data class ArtworkCandidate(val title: String?, val artist: String?, val album: String?, val artworkUrl: String?)

/**
 * String normalisation and conservative matching for catalog lookups. We'd rather show no art than the
 * wrong art, so a title match is always required, plus an artist match (or, without an artist, an album match).
 */
object ArtworkMatch {
    private val marks = Regex("\\p{M}+")
    private val brackets = Regex("[(\\[{【（][^)\\]}】）]*[)\\]}】）]")
    private val versionSuffix = Regex("\\s[-–—]\\s.*\\b(remaster(ed)?|live|version|edit|mix|mono|stereo|deluxe|bonus|acoustic|radio|single|demo|instrumental)\\b.*$")
    private val feat = Regex("\\s(feat|ft|featuring|with)\\.?\\s.*$")
    private val topic = Regex("\\s[-–—]\\stopic$")
    private val nonWord = Regex("[^\\p{L}\\p{N}]+")
    private val artistSplit = Regex("\\s*(,|&|;|/|\\+|\\sfeat\\.?\\s|\\sft\\.?\\s|\\sfeaturing\\s|\\sand\\s|\\swith\\s|、)\\s*", RegexOption.IGNORE_CASE)

    fun normalize(s: String?): String {
        if (s.isNullOrBlank()) return ""
        var t = Normalizer.normalize(s, Normalizer.Form.NFKD).replace(marks, "").lowercase().trim()
        t = t.replace(topic, "")
        t = t.replace(brackets, " ")
        t = t.replace(versionSuffix, "")
        t = t.replace(feat, "")
        t = t.replace(nonWord, " ").trim().replace(Regex("\\s+"), " ")
        return t
    }

    fun artists(s: String?): Set<String> {
        if (s.isNullOrBlank()) return emptySet()
        val base = s.replace(topic, "")
        return base.split(artistSplit).map { normalize(it) }.filter { it.isNotEmpty() }.toSet()
    }

    fun titleMatches(ours: String?, theirs: String?): Boolean {
        val a = normalize(ours)
        return a.isNotEmpty() && a == normalize(theirs)
    }

    fun artistMatches(ours: String?, theirs: String?): Boolean {
        val a = normalize(ours)
        val b = normalize(theirs)
        if (a.isEmpty() || b.isEmpty()) return false
        if (a == b) return true
        if (artists(ours).intersect(artists(theirs)).isNotEmpty()) return true
        // "Artist" vs "Artist & Someone" where the split didn't catch it.
        return (a.length >= 4 && b.startsWith("$a ")) || (b.length >= 4 && a.startsWith("$b "))
    }

    /** The best acceptable candidate, or null. */
    fun pick(candidates: List<ArtworkCandidate>, title: String, artist: String?, album: String?): ArtworkCandidate? {
        var best: ArtworkCandidate? = null
        var bestScore = 0
        for (c in candidates) {
            if (c.artworkUrl.isNullOrBlank()) continue
            if (!titleMatches(title, c.title)) continue
            val artistOk = artistMatches(artist, c.artist)
            val albumOk = !album.isNullOrBlank() && normalize(album) == normalize(c.album)
            if (!artistOk && !(artist.isNullOrBlank() && albumOk) && !(albumOk && normalize(album).length >= 4)) continue
            val score = 1 + (if (artistOk) 2 else 0) + (if (albumOk) 1 else 0)
            if (score > bestScore) {
                best = c
                bestScore = score
            }
        }
        return best
    }

    /** Podcasts: match the show (collection) name against artist/album. */
    fun pickPodcast(candidates: List<ArtworkCandidate>, show: String): ArtworkCandidate? {
        val s = normalize(show)
        if (s.isEmpty()) return null
        return candidates.firstOrNull { !it.artworkUrl.isNullOrBlank() && (normalize(it.album) == s || normalize(it.title) == s) }
    }

    /** A cache key from the identifying fields. */
    fun cacheKey(title: String, artist: String?, album: String?) = "${normalize(artist)}|${normalize(title)}|${normalize(album)}"

    /** Search term with noise removed (brackets, feat.), which catalogs match better. */
    fun searchTerm(title: String, artist: String?): String {
        fun clean(s: String) = s.replace(brackets, " ").replace(feat, "").replace(topic, "").trim().replace(Regex("\\s+"), " ")
        return listOfNotNull(artist?.let { clean(it) }?.takeIf { it.isNotEmpty() }, clean(title).takeIf { it.isNotEmpty() }).joinToString(" ")
    }

    // ---- catalog response parsing ----

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Serializable
    private class ItunesResponse(val results: List<ItunesItem> = emptyList())

    @Serializable
    private class ItunesItem(
        val trackName: String? = null,
        val artistName: String? = null,
        val collectionName: String? = null,
        val artworkUrl100: String? = null,
        val artworkUrl600: String? = null,
    )

    fun parseItunes(body: String, podcast: Boolean = false): List<ArtworkCandidate> =
        json.decodeFromString(ItunesResponse.serializer(), body).results.map {
            val art = it.artworkUrl600 ?: it.artworkUrl100?.replace("100x100", "600x600")
            if (podcast) ArtworkCandidate(it.collectionName, it.artistName, it.collectionName, art)
            else ArtworkCandidate(it.trackName, it.artistName, it.collectionName, art)
        }

    @Serializable
    private class DeezerResponse(val data: List<DeezerItem> = emptyList())

    @Serializable
    private class DeezerItem(val title: String? = null, val artist: DeezerArtist? = null, val album: DeezerAlbum? = null)

    @Serializable
    private class DeezerArtist(val name: String? = null)

    @Serializable
    private class DeezerAlbum(val title: String? = null, @SerialName("cover_xl") val coverXl: String? = null, @SerialName("cover_big") val coverBig: String? = null)

    fun parseDeezer(body: String): List<ArtworkCandidate> =
        json.decodeFromString(DeezerResponse.serializer(), body).data.map {
            ArtworkCandidate(it.title, it.artist?.name, it.album?.title, it.album?.coverXl ?: it.album?.coverBig)
        }

    /** Extracts the Play Store listing icon (og:image) and asks for a 512 px rendition. */
    fun playIconFrom(html: CharSequence): String? {
        val i = html.indexOf(OG_IMAGE)
        if (i < 0) return null
        val start = i + OG_IMAGE.length
        val end = html.indexOf("\"", start)
        if (end < 0) return null
        val url = html.substring(start, end).replace("&amp;", "&")
        if (!url.startsWith("https://play-lh.googleusercontent.com/")) return null
        return url.substringBefore('=') + "=s512"
    }

    const val OG_IMAGE = "property=\"og:image\" content=\""
}
