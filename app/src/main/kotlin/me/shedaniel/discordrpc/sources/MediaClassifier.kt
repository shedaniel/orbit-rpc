package me.shedaniel.discordrpc.sources

import me.shedaniel.discordrpc.core.model.MediaKind

/**
 * Curated package table. Overrides whatever the app declares in its manifest category, because many
 * media apps declare nothing (or the wrong thing).
 */
object KnownApps {
    const val SPOTIFY = "com.spotify.music"
    const val YOUTUBE = "com.google.android.youtube"
    const val YOUTUBE_MUSIC = "com.google.android.apps.youtube.music"

    private val music = setOf(
        SPOTIFY, "com.spotify.lite",
        YOUTUBE_MUSIC,
        "app.revanced.android.apps.youtube.music", "app.rvx.android.apps.youtube.music",
        "com.vanced.android.apps.youtube.music",
        "com.apple.android.music", "deezer.android.app", "com.aspiro.tidal", "com.soundcloud.android",
        "com.amazon.mp3", "com.pandora.android", "com.maxmpz.audioplayer", "in.krosbits.musicolet",
        "code.name.monkey.retromusic", "io.github.muntashirakon.Music", "com.sec.android.app.music",
        "com.piyush.music", "app.symfonik.music.player", "com.msob7y.namida", "com.qobuz.music",
        "com.napster.android", "com.anghami", "com.jio.media.jiobeats", "com.gaana", "com.shazam.android",
        "com.bandcamp.android", "com.audiomack", "com.google.android.music", "com.miui.player",
        "com.huawei.music", "com.oneplus.music", "com.vivo.musicplayer", "com.oppo.music",
        "com.tencent.qqmusic", "com.netease.cloudmusic", "com.kugou.android", "com.kapp.youtube.final",
        "com.zionhuang.music", "it.vfsfitvnm.vimusic", "app.vitune.android",
        "org.oxycblt.auxio", "com.simplemobiletools.musicplayer", "org.fossify.musicplayer",
        "com.doubleTwist.cloudPlayer", "com.jetappfactory.jetaudio", "com.neutroncode.mp", "com.awedea.nyx",
        "ru.yandex.music", "com.vkontakte.android.music", "com.melodis.midomiMusicIdentifier.freemium",
        "com.plexapp.plexamp", "com.lastfm.lastfmandroid", "org.jellyfin.finamp", "com.ultrasonic",
        "org.moire.ultrasonic",
    )
    private val video = setOf(
        YOUTUBE, "app.revanced.android.youtube", "app.rvx.android.youtube", "com.vanced.android.youtube",
        "com.google.android.apps.youtube.kids", "com.google.android.youtube.tv",
        "org.schabi.newpipe", "org.polymorphicshade.newpipe", "org.polymorphicshade.tubular",
        "com.github.libretube", "app.futo.grayjay", "com.futo.platformplayer", "free.rm.skytube.oss",
        "com.netflix.mediaclient", "com.amazon.avod.thirdpartyclient", "com.amazon.avod",
        "com.disney.disneyplus", "com.wbd.stream", "com.hbo.hbonow", "com.hulu.plus",
        "com.crunchyroll.crunchyroid", "com.plexapp.android", "org.jellyfin.mobile",
        "org.jellyfin.androidtv", "org.xbmc.kodi", "org.videolan.vlc", "com.mxtech.videoplayer.ad",
        "com.mxtech.videoplayer.pro", "tv.twitch.android.app", "tv.danmaku.bili", "com.bstar.intl",
        "com.cbs.app", "com.peacocktv.peacockandroid", "com.apple.atve.androidtv.appletv",
        "com.apple.atve.amazon.appletv", "com.stremio.one", "com.strem.io", "is.xyz.mpv",
        "com.firecore.infuse", "com.google.android.videos", "com.google.android.apps.youtube.creator",
        "com.vimeo.android.videoapp", "com.dailymotion.dailymotion", "com.zhiliaoapp.musically",
        "com.ss.android.ugc.trill", "com.kick.mobile", "com.mubi", "com.tubitv", "com.pluto.tv",
        "com.showtime.standalone", "com.starz.starzplay.android", "com.discovery.discoveryplus.mobile",
        "air.com.vudu.air.DownloaderTablet", "com.sling", "com.fubo.firetv.screen", "com.fubotv.android",
        "com.espn.score_center", "com.dazn", "com.funimation.FunimationNow", "com.viki.android",
        "in.startv.hotstar", "com.jio.jioplay.tv", "com.zee5.mobile", "com.sonyliv",
        "com.iqiyi.i18n", "com.tencent.qqlive", "com.youku.phone", "com.bilibili.app.in",
        "com.archos.mediacenter.videofree", "org.courville.nova",
        "com.brouken.player", "dev.anilbeesetti.nextplayer", "app.marlboroadvance.mpvex",
        
    )
    private val podcast = setOf(
        "au.com.shiftyjelly.pocketcasts", "de.danoeh.antennapod", "com.bambuna.podcastaddict",
        "com.google.android.apps.podcasts",
        "fm.castbox.audiobook.radio.podcast", "com.podcast.podcasts", "com.stitcher.app",
        "com.clearchannel.iheartradio.controller", "app.podcast.cosmos",
        "fm.player", "com.audioboom.android", "tunein.player",
        "com.spreaker.android", "mobi.beyondpod", "com.reallybadapps.podcastguru",
        "allen.town.focus.podcast", "com.podverse.app", "ac.mdiq.podcini.R",
    )
    private val audiobook = setOf(
        "com.audible.application", "com.overdrive.mobile.android.libby", "ak.alizandro.smartaudiobookplayer",
        "de.ph1b.audiobook", "com.storytel", "com.bookbeat", "com.google.android.apps.books",
        "com.kobo.books.ereader", "com.everand.app", "com.scribd.app.reader0", "com.audiobooks.androidapp",
        "com.chirpbooks.chirp", "com.hoopladigital.android", "com.librivox.android", "net.libri.audiobook",
        "com.nextory.app", "com.audiobookshelf.app", "com.bookmate",
    )
    private val browsers = setOf(
        "com.android.chrome", "com.chrome.beta", "com.chrome.dev", "com.chrome.canary",
        "org.chromium.chrome", "org.mozilla.firefox", "org.mozilla.firefox_beta", "org.mozilla.fenix",
        "org.mozilla.focus", "org.mozilla.fennec_fdroid", "io.github.forkmaintainers.iceraven",
        "com.brave.browser", "com.brave.browser_beta", "com.brave.browser_nightly",
        "com.microsoft.emmx", "com.opera.browser", "com.opera.mini.native", "com.opera.gx",
        "com.vivaldi.browser", "com.duckduckgo.mobile.android", "com.sec.android.app.sbrowser",
        "com.kiwibrowser.browser", "app.vanadium.browser", "org.bromite.bromite", "com.cromite.cromite",
        "com.mi.globalbrowser", "com.UCMobile.intl", "net.waterfox.android.release",
        "org.torproject.torbrowser", "us.spotco.mulch", "com.ecosia.android",
    )

