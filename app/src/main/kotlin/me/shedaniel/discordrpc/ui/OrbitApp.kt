package me.shedaniel.discordrpc.ui

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import me.shedaniel.discordrpc.graph
import me.shedaniel.discordrpc.ui.nav.LocalNavigator
import me.shedaniel.discordrpc.ui.nav.Navigator
import me.shedaniel.discordrpc.ui.nav.Route
import me.shedaniel.discordrpc.ui.screens.LogScreen
import me.shedaniel.discordrpc.ui.screens.LoginScreen
import me.shedaniel.discordrpc.ui.screens.MainScreen
import me.shedaniel.discordrpc.ui.screens.OnboardingScreen
import me.shedaniel.discordrpc.ui.screens.RuleEditorScreen
import me.shedaniel.discordrpc.ui.screens.SettingsScreen
import me.shedaniel.discordrpc.ui.theme.LocalHapticsEnabled
import me.shedaniel.discordrpc.ui.theme.OrbitTheme

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun OrbitApp() {
    val graph = LocalContext.current.graph
    val settings by graph.settings.collectAsStateWithLifecycle()
    val navigator = remember {
        Navigator(mutableStateListOf(if (graph.settingsStore.value.onboarded) Route.Main else Route.Onboarding))
    }

    OrbitTheme(settings.ui) {
        CompositionLocalProvider(
            LocalNavigator provides navigator,
            LocalHapticsEnabled provides settings.ui.haptics,
        ) {
            SharedTransitionLayout(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                CompositionLocalProvider(LocalSharedScope provides this, androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.IntOffset>()
                    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
                    NavDisplay(
                        backStack = navigator.backStack,
                        onBack = { navigator.pop() },
                        transitionSpec = {
                            (slideInHorizontally(spatial) { it / 3 } + fadeIn(effects)) togetherWith
                                (slideOutHorizontally(spatial) { -it / 6 } + fadeOut(tween(150)))
                        },
                        popTransitionSpec = {
                            (slideInHorizontally(spatial) { -it / 6 } + fadeIn(effects)) togetherWith
                                (slideOutHorizontally(spatial) { it / 3 } + fadeOut(tween(150)))
                        },
                        predictivePopTransitionSpec = {
                            fadeIn(effects) togetherWith (scaleOut(targetScale = 0.9f) + fadeOut(effects))
                        },
                        entryProvider = entryProvider {
                            entry<Route.Main> { MainScreen() }
                            entry<Route.Onboarding> { OnboardingScreen() }
                            entry<Route.Login> { LoginScreen() }
                            entry<Route.Settings> { SettingsScreen() }
                            entry<Route.Log> { LogScreen() }
                            // The editor grows out of its rule card (shared bounds), so the page itself only fades.
                            entry<Route.RuleEditor>(
                                metadata = NavDisplay.transitionSpec { fadeIn(tween(220, 60)) togetherWith fadeOut(tween(160)) } +
                                    NavDisplay.popTransitionSpec { fadeIn(tween(220)) togetherWith fadeOut(tween(160, 60)) } +
                                    NavDisplay.predictivePopTransitionSpec { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                            ) { RuleEditorScreen(it) }
                        },
                    )
                }
            }
        }
    }
}
