package me.shedaniel.discordrpc.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import me.shedaniel.discordrpc.R
import me.shedaniel.discordrpc.ui.theme.NumberFamily
import me.shedaniel.discordrpc.ui.theme.rememberHaptics
import kotlin.math.roundToInt

class GroupScope {
    internal val items = mutableListOf<@Composable (ListItemShapes) -> Unit>()
    fun item(content: @Composable (shapes: ListItemShapes) -> Unit) {
        items += content
    }
}

/** A titled run of connected list items (first/last get the big corners). */
@Composable
fun SettingsGroup(title: String?, modifier: Modifier = Modifier, build: GroupScope.() -> Unit) {
    val scope = GroupScope().apply(build)
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        if (title != null) {
            Text(
                title,
                style = MaterialTheme.typography.labelLargeEmphasized,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 12.dp, top = 20.dp, bottom = 8.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            scope.items.forEachIndexed { i, item ->
                item(ListItemDefaults.segmentedShapes(index = i, count = scope.items.size))
            }
        }
    }
}

@Composable
private fun Leading(icon: ImageVector?) {
    if (icon != null) Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun groupColors() = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)

@Composable
fun SwitchItem(
    shapes: ListItemShapes,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector? = null,
    summary: String? = null,
    enabled: Boolean = true,
) {
    val haptics = rememberHaptics()
    SegmentedListItem(
        checked = checked,
        onCheckedChange = { haptics.toggle(it); onCheckedChange(it) },
        shapes = shapes,
        enabled = enabled,
        colors = groupColors(),
        leadingContent = icon?.let { { Leading(it) } },
        supportingContent = summary?.let { { Text(it) } },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
                thumbContent = if (checked) {
                    { Icon(Icons.Rounded.Check, null, Modifier.size(SwitchDefaults.IconSize)) }
                } else null,
            )
        },
    ) { Text(title) }
}

@Composable
fun ClickItem(
    shapes: ListItemShapes,
    title: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    summary: String? = null,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val haptics = rememberHaptics()
    SegmentedListItem(
        onClick = { haptics.press(); onClick() },
        shapes = shapes,
        enabled = enabled,
        colors = groupColors(),
        leadingContent = leading ?: icon?.let { { Leading(it) } },
        supportingContent = summary?.let { { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) } },
        trailingContent = trailing,
    ) { Text(title) }
}

@Composable
fun InfoItem(
    shapes: ListItemShapes,
    title: String,
    icon: ImageVector? = null,
    summary: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    SegmentedListItem(
        shapes = shapes,
        colors = groupColors(),
        leadingContent = icon?.let { { Leading(it) } },
        supportingContent = summary?.let { { Text(it) } },
        trailingContent = trailing,
    ) { Text(title) }
}

/**
 * A slider with its value as a number. The title row and the slider share the full content width so the track
 * runs under the value, and the item keeps even padding above the title and below the thumb.
 */
@Composable
fun SliderItem(
    shapes: ListItemShapes,
    title: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    icon: ImageVector? = null,
    step: Int = 1,
    format: (Int) -> String = { it.toString() },
    enabled: Boolean = true,
) {
    val haptics = rememberHaptics()
    var live by remember(value) { mutableFloatStateOf(value.toFloat()) }
    // Long ranges get no tick marks (snapping still happens in onValueChange).
    val steps = ((range.last - range.first) / step - 1).coerceAtLeast(0).let { if (it > 20) 0 else it }
    SegmentedListItem(
        shapes = shapes,
        enabled = enabled,
        colors = groupColors(),
        verticalAlignment = Alignment.Top,
        leadingContent = icon?.let { { Leading(it) } },
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f))
                Text(
                    format(live.roundToInt()),
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = NumberFamily),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            Slider(
                value = live,
                onValueChange = {
                    val snapped = (((it - range.first) / step).roundToInt() * step + range.first).toFloat()
                    if (snapped != live) haptics.frequentTick()
                    live = snapped
                },
                onValueChangeFinished = { onValueChange(live.roundToInt()) },
                valueRange = range.first.toFloat()..range.last.toFloat(),
                steps = steps,
                enabled = enabled,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/**
 * One option of a [ChoiceRow]. [detail] is a resolved value shown inside the segment while it is selected
 * (e.g. "Auto" resolving to "Music"); [glyph] replaces [icon] with custom drawing; [marked] adds a small dot.
 */
data class Choice<T>(
    val value: T,
    val label: String,
    val icon: ImageVector? = null,
    val detail: String? = null,
    val marked: Boolean = false,
    val glyph: (@Composable () -> Unit)? = null,
)

/** How a [ChoiceRow] lays out each segment. */
enum class ChoiceStyle {
    /** Icon and label side by side. */
    Inline,
    /** Icon over label in a taller segment, for four or more options. */
    Stacked,
}

/**
 * A titled item whose button group spans the full item width (the title row carries the icon, so the group is
 * not indented by a leading slot).
 */
@Composable
fun <T> ChoiceItem(
    shapes: ListItemShapes,
    title: String,
    choices: List<Choice<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    icon: ImageVector? = null,
    summary: String? = null,
    leading: (@Composable () -> Unit)? = null,
    style: ChoiceStyle = ChoiceStyle.Inline,
) {
    SegmentedListItem(
        shapes = shapes,
        colors = groupColors(),
    ) {
        Column(Modifier.padding(vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                    if (leading != null) leading() else Leading(icon)
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(title)
                    AnimatedContent(
                        summary,
                        transitionSpec = { fadeIn() togetherWith fadeOut() using SizeTransform(clip = false) },
                        label = "summary",
                    ) { s ->
                        if (s != null) {
                            Text(s, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            ChoiceRow(choices, selected, onSelect, style = style)
        }
    }
}

/** A row that expands to reveal more items below it; the chevron turns and the device ticks. */
@Composable
fun ExpandItem(shapes: ListItemShapes, title: String, expanded: Boolean, onToggle: (Boolean) -> Unit, icon: ImageVector? = null) {
    val haptics = rememberHaptics()
    val turn by animateFloatAsState(if (expanded) 180f else 0f, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "chevron")
    val state = stringResource(if (expanded) R.string.settings_expanded else R.string.settings_collapsed)
    SegmentedListItem(
        onClick = { haptics.tick(); onToggle(!expanded) },
        shapes = shapes,
        colors = groupColors(),
        leadingContent = icon?.let { { Leading(it) } },
        trailingContent = {
            Icon(
                Icons.Rounded.ExpandMore, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(turn),
            )
        },
        modifier = Modifier.semantics { stateDescription = state },
    ) { Text(title) }
}
