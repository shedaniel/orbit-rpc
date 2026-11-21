package me.shedaniel.discordrpc.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.NetworkCell
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.shedaniel.discordrpc.R
import me.shedaniel.discordrpc.core.model.AppSignal
import me.shedaniel.discordrpc.core.model.ConnectionState
import me.shedaniel.discordrpc.core.model.MediaSignal
import me.shedaniel.discordrpc.core.model.PauseReason
import me.shedaniel.discordrpc.core.model.Signal
import me.shedaniel.discordrpc.core.model.SignalOrigin
import me.shedaniel.discordrpc.graph
import me.shedaniel.discordrpc.net.Transport
import me.shedaniel.discordrpc.service.PresenceService
import me.shedaniel.discordrpc.sources.Permissions
import me.shedaniel.discordrpc.ui.components.AppIcon
import me.shedaniel.discordrpc.ui.components.Avatar
import me.shedaniel.discordrpc.ui.components.Glyphs
import me.shedaniel.discordrpc.ui.components.OrbitHero
import me.shedaniel.discordrpc.ui.components.PresenceCard
import me.shedaniel.discordrpc.ui.components.ShapeGlyph
import me.shedaniel.discordrpc.ui.components.formatBytes
import me.shedaniel.discordrpc.ui.components.glyphIcon
import me.shedaniel.discordrpc.ui.components.heroMode
import me.shedaniel.discordrpc.ui.nav.LocalNavigator
import me.shedaniel.discordrpc.ui.nav.Route
import me.shedaniel.discordrpc.ui.nav.Tab
import me.shedaniel.discordrpc.ui.theme.NumberFamily
import me.shedaniel.discordrpc.ui.theme.rememberHaptics

