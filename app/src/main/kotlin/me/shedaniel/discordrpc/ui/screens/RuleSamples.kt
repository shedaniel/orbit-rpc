package me.shedaniel.discordrpc.ui.screens

import me.shedaniel.discordrpc.core.model.AppSignal
import me.shedaniel.discordrpc.core.model.MediaKind
import me.shedaniel.discordrpc.core.model.MediaSignal
import me.shedaniel.discordrpc.core.model.Signal
import me.shedaniel.discordrpc.core.model.SignalOrigin
import me.shedaniel.discordrpc.core.rules.Rule
import me.shedaniel.discordrpc.core.rules.Trigger
import me.shedaniel.discordrpc.sources.AppCategory
import me.shedaniel.discordrpc.sources.InstalledApp

/** A plausible signal for previews when nothing live matches, using a real installed app where possible. */
fun sampleSignal(rule: Rule, apps: List<InstalledApp>, mediaApps: Set<String>): Signal? {
    val now = System.currentTimeMillis()
    fun app(pkg: String?) = pkg?.let { p -> apps.firstOrNull { it.packageName == p } }
    return when (val t = rule.trigger) {
        is Trigger.Media -> {
            val kind = t.kinds.firstOrNull { it != MediaKind.UNKNOWN } ?: MediaKind.MUSIC
            val wanted = when (kind) {
                MediaKind.VIDEO -> AppCategory.VIDEO
                MediaKind.PODCAST, MediaKind.AUDIOBOOK -> AppCategory.PODCAST
                else -> AppCategory.MUSIC
            }
            val a = app(t.packages.firstOrNull())
                ?: apps.firstOrNull { it.category == wanted && it.packageName in mediaApps }
                ?: apps.firstOrNull { it.category == wanted }
                ?: apps.firstOrNull { it.packageName in mediaApps }
            val (title, artist, album) = when (kind) {
                MediaKind.VIDEO -> Triple("Building a tiny synth from scratch", "Studio Hollow", null)
                MediaKind.PODCAST, MediaKind.AUDIOBOOK -> Triple("Episode 42: Slow mornings", "The Long Table", "The Long Table")
                else -> Triple("Golden Hour", "Mara Vey", "Low Tide")
            }
            MediaSignal(
                id = "sample", origin = SignalOrigin.MEDIA_SESSION, packageName = a?.packageName, appLabel = a?.label ?: "Music",
                since = now - 64_000, title = title, artist = artist, album = album, artwork = null,
                durationMs = 214_000, positionMs = 64_000, positionAtMs = now, playing = true, kind = kind,
            )
        }
        is Trigger.App -> {
            val a = app(t.packages.firstOrNull()) ?: apps.firstOrNull { it.isGame }
            AppSignal("sample", a?.packageName ?: "", a?.label ?: "Game", now - 754_000, isGame = true)
        }
        Trigger.Always -> null
    }
}
