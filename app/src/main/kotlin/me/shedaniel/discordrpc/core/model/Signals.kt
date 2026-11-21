package me.shedaniel.discordrpc.core.model

import android.graphics.Bitmap
import kotlinx.serialization.Serializable

/**
 * What a piece of media is, which decides "Listening to" vs "Watching" by default.
 */
@Serializable
enum class MediaKind { MUSIC, VIDEO, PODCAST, AUDIOBOOK, UNKNOWN }

/** Where a signal came from. */
@Serializable
enum class SignalOrigin { MEDIA_SESSION, SPOTIFY_CONNECT, FOREGROUND_APP, MANUAL }

/**
 * Album / video artwork for a media signal. [key] is a stable content hash used for caching uploads
 * and for equality, so two signals with the same artwork compare equal even if the bitmap differs.
 */
class Artwork(
    /** A public http(s) URL to the artwork, if the source provided one. */
    val url: String?,
    /** A local bitmap, if the source only gave pixels. Never uploaded unless the network profile allows. */
    val bitmap: Bitmap?,
    /** Stable identity: url, or a hash of the downscaled pixels. */
    val key: String,
) {
    override fun equals(other: Any?) = other is Artwork && other.key == key
    override fun hashCode() = key.hashCode()
    override fun toString() = "Artwork(key=$key, url=$url, bitmap=${bitmap != null})"
}

/**
 * A raw observation from a source. The rules engine turns signals into Discord activities.
 */
sealed interface Signal {
    /** Unique within the current snapshot, e.g. "media:com.spotify.music". */
    val id: String
    val origin: SignalOrigin
    val packageName: String?
    val appLabel: String
    /** Wall-clock millis when this signal (track/app session) started. */
    val since: Long
}

data class MediaSignal(
    override val id: String,
    override val origin: SignalOrigin,
    override val packageName: String?,
    override val appLabel: String,
    override val since: Long,
    val title: String,
    val artist: String?,
    val album: String?,
    val artwork: Artwork?,
    val durationMs: Long?,
    /** Playback position at [positionAtMs] (wall clock). */
    val positionMs: Long?,
    val positionAtMs: Long,
    val playbackSpeed: Float = 1f,
    val playing: Boolean,
    val kind: MediaKind,
    /** For remote playback (Spotify Connect), the device name. */
    val deviceName: String? = null,
    /** A public link to the item when known (e.g. open.spotify.com/track/...). */
    val link: String? = null,
) : Signal {
    /** Estimated wall-clock millis at which playback started (position 0). Null when unknown. */
    fun startedAtMs(): Long? = positionMs?.let { positionAtMs - (it / playbackSpeed.coerceAtLeast(0.01f)).toLong() }

    /** Estimated wall-clock millis at which playback will end. Null when unknown. */
    fun endsAtMs(): Long? {
        val start = startedAtMs() ?: return null
        val d = durationMs?.takeIf { it > 0 } ?: return null
        return start + (d / playbackSpeed.coerceAtLeast(0.01f)).toLong()
    }
}

data class AppSignal(
    override val id: String,
    override val packageName: String,
    override val appLabel: String,
    override val since: Long,
    val isGame: Boolean,
) : Signal {
    override val origin: SignalOrigin get() = SignalOrigin.FOREGROUND_APP
}