@Composable
fun HomeScreen(contentPadding: PaddingValues, onOpenTab: (Tab) -> Unit) {
    val context = LocalContext.current
    val graph = context.graph
    val nav = LocalNavigator.current
    val haptics = rememberHaptics()
    val state by graph.engine.state.collectAsStateWithLifecycle()
    val settings by graph.settings.collectAsStateWithLifecycle()
    val user by graph.tokens.user.collectAsStateWithLifecycle()
    val history by graph.networkMeter.history.collectAsStateWithLifecycle()

    // Permissions can change behind our back (system settings), so re-check on every resume.
    var permTick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { permTick++; onPauseOrDispose { } }
    val notifAccess = remember(permTick) { Permissions.hasNotificationAccess(context) }
    val usageAccess = remember(permTick) { Permissions.hasUsageAccess(context) }

    val activity = state.activities.firstOrNull()
    val mode = heroMode(if (settings.enabled) state.connection else ConnectionState.Paused(PauseReason.DISABLED), activity)
    val preview: (me.shedaniel.discordrpc.core.model.ImageRef) -> String? = { graph.assets.previewUrl(it) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(contentPadding)
            .padding(bottom = 24.dp),
    ) {
        // Top row: connection pill + account.
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusPill(settings.enabled, state.connection, activity != null)
            Spacer(Modifier.weight(1f))
            val today = history.lastOrNull()
            val todayBytes = today?.total(state.transport.takeIf { it != Transport.NONE })?.total ?: 0L
            if (today != null && todayBytes > 0) {
                DataPill(
                    bytes = todayBytes,
                    transport = state.transport,
                    onClick = { haptics.tick(); onOpenTab(Tab.DATA) },
                )
                Spacer(Modifier.width(8.dp))
            }
            IconButton(onClick = { haptics.press(); nav.push(Route.Settings) }) {
                Avatar(user, Modifier.size(36.dp).clip(CircleShape))
            }
        }

        OrbitHero(
            mode = mode,
            activity = activity,
            user = user,
            connection = state.connection,
            modifier = Modifier.padding(horizontal = 24.dp),
            previewUrl = preview,
        )

        // Master switch: shape morphs between pill (on) and square (off). Signed out: sign in instead.
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val h = ButtonDefaults.MediumContainerHeight
            if (state.connection is ConnectionState.LoggedOut || (state.connection as? ConnectionState.Paused)?.reason == PauseReason.TOKEN_INVALID) {
                Button(
                    onClick = { haptics.press(); nav.push(Route.Login) },
                    modifier = Modifier.heightIn(min = h),
                    shapes = ButtonDefaults.shapesFor(h),
                    contentPadding = ButtonDefaults.contentPaddingFor(h),
                ) {
                    Icon(Icons.Rounded.Login, null, Modifier.size(ButtonDefaults.iconSizeFor(h)))
                    Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(h)))
                    Text(stringResource(R.string.home_sign_in), style = ButtonDefaults.textStyleFor(h))
                }
            } else {
                ToggleButton(
                    checked = settings.enabled,
                    onCheckedChange = { on ->
                        haptics.toggle(on)
                        graph.settingsStore.update { it.copy(enabled = on) }
                        if (on) PresenceService.start(context) else PresenceService.stop(context)
                    },
                    modifier = Modifier.heightIn(min = h),
                    shapes = ToggleButtonDefaults.shapesFor(h),
                    contentPadding = ButtonDefaults.contentPaddingFor(h),
                ) {
                    Icon(Icons.Rounded.PowerSettingsNew, null, Modifier.size(ButtonDefaults.iconSizeFor(h)))
                    Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(h)))
                    AnimatedContent(settings.enabled, label = "power") { on ->
                        Text(stringResource(if (on) R.string.home_sharing else R.string.home_off), style = ButtonDefaults.textStyleFor(h))
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // What people see.
        val cardIn = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
        val cardScale = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
        val cardOut = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
        AnimatedContent(
            targetState = state.activities,
            contentKey = { list -> list.map { it.signalId + it.ruleId } },
            transitionSpec = {
                (fadeIn(cardIn) + scaleIn(cardScale, initialScale = 0.94f)) togetherWith fadeOut(cardOut)
            },
            label = "cards",
            modifier = Modifier.padding(horizontal = 16.dp),
        ) { list ->
            if (list.isEmpty()) {
                EmptyCard(notifAccess, settings.foreground.enabled && usageAccess)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    list.forEach { a ->
                        PresenceCard(a, Modifier.fillMaxWidth(), previewUrl = preview, live = mode == me.shedaniel.discordrpc.ui.components.HeroMode.LIVE)
                    }
                }
            }
        }

        // Other things we can see but aren't showing.
        val shownIds = state.activities.map { it.signalId }.toSet()
        val others = state.signals.filter { it.id !in shownIds }
        AnimatedVisibility(others.isNotEmpty(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    stringResource(R.string.home_also_detected),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 12.dp, bottom = 8.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    others.forEach { SignalRow(it) }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.home_sources),
            style = MaterialTheme.typography.labelLargeEmphasized,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 28.dp, bottom = 8.dp),
        )
        // Three tiles side by side; with large text they become full-width rows rather than truncating labels.
        val sourceLabels = listOf(stringResource(R.string.source_media), stringResource(R.string.source_games), stringResource(R.string.source_spotify))
        val tileStyle = MaterialTheme.typography.titleSmall
        val measurer = androidx.compose.ui.text.rememberTextMeasurer()
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.padding(horizontal = 16.dp)) {
            val density = androidx.compose.ui.platform.LocalDensity.current
            val labelRoom = (maxWidth - 20.dp) / 3 - 28.dp
            val inline = sourceLabels.any { l -> with(density) { measurer.measure(l, tileStyle, maxLines = 1, softWrap = false).size.width.toDp() } > labelRoom }
            val tiles: @Composable (Modifier) -> Unit = { tileModifier ->
                SourceTile(
                        inline = inline,
                    glyph = "music",
                    label = stringResource(R.string.source_media),
                    on = settings.media.enabled && notifAccess,
                    needsPermission = !notifAccess,
                    modifier = tileModifier,
                ) {
                    if (!notifAccess) context.startActivity(Permissions.notificationAccessIntent(context))
                    else graph.settingsStore.update { it.copy(media = it.media.copy(enabled = !it.media.enabled)) }
                }
                SourceTile(
                        inline = inline,
                    glyph = "game",
                    label = stringResource(R.string.source_games),
                    on = settings.foreground.enabled && usageAccess,
                    needsPermission = !usageAccess,
                    modifier = tileModifier,
                ) {
                    if (!usageAccess) context.startActivity(Permissions.usageAccessIntent(context))
                    else graph.settingsStore.update { it.copy(foreground = it.foreground.copy(enabled = !it.foreground.enabled)) }
                }
                SourceTile(
                        inline = inline,
                    glyph = "spotify",
                    label = stringResource(R.string.source_spotify),
                    on = settings.spotify.enabled && settings.spotify.clientId.isNotBlank(),
                    needsPermission = false,
                    modifier = tileModifier,
                ) { nav.push(Route.Settings) }
            
            }
            if (inline) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { tiles(Modifier.fillMaxWidth()) }
            else Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { tiles(Modifier.weight(1f)) }
        }

        // Without a battery exemption Android may stop Orbit; surface it where it matters.
        val batteryOk = remember(permTick) { Permissions.isIgnoringBatteryOptimizations(context) }
        AnimatedVisibility(!batteryOk && settings.enabled, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Surface(
                onClick = { haptics.press(); context.startActivity(Permissions.batteryOptimizationIntent(context)) },
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    ShapeGlyph(
                        Icons.Rounded.BatteryAlert, MaterialShapes.Pill, size = 40.dp,
                        container = MaterialTheme.colorScheme.error, content = MaterialTheme.colorScheme.onError,
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(stringResource(R.string.home_battery), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
    }
}

@Composable
private fun StatusPill(enabled: Boolean, c: ConnectionState, hasActivity: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val (label, color) = when {
        !enabled -> stringResource(R.string.status_off) to scheme.outline
        c is ConnectionState.Connected && hasActivity -> stringResource(R.string.status_live) to Color(0xFF3BA55D)
        c is ConnectionState.Connected -> stringResource(R.string.status_connected) to Color(0xFF3BA55D)
        c is ConnectionState.Connecting -> stringResource(R.string.status_connecting) to scheme.tertiary
        c is ConnectionState.Idle -> stringResource(R.string.status_standby) to scheme.outline
        c is ConnectionState.Paused -> stringResource(pauseLabel(c.reason)) to scheme.outline
        c is ConnectionState.Error -> stringResource(R.string.status_reconnecting) to scheme.error
        else -> stringResource(R.string.status_signed_out) to scheme.error
    }
    val dot by animateColorAsState(color, label = "dot")
    Surface(shape = CircleShape, color = scheme.surfaceContainerHigh) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(dot, CircleShape))
            Spacer(Modifier.width(8.dp))
            AnimatedContent(label, label = "status") { Text(it, style = MaterialTheme.typography.labelLarge) }
        }
    }
}

