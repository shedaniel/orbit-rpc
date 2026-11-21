package me.shedaniel.discordrpc.core.settings

import kotlinx.serialization.Serializable
import me.shedaniel.discordrpc.core.model.MediaKind

/** Online status sent with the presence. */
@Serializable
enum class OnlineStatus(val wire: String) { ONLINE("online"), IDLE("idle"), DND("dnd"), INVISIBLE("invisible") }

@Serializable
enum class ArtworkLookup { NONE, ITUNES, DEEZER }

/** Where local-only bitmaps (artwork / icons without a public URL) may be uploaded. */
@Serializable
enum class ImageHost { NONE, LITTERBOX, CATBOX }

@Serializable
data class DiscordSettings(
    /** Application id used for activities and external-assets. Empty -> built-in default. */
    val applicationId: String = "",
    val status: OnlineStatus = OnlineStatus.ONLINE,
    /** Marks the session as AFK so phone push notifications keep working. */
    val afk: Boolean = true,
    /** Show every matching rule's activity instead of only the highest priority one. */
    val multipleActivities: Boolean = false,
    /** Seconds to keep the socket open after the last activity disappears before closing it. */
    val idleDisconnectSeconds: Int = 90,
    /** Min millis between presence updates; changes inside the window are coalesced. */
    val updateDebounceMs: Long = 1500,
    /** Ask the gateway for no extra events (falls back automatically if rejected). */
    val leanGateway: Boolean = true,
    /** Transport compression. */
    val compression: Boolean = true,
)

/**
 * What the app may do on a given kind of network. Separate profiles for Wi-Fi and mobile data.
 */
@Serializable
data class NetworkProfile(
    /** Broadcast at all on this network. */
    val enabled: Boolean = true,
    /** Look up album art from a public catalog when the player gives no URL. */
    val artworkLookup: Boolean = true,
    /** Upload local bitmaps to [ImageSettings.host]. */
    val uploadImages: Boolean = true,
    /** Resolve app icons (Play Store lookup, cached forever). */
    val appIcons: Boolean = true,
    /** Spotify Connect polling. */
    val spotifyConnect: Boolean = true,
    /** Keep the socket open when nothing is showing (no idle disconnect). */
    val stayConnected: Boolean = false,
)

@Serializable
data class NetworkSettings(
    val wifi: NetworkProfile = NetworkProfile(),
    val cellular: NetworkProfile = NetworkProfile(uploadImages = false, spotifyConnect = false),
    /** Pause entirely while roaming. */
    val pauseWhenRoaming: Boolean = true,
    /** Honour Android's Data Saver as "cellular with everything off except presence". */
    val respectDataSaver: Boolean = true,
)

@Serializable
data class ImageSettings(
    val lookup: ArtworkLookup = ArtworkLookup.ITUNES,
    val host: ImageHost = ImageHost.NONE,
)

/** Per-app overrides. Absent entry = defaults (auto-classify, included for media). */
@Serializable
data class AppProfile(
    /** Media from this app is considered. */
    val media: Boolean = true,
    /** Force classification; null = automatic. */
    val kind: MediaKind? = null,
    /** Show "Playing <app>" while this app is in the foreground. null = automatic (games only). */
    val foreground: Boolean? = null,
    /** Display name override. */
    val customName: String? = null,
)

@Serializable
data class MediaSettings(
    val enabled: Boolean = true,
    /** Keep showing paused media for this many seconds (0 = hide immediately on pause). */
    val pausedGraceSeconds: Int = 0,
    /** Ignore media shorter than this (notification sounds, short clips). */
    val minDurationSeconds: Int = 0,
    /** New apps are included by default. When false, only apps explicitly enabled are used. */
    val includeNewApps: Boolean = true,
)

@Serializable
data class ForegroundSettings(
    val enabled: Boolean = true,
    /** Treat apps the system marks as games as "Playing" automatically. */
    val autoGames: Boolean = true,
    /** Seconds between foreground checks while the screen is on. */
    val pollSeconds: Int = 5,
    /** Keep showing an app for this long after it leaves the foreground. */
    val lingerSeconds: Int = 15,
)

@Serializable
data class SpotifySettings(
    val enabled: Boolean = false,
    /** Spotify developer app client id (PKCE, no secret). */
    val clientId: String = "",
    /** Polling cadence while something is playing remotely. */
    val pollSeconds: Int = 20,
    /** Polling cadence while nothing is playing. */
    val idlePollSeconds: Int = 120,
    /** Skip remote polling while Spotify itself is playing on this phone. */
    val skipWhenLocal: Boolean = true,
)

@Serializable
data class UiSettings(
    val dynamicColor: Boolean = true,
    /** 0 = system, 1 = light, 2 = dark */
    val themeMode: Int = 0,
    val haptics: Boolean = true,
    val amoled: Boolean = false,
)

@Serializable
data class AppSettings(
    val enabled: Boolean = true,
    val onboarded: Boolean = false,
    val discord: DiscordSettings = DiscordSettings(),
    val network: NetworkSettings = NetworkSettings(),
    val images: ImageSettings = ImageSettings(),
    val media: MediaSettings = MediaSettings(),
    val foreground: ForegroundSettings = ForegroundSettings(),
    val spotify: SpotifySettings = SpotifySettings(),
    val ui: UiSettings = UiSettings(),
    val apps: Map<String, AppProfile> = emptyMap(),
) {
    fun app(pkg: String): AppProfile = apps[pkg] ?: AppProfile(media = media.includeNewApps)
}
