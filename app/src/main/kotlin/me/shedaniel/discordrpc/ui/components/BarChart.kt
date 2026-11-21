package me.shedaniel.discordrpc.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** One bar made of two stacked segments; [lower] sits on the baseline. */
@Immutable
data class StackedBar(val lower: Long, val upper: Long) {
    val total get() = lower + upper
}

/**
 * Stacked bar chart drawn on a canvas. Segments are separate pills with a small gap, bars grow in with the
 * theme's spatial spring (staggered left to right) and re-animate when data changes. Tap or scrub to select.
 */
@Composable
fun StackedBarChart(
    bars: List<StackedBar>,
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    lowerColor: Color,
    upperColor: Color,
    modifier: Modifier = Modifier,
    height: Dp = 168.dp,
) {
    val scheme = MaterialTheme.colorScheme
    val n = bars.size
    val max = (bars.maxOfOrNull { it.total } ?: 0L).coerceAtLeast(1L).toFloat()
    val lower = remember(n) { List(n) { Animatable(0f) } }
    val upper = remember(n) { List(n) { Animatable(0f) } }
    var firstRun by remember(n) { mutableStateOf(true) }
    val spec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(bars) {
        val stagger = firstRun
        firstRun = false
        bars.forEachIndexed { i, b ->
            launch {
                if (stagger) delay(i * 22L)
                launch { lower[i].animateTo(b.lower / max, spec) }
                upper[i].animateTo(b.upper / max, spec)
            }
        }
    }
    val effects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val emphasis = List(n) { i -> animateFloatAsState(if (i == selected) 1f else 0f, effects, label = "bar$i") }
    val stub = scheme.outlineVariant
    val track = scheme.surfaceContainerHighest
    val currentSelect by rememberUpdatedState(onSelect)

    Column(modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
                .pointerInput(n) {
                    detectTapGestures { currentSelect(((it.x / size.width) * n).toInt().coerceIn(0, n - 1)) }
                }
                .pointerInput(n) {
                    detectHorizontalDragGestures { change, _ ->
                        currentSelect(((change.position.x / size.width) * n).toInt().coerceIn(0, n - 1))
                    }
                },
        ) {
            if (n == 0) return@Canvas
            val slot = size.width / n
            val barW = slot * 0.6f
            val gap = 3.dp.toPx()
            val minSeg = 4.dp.toPx()
            val avail = size.height - gap
            for (i in 0 until n) {
                val e = emphasis[i].value
                val left = i * slot + (slot - barW) / 2f
                if (e > 0f) {
                    val trackW = slot * 0.86f
                    drawRoundRect(
                        track.copy(alpha = track.alpha * e),
                        topLeft = Offset(i * slot + (slot - trackW) / 2f, 0f),
                        size = Size(trackW, size.height),
                        cornerRadius = CornerRadius(trackW / 2f),
                    )
                }
                val alpha = 0.42f + 0.58f * e
                val lo = lower[i].value.coerceAtLeast(0f)
                val up = upper[i].value.coerceAtLeast(0f)
                if (bars[i].total == 0L && lo < 0.002f && up < 0.002f) {
                    pill(stub, left + barW * 0.2f, size.height, barW * 0.6f, minSeg)
                    continue
                }
                var base = size.height
                if (bars[i].lower > 0 || lo > 0.002f) {
                    val h = (avail * lo).coerceAtLeast(minSeg)
                    pill(lowerColor.copy(alpha = alpha), left, base, barW, h)
                    base -= h + gap
                }
                if (bars[i].upper > 0 || up > 0.002f) {
                    val h = (avail * up).coerceAtLeast(minSeg)
                    pill(upperColor.copy(alpha = alpha), left, base, barW, h)
                }
            }
        }
        if (labels.size == n) {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                labels.forEachIndexed { i, l ->
                    val color by animateColorAsState(
                        if (i == selected) scheme.onSurface else scheme.onSurfaceVariant.copy(alpha = 0.7f),
                        label = "label$i",
                    )
                    Text(
                        l,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = if (i == selected) MaterialTheme.typography.labelMediumEmphasized else MaterialTheme.typography.labelMedium,
                        color = color,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** A fully rounded segment standing on [bottom]. */
private fun DrawScope.pill(color: Color, left: Float, bottom: Float, width: Float, height: Float) {
    drawRoundRect(
        color,
        topLeft = Offset(left, bottom - height),
        size = Size(width, height),
        cornerRadius = CornerRadius(minOf(width, height) / 2f),
    )
}
