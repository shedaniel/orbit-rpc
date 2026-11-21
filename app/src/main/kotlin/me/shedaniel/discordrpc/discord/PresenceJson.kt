package me.shedaniel.discordrpc.discord

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import me.shedaniel.discordrpc.core.model.ActivityType
import me.shedaniel.discordrpc.core.model.StatusDisplay
import me.shedaniel.discordrpc.core.settings.OnlineStatus

/**
 * Builds gateway presence payloads (op 3 `d`, and identify's `presence`) from [ResolvedActivity]s,
 * dropping empty fields and enforcing Discord's field limits so a single bad field can't get the
 * whole activity rejected.
 */
object PresenceJson {
    const val MAX_TEXT = 128
    const val MIN_TEXT = 2
    const val MAX_BUTTON_LABEL = 32
    const val MAX_URL = 512
    const val MAX_ACTIVITIES = 4

    /** Invisible, non-whitespace padding for too-short strings (Discord trims regular spaces). */
    const val PAD = "⠀"

    fun presence(activities: List<ResolvedActivity>, status: OnlineStatus, afk: Boolean, applicationId: String): JsonObject = buildJsonObject {
        put("since", 0)
        putJsonArray("activities") { activities.take(MAX_ACTIVITIES).forEach { add(activity(it, applicationId)) } }
        put("status", status.wire)
        put("afk", afk)
    }

    fun activity(ra: ResolvedActivity, applicationId: String): JsonObject {
        val a = ra.activity
        return buildJsonObject {
            put("name", text(a.name, min = 1) ?: "Android")
            put("type", a.type.code)
            if (a.type == ActivityType.STREAMING) url(a.url)?.let { put("url", it) }
            applicationId.trim().takeIf { it.isNotEmpty() && it.all(Char::isDigit) }?.let { put("application_id", it) }
            text(a.details)?.let { put("details", it) }
            url(a.detailsUrl)?.let { if (a.details.isNullOrBlank().not()) put("details_url", it) }
            text(a.state)?.let { put("state", it) }
            url(a.stateUrl)?.let { if (a.state.isNullOrBlank().not()) put("state_url", it) }
            if (a.statusDisplay != StatusDisplay.NAME) put("status_display_type", a.statusDisplay.code)

            val start = a.startMs?.takeIf { it > 0 }
            val end = a.endMs?.takeIf { it > 0 && (start == null || it > start) }
            if (start != null || end != null) putJsonObject("timestamps") {
                start?.let { put("start", it) }
                end?.let { put("end", it) }
            }

            val large = ra.largeImage?.trim()?.takeIf { it.isNotEmpty() }
            val small = ra.smallImage?.trim()?.takeIf { it.isNotEmpty() }
            if (large != null || small != null) putJsonObject("assets") {
                large?.let { put("large_image", it); text(a.largeText)?.let { t -> put("large_text", t) } }
                small?.let { put("small_image", it); text(a.smallText)?.let { t -> put("small_text", t) } }
            }

            val buttons = a.buttons.mapNotNull { b ->
                val label = text(b.label, min = 1, max = MAX_BUTTON_LABEL) ?: return@mapNotNull null
                val u = url(b.url) ?: return@mapNotNull null
                label to u
            }.take(2)
            if (buttons.isNotEmpty()) {
                putJsonArray("buttons") { buttons.forEach { add(it.first) } }
                putJsonObject("metadata") { putJsonArray("button_urls") { buttons.forEach { add(it.second) } } }
            }
        }
    }

    /** Trims, drops blanks, truncates (surrogate-safe, with an ellipsis) and pads to Discord's limits. */
    fun text(s: String?, min: Int = MIN_TEXT, max: Int = MAX_TEXT): String? {
        val t = s?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        var r = if (t.length <= max) t else {
            var cut = max - 1
            if (Character.isHighSurrogate(t[cut - 1])) cut--
            t.substring(0, cut).trimEnd() + "…"
        }
        while (r.length < min) r += PAD
        return r
    }

    fun url(s: String?): String? {
        val t = s?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (t.length > MAX_URL) return null
        if (!t.startsWith("https://", ignoreCase = true) && !t.startsWith("http://", ignoreCase = true)) return null
        return t
    }
}
