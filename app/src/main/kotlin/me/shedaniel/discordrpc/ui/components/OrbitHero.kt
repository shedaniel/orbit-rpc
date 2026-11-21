package me.shedaniel.discordrpc.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.SignalCellularOff
import androidx.compose.material.icons.rounded.SyncProblem
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import me.shedaniel.discordrpc.core.model.ConnectionState
import me.shedaniel.discordrpc.core.model.DiscordUser
import me.shedaniel.discordrpc.core.model.ImageRef
import me.shedaniel.discordrpc.core.model.PauseReason
import me.shedaniel.discordrpc.core.model.PresenceActivity
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

enum class HeroMode { LIVE, CONNECTING, IDLE, PAUSED, ERROR, SIGNED_OUT }

fun heroMode(connection: ConnectionState, activity: PresenceActivity?): HeroMode = when (connection) {
    is ConnectionState.Connected -> if (activity != null) HeroMode.LIVE else HeroMode.IDLE
    ConnectionState.Connecting -> if (activity != null) HeroMode.CONNECTING else HeroMode.IDLE
    ConnectionState.Idle -> if (activity != null) HeroMode.CONNECTING else HeroMode.IDLE
    is ConnectionState.Paused -> HeroMode.PAUSED
    is ConnectionState.Error -> HeroMode.ERROR
    ConnectionState.LoggedOut -> HeroMode.SIGNED_OUT
}

/**
 * The planet is you: your avatar when nothing is shared, the artwork when something is.
 * The app the activity comes from orbits it as a satellite. State is carried by shape:
 * a spinning cookie while live, a calm circle while idle, a square when paused, a burst on error.
 */
