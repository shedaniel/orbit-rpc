package me.shedaniel.discordrpc.core.model

import kotlinx.serialization.Serializable

/** Discord activity types. [code] is the gateway value. */
@Serializable
enum class ActivityType(val code: Int) {
    PLAYING(0), STREAMING(1), LISTENING(2), WATCHING(3), COMPETING(5)
}

/** Which field Discord shows next to your name in the member list ("Listening to X"). */
@Serializable
enum class StatusDisplay(val code: Int) { NAME(0), STATE(1), DETAILS(2) }

/**
 * An image reference before it is resolved into a Discord asset key.
 */
sealed interface ImageRef {
    /** The launcher icon of an installed app. */
    data class AppIcon(val packageName: String) : ImageRef
    /** Media artwork (album art / thumbnail). [fallback] is used when it can't be resolved. */
    data class Media(val artwork: Artwork?, val title: String, val artist: String?, val album: String?, val kind: MediaKind, val fallback: ImageRef?) : ImageRef
    /** Any public URL. */
    data class Url(val url: String) : ImageRef
    /** An already-resolved Discord asset key, e.g. "mp:external/..." or an application asset id. */
    data class Raw(val key: String) : ImageRef
}

data class PresenceButton(val label: String, val url: String)

/**
 * A fully templated activity, ready to be resolved and sent. Images are unresolved [ImageRef]s.
 */
data class PresenceActivity(
    /** The rule that produced it (for logs / UI). */
    val ruleId: String,
    /** The signal that produced it. */
    val signalId: String,
    val type: ActivityType,
    val name: String,
    val details: String? = null,
    val detailsUrl: String? = null,
    val state: String? = null,
    val stateUrl: String? = null,
    val largeImage: ImageRef? = null,
    val largeText: String? = null,
    val smallImage: ImageRef? = null,
    val smallText: String? = null,
    val startMs: Long? = null,
    val endMs: Long? = null,
    val buttons: List<PresenceButton> = emptyList(),
    val statusDisplay: StatusDisplay = StatusDisplay.NAME,
    /** Streaming url (twitch/youtube) for [ActivityType.STREAMING]. */
    val url: String? = null,
)

/** The discord user, as known from READY / users/@me. */
@Serializable
data class DiscordUser(
    val id: String,
    val username: String,
    val globalName: String? = null,
    val avatar: String? = null,
) {
    val displayName: String get() = globalName ?: username
    fun avatarUrl(size: Int = 256): String? = avatar?.let {
        val ext = if (it.startsWith("a_")) "gif" else "png"
        "https://cdn.discordapp.com/avatars/$id/$it.$ext?size=$size"
    }
}

/** Gateway connection state for UI. */
sealed interface ConnectionState {
    data object LoggedOut : ConnectionState
    /** Logged in, nothing to show, socket closed to save data. */
    data object Idle : ConnectionState
    data object Connecting : ConnectionState
    data class Connected(val user: DiscordUser) : ConnectionState
    /** Paused because of a network policy (e.g. cellular disabled). */
    data class Paused(val reason: PauseReason) : ConnectionState
    data class Error(val message: String, val retryAtMs: Long?) : ConnectionState
}

enum class PauseReason { DISABLED, NO_NETWORK, CELLULAR_BLOCKED, ROAMING_BLOCKED, DATA_SAVER, TOKEN_INVALID }
