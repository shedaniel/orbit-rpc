package me.shedaniel.discordrpc.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.graphics.shapes.RoundedPolygon
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.shedaniel.discordrpc.R
import me.shedaniel.discordrpc.graph
import me.shedaniel.discordrpc.service.PresenceService
import me.shedaniel.discordrpc.sources.Permissions
import me.shedaniel.discordrpc.ui.components.Avatar
import me.shedaniel.discordrpc.ui.components.PolygonShape
import me.shedaniel.discordrpc.ui.components.animatedMorphShape
import me.shedaniel.discordrpc.ui.components.rememberSpin
import me.shedaniel.discordrpc.ui.nav.LocalNavigator
import me.shedaniel.discordrpc.ui.nav.Route
import me.shedaniel.discordrpc.ui.theme.rememberHaptics

private data class Step(
    val polygon: RoundedPolygon,
    val icon: ImageVector,
    val title: Int,
    val body: Int,
    val action: Int,
    val optional: Boolean,
)

private val steps = listOf(
    Step(MaterialShapes.Cookie9Sided, Icons.Rounded.Login, R.string.onb_discord_title, R.string.onb_discord_body, R.string.onb_discord_action, false),
    Step(MaterialShapes.Sunny, Icons.Rounded.MusicNote, R.string.onb_media_title, R.string.onb_media_body, R.string.onb_grant, true),
    Step(MaterialShapes.Clover4Leaf, Icons.Rounded.SportsEsports, R.string.onb_games_title, R.string.onb_games_body, R.string.onb_grant, true),
    Step(MaterialShapes.Cookie6Sided, Icons.Rounded.NotificationsActive, R.string.onb_notif_title, R.string.onb_notif_body, R.string.onb_allow, true),
    Step(MaterialShapes.Pill, Icons.Rounded.BatteryChargingFull, R.string.onb_battery_title, R.string.onb_battery_body, R.string.onb_allow, true),
)

@Composable
fun OnboardingScreen() {
    val context = LocalContext.current
    val graph = context.graph
    val nav = LocalNavigator.current
    val haptics = rememberHaptics()
    val user by graph.tokens.user.collectAsStateWithLifecycle()
    val token by graph.tokens.token.collectAsStateWithLifecycle()
    var index by rememberSaveable { mutableIntStateOf(0) }

    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { tick++; onPauseOrDispose { } }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }

    fun done(i: Int): Boolean {
        @Suppress("UNUSED_EXPRESSION") tick
        return when (i) {
            0 -> token != null
            1 -> Permissions.hasNotificationAccess(context)
            2 -> Permissions.hasUsageAccess(context)
            3 -> Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            4 -> Permissions.isIgnoringBatteryOptimizations(context)
            else -> true
        }
    }

    fun finish() {
        haptics.confirm()
        graph.settingsStore.update { it.copy(onboarded = true, enabled = true) }
        PresenceService.start(context)
        nav.replaceAll(Route.Main)
    }

    fun next() {
        // Skip steps that are already satisfied.
        var n = index + 1
        while (n < steps.size && done(n)) n++
        if (n >= steps.size) finish() else { haptics.tick(); index = n }
    }

    // Advance automatically once the current step is satisfied (e.g. returning from system settings).
    val currentDone = done(index)
    LaunchedEffect(currentDone, index) {
        if (currentDone && index > 0) {
            haptics.confirm()
            kotlinx.coroutines.delay(550)
            next()
        }
    }

    val step = steps[index]
    val spin by rememberSpin(30_000)
    val shape = animatedMorphShape(step.polygon, rotation = spin)
    val scheme = MaterialTheme.colorScheme

    Column(
        Modifier.fillMaxSize().background(scheme.surface).statusBarsPadding().navigationBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Progress as a row of shapes.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            steps.forEachIndexed { i, s ->
                val size by animateDpAsState(if (i == index) 22.dp else 14.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "dot")
                val color by animateColorAsState(
                    when {
                        done(i) -> scheme.primary
                        i == index -> scheme.tertiary
                        else -> scheme.surfaceContainerHighest
                    }, label = "dotColor",
                )
                Box(Modifier.size(size).clip(remember(s.polygon) { PolygonShape(s.polygon) }).background(color))
            }
        }

        Spacer(Modifier.weight(1f))

        Box(Modifier.size(220.dp).clip(shape).background(if (currentDone) scheme.primary else scheme.primaryContainer), contentAlignment = Alignment.Center) {
            AnimatedContent(Triple(index, currentDone, user), label = "glyph") { (i, d, u) ->
                when {
                    i == 0 && u != null -> Avatar(u, Modifier.fillMaxSize())
                    d -> Icon(Icons.Rounded.Check, null, Modifier.size(88.dp), tint = scheme.onPrimary)
                    else -> Icon(steps[i].icon, null, Modifier.size(88.dp), tint = scheme.onPrimaryContainer)
                }
            }
        }

        Spacer(Modifier.height(40.dp))

        val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.IntOffset>()
        val fx = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
        AnimatedContent(
            index,
            transitionSpec = { (slideInHorizontally(spatial) { it / 4 } + fadeIn(fx)) togetherWith (slideOutHorizontally(spatial) { -it / 4 } + fadeOut(fx)) },
            label = "text",
        ) { i ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (i == 0 && user != null) stringResource(R.string.onb_discord_done, user!!.displayName) else stringResource(steps[i].title),
                    style = MaterialTheme.typography.headlineLargeEmphasized,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(steps[i].body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }

        Spacer(Modifier.weight(1f))

        val h = ButtonDefaults.MediumContainerHeight
        Button(
            onClick = {
                haptics.press()
                if (currentDone) { next(); return@Button }
                when (index) {
                    0 -> nav.push(Route.Login)
                    1 -> context.startActivity(Permissions.notificationAccessIntent(context))
                    2 -> context.startActivity(Permissions.usageAccessIntent(context))
                    3 -> if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    4 -> context.startActivity(Permissions.batteryOptimizationIntent(context))
                }
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = h),
            shapes = ButtonDefaults.shapesFor(h),
        ) {
            AnimatedContent(currentDone, label = "cta") { d ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(if (d) R.string.onb_continue else step.action), style = ButtonDefaults.textStyleFor(h))
                    if (d) {
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null)
                    }
                }
            }
        }
        Box(Modifier.height(56.dp), contentAlignment = Alignment.Center) {
            if (step.optional && !currentDone) {
                TextButton(onClick = { next() }) { Text(stringResource(R.string.onb_skip)) }
            }
        }
        if (index == 1 && !currentDone && Permissions.mayBeRestricted(context)) {
            TextButton(onClick = { context.startActivity(Permissions.appDetailsIntent(context)) }) {
                Text(stringResource(R.string.onb_restricted), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
