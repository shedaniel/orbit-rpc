package me.shedaniel.discordrpc.ui.components

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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

/** A slider with the value shown as a number, ticking haptically on each step. */
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
    val steps = ((range.last - range.first) / step - 1).coerceAtLeast(0)
    SegmentedListItem(
        shapes = shapes,
        enabled = enabled,
        colors = groupColors(),
        leadingContent = icon?.let { { Leading(it) } },
        supportingContent = {
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
            )
        },
        trailingContent = {
            Text(
                format(live.roundToInt()),
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = NumberFamily),
                color = MaterialTheme.colorScheme.primary,
            )
        },
    ) { Text(title) }
}

data class Choice<T>(val value: T, val label: String, val icon: ImageVector? = null)

/** Connected button group for picking one of a few options. */
@Composable
fun <T> ChoiceRow(
    choices: List<Choice<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    showLabels: Boolean = true,
) {
    val haptics = rememberHaptics()
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        choices.forEachIndexed { i, c ->
            val checked = c.value == selected
            ToggleButton(
                checked = checked,
                onCheckedChange = { if (!checked) { haptics.tick(); onSelect(c.value) } },
                shapes = when {
                    choices.size == 1 -> ToggleButtonDefaults.shapesFor(ButtonDefaults.MinHeight)
                    i == 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    i == choices.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
            ) {
                if (c.icon != null) {
                    Icon(c.icon, null, Modifier.size(18.dp))
                    if (showLabels) Spacer(Modifier.width(6.dp))
                }
                if (showLabels || c.icon == null) Text(c.label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
fun <T> ChoiceItem(
    shapes: ListItemShapes,
    title: String,
    choices: List<Choice<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    icon: ImageVector? = null,
    summary: String? = null,
) {
    SegmentedListItem(
        shapes = shapes,
        colors = groupColors(),
        leadingContent = icon?.let { { Leading(it) } },
        verticalAlignment = Alignment.Top,
        supportingContent = {
            Column {
                if (summary != null) Text(summary)
                Spacer(Modifier.height(10.dp))
                ChoiceRow(choices, selected, onSelect)
            }
        },
    ) { Text(title) }
}
