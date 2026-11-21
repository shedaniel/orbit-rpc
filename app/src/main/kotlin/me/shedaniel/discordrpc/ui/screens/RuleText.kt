package me.shedaniel.discordrpc.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import me.shedaniel.discordrpc.R
import me.shedaniel.discordrpc.core.model.MediaKind
import me.shedaniel.discordrpc.core.rules.Rule
import me.shedaniel.discordrpc.core.rules.Trigger

fun MediaKind.labelRes(): Int = when (this) {
    MediaKind.MUSIC -> R.string.kind_music
    MediaKind.VIDEO -> R.string.kind_video
    MediaKind.PODCAST -> R.string.kind_podcast
    MediaKind.AUDIOBOOK -> R.string.kind_audiobook
    MediaKind.UNKNOWN -> R.string.kind_unknown
}

/** One short line describing what makes a rule fire, e.g. "Music · Any app". */
@Composable
fun describeTrigger(rule: Rule, labelOf: (String) -> String): String = when (val t = rule.trigger) {
    is Trigger.Media -> {
        val res = androidx.compose.ui.platform.LocalContext.current.resources
        val kinds = if (t.kinds.isEmpty()) stringResource(R.string.trigger_any_media)
        else t.kinds.filter { it != MediaKind.UNKNOWN }.ifEmpty { t.kinds.toList() }.joinToString(", ") { res.getString(it.labelRes()) }
        "$kinds · ${appsText(t.packages, labelOf)}"
    }
    is Trigger.App -> when {
        t.packages.isNotEmpty() -> appsText(t.packages, labelOf)
        t.games -> stringResource(R.string.trigger_games)
        else -> stringResource(R.string.trigger_chosen_apps)
    }
    Trigger.Always -> stringResource(R.string.trigger_always)
}

@Composable
private fun appsText(packages: Set<String>, labelOf: (String) -> String): String = when (packages.size) {
    0 -> stringResource(R.string.trigger_any_app)
    1 -> labelOf(packages.first())
    else -> pluralStringResource(R.plurals.trigger_n_apps, packages.size, packages.size)
}
