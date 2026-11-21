package me.shedaniel.discordrpc.ui.components

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import me.shedaniel.discordrpc.ui.LocalSharedScope

/**
 * Container transform between two screens: the element with the same [key] on both sides morphs
 * its bounds (and corner shape) during navigation.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedContainer(key: Any, shape: Shape = MaterialTheme.shapes.extraLarge): Modifier {
    val shared = LocalSharedScope.current ?: return this
    val animated = LocalNavAnimatedContentScope.current
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.geometry.Rect>()
    return with(shared) {
        this@sharedContainer.sharedBounds(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = animated,
            boundsTransform = { _, _ -> spec },
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
            clipInOverlayDuringTransition = OverlayClip(shape),
        )
    }
}
