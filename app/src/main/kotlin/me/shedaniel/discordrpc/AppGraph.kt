package me.shedaniel.discordrpc

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import me.shedaniel.discordrpc.core.settings.NetworkProfile
import me.shedaniel.discordrpc.core.rules.RuleSet
import me.shedaniel.discordrpc.core.settings.AppSettings
import me.shedaniel.discordrpc.core.settings.JsonStore
import me.shedaniel.discordrpc.discord.DiscordAssetResolver
import me.shedaniel.discordrpc.discord.DiscordRest
import me.shedaniel.discordrpc.discord.GatewayClient
import me.shedaniel.discordrpc.discord.TokenStore
import me.shedaniel.discordrpc.net.Http
import me.shedaniel.discordrpc.net.NetworkMeter
import me.shedaniel.discordrpc.net.NetworkMonitor
import me.shedaniel.discordrpc.service.PresenceEngine
import me.shedaniel.discordrpc.sources.AppCatalog
import me.shedaniel.discordrpc.sources.ForegroundAppSource
import me.shedaniel.discordrpc.sources.MediaSessionSource
import me.shedaniel.discordrpc.sources.SpotifyAuth
import me.shedaniel.discordrpc.sources.SpotifyConnectSource
import java.io.File

/** Manual dependency graph; one per process. */
class AppGraph(val context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settingsStore = JsonStore(File(context.filesDir, "settings.json"), AppSettings.serializer(), { AppSettings() }, scope)
    val rulesStore = JsonStore(File(context.filesDir, "rules.json"), RuleSet.serializer(), { RuleSet() }, scope)
    val settings get() = settingsStore.state

    val networkMonitor = NetworkMonitor(context, scope)
    val networkMeter = NetworkMeter(context, scope, networkMonitor)
    val http = Http(networkMeter)

    val tokens = TokenStore(context)
    val rest = DiscordRest(http)
    val gateway = GatewayClient(http, networkMeter, scope, { settingsStore.value.discord }, networkMonitor.conditions)

    val catalog = AppCatalog(context, scope)
    val assets = DiscordAssetResolver(context, http, catalog, scope) { settingsStore.value.images }

    val mediaSource = MediaSessionSource(context, catalog, settingsStore.state, scope)
    val foregroundSource = ForegroundAppSource(context, catalog, settingsStore.state, scope)
    val spotifyAuth = SpotifyAuth(context, http, scope)

    private val profileHolder = MutableStateFlow<NetworkProfile?>(null)
    val spotifySource = SpotifyConnectSource(context, http, settingsStore.state, profileHolder, mediaSource.signals, scope)

    val engine = PresenceEngine(
        scope = scope,
        settings = settingsStore.state,
        rules = rulesStore.state,
        tokens = tokens,
        conditions = networkMonitor.conditions,
        sources = listOf(mediaSource, foregroundSource, spotifySource),
        session = gateway,
        resolver = assets,
    )

    init {
        // Feed the engine's network profile to the Spotify poller.
        scope.launch { engine.profile.collect { profileHolder.value = it } }
    }
}
