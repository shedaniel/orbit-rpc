package me.shedaniel.discordrpc.core.rules

import me.shedaniel.discordrpc.core.model.ActivityType
import me.shedaniel.discordrpc.core.model.AppSignal
import me.shedaniel.discordrpc.core.model.ImageRef
import me.shedaniel.discordrpc.core.model.MediaKind
import me.shedaniel.discordrpc.core.model.MediaSignal
import me.shedaniel.discordrpc.core.model.PresenceActivity
import me.shedaniel.discordrpc.core.model.PresenceButton
import me.shedaniel.discordrpc.core.model.Signal
import me.shedaniel.discordrpc.core.model.SignalOrigin

/**
 * Pure function from (rules, signals) to activities. Rules are ordered by priority (first wins).
 * Each signal is consumed by at most one rule; each rule produces at most one activity.
 */
object RulesEngine {

    data class Match(val rule: Rule, val signal: Signal?)

    fun matches(trigger: Trigger, signal: Signal): Boolean = when (trigger) {
        is Trigger.Media -> signal is MediaSignal &&
            (signal.playing || trigger.includePaused) &&
            (trigger.includeRemote || signal.origin != SignalOrigin.SPOTIFY_CONNECT) &&
            (trigger.kinds.isEmpty() || signal.kind in trigger.kinds) &&
            (trigger.packages.isEmpty() || signal.packageName in trigger.packages)
        is Trigger.App -> signal is AppSignal && when {
            trigger.packages.isNotEmpty() -> signal.packageName in trigger.packages
            trigger.games -> signal.isGame
            else -> true
        }
        Trigger.Always -> false
    }

    /** Signals sorted so the most relevant come first: playing media, then newest. */
    private fun rank(signals: List<Signal>): List<Signal> = signals.sortedWith(
        compareByDescending<Signal> { (it as? MediaSignal)?.playing ?: true }.thenByDescending { it.since }
    )

    fun evaluate(rules: List<Rule>, signals: List<Signal>, max: Int): List<Match> {
        val ranked = rank(signals)
        val used = mutableSetOf<String>()
        val out = mutableListOf<Match>()
        for (rule in rules) {
            if (!rule.enabled) continue
            if (out.size >= max) break
            if (rule.trigger is Trigger.Always) {
                out += Match(rule, null); continue
            }
            val s = ranked.firstOrNull { it.id !in used && matches(rule.trigger, it) } ?: continue
            used += s.id
            out += Match(rule, s)
        }
        // "Always" rules are fallbacks: only keep them if nothing else matched.
        val real = out.filter { it.signal != null }
        return if (real.isNotEmpty()) real.take(max) else out.take(max)
    }

    fun values(signal: Signal?, appName: String?): Map<String, String?> = buildMap {
        put("app", appName ?: signal?.appLabel)
        if (signal is MediaSignal) {
            put("title", signal.title)
            put("artist", signal.artist)
            put("album", signal.album)
            put("device", signal.deviceName)
            put("kind", signal.kind.name.lowercase())
        }
    }

    fun autoType(signal: Signal?): ActivityType = when ((signal as? MediaSignal)?.kind) {
        MediaKind.MUSIC, MediaKind.PODCAST, MediaKind.AUDIOBOOK -> ActivityType.LISTENING
        MediaKind.VIDEO -> ActivityType.WATCHING
        MediaKind.UNKNOWN -> ActivityType.LISTENING
        null -> ActivityType.PLAYING
    }

    /**
     * Render a template against a signal. [appName] is the user's display-name override, if any.
     * Returns null if the rendered name would be empty (Discord rejects that).
     */
    fun render(rule: Rule, signal: Signal?, appName: String?, nowMs: Long): PresenceActivity? {
        val t = rule.template
        val v = values(signal, appName)
        fun r(s: String) = Template.render(s, v).take(128).ifBlank { null }
        val name = r(t.name) ?: return null
        val media = signal as? MediaSignal
        val pkg = signal?.packageName?.takeIf { it.isNotBlank() }

        fun image(src: ImageSource, url: String): ImageRef? = when (src) {
            ImageSource.NONE -> null
            ImageSource.APP_ICON -> pkg?.let { ImageRef.AppIcon(it) }
            ImageSource.URL -> url.takeIf { it.startsWith("http") || it.startsWith("mp:") }?.let {
                if (it.startsWith("mp:")) ImageRef.Raw(it) else ImageRef.Url(it)
            }
            ImageSource.ARTWORK -> if (media != null) ImageRef.Media(
                artwork = media.artwork,
                title = media.title,
                artist = media.artist,
                album = media.album,
                kind = media.kind,
                fallback = pkg?.let { ImageRef.AppIcon(it) },
            ) else pkg?.let { ImageRef.AppIcon(it) }
        }

        var start: Long? = null
        var end: Long? = null
        when (t.timestamps) {
            TimestampMode.NONE -> Unit
            TimestampMode.ELAPSED -> start = signal?.since ?: nowMs
            TimestampMode.PROGRESS -> {
                if (media != null && media.playing) {
                    start = media.startedAtMs()
                    end = media.endsAtMs()
                    if (start == null) start = media.since
                } else if (media == null) start = signal?.since ?: nowMs
            }
        }

        var large = image(t.largeImage, t.largeImageUrl)
        var small = image(t.smallImage, t.smallImageUrl)
        // Don't show the same icon twice.
        if (small is ImageRef.AppIcon && large is ImageRef.AppIcon && small == large) small = null

        return PresenceActivity(
            ruleId = rule.id,
            signalId = signal?.id ?: rule.id,
            type = if (t.autoType) autoType(signal) else t.type,
            name = name,
            details = r(t.details),
            detailsUrl = media?.link?.takeIf { t.linkDetails },
            state = r(t.state)?.let { if (it.length < 2) "$it " else it },
            largeImage = large,
            largeText = r(t.largeText)?.let { if (it.length < 2) "$it " else it },
            smallImage = small,
            smallText = r(t.smallText)?.let { if (it.length < 2) "$it " else it },
            startMs = start,
            endMs = end,
            buttons = t.buttons.mapNotNull { b ->
                val label = Template.render(b.label, v).take(32)
                val url = Template.render(b.url, v)
                if (label.isBlank() || !url.startsWith("http")) null else PresenceButton(label, url)
            }.take(2),
            statusDisplay = t.statusDisplay,
        )
    }
}