    /** Known category for [pkg], or null when the table doesn't know it. */
    fun category(pkg: String): AppCategory? = when (pkg) {
        in music -> AppCategory.MUSIC
        in video -> AppCategory.VIDEO
        in podcast, in audiobook -> AppCategory.PODCAST
        else -> null
    }

    /** Known media kind for [pkg]; finer than [category] (audiobooks are separate). */
    fun kind(pkg: String): MediaKind? = when (pkg) {
        in music -> MediaKind.MUSIC
        in video -> MediaKind.VIDEO
        in podcast -> MediaKind.PODCAST
        in audiobook -> MediaKind.AUDIOBOOK
        else -> null
    }

    fun isBrowser(pkg: String) = pkg in browsers
    fun isYouTubeMusic(pkg: String) = pkg == YOUTUBE_MUSIC || (pkg.contains("youtube.music"))
    fun isYouTube(pkg: String) = !isYouTubeMusic(pkg) && (pkg == YOUTUBE || pkg.endsWith(".android.youtube"))
}

/** The parts of a media session that classification looks at. Plain values so it's unit-testable. */
data class MediaFacts(
    val packageName: String,
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val albumArtist: String? = null,
    val durationMs: Long? = null,
    val mediaId: String? = null,
    val genre: String? = null,
    val trackNumber: Long = 0,
    /** Category the catalog derived (manifest + table), if the app is known. */
    val appCategory: AppCategory? = null,
)