fun pauseLabel(r: PauseReason) = when (r) {
    PauseReason.DISABLED -> R.string.pause_disabled
    PauseReason.NO_NETWORK -> R.string.pause_no_network
    PauseReason.CELLULAR_BLOCKED -> R.string.pause_cellular
    PauseReason.ROAMING_BLOCKED -> R.string.pause_roaming
    PauseReason.DATA_SAVER -> R.string.pause_data_saver
    PauseReason.TOKEN_INVALID -> R.string.pause_token
}

@Composable
private fun DataPill(bytes: Long, transport: Transport, onClick: () -> Unit) {
    val icon: ImageVector = when (transport) {
        Transport.WIFI -> Icons.Rounded.Wifi
        Transport.CELLULAR -> Icons.Rounded.NetworkCell
        else -> Icons.Rounded.Cloud
    }
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh, onClick = onClick) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            Text(formatBytes(bytes), style = MaterialTheme.typography.labelLarge.copy(fontFamily = NumberFamily))
        }
    }
}

@Composable
private fun EmptyCard(mediaReady: Boolean, appsReady: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val spin by me.shedaniel.discordrpc.ui.components.rememberSpin(40_000)
    Surface(shape = MaterialTheme.shapes.extraLarge, color = scheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(vertical = 22.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // What can be shared, lit when its source is ready; slowly turning while waiting.
            listOf("music" to mediaReady, "video" to mediaReady, "game" to appsReady).forEachIndexed { i, (g, ready) ->
                ShapeGlyph(
                    glyphIcon(g), Glyphs.polygon(g), size = 52.dp,
                    container = if (ready) scheme.secondaryContainer else scheme.surfaceContainerHighest,
                    content = if (ready) scheme.onSecondaryContainer else scheme.outline,
                    rotation = if (i % 2 == 0) spin else -spin,
                )
            }
        }
    }
}

@Composable
private fun SignalRow(s: Signal) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            s.packageName?.let { AppIcon(it, Modifier.size(36.dp).clip(CircleShape)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                val title = when (s) {
                    is MediaSignal -> s.title
                    is AppSignal -> s.appLabel
                }
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub = when (s) {
                    is MediaSignal -> listOfNotNull(
                        s.appLabel,
                        s.deviceName?.takeIf { s.origin == SignalOrigin.SPOTIFY_CONNECT },
                        stringResource(if (s.playing) R.string.signal_playing else R.string.signal_paused),
                    ).joinToString(" · ")
                    is AppSignal -> stringResource(if (s.isGame) R.string.signal_game else R.string.signal_open)
                }
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun SourceTile(glyph: String, label: String, on: Boolean, needsPermission: Boolean, modifier: Modifier, inline: Boolean = false, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val haptics = rememberHaptics()
    val container by animateColorAsState(if (on) scheme.primaryContainer else scheme.surfaceContainer, label = "tile")
    Surface(
        onClick = { haptics.toggle(!on); onClick() },
        shape = MaterialTheme.shapes.extraLarge,
        color = container,
        modifier = modifier,
    ) {
        val content: @Composable () -> Unit = {
            Box {
                ShapeGlyph(
                    glyphIcon(glyph), Glyphs.polygon(glyph), size = 40.dp,
                    container = if (on) scheme.primary else scheme.surfaceContainerHighest,
                    content = if (on) scheme.onPrimary else scheme.onSurfaceVariant,
                )
                if (needsPermission) {
                    Box(Modifier.align(Alignment.TopEnd).size(12.dp).border(2.dp, container, CircleShape).background(scheme.error, CircleShape))
                }
            }
            Spacer(Modifier.size(if (inline) 14.dp else 12.dp))
            Text(label, style = MaterialTheme.typography.titleSmall, maxLines = if (inline) 2 else 1, overflow = TextOverflow.Ellipsis)
        }
        if (inline) Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { content() }
        else Column(Modifier.padding(14.dp)) { content() }
    }
}