@Composable
fun OrbitHero(
    mode: HeroMode,
    activity: PresenceActivity?,
    user: DiscordUser?,
    connection: ConnectionState,
    modifier: Modifier = Modifier,
    previewUrl: (ImageRef) -> String? = { null },
) {
    val scheme = MaterialTheme.colorScheme
    val live = mode == HeroMode.LIVE
    val spin by rememberSpin(if (live) 24_000 else 60_000, enabled = mode == HeroMode.LIVE || mode == HeroMode.IDLE)
    val orbit by rememberSpin(14_000, enabled = live)

    val polygon = when (mode) {
        HeroMode.LIVE -> MaterialShapes.Cookie9Sided
        HeroMode.CONNECTING -> MaterialShapes.Cookie12Sided
        HeroMode.IDLE -> MaterialShapes.Circle
        HeroMode.PAUSED -> MaterialShapes.Square
        HeroMode.ERROR -> MaterialShapes.SoftBurst
        HeroMode.SIGNED_OUT -> MaterialShapes.Ghostish
    }
    val planetShape = animatedMorphShape(polygon, rotation = if (mode == HeroMode.LIVE || mode == HeroMode.IDLE) spin else 0f)
    val ringColor by animateColorAsState(
        when (mode) {
            HeroMode.LIVE -> scheme.primary
            HeroMode.CONNECTING -> scheme.secondary
            HeroMode.ERROR -> scheme.error
            else -> scheme.outlineVariant
        }, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "ring",
    )
    val ringAlpha by animateFloatAsState(if (mode == HeroMode.SIGNED_OUT) 0f else 1f, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "ringAlpha")
    val planetScale by animateFloatAsState(
        when (mode) {
            HeroMode.LIVE -> 1f
            HeroMode.PAUSED, HeroMode.SIGNED_OUT -> 0.82f
            else -> 0.9f
        }, MaterialTheme.motionScheme.slowSpatialSpec(), label = "planetScale",
    )
    val saturation by animateFloatAsState(if (mode == HeroMode.PAUSED || mode == HeroMode.ERROR) 0f else 1f, MaterialTheme.motionScheme.slowEffectsSpec(), label = "sat")

    BoxWithConstraints(modifier.fillMaxWidth().aspectRatio(1.15f), contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, maxHeight)
        val planet = side * 0.62f
        val sat = side * 0.17f

        // Orbit ring: a tilted dashed ellipse, rotating slowly while live.
        Canvas(Modifier.size(side).graphicsLayer { alpha = ringAlpha }) {
            val rx = size.width * 0.47f
            val ry = size.height * 0.2f
            rotate(-24f) {
                drawOval(
                    color = ringColor.copy(alpha = 0.55f),
                    topLeft = Offset(center.x - rx, center.y - ry),
                    size = androidx.compose.ui.geometry.Size(rx * 2, ry * 2),
                    style = Stroke(
                        width = 3.dp.toPx(), cap = StrokeCap.Round,
                        pathEffect = if (live) null else PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 10.dp.toPx()), spin),
                    ),
                )
            }
        }

        val satFront = sin(Math.toRadians(orbit.toDouble())) > 0
        // Near half of the ring passes in front of the planet, for depth.
        Canvas(Modifier.size(side).zIndex(1.5f).graphicsLayer { alpha = ringAlpha }) {
            val rx = size.width * 0.47f
            val ry = size.height * 0.2f
            rotate(-24f) {
                drawArc(
                    color = ringColor.copy(alpha = 0.9f),
                    startAngle = 8f, sweepAngle = 164f, useCenter = false,
                    topLeft = Offset(center.x - rx, center.y - ry),
                    size = androidx.compose.ui.geometry.Size(rx * 2, ry * 2),
                    style = Stroke(
                        width = 3.dp.toPx(), cap = StrokeCap.Round,
                        pathEffect = if (live) null else PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 10.dp.toPx()), spin),
                    ),
                )
            }
        }
        // Planet.
        Box(
            Modifier
                .zIndex(1f)
                .size(planet)
                .graphicsLayer { scaleX = planetScale; scaleY = planetScale }
                .clip(planetShape)
                .background(when (mode) { HeroMode.ERROR -> scheme.errorContainer; HeroMode.SIGNED_OUT -> scheme.surfaceContainerHigh; else -> scheme.primaryContainer }),
            contentAlignment = Alignment.Center,
        ) {
            val filter = if (saturation < 1f) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(saturation) }) else null
            Crossfade(targetState = if (live || mode == HeroMode.CONNECTING) activity?.largeImage else null, label = "planet") { img ->
                if (img != null) {
                    RefImage(img, Modifier.fillMaxSize(), previewUrl)
                } else if (user != null && mode != HeroMode.SIGNED_OUT) {
                    Avatar(user, Modifier.fillMaxSize().graphicsLayer { if (filter != null) alpha = 0.75f })
                }
            }
            when (mode) {
                HeroMode.PAUSED -> Icon(
                    when ((connection as? ConnectionState.Paused)?.reason) {
                        PauseReason.CELLULAR_BLOCKED, PauseReason.ROAMING_BLOCKED, PauseReason.DATA_SAVER -> Icons.Rounded.SignalCellularOff
                        PauseReason.NO_NETWORK -> Icons.Rounded.CloudOff
                        else -> Icons.Rounded.Pause
                    },
                    null, tint = scheme.onPrimaryContainer,
                    modifier = Modifier.size(planet * 0.28f).background(scheme.primaryContainer.copy(alpha = 0.85f), CircleShape).padding(planet * 0.05f),
                )
                HeroMode.SIGNED_OUT -> Icon(Icons.Rounded.Person, null, tint = scheme.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.size(planet * 0.4f))
                HeroMode.ERROR -> Icon(Icons.Rounded.SyncProblem, null, tint = scheme.onErrorContainer, modifier = Modifier.size(planet * 0.3f))
                HeroMode.CONNECTING -> if (activity?.largeImage == null) LoadingIndicator(Modifier.size(planet * 0.4f), color = scheme.onPrimaryContainer)
                else -> Unit
            }
        }

        // Satellite: the source app, riding the ring.
        AnimatedVisibility(
            visible = live && activity?.smallImage != null,
            enter = scaleIn(MaterialTheme.motionScheme.fastSpatialSpec()),
            exit = scaleOut(MaterialTheme.motionScheme.fastSpatialSpec()),
            modifier = Modifier.fillMaxSize().zIndex(if (satFront) 2f else 0f),
        ) {
            BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val rxPx = constraints.maxWidth * 0.47f
                val ryPx = constraints.maxWidth * 0.2f
                val a = Math.toRadians(orbit.toDouble())
                val tilt = Math.toRadians(-24.0)
                val x0 = rxPx * cos(a)
                val y0 = ryPx * sin(a)
                val x = x0 * cos(tilt) - y0 * sin(tilt)
                val y = x0 * sin(tilt) + y0 * cos(tilt)
                // Behind the planet on the far half of the orbit.
                val front = sin(a) > 0
                val satScale = if (front) 1f else 0.78f
                Box(
                    Modifier
                        .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                        .size(sat)
                        .graphicsLayer { scaleX = satScale; scaleY = satScale; alpha = if (front) 1f else 0.7f }
                        .border(4.dp, scheme.surface, CircleShape)
                        .clip(CircleShape),
                ) {
                    RefImage(activity?.smallImage, Modifier.fillMaxSize(), previewUrl)
                }
            }
        }
    }
}

