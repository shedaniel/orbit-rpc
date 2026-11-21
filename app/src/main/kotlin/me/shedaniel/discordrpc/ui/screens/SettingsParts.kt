package me.shedaniel.discordrpc.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.shedaniel.discordrpc.R
import me.shedaniel.discordrpc.core.settings.OnlineStatus
import me.shedaniel.discordrpc.ui.theme.rememberHaptics

@Composable
internal fun settingsItemColors() = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)

// ---- Battery helpers -------------------------------------------------------------------------

internal fun isIgnoringBatteryOptimizations(context: Context): Boolean =
    me.shedaniel.discordrpc.sources.Permissions.isIgnoringBatteryOptimizations(context)

internal fun batteryOptimizationIntent(context: Context): Intent =
    me.shedaniel.discordrpc.sources.Permissions.batteryOptimizationIntent(context)

// ---- Dialogs ---------------------------------------------------------------------------------

/** Destructive confirmation: confirm/reject haptics on the two outcomes. */
@Composable
internal fun ConfirmDialog(
    title: String,
    confirmLabel: String,
    icon: ImageVector,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val haptics = rememberHaptics()
    AlertDialog(
        onDismissRequest = { haptics.reject(); onDismiss() },
        icon = { Icon(icon, null) },
        title = { Text(title) },
        confirmButton = {
            TextButton(onClick = { haptics.confirm(); onConfirm(); onDismiss() }) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = { haptics.reject(); onDismiss() }) { Text(androidx.compose.ui.res.stringResource(R.string.screen_cancel)) }
        },
    )
}

/** Single-line text input dialog. [filter] maps raw input to the allowed characters. */
@Composable
internal fun TextFieldDialog(
    title: String,
    initial: String,
    icon: ImageVector,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
    placeholder: String? = null,
    numeric: Boolean = false,
    filter: (String) -> String = { it },
    extra: (@Composable () -> Unit)? = null,
) {
    val haptics = rememberHaptics()
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(icon, null) },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = filter(it) },
                    singleLine = true,
                    placeholder = placeholder?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Ascii),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = me.shedaniel.discordrpc.ui.theme.NumberFamily),
                    modifier = Modifier.fillMaxWidth(),
                )
                extra?.invoke()
            }
        },
        confirmButton = {
            TextButton(onClick = { haptics.confirm(); onSave(text.trim()); onDismiss() }) {
                Text(androidx.compose.ui.res.stringResource(R.string.screen_save))
            }
        },
        dismissButton = {
            TextButton(onClick = { haptics.press(); onDismiss() }) { Text(androidx.compose.ui.res.stringResource(R.string.screen_cancel)) }
        },
    )
}

// ---- Discord status --------------------------------------------------------------------------

private val Online = Color(0xFF3BA55D)
private val Idle = Color(0xFFFAA81A)
private val Dnd = Color(0xFFED4245)

/** Discord-style status mark: dot, crescent, dash or ring. */
@Composable
internal fun StatusDot(status: OnlineStatus, modifier: Modifier = Modifier, size: Dp = 14.dp) {
    val outline = MaterialTheme.colorScheme.outline
    Canvas(modifier.size(size).graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        val r = this.size.minDimension / 2f
        val c = center
        when (status) {
            OnlineStatus.ONLINE -> drawCircle(Online, r, c)
            OnlineStatus.IDLE -> {
                drawCircle(Idle, r, c)
                drawCircle(Color.Black, r * 0.62f, Offset(c.x - r * 0.42f, c.y - r * 0.42f), blendMode = BlendMode.Clear)
            }
            OnlineStatus.DND -> {
                drawCircle(Dnd, r, c)
                val w = r * 1.2f
                val h = r * 0.42f
                drawRoundRect(
                    Color.Black, Offset(c.x - w / 2, c.y - h / 2), Size(w, h),
                    androidx.compose.ui.geometry.CornerRadius(h / 2), blendMode = BlendMode.Clear,
                )
            }
            OnlineStatus.INVISIBLE -> drawCircle(outline, r - r * 0.22f, c, style = Stroke(r * 0.44f))
        }
    }
}

/**
 * Status picker: a connected button group where the selected button widens to show its label and the others
 * collapse to their status mark.
 */
@Composable
internal fun StatusChoiceItem(shapes: ListItemShapes, title: String, selected: OnlineStatus, onSelect: (OnlineStatus) -> Unit) {
    val haptics = rememberHaptics()
    val labels = mapOf(
        OnlineStatus.ONLINE to R.string.settings_status_online,
        OnlineStatus.IDLE to R.string.settings_status_idle,
        OnlineStatus.DND to R.string.settings_status_dnd,
        OnlineStatus.INVISIBLE to R.string.settings_status_invisible,
    )
    val all = OnlineStatus.entries
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    SegmentedListItem(
        shapes = shapes,
        colors = settingsItemColors(),
        verticalAlignment = Alignment.Top,
        leadingContent = { StatusDot(selected, size = 20.dp) },
        supportingContent = {
            Column {
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                    all.forEachIndexed { i, s ->
                        val checked = s == selected
                        val weight by animateFloatAsState(if (checked) 2.6f else 1f, spatial, label = "w")
                        val label = androidx.compose.ui.res.stringResource(labels.getValue(s))
                        ToggleButton(
                            checked = checked,
                            onCheckedChange = { if (!checked) { haptics.tick(); onSelect(s) } },
                            shapes = when (i) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                all.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            },
                            modifier = Modifier.weight(weight).semantics { role = Role.RadioButton; contentDescription = label },
                            contentPadding = PaddingValues(horizontal = 8.dp),
                        ) {
                            StatusDot(s, size = 14.dp)
                            AnimatedVisibility(
                                checked,
                                enter = fadeIn() + expandHorizontally(),
                                exit = fadeOut() + shrinkHorizontally(),
                            ) {
                                Row {
                                    Spacer(Modifier.width(8.dp))
                                    Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                }
            }
        },
    ) { Text(title) }
}

/** Trailing status for a permission row: a check glyph when granted, a tonal button otherwise. */
@Composable
internal fun GrantedMark(granted: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(if (granted) scheme.primary else scheme.surfaceContainerHighest, label = "g")
    me.shedaniel.discordrpc.ui.components.ShapeGlyph(
        Icons.Rounded.Check,
        androidx.compose.material3.MaterialShapes.Cookie9Sided,
        size = 32.dp,
        container = container,
        content = scheme.onPrimary,
    )
}

@Composable
internal fun GrantButton(onClick: () -> Unit) {
    androidx.compose.material3.FilledTonalButton(
        onClick = onClick,
        contentPadding = ButtonDefaults.ExtraSmallContentPadding,
        modifier = Modifier.height(ButtonDefaults.ExtraSmallContainerHeight),
        shapes = ButtonDefaults.shapes(),
    ) { Text(androidx.compose.ui.res.stringResource(R.string.settings_grant), style = MaterialTheme.typography.labelLarge) }
}
