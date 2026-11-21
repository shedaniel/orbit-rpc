package me.shedaniel.discordrpc.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.TimerOff
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.shedaniel.discordrpc.BuildConfig
import me.shedaniel.discordrpc.R
import me.shedaniel.discordrpc.core.rules.RuleSet
import me.shedaniel.discordrpc.core.settings.AppSettings
import me.shedaniel.discordrpc.core.settings.ArtworkLookup
import me.shedaniel.discordrpc.core.settings.ImageHost
import me.shedaniel.discordrpc.graph
import me.shedaniel.discordrpc.sources.Permissions
import me.shedaniel.discordrpc.sources.SpotifyAuth
import me.shedaniel.discordrpc.ui.components.Avatar
import me.shedaniel.discordrpc.ui.components.Choice
import me.shedaniel.discordrpc.ui.components.ChoiceItem
import me.shedaniel.discordrpc.ui.components.ClickItem
import me.shedaniel.discordrpc.ui.components.InfoItem
import me.shedaniel.discordrpc.ui.components.PolygonShape
import me.shedaniel.discordrpc.ui.components.SettingsGroup
import me.shedaniel.discordrpc.ui.components.SliderItem
import me.shedaniel.discordrpc.ui.components.SwitchItem
import me.shedaniel.discordrpc.ui.nav.LocalNavigator
import me.shedaniel.discordrpc.ui.nav.Route
import me.shedaniel.discordrpc.ui.theme.NumberFamily
import me.shedaniel.discordrpc.ui.theme.rememberHaptics

private const val SOURCE_URL = "https://github.com/shedaniel/orbit-rpc"
private const val SPOTIFY_DASHBOARD_URL = "https://developer.spotify.com/dashboard"

