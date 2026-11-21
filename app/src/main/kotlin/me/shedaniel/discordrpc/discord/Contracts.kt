package me.shedaniel.discordrpc.discord

import kotlinx.coroutines.flow.StateFlow
import me.shedaniel.discordrpc.core.model.DiscordUser
import me.shedaniel.discordrpc.core.model.ImageRef
import me.shedaniel.discordrpc.core.model.PresenceActivity
import me.shedaniel.discordrpc.core.settings.NetworkProfile
import me.shedaniel.discordrpc.core.settings.OnlineStatus

/** An activity with its images resolved to Discord asset keys ("mp:external/..."). */
data class ResolvedActivity(
    val activity: PresenceActivity,
    val largeImage: String?,
    val smallImage: String?,
)

sealed interface GatewayStatus {
    data object Closed : GatewayStatus
    data object Connecting : GatewayStatus
    data class Ready(val user: DiscordUser) : GatewayStatus
    /** [fatal] = token invalid / account problem; do not retry until the token changes. */
    data class Failed(val code: Int?, val message: String, val fatal: Boolean, val retryAtMs: Long?) : GatewayStatus
}

/**
 * One gateway session for the logged-in user. Implementations must:
 *  - connect lazily on the first [setActivities] call,
 *  - use transport compression and RESUME when possible (avoid re-downloading READY),
 *  - coalesce identical updates (never send the same presence twice),
 *  - reconnect with backoff on transient failures.
 */
interface DiscordSession {
    val status: StateFlow<GatewayStatus>

    /** Set the full list of activities. An empty list clears the presence but keeps the socket. */
    suspend fun setActivities(token: String, activities: List<ResolvedActivity>, status: OnlineStatus, afk: Boolean, applicationId: String)

    /** Clear presence (best effort) and close the socket. */
    suspend fun disconnect()
}

/** Turns [ImageRef]s into Discord asset keys while respecting the current network profile. */
interface AssetResolver {
    /** Returns a Discord image key or null when it can't (or may not) be resolved. Must be cache-first. */
    suspend fun resolve(ref: ImageRef, profile: NetworkProfile, token: String, applicationId: String): String?

    /**
     * Returns a URL (https or file/content) the UI can display for previews, without using the
     * network beyond what [resolve] would. May return null.
     */
    fun previewUrl(ref: ImageRef): String?
}

object DiscordDefaults {
    /** Fallback application id (a widely used public one) used for activities and external assets when the user has not set their own. */
    const val APPLICATION_ID = "962990036020756480"
}