object MediaClassifier {
    private const val LONG_MS = 20 * 60_000L

    fun classify(f: MediaFacts): MediaKind {
        val genre = f.genre?.lowercase().orEmpty()
        val id = f.mediaId?.lowercase().orEmpty()
        val podcastHint = "podcast" in genre || id.startsWith("spotify:episode:") || id.startsWith("spotify:show:") ||
            "/episode" in id || id.startsWith("episode")
        val audiobookHint = "audiobook" in genre || "audio book" in genre || id.startsWith("spotify:audiobook:") ||
            id.startsWith("spotify:chapter:")

        val known = KnownApps.kind(f.packageName)
        when {
            // Music apps that also carry podcasts/audiobooks (Spotify, YT Music...) tell us via ids/genre.
            known == MediaKind.MUSIC && audiobookHint -> return MediaKind.AUDIOBOOK
            known == MediaKind.MUSIC && podcastHint -> return MediaKind.PODCAST
            known != null -> return known
        }

        val album = f.album?.takeIf { it.isNotBlank() }
        val looksLikeMusic = album != null || f.trackNumber > 0 || !f.albumArtist.isNullOrBlank() ||
            id.startsWith("spotify:track:") || "content://media/external/audio" in id || id.contains("/audio/media")

        if (KnownApps.isBrowser(f.packageName)) {
            // music.youtube.com and most web players fill in the album; YouTube in a tab doesn't.
            return if (looksLikeMusic && !podcastHint) MediaKind.MUSIC else if (podcastHint) MediaKind.PODCAST else MediaKind.VIDEO
        }

        if (audiobookHint) return MediaKind.AUDIOBOOK
        if (podcastHint) return MediaKind.PODCAST

        when (f.appCategory) {
            AppCategory.MUSIC -> return MediaKind.MUSIC
            AppCategory.VIDEO -> return MediaKind.VIDEO
            AppCategory.PODCAST -> return MediaKind.PODCAST
            else -> {}
        }

        if (looksLikeMusic) return MediaKind.MUSIC
        val d = f.durationMs?.takeIf { it > 0 }
        if (d != null && d > LONG_MS) {
            // Long and album-less: a show/podcast if it names an author, otherwise probably a video.
            return if (!f.artist.isNullOrBlank()) MediaKind.PODCAST else MediaKind.VIDEO
        }
        if (d != null && !f.artist.isNullOrBlank()) return MediaKind.MUSIC
        return MediaKind.UNKNOWN
    }
}

/** Public links derived from session metadata. */
object MediaLinks {
    private val spotifyUri = Regex("""spotify:(track|episode):([A-Za-z0-9]{22})""")
    private val spotifyUrl = Regex("""open\.spotify\.com/(track|episode)/([A-Za-z0-9]{22})""")
    private val ytUrl = Regex("""(?:youtube\.com/watch\?(?:.*&)?v=|youtu\.be/)([A-Za-z0-9_-]{11})""")
    private val ytId = Regex("""^[A-Za-z0-9_-]{11}$""")

    /**
     * @param candidates other string values from metadata / session extras that may contain an id.
     */
    fun find(pkg: String, mediaId: String?, candidates: Collection<String> = emptyList()): String? {
        val all = listOfNotNull(mediaId) + candidates
        for (s in all) {
            (spotifyUri.find(s) ?: spotifyUrl.find(s))?.let { m ->
                return "https://open.spotify.com/${m.groupValues[1]}/${m.groupValues[2]}"
            }
        }
        val yt = KnownApps.isYouTube(pkg) || KnownApps.isYouTubeMusic(pkg)
        for (s in all) {
            ytUrl.find(s)?.let { return youtube(pkg, it.groupValues[1]) }
        }
        if (yt) {
            mediaId?.trim()?.let { if (ytId.matches(it)) return youtube(pkg, it) }
        }
        return null
    }

    private fun youtube(pkg: String, id: String) =
        if (KnownApps.isYouTubeMusic(pkg)) "https://music.youtube.com/watch?v=$id" else "https://www.youtube.com/watch?v=$id"
}