private enum class SettingsDialog { SIGN_OUT, APP_ID, CLIENT_ID, SPOTIFY_SIGN_OUT, RESET_RULES }

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val graph = context.graph
    val nav = LocalNavigator.current
    val haptics = rememberHaptics()
    val settings by graph.settings.collectAsStateWithLifecycle()
    val user by graph.tokens.user.collectAsStateWithLifecycle()
    val token by graph.tokens.token.collectAsStateWithLifecycle()
    val spotifyAccount by graph.spotifyAuth.account.collectAsStateWithLifecycle()
    var dialog by rememberSaveable { mutableStateOf<SettingsDialog?>(null) }

    val update: ((AppSettings) -> AppSettings) -> Unit = { graph.settingsStore.update(it) }
    val open: (Intent) -> Unit = { runCatching { context.startActivity(it) } }

    // Permission state can change in system settings; re-read on every resume.
    var permTick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { permTick++; onPauseOrDispose { } }
    val notifAccess = remember(permTick) { Permissions.hasNotificationAccess(context) }
    val usageAccess = remember(permTick) { Permissions.hasUsageAccess(context) }
    val battery = remember(permTick) { isIgnoringBatteryOptimizations(context) }
    val postNotif = remember(permTick) { hasPostNotifications(context) }
    var askedNotif by rememberSaveable { mutableStateOf(false) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) haptics.confirm()
        permTick++
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = { haptics.press(); nav.pop() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.screen_back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { inner ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = inner.calculateTopPadding(), bottom = inner.calculateBottomPadding() + 24.dp),
        ) {
            // ---- Account
            item(key = "account") {
                SettingsGroup(stringResource(R.string.settings_account)) {
                    item { shapes ->
                        SegmentedListItem(
                            shapes = shapes,
                            colors = settingsItemColors(),
                            leadingContent = {
                                Avatar(user, Modifier.size(56.dp).clip(remember { PolygonShape(MaterialShapes.Cookie12Sided) }))
                            },
                            supportingContent = user?.let { u -> { Text(stringResource(R.string.settings_username, u.username)) } },
                        ) {
                            Text(
                                user?.displayName ?: stringResource(R.string.settings_signed_out),
                                style = MaterialTheme.typography.titleLargeEmphasized,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    item {
                        if (token != null) {
                            ClickItem(it, stringResource(R.string.settings_sign_out), { dialog = SettingsDialog.SIGN_OUT }, icon = Icons.AutoMirrored.Rounded.Logout)
                        } else {
                            ClickItem(it, stringResource(R.string.settings_sign_in), { nav.push(Route.Login) }, icon = Icons.AutoMirrored.Rounded.Login)
                        }
                    }
                }
            }

            // ---- Discord
            item(key = "discord") {
                val d = settings.discord
                val setD: ((me.shedaniel.discordrpc.core.settings.DiscordSettings) -> me.shedaniel.discordrpc.core.settings.DiscordSettings) -> Unit =
                    { f -> update { it.copy(discord = f(it.discord)) } }
                SettingsGroup(stringResource(R.string.settings_discord)) {
                    item { StatusChoiceItem(it, stringResource(R.string.settings_status), d.status) { s -> setD { it.copy(status = s) } } }
                    item { SwitchItem(it, stringResource(R.string.settings_afk), d.afk, { v -> setD { it.copy(afk = v) } }, icon = Icons.Rounded.NotificationsActive) }
                    item { SwitchItem(it, stringResource(R.string.settings_multiple), d.multipleActivities, { v -> setD { it.copy(multipleActivities = v) } }, icon = Icons.Rounded.Layers) }
                    item {
                        SliderItem(
                            it, stringResource(R.string.settings_idle_disconnect), d.idleDisconnectSeconds, 15..600,
                            { v -> setD { it.copy(idleDisconnectSeconds = v) } }, icon = Icons.Rounded.TimerOff, step = 15,
                            format = { s -> formatSecondsPlain(context, s) },
                        )
                    }
                    item {
                        SliderItem(
                            it, stringResource(R.string.settings_debounce), d.updateDebounceMs.toInt(), 500..5000,
                            { v -> setD { it.copy(updateDebounceMs = v.toLong()) } }, icon = Icons.Rounded.Speed, step = 250,
                            format = { ms -> formatMillisPlain(context, ms) },
                        )
                    }
                    item {
                        ClickItem(
                            it, stringResource(R.string.settings_app_id), { dialog = SettingsDialog.APP_ID },
                            icon = Icons.Rounded.Tag,
                            summary = d.applicationId.ifBlank { stringResource(R.string.screen_default) },
                        )
                    }
                }
                SettingsGroup(stringResource(R.string.settings_advanced)) {
                    item { SwitchItem(it, stringResource(R.string.settings_lean), d.leanGateway, { v -> setD { it.copy(leanGateway = v) } }, icon = Icons.Rounded.Bolt) }
                    item { SwitchItem(it, stringResource(R.string.settings_compression), d.compression, { v -> setD { it.copy(compression = v) } }, icon = Icons.Rounded.Compress) }
                }
            }

            // ---- Media
            item(key = "media") {
                val m = settings.media
                SettingsGroup(stringResource(R.string.settings_media)) {
                    item {
                        SliderItem(
                            it, stringResource(R.string.settings_paused_grace), m.pausedGraceSeconds, 0..120,
                            { v -> update { s -> s.copy(media = s.media.copy(pausedGraceSeconds = v)) } },
                            icon = Icons.Rounded.PauseCircle, step = 5, format = { s -> formatSecondsPlain(context, s, zeroIsOff = true) },
                        )
                    }
                    item {
                        SliderItem(
                            it, stringResource(R.string.settings_min_duration), m.minDurationSeconds, 0..120,
                            { v -> update { s -> s.copy(media = s.media.copy(minDurationSeconds = v)) } },
                            icon = Icons.Rounded.Timer, step = 5, format = { s -> formatSecondsPlain(context, s, zeroIsOff = true) },
                        )
                    }
                    item {
                        SwitchItem(
                            it, stringResource(R.string.settings_new_apps), m.includeNewApps,
                            { v -> update { s -> s.copy(media = s.media.copy(includeNewApps = v)) } }, icon = Icons.Rounded.AddCircle,
                        )
                    }
                }
            }

            // ---- Apps & games
            item(key = "apps") {
                val f = settings.foreground
                SettingsGroup(stringResource(R.string.settings_apps)) {
                    item {
                        SwitchItem(
                            it, stringResource(R.string.settings_auto_games), f.autoGames,
                            { v -> update { s -> s.copy(foreground = s.foreground.copy(autoGames = v)) } }, icon = Icons.Rounded.SportsEsports,
                        )
                    }
                    item {
                        SliderItem(
                            it, stringResource(R.string.settings_poll), f.pollSeconds, 2..30,
                            { v -> update { s -> s.copy(foreground = s.foreground.copy(pollSeconds = v)) } },
                            icon = Icons.Rounded.Update, format = { s -> formatSecondsPlain(context, s) },
                        )
                    }
                    item {
                        SliderItem(
                            it, stringResource(R.string.settings_linger), f.lingerSeconds, 0..120,
                            { v -> update { s -> s.copy(foreground = s.foreground.copy(lingerSeconds = v)) } },
                            icon = Icons.Rounded.HourglassBottom, step = 5, format = { s -> formatSecondsPlain(context, s, zeroIsOff = true) },
                        )
                    }
                }
            }

            // ---- Spotify Connect
            item(key = "spotify") {
                val sp = settings.spotify
                SettingsGroup(
                    stringResource(R.string.settings_spotify),
                    Modifier.animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
                ) {
                    item {
                        SwitchItem(
                            it, stringResource(R.string.settings_spotify_enable), sp.enabled,
                            { v -> update { s -> s.copy(spotify = s.spotify.copy(enabled = v)) } }, icon = Icons.Rounded.Speaker,
                        )
                    }
                    if (sp.enabled) {
                        item {
                            ClickItem(
                                it, stringResource(R.string.settings_client_id), { dialog = SettingsDialog.CLIENT_ID },
                                icon = Icons.Rounded.Key,
                                summary = sp.clientId.ifBlank { stringResource(R.string.settings_client_id_not_set) },
                            )
                        }
                        item {
                            val account = spotifyAccount
                            ClickItem(
                                it, stringResource(R.string.settings_spotify_account),
                                onClick = {
                                    if (account != null) dialog = SettingsDialog.SPOTIFY_SIGN_OUT
                                    else if (activity != null) graph.spotifyAuth.beginLogin(activity, sp.clientId)
                                },
                                icon = Icons.Rounded.AccountCircle,
                                enabled = account != null || sp.clientId.isNotBlank(),
                                summary = when {
                                    account != null -> stringResource(R.string.settings_spotify_signed_in, account.displayName)
                                    sp.clientId.isBlank() -> stringResource(R.string.settings_spotify_needs_id)
                                    else -> null
                                },
                                trailing = if (account == null && sp.clientId.isNotBlank()) {
                                    { Icon(Icons.AutoMirrored.Rounded.Login, null, tint = MaterialTheme.colorScheme.primary) }
                                } else null,
                            )
                        }
                        item {
                            SliderItem(
                                it, stringResource(R.string.settings_spotify_poll), sp.pollSeconds, 10..120,
                                { v -> update { s -> s.copy(spotify = s.spotify.copy(pollSeconds = v)) } },
                                icon = Icons.Rounded.Sync, step = 5, format = { s -> formatSecondsPlain(context, s) },
                            )
                        }
                        item {
                            SliderItem(
                                it, stringResource(R.string.settings_spotify_idle_poll), sp.idlePollSeconds, 30..600,
                                { v -> update { s -> s.copy(spotify = s.spotify.copy(idlePollSeconds = v)) } },
                                icon = Icons.Rounded.Snooze, step = 30, format = { s -> formatSecondsPlain(context, s) },
                            )
                        }
                        item {
                            SwitchItem(
                                it, stringResource(R.string.settings_spotify_skip_local), sp.skipWhenLocal,
                                { v -> update { s -> s.copy(spotify = s.spotify.copy(skipWhenLocal = v)) } }, icon = Icons.Rounded.PhoneAndroid,
                            )
                        }
                    }
                }
            }

            // ---- Images
            item(key = "images") {
                val im = settings.images
                SettingsGroup(stringResource(R.string.settings_images)) {
                    item {
                        ChoiceItem(
                            it, stringResource(R.string.settings_lookup),
                            listOf(
                                Choice(ArtworkLookup.NONE, stringResource(R.string.screen_off)),
                                Choice(ArtworkLookup.ITUNES, stringResource(R.string.settings_lookup_itunes)),
                                Choice(ArtworkLookup.DEEZER, stringResource(R.string.settings_lookup_deezer)),
                            ),
                            im.lookup, { v -> update { s -> s.copy(images = s.images.copy(lookup = v)) } }, icon = Icons.Rounded.Album,
                        )
                    }
                    item {
                        ChoiceItem(
                            it, stringResource(R.string.settings_host),
                            listOf(
                                Choice(ImageHost.NONE, stringResource(R.string.screen_off)),
                                Choice(ImageHost.LITTERBOX, stringResource(R.string.settings_host_litterbox)),
                                Choice(ImageHost.CATBOX, stringResource(R.string.settings_host_catbox)),
                            ),
                            im.host, { v -> update { s -> s.copy(images = s.images.copy(host = v)) } }, icon = Icons.Rounded.CloudUpload,
                            summary = when (im.host) {
                                ImageHost.LITTERBOX -> stringResource(R.string.settings_host_litterbox_summary)
                                ImageHost.CATBOX -> stringResource(R.string.settings_host_catbox_summary)
                                ImageHost.NONE -> null
                            },
                        )
                    }
                    item {
                        var cleared by remember { mutableStateOf(false) }
                        ClickItem(
                            it, stringResource(R.string.settings_clear_images),
                            onClick = { graph.assets.clearCache(); cleared = true },
                            icon = Icons.Rounded.DeleteSweep,
                            summary = if (cleared) stringResource(R.string.settings_clear_images_done) else null,
                        )
                    }
                }
            }

            // ---- Appearance
            item(key = "appearance") {
                val ui = settings.ui
                SettingsGroup(stringResource(R.string.settings_appearance)) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        item {
                            SwitchItem(
                                it, stringResource(R.string.settings_dynamic_color), ui.dynamicColor,
                                { v -> update { s -> s.copy(ui = s.ui.copy(dynamicColor = v)) } }, icon = Icons.Rounded.Palette,
                            )
                        }
                    }
                    item {
                        ChoiceItem(
                            it, stringResource(R.string.settings_theme),
                            listOf(
                                Choice(0, stringResource(R.string.settings_theme_system), Icons.Rounded.BrightnessAuto),
                                Choice(1, stringResource(R.string.settings_theme_light), Icons.Rounded.LightMode),
                                Choice(2, stringResource(R.string.settings_theme_dark), Icons.Rounded.DarkMode),
                            ),
                            ui.themeMode, { v -> update { s -> s.copy(ui = s.ui.copy(themeMode = v)) } }, icon = Icons.Rounded.Contrast,
                        )
                    }
                    item {
                        SwitchItem(
                            it, stringResource(R.string.settings_amoled), ui.amoled,
                            { v -> update { s -> s.copy(ui = s.ui.copy(amoled = v)) } },
                            icon = Icons.Rounded.DarkMode, enabled = ui.themeMode != 1,
                        )
                    }
                    item {
                        SwitchItem(
                            it, stringResource(R.string.settings_haptics), ui.haptics,
                            { v -> update { s -> s.copy(ui = s.ui.copy(haptics = v)) } }, icon = Icons.Rounded.Vibration,
                        )
                    }
                }
            }

            // ---- Permissions
            item(key = "permissions") {
                SettingsGroup(stringResource(R.string.settings_permissions)) {
                    item {
                        PermissionItem(
                            it, stringResource(R.string.settings_perm_notification_access), stringResource(R.string.settings_perm_notification_access_summary),
                            Icons.Rounded.MusicNote, notifAccess,
                        ) { open(Permissions.notificationAccessIntent(context)) }
                    }
                    item {
                        PermissionItem(
                            it, stringResource(R.string.settings_perm_usage), stringResource(R.string.settings_perm_usage_summary),
                            Icons.Rounded.Apps, usageAccess,
                        ) { open(Permissions.usageAccessIntent(context)) }
                    }
                    item {
                        PermissionItem(
                            it, stringResource(R.string.settings_perm_battery), stringResource(R.string.settings_perm_battery_summary),
                            Icons.Rounded.BatteryChargingFull, battery,
                        ) {
                            if (battery) open(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                            else open(batteryOptimizationIntent(context))
                        }
                    }
                    item {
                        PermissionItem(
                            it, stringResource(R.string.settings_perm_notifications), stringResource(R.string.settings_perm_notifications_summary),
                            Icons.Rounded.Notifications, postNotif,
                        ) {
                            if (!postNotif && !askedNotif && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                askedNotif = true
                                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                open(
                                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                                )
                            }
                        }
                    }
                }
            }

            // ---- More
            item(key = "more") {
                SettingsGroup(stringResource(R.string.settings_more)) {
                    item { ClickItem(it, stringResource(R.string.settings_log), { nav.push(Route.Log) }, icon = Icons.AutoMirrored.Rounded.ReceiptLong) }
                    item { ClickItem(it, stringResource(R.string.settings_reset_rules), { dialog = SettingsDialog.RESET_RULES }, icon = Icons.Rounded.RestartAlt) }
                    item {
                        ClickItem(
                            it, stringResource(R.string.settings_source), { open(Intent(Intent.ACTION_VIEW, Uri.parse(SOURCE_URL))) },
                            icon = Icons.Rounded.Code,
                            trailing = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        )
                    }
                    item {
                        InfoItem(
                            it, stringResource(R.string.settings_version), icon = Icons.Rounded.Info,
                            trailing = {
                                Text(
                                    BuildConfig.VERSION_NAME,
                                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = NumberFamily),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                        )
                    }
                }
            }
        }
    }

    // ---- Dialogs
    when (dialog) {
        SettingsDialog.SIGN_OUT -> ConfirmDialog(
            title = stringResource(R.string.settings_sign_out_confirm),
            confirmLabel = stringResource(R.string.settings_sign_out),
            icon = Icons.AutoMirrored.Rounded.Logout,
            onConfirm = { graph.tokens.setToken(null) },
            onDismiss = { dialog = null },
        )
        SettingsDialog.SPOTIFY_SIGN_OUT -> ConfirmDialog(
            title = stringResource(R.string.settings_spotify_sign_out_confirm),
            confirmLabel = stringResource(R.string.settings_sign_out),
            icon = Icons.AutoMirrored.Rounded.Logout,
            onConfirm = { graph.spotifyAuth.logout() },
            onDismiss = { dialog = null },
        )
        SettingsDialog.RESET_RULES -> ConfirmDialog(
            title = stringResource(R.string.settings_reset_rules_confirm),
            confirmLabel = stringResource(R.string.settings_reset),
            icon = Icons.Rounded.RestartAlt,
            onConfirm = { graph.rulesStore.update { RuleSet() } },
            onDismiss = { dialog = null },
        )
        SettingsDialog.APP_ID -> TextFieldDialog(
            title = stringResource(R.string.settings_app_id),
            initial = settings.discord.applicationId,
            icon = Icons.Rounded.Tag,
            placeholder = stringResource(R.string.settings_app_id_hint),
            numeric = true,
            filter = { s -> s.filter { it.isDigit() }.take(24) },
            onSave = { v -> update { it.copy(discord = it.discord.copy(applicationId = v)) } },
            onDismiss = { dialog = null },
        )
        SettingsDialog.CLIENT_ID -> TextFieldDialog(
            title = stringResource(R.string.settings_client_id),
            initial = settings.spotify.clientId,
            icon = Icons.Rounded.Key,
            filter = { s -> s.filter { it.isLetterOrDigit() }.take(64) },
            onSave = { v -> update { it.copy(spotify = it.spotify.copy(clientId = v)) } },
            onDismiss = { dialog = null },
            extra = { SpotifySetupHints(open) },
        )
        null -> Unit
    }
}

@Composable
private fun SpotifySetupHints(open: (Intent) -> Unit) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val scheme = MaterialTheme.colorScheme
    Column {
        Text(
            stringResource(R.string.settings_client_id_dashboard),
            style = MaterialTheme.typography.bodyMedium.copy(textDecoration = TextDecoration.Underline),
            color = scheme.primary,
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .clickable { haptics.press(); open(Intent(Intent.ACTION_VIEW, Uri.parse(SPOTIFY_DASHBOARD_URL))) }
                .padding(vertical = 6.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_redirect_uri), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                Text(SpotifyAuth.REDIRECT_URI, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = NumberFamily))
            }
            var copied by remember { mutableStateOf(false) }
            IconButton(onClick = {
                val cm = context.getSystemService(ClipboardManager::class.java)
                cm?.setPrimaryClip(ClipData.newPlainText(SpotifyAuth.REDIRECT_URI, SpotifyAuth.REDIRECT_URI))
                haptics.confirm()
                copied = true
            }) {
                AnimatedContent(copied, transitionSpec = { (fadeIn() + scaleIn()) togetherWith fadeOut() }, label = "copy") { done ->
                    Icon(
                        if (done) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                        stringResource(if (done) R.string.settings_copied else R.string.settings_copy),
                        tint = if (done) scheme.primary else scheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionItem(
    shapes: androidx.compose.material3.ListItemShapes,
    title: String,
    summary: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    granted: Boolean,
    onGrant: () -> Unit,
) {
    ClickItem(
        shapes, title, onGrant, icon = icon, summary = summary,
        trailing = {
            AnimatedContent(granted, transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.6f)) togetherWith fadeOut() }, label = "perm") { g ->
                if (g) GrantedMark(true) else GrantButton(onGrant)
            }
        },
    )
}

private fun hasPostNotifications(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

/** Non-composable formatting for slider labels (called from SliderItem's lambda). */
internal fun formatSecondsPlain(context: Context, s: Int, zeroIsOff: Boolean = false): String = when {
    s == 0 && zeroIsOff -> context.getString(R.string.screen_off)
    s >= 60 && s % 60 == 0 -> context.getString(R.string.unit_minutes, s / 60)
    else -> context.getString(R.string.unit_seconds, s)
}

internal fun formatMillisPlain(context: Context, ms: Int): String =
    if (ms < 1000) context.getString(R.string.unit_ms, ms)
    else context.getString(
        R.string.unit_seconds_decimal,
        String.format(java.util.Locale.getDefault(), "%.2f", ms / 1000f).trimEnd('0').trimEnd('.', ','),
    )
