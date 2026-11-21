package me.shedaniel.discordrpc.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.shedaniel.discordrpc.ui.theme.rememberHaptics

private val GlyphSize = 18.dp
private val InlinePadding = 8.dp
private val StackedPadding = 6.dp
private val MinSegment = 48.dp

/**
 * Full-width connected button group for picking one of a few options.
 *
 * Segments get their natural width plus an equal share of what's left, so long labels take the room they need. When
 * even that doesn't fit (large text, narrow screens), the group becomes a vertical list instead of truncating.
 */
@Composable
fun <T> ChoiceRow(
    choices: List<Choice<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    showLabels: Boolean = true,
    style: ChoiceStyle = ChoiceStyle.Inline,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
) {
    val haptics = rememberHaptics()
    val scheme = MaterialTheme.colorScheme
    val colors = ToggleButtonDefaults.colors(
        containerColor = containerColor,
        contentColor = scheme.onSurfaceVariant,
        checkedContainerColor = scheme.primary,
        checkedContentColor = scheme.onPrimary,
    )
    val stacked = style == ChoiceStyle.Stacked
    val labelStyle = if (stacked) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge
    val detailStyle = MaterialTheme.typography.labelSmall
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    // Natural width of each segment's content, in Dp, with and without icons.
    fun measureAll(withGlyphs: Boolean): List<Dp> {
        fun w(text: String, ts: TextStyle) = with(density) { measurer.measure(text, ts, maxLines = 1, softWrap = false).size.width.toDp() }
        return choices.map { c ->
            val hasGlyph = withGlyphs && (c.icon != null || c.glyph != null)
            val dot = if (c.marked) 10.dp else 0.dp
            val content = if (stacked) {
                maxOf(GlyphSize + dot, w(c.label, labelStyle), c.detail?.let { w(it, detailStyle) } ?: 0.dp) + StackedPadding * 2
            } else {
                val glyph = if (hasGlyph) GlyphSize + (if (showLabels) 6.dp else 0.dp) else 0.dp
                val label = if (showLabels || !hasGlyph) w(c.label, labelStyle) else 0.dp
                glyph + label + dot + InlinePadding * 2
            }
            maxOf(content + 2.dp, MinSegment)
        }
    }
    val withIcons = remember(choices, stacked, showLabels, labelStyle, density) { measureAll(true) }
    // Labels alone, used when icons and labels together don't fit but labels would.
    val labelsOnly = remember(choices, stacked, showLabels, labelStyle, density) {
        if (!stacked && showLabels && choices.any { it.icon != null || it.glyph != null }) measureAll(false) else null
    }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val gap = ButtonGroupDefaults.ConnectedSpaceBetween
        val available = maxWidth - gap * (choices.size - 1)
        fun sum(l: List<Dp>) = l.fold(0.dp) { a, b -> a + b }
        val dropIcons = sum(withIcons) > available && labelsOnly != null && sum(labelsOnly) <= available
        val natural = if (dropIcons) labelsOnly!! else withIcons
        val total = sum(natural)
        val fits = total <= available

        val segment: @Composable (Int, Choice<T>, Modifier, ToggleButtonShapes, Boolean) -> Unit = { i, c, mod, shapes, vertical ->
            val checked = c.value == selected
            ToggleButton(
                checked = checked,
                onCheckedChange = { if (!checked) { haptics.tick(); onSelect(c.value) } },
                shapes = shapes,
                colors = colors,
                modifier = mod.semantics {
                    role = Role.RadioButton
                    if (!showLabels) contentDescription = c.label
                },
                contentPadding = when {
                    vertical -> PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                    stacked -> PaddingValues(horizontal = StackedPadding, vertical = 8.dp)
                    else -> PaddingValues(horizontal = InlinePadding)
                },
            ) {
                when {
                    vertical -> VerticalContent(c, checked)
                    stacked -> StackedContent(c, checked, labelStyle, detailStyle)
                    else -> InlineContent(if (dropIcons) c.copy(icon = null, glyph = null) else c, checked, showLabels, labelStyle)
                }
            }
        }

        if (fits) {
            val share = (available - total) / choices.size
            Row(
                Modifier.fillMaxWidth().then(if (stacked) Modifier.height(IntrinsicSize.Min) else Modifier),
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                choices.forEachIndexed { i, c ->
                    val shapes = when {
                        choices.size == 1 -> ToggleButtonDefaults.shapesFor(ButtonDefaults.MinHeight)
                        i == 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        i == choices.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    }
                    segment(
                        i, c,
                        Modifier.weight((natural[i] + share).value)
                            .then(if (stacked) Modifier.fillMaxHeight().heightIn(min = 64.dp) else Modifier),
                        shapes, false,
                    )
                }
            }
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(gap)) {
                choices.forEachIndexed { i, c ->
                    segment(i, c, Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalShapes(i, choices.size), true)
                }
            }
        }
    }
}

/** Connected shapes for a vertical group: outer corners round, inner corners tight, selected becomes a pill. */
@Composable
private fun verticalShapes(index: Int, count: Int): ToggleButtonShapes {
    val outer = 20.dp
    val inner = 6.dp
    val top = if (index == 0) outer else inner
    val bottom = if (index == count - 1) outer else inner
    val shape = RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
    return ToggleButtonShapes(shape = shape, pressedShape = RoundedCornerShape(inner), checkedShape = RoundedCornerShape(percent = 50))
}

@Composable
private fun Glyph(c: Choice<*>) {
    val g = c.glyph
    when {
        g != null -> g()
        c.icon != null -> Icon(c.icon, null, Modifier.size(GlyphSize))
    }
}

@Composable
private fun InlineContent(c: Choice<*>, checked: Boolean, showLabels: Boolean, style: TextStyle) {
    val hasGlyph = c.icon != null || c.glyph != null
    if (hasGlyph) {
        Glyph(c)
        if (showLabels) Spacer(Modifier.width(6.dp))
    }
    if (showLabels || !hasGlyph) {
        val detail = c.detail?.takeIf { checked }
        Text(if (detail != null) "${c.label} · $detail" else c.label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = style)
    }
    if (c.marked) MarkDot(Modifier.padding(start = 6.dp))
}

@Composable
private fun StackedContent(c: Choice<*>, checked: Boolean, labelStyle: TextStyle, detailStyle: TextStyle) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Glyph(c)
            if (c.marked) MarkDot(Modifier.padding(start = 4.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(c.label, maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis, style = labelStyle)
        if (c.detail != null) {
            // Always laid out so the group keeps its height; only fades with selection.
            val shown by animateFloatAsState(if (checked) 0.78f else 0f, MaterialTheme.motionScheme.fastEffectsSpec(), label = "detail")
            Text(c.detail, maxLines = 1, overflow = TextOverflow.Ellipsis, style = detailStyle, modifier = Modifier.graphicsLayer { alpha = shown })
        }
    }
}

/** A row in the vertical fallback: glyph, label (may wrap), then the resolved detail at the end. */
@Composable
private fun VerticalContent(c: Choice<*>, checked: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (c.icon != null || c.glyph != null) {
            Glyph(c)
            Spacer(Modifier.width(12.dp))
        }
        Text(c.label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        val detail = c.detail?.takeIf { checked }
        if (detail != null) {
            Text(detail, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 8.dp).graphicsLayer { alpha = 0.78f })
        }
        if (c.marked) MarkDot(Modifier.padding(start = 8.dp))
    }
}

/** Small dot in the content color, e.g. marking the network in use. */
@Composable
internal fun MarkDot(modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    Canvas(modifier.size(6.dp)) { drawCircle(color) }
}
