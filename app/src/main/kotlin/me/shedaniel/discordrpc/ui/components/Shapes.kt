package me.shedaniel.discordrpc.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon

/**
 * A [Shape] drawing a [Morph] at [progress], rotated by [rotation] degrees around its centre.
 * Polygons from [MaterialShapes] are normalised to a unit square.
 */
class MorphShape(
    private val morph: Morph,
    private val progress: Float,
    private val rotation: Float = 0f,
) : Shape {
    private val path = Path()
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        path.rewind()
        morph.toPath(progress, path)
        val m = Matrix()
        m.translate(size.width / 2f, size.height / 2f)
        m.rotateZ(rotation)
        m.translate(-size.width / 2f, -size.height / 2f)
        m.scale(size.width, size.height)
        path.transform(m)
        return Outline.Generic(path)
    }
}

/** Static polygon shape with rotation. */
class PolygonShape(polygon: RoundedPolygon, rotation: Float = 0f) : Shape by MorphShape(Morph(polygon, polygon), 0f, rotation)

/**
 * Morphs smoothly to [target] whenever it changes, using the theme's expressive spatial spring.
 */
@Composable
fun animatedMorphShape(target: RoundedPolygon, rotation: Float = 0f): Shape {
    var from by remember { mutableStateOf(target) }
    var to by remember { mutableStateOf(target) }
    val progress = remember { Animatable(1f) }
    val spec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(target) {
        if (target !== to) {
            from = if (progress.value >= 0.5f) to else from
            to = target
            progress.snapTo(0f)
            progress.animateTo(1f, spec)
        }
    }
    val morph = remember(from, to) { Morph(from, to) }
    return MorphShape(morph, progress.value, rotation)
}

/** A slow endless rotation in degrees; [periodMs] per turn. */
@Composable
fun rememberSpin(periodMs: Int, enabled: Boolean = true): State<Float> {
    if (!enabled) return remember { mutableStateOf(0f) }
    val t = rememberInfiniteTransition(label = "spin")
    return t.animateFloat(0f, 360f, infiniteRepeatable(tween(periodMs, easing = LinearEasing), RepeatMode.Restart), label = "spin")
}

/** An icon sitting inside an expressive shape — the app's glyph language. */
@Composable
fun ShapeGlyph(
    icon: ImageVector,
    polygon: RoundedPolygon,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    container: Color = MaterialTheme.colorScheme.primaryContainer,
    content: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    rotation: Float = 0f,
) {
    Box(
        modifier.size(size).clip(remember(polygon, rotation) { PolygonShape(polygon, rotation) }).background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(size * 0.46f))
    }
}

/** Shapes used for rule glyphs; stable mapping from a glyph key. */
object Glyphs {
    fun polygon(key: String): RoundedPolygon = when (key) {
        "music" -> MaterialShapes.Cookie9Sided
        "video" -> MaterialShapes.Cookie4Sided
        "game" -> MaterialShapes.Clover4Leaf
        "podcast" -> MaterialShapes.Sunny
        "app" -> MaterialShapes.Pentagon
        "always" -> MaterialShapes.Flower
        "spotify" -> MaterialShapes.Cookie12Sided
        else -> MaterialShapes.SoftBurst
    }
}
