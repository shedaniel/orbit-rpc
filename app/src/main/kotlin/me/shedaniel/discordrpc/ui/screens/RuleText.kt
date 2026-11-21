package me.shedaniel.discordrpc.ui.screens

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
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

/**
 * What makes a rule fire, as a row of small marks: media kinds as icons, chosen apps as their icons.
 * Nothing is drawn for "any app", since that's the default.
 */
@Composable
fun TriggerMarks(rule: Rule, labelOf: (String) -> String, tint: androidx.compose.ui.graphics.Color, modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier) {
    val label = describeTrigger(rule, labelOf)
    val res = androidx.compose.ui.platform.LocalContext.current.resources
    val kinds: List<MediaKind>
    val packages: Set<String>
    when (val t = rule.trigger) {
        is Trigger.Media -> { kinds = t.kinds.filter { it != MediaKind.UNKNOWN }; packages = t.packages }
        is Trigger.App -> { kinds = emptyList(); packages = t.packages }
        Trigger.Always -> return
    }
    val games = (rule.trigger as? Trigger.App)?.let { it.games && it.packages.isEmpty() } == true
    if (kinds.isEmpty() && packages.isEmpty() && !games) return
    androidx.compose.foundation.layout.Row(
        modifier.clearAndSetSemantics { contentDescription = label },
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        val mark = androidx.compose.ui.Modifier.size(18.dp)
        kinds.distinctBy { it.icon() }.forEach { androidx.compose.material3.Icon(it.icon(), null, mark, tint = tint) }
        if (games) androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.SportsEsports, null, mark, tint = tint)
        if (kinds.isNotEmpty() && packages.isNotEmpty()) androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.width(4.dp))
        packages.take(4).forEach { me.shedaniel.discordrpc.ui.components.AppIcon(it, mark) }
        if (packages.size > 4) {
            androidx.compose.material3.Text(
                "+${packages.size - 4}",
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall.copy(fontFamily = me.shedaniel.discordrpc.ui.theme.NumberFamily),
                color = tint,
            )
        }
    }
}

