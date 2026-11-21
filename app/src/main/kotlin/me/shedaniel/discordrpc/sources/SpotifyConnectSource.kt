package me.shedaniel.discordrpc.sources

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import me.shedaniel.discordrpc.core.model.Signal
import me.shedaniel.discordrpc.core.settings.AppSettings
import me.shedaniel.discordrpc.core.settings.NetworkProfile
import me.shedaniel.discordrpc.net.Http

// STUB — implemented by the sources agent.
class SpotifyConnectSource(
    context: Context,
    http: Http,
    settings: StateFlow<AppSettings>,
    /** Current network profile, null = paused (no polling). */
    profile: StateFlow<NetworkProfile?>,
    /** Local media signals, to skip polling while Spotify plays on this phone. */
    local: StateFlow<List<Signal>>,
    scope: CoroutineScope,
) : SignalSource {
    override val signals: StateFlow<List<Signal>> = MutableStateFlow(emptyList())
    override fun start() {}
    override fun stop() {}
}
