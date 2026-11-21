package me.shedaniel.discordrpc.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material.icons.outlined.Rule
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material.icons.rounded.DataUsage
import androidx.compose.material.icons.rounded.Rule
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import me.shedaniel.discordrpc.R
import me.shedaniel.discordrpc.ui.nav.Tab
import me.shedaniel.discordrpc.ui.theme.rememberHaptics

private data class TabSpec(val tab: Tab, val label: Int, val icon: ImageVector, val selectedIcon: ImageVector)

private val tabs = listOf(
    TabSpec(Tab.NOW, R.string.tab_now, Icons.Outlined.Circle, Icons.Rounded.Circle),
    TabSpec(Tab.RULES, R.string.tab_rules, Icons.Outlined.Rule, Icons.Rounded.Rule),
    TabSpec(Tab.APPS, R.string.tab_apps, Icons.Outlined.Apps, Icons.Rounded.Apps),
    TabSpec(Tab.DATA, R.string.tab_data, Icons.Outlined.DataUsage, Icons.Rounded.DataUsage),
)

@Composable
fun MainScreen() {
    var tab by rememberSaveable { mutableStateOf(Tab.NOW) }
    val haptics = rememberHaptics()
    BackHandler(enabled = tab != Tab.NOW) { tab = Tab.NOW }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = {
            ShortNavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                tabs.forEach { spec ->
                    val selected = tab == spec.tab
                    ShortNavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (!selected) haptics.tick()
                            tab = spec.tab
                        },
                        icon = { Icon(if (selected) spec.selectedIcon else spec.icon, contentDescription = null) },
                        label = { Text(stringResource(spec.label)) },
                    )
                }
            }
        },
    ) { padding ->
        val slide = MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.IntOffset>()
        val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
        val fastOut = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
        AnimatedContent(
            targetState = tab,
            // Slide in the direction of the destination tab.
            transitionSpec = {
                val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                (slideInHorizontally(slide) { dir * it / 5 } + fadeIn(effects)) togetherWith
                    (slideOutHorizontally(slide) { -dir * it / 5 } + fadeOut(fastOut))
            },
            label = "tab",
            modifier = Modifier.fillMaxSize(),
        ) { t ->
            Box(Modifier.fillMaxSize()) {
                val bottom = PaddingValues(bottom = padding.calculateBottomPadding())
                when (t) {
                    Tab.NOW -> HomeScreen(bottom, onOpenTab = { tab = it })
                    Tab.RULES -> RulesScreen(bottom)
                    Tab.APPS -> AppsScreen(bottom)
                    Tab.DATA -> DataScreen(bottom)
                }
            }
        }
    }
}

