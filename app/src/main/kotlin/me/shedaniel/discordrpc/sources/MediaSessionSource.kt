package me.shedaniel.discordrpc.sources

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.shedaniel.discordrpc.BuildConfig
import me.shedaniel.discordrpc.core.model.Artwork
import me.shedaniel.discordrpc.core.model.MediaSignal
import me.shedaniel.discordrpc.core.model.Signal
import me.shedaniel.discordrpc.core.model.SignalOrigin
import me.shedaniel.discordrpc.core.settings.AppSettings
import me.shedaniel.discordrpc.core.util.EventLog
import java.security.MessageDigest

/**
 * Media sessions of other apps, via notification access. Entirely event-driven: one callback per
 * controller, coalesced into at most one rebuild every [DEBOUNCE_MS]. All mutable state lives on a
 * private looper thread.
 */
class MediaSessionSource(
    context: Context,
    private val catalog: AppCatalog,
    @Suppress("unused") private val settings: StateFlow<AppSettings>,
    @Suppress("unused") private val scope: CoroutineScope,
) : SignalSource {
    private val app = context.applicationContext
    private val msm = app.getSystemService(MediaSessionManager::class.java)
    private val component = Permissions.listenerComponent(app)
    private val thread = HandlerThread("orbit-media").apply { start() }
    private val handler = Handler(thread.looper)

    private val _signals = MutableStateFlow<List<Signal>>(emptyList())
    override val signals: StateFlow<List<Signal>> = _signals.asStateFlow()

    // ---- handler-thread state ----
    private var started = false
    private var listening = false
    private var warnedNoAccess = false
    private val entries = LinkedHashMap<MediaSession.Token, Entry>()
    private val stamps = HashMap<String, TrackStamp>()
    private var pending = false

    private class TrackStamp(val key: String, val since: Long, var goneAt: Long? = null)

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list -> sync(list.orEmpty()) }

    override fun start() {
        handler.post {
            if (started) return@post
            started = true
            attach()
        }
    }

    override fun stop() {
        handler.post {
            if (!started) return@post
            started = false
            detach()
        }
    }

    /** Re-checks access and resyncs sessions; call when the app resumes or access was just granted. */
    fun refresh() {
        handler.post {
            if (!started) return@post
            if (!listening) attach() else sync(activeSessions() ?: return@post)
        }
    }

    private fun activeSessions(): List<MediaController>? = try {
        msm.getActiveSessions(component)
    } catch (_: SecurityException) {
        null
    }

    private fun attach() {
        if (listening) return
        try {
            msm.addOnActiveSessionsChangedListener(sessionsListener, component, handler)
            listening = true
            warnedNoAccess = false
            EventLog.i("Media", "Listening to media sessions")
            sync(msm.getActiveSessions(component))
        } catch (_: SecurityException) {
            listening = false
            if (!warnedNoAccess) EventLog.w("Media", "Notification access not granted; media sessions unavailable")
            warnedNoAccess = true
            _signals.value = emptyList()
        }
    }

    private fun detach() {
        if (listening) runCatching { msm.removeOnActiveSessionsChangedListener(sessionsListener) }
        listening = false
        entries.values.forEach { it.release() }
        entries.clear()
        stamps.clear()
        handler.removeCallbacks(rebuild)
        pending = false
        _signals.value = emptyList()
    }

    private fun sync(controllers: List<MediaController>) {
        if (!started) return
        val now = System.currentTimeMillis()
        val seen = HashSet<MediaSession.Token>()
        controllers.forEachIndexed { index, c ->
            if (c.packageName == app.packageName) return@forEachIndexed
            seen += c.sessionToken
            if (c.sessionToken !in entries) {
                // getActiveSessions is ordered by priority, most relevant first.
                entries[c.sessionToken] = Entry(c, now - index)
                catalog.markMediaApp(c.packageName)
            }
        }
        entries.keys.filter { it !in seen }.forEach { entries.remove(it)?.release() }
        schedule()
    }

    private fun schedule() {
        if (pending || !started) return
        pending = true
        handler.postDelayed(rebuild, DEBOUNCE_MS)
    }

    private val rebuildRef: Runnable get() = rebuild

    private val rebuild = Runnable {
        pending = false
        if (!started) return@Runnable
        // Players often publish a track in stages (title first, artist/art a few hundred ms later).
        val now = SystemClock.elapsedRealtime()
        val settling = entries.values.maxOfOrNull { e -> if (e.incomplete()) e.metadataAt + SETTLE_MS - now else 0L } ?: 0L
        if (settling > 0) {
            pending = true
            handler.postDelayed(rebuildRef, settling)
            return@Runnable
        }
        val fresh = build(System.currentTimeMillis())
        val prev = _signals.value
        val merged = SignalDiff.merge(prev, fresh)
        if (merged !== prev) {
            _signals.value = merged
            EventLog.i("Media", if (merged.isEmpty()) "No media" else merged.joinToString(" | ") { describe(it as MediaSignal) })
        }
    }

    private fun build(now: Long): List<MediaSignal> {
        val byPkg = entries.values.mapNotNull { e -> e.snapshot()?.let { e to it } }.groupBy { it.first.pkg }
        val out = mutableListOf<MediaSignal>()
        for ((pkg, list) in byPkg) {
            val (entry, snap) = list.sortedWith(compareByDescending<Pair<Entry, Snapshot>> { it.second.playing }.thenByDescending { it.first.lastActive }).first()
            val key = "${snap.title}\u0000${snap.artist}\u0000${snap.album}"
            val stamp = stamps[pkg]?.takeIf { it.key == key } ?: TrackStamp(key, now).also { stamps[pkg] = it }
            stamp.goneAt = null
            val kind = MediaClassifier.classify(
                MediaFacts(
                    packageName = pkg, title = snap.title, artist = snap.artist, album = snap.album,
                    albumArtist = snap.albumArtist, durationMs = snap.durationMs, mediaId = snap.mediaId,
                    genre = snap.genre, trackNumber = snap.trackNumber, appCategory = catalog.get(pkg)?.category,
                )
            )
            out += MediaSignal(
                id = "media:$pkg",
                origin = SignalOrigin.MEDIA_SESSION,
                packageName = pkg,
                appLabel = catalog.label(pkg),
                since = stamp.since,
                title = snap.title,
                artist = snap.artist,
                album = snap.album,
                artwork = entry.artwork,
                durationMs = snap.durationMs,
                positionMs = snap.positionMs,
                positionAtMs = snap.positionAtMs,
                playbackSpeed = snap.speed,
                playing = snap.playing,
                kind = kind,
                link = entry.link,
            )
        }
        // Forget track stamps of apps gone for a while, keep them across brief session churn.
        val live = byPkg.keys
        stamps.entries.removeAll { (pkg, s) ->
            if (pkg in live) false else {
                val gone = s.goneAt ?: now.also { s.goneAt = it }
                now - gone > STAMP_TTL_MS
            }
        }
        return out.sortedBy { it.id }
    }

    private class Snapshot(
        val title: String,
        val artist: String?,
        val album: String?,
        val albumArtist: String?,
        val genre: String?,
        val mediaId: String?,
        val trackNumber: Long,
        val durationMs: Long?,
        val positionMs: Long?,
        val positionAtMs: Long,
        val speed: Float,
        val playing: Boolean,
    )

    /** One controller and what its callbacks last told us. */
    private inner class Entry(val controller: MediaController, var lastActive: Long) : MediaController.Callback() {
        val pkg: String = controller.packageName
        var metadata: MediaMetadata? = controller.metadata
        var state: PlaybackState? = controller.playbackState
        var playing = false
        var artwork: Artwork? = null
        var link: String? = null
        var metadataAt = 0L

        /** Looks like a track change still in progress: something is missing or playback hasn't resumed. */
        fun incomplete(): Boolean {
            val m = metadata ?: return false
            return (m.text(MediaMetadata.METADATA_KEY_ARTIST) == null && m.text(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE) == null) ||
                artwork == null || m.getLong(MediaMetadata.METADATA_KEY_DURATION) <= 0 || !playing
        }

        init {
            playing = effectivePlaying(state)
            onMetadata()
            controller.registerCallback(this, handler)
        }

        fun release() = runCatching { controller.unregisterCallback(this) }

        override fun onMetadataChanged(m: MediaMetadata?) {
            metadata = m
            lastActive = System.currentTimeMillis()
            metadataAt = SystemClock.elapsedRealtime()
            onMetadata()
            schedule()
        }

        override fun onPlaybackStateChanged(s: PlaybackState?) {
            val was = playing
            state = s
            playing = effectivePlaying(s)
            if (playing && !was) lastActive = System.currentTimeMillis()
            schedule()
        }

        override fun onExtrasChanged(extras: Bundle?) {
            link = findLink()
            schedule()
        }

        override fun onSessionDestroyed() {
            release()
            entries.remove(controller.sessionToken)
            schedule()
        }

        private fun effectivePlaying(s: PlaybackState?): Boolean = when (s?.state) {
            PlaybackState.STATE_PLAYING -> true
            PlaybackState.STATE_BUFFERING, PlaybackState.STATE_CONNECTING, PlaybackState.STATE_FAST_FORWARDING,
            PlaybackState.STATE_REWINDING, PlaybackState.STATE_SKIPPING_TO_NEXT, PlaybackState.STATE_SKIPPING_TO_PREVIOUS,
            PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM -> playing
            else -> false
        }

        private fun onMetadata() {
            val m = metadata
            artwork = m?.let { artworkOf(it) }
            link = findLink()
            // Debug builds: dump raw metadata so new players are easy to support.
            if (BuildConfig.DEBUG && m != null) EventLog.d("MediaRaw", "$pkg: " + dumpMetadata(m))
        }

        private fun findLink(): String? {
            val m = metadata ?: return null
            val strings = ArrayList<String>()
            m.getString(MediaMetadata.METADATA_KEY_MEDIA_URI)?.let { strings += it }
            for (k in m.keySet()) if (k !in STANDARD_KEYS) runCatching { m.getString(k) }.getOrNull()?.let { strings += it }
            runCatching { controller.extras }.getOrNull()?.let { strings += bundleStrings(it) }
            return MediaLinks.find(pkg, m.getString(MediaMetadata.METADATA_KEY_MEDIA_ID), strings)
        }

        fun snapshot(): Snapshot? {
            val s = state ?: return null
            if (s.state == PlaybackState.STATE_NONE || s.state == PlaybackState.STATE_STOPPED || s.state == PlaybackState.STATE_ERROR) return null
            val m = metadata ?: return null
            val title = (m.text(MediaMetadata.METADATA_KEY_TITLE) ?: m.text(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)) ?: return null
            val albumArtist = m.text(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            val artist = m.text(MediaMetadata.METADATA_KEY_ARTIST) ?: albumArtist
                ?: m.text(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)?.takeIf { it != title }
            val duration = m.getLong(MediaMetadata.METADATA_KEY_DURATION).takeIf { it > 0 }
            val now = System.currentTimeMillis()
            val pos = s.position.takeIf { it >= 0 }
            val at = if (s.lastPositionUpdateTime > 0) now - (SystemClock.elapsedRealtime() - s.lastPositionUpdateTime).coerceAtLeast(0) else now
            val speed = s.playbackSpeed.takeIf { playing && it > 0f } ?: 1f
            return Snapshot(
                title = title,
                artist = artist,
                album = m.text(MediaMetadata.METADATA_KEY_ALBUM),
                albumArtist = albumArtist,
                genre = m.text(MediaMetadata.METADATA_KEY_GENRE),
                mediaId = m.getString(MediaMetadata.METADATA_KEY_MEDIA_ID),
                trackNumber = m.getLong(MediaMetadata.METADATA_KEY_TRACK_NUMBER),
                durationMs = duration,
                positionMs = pos?.let { if (duration != null) it.coerceAtMost(duration) else it },
                positionAtMs = at,
                speed = speed,
                playing = playing,
            )
        }
    }

    companion object {
        const val DEBOUNCE_MS = 300L
        /** Extra wait for staged metadata, measured from the metadata change. */
        private const val SETTLE_MS = 1000L
        private const val STAMP_TTL_MS = 60_000L
        private const val MAX_ART_PX = 512

        private val STANDARD_KEYS = setOf(
            MediaMetadata.METADATA_KEY_DURATION, MediaMetadata.METADATA_KEY_TRACK_NUMBER, MediaMetadata.METADATA_KEY_NUM_TRACKS,
            MediaMetadata.METADATA_KEY_DISC_NUMBER, MediaMetadata.METADATA_KEY_YEAR, MediaMetadata.METADATA_KEY_BT_FOLDER_TYPE,
            MediaMetadata.METADATA_KEY_RATING, MediaMetadata.METADATA_KEY_USER_RATING, MediaMetadata.METADATA_KEY_ART,
            MediaMetadata.METADATA_KEY_ALBUM_ART, MediaMetadata.METADATA_KEY_DISPLAY_ICON, MediaMetadata.METADATA_KEY_TITLE,
            MediaMetadata.METADATA_KEY_ARTIST, MediaMetadata.METADATA_KEY_ALBUM, MediaMetadata.METADATA_KEY_ALBUM_ARTIST,
            MediaMetadata.METADATA_KEY_AUTHOR, MediaMetadata.METADATA_KEY_WRITER, MediaMetadata.METADATA_KEY_COMPOSER,
            MediaMetadata.METADATA_KEY_GENRE, MediaMetadata.METADATA_KEY_DISPLAY_TITLE, MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE,
            MediaMetadata.METADATA_KEY_DISPLAY_DESCRIPTION, MediaMetadata.METADATA_KEY_COMPILATION, MediaMetadata.METADATA_KEY_DATE,
            MediaMetadata.METADATA_KEY_MEDIA_ID, MediaMetadata.METADATA_KEY_MEDIA_URI,
            MediaMetadata.METADATA_KEY_ART_URI, MediaMetadata.METADATA_KEY_ALBUM_ART_URI, MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI,
            "android.media.metadata.ADVERTISEMENT", "android.media.metadata.DOWNLOAD_STATUS",
        )

        private fun MediaMetadata.text(key: String): String? = getText(key)?.toString()?.trim()?.takeIf { it.isNotEmpty() }

        @Suppress("DEPRECATION")
        private fun bundleStrings(b: Bundle): List<String> = runCatching {
            b.keySet().mapNotNull { k -> (b.get(k) as? CharSequence)?.toString() }
        }.getOrDefault(emptyList())

        fun artworkOf(m: MediaMetadata): Artwork? {
            for (k in listOf(MediaMetadata.METADATA_KEY_ART_URI, MediaMetadata.METADATA_KEY_ALBUM_ART_URI, MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)) {
                val u = m.getString(k)?.trim() ?: continue
                if (u.startsWith("https://") || u.startsWith("http://")) return Artwork(url = u, bitmap = null, key = u)
            }
            val bmp = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: m.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
                ?: return null
            if (bmp.isRecycled || bmp.width <= 0 || bmp.height <= 0) return null
            return runCatching {
                val scaled = downscale(bmp, MAX_ART_PX)
                Artwork(url = null, bitmap = scaled, key = "bmp:" + pixelHash(scaled))
            }.getOrNull()
        }

        private fun downscale(b: Bitmap, max: Int): Bitmap {
            val m = maxOf(b.width, b.height)
            if (m <= max) return b
            val f = max.toFloat() / m
            return Bitmap.createScaledBitmap(b, (b.width * f).toInt().coerceAtLeast(1), (b.height * f).toInt().coerceAtLeast(1), true)
        }

        /** Hash of a 16x16 thumbnail with 4-bit channels, so re-encoded copies of the same art match. */
        private fun pixelHash(b: Bitmap): String {
            val src = if (b.config == Bitmap.Config.HARDWARE) b.copy(Bitmap.Config.ARGB_8888, false) else b
            val t = Bitmap.createScaledBitmap(src, 16, 16, true)
            val px = IntArray(256)
            t.getPixels(px, 0, 16, 0, 0, 16, 16)
            val bytes = ByteArray(256 * 3)
            px.forEachIndexed { i, p ->
                bytes[i * 3] = ((p shr 20) and 0xF).toByte()
                bytes[i * 3 + 1] = ((p shr 12) and 0xF).toByte()
                bytes[i * 3 + 2] = ((p shr 4) and 0xF).toByte()
            }
            val d = MessageDigest.getInstance("SHA-1").digest(bytes)
            return d.take(8).joinToString("") { "%02x".format(it) }
        }

        /** Debug aid: every metadata key with a short rendering of its value. */
        private fun dumpMetadata(m: MediaMetadata): String = m.keySet().sorted().joinToString(", ") { k ->
            val short = k.removePrefix("android.media.metadata.")
            val v: Any? = when (k) {
                MediaMetadata.METADATA_KEY_ART, MediaMetadata.METADATA_KEY_ALBUM_ART, MediaMetadata.METADATA_KEY_DISPLAY_ICON ->
                    m.getBitmap(k)?.let { "bitmap ${it.width}x${it.height}" }
                MediaMetadata.METADATA_KEY_DURATION, MediaMetadata.METADATA_KEY_TRACK_NUMBER, MediaMetadata.METADATA_KEY_NUM_TRACKS,
                MediaMetadata.METADATA_KEY_DISC_NUMBER, MediaMetadata.METADATA_KEY_YEAR, MediaMetadata.METADATA_KEY_BT_FOLDER_TYPE,
                "android.media.metadata.ADVERTISEMENT", "android.media.metadata.DOWNLOAD_STATUS" -> m.getLong(k)
                MediaMetadata.METADATA_KEY_RATING, MediaMetadata.METADATA_KEY_USER_RATING -> m.getRating(k)
                else -> runCatching { m.getText(k) }.getOrNull()
            }
            "$short=$v"
        }

        fun describe(s: MediaSignal): String = buildString {
            append(if (s.playing) "▶ " else "⏸ ").append(s.appLabel).append(" [").append(s.kind).append("] ")
            append('"').append(s.title).append('"')
            s.artist?.let { append(" by ").append(it) }
            s.album?.let { append(" on ").append(it) }
            s.durationMs?.let { append(" ").append(it / 1000).append("s") }
            s.positionMs?.let { append(" @").append(it / 1000).append("s") }
            s.deviceName?.let { append(" on device ").append(it) }
            append(" art=").append(s.artwork?.let { a -> a.url ?: (a.key + " " + a.bitmap?.let { "${it.width}x${it.height}" }) })
            s.link?.let { append(" link=").append(it) }
        }
    }
}
