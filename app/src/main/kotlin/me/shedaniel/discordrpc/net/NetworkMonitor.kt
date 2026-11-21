package me.shedaniel.discordrpc.net

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// STUB — implemented by the discord/net agent.
class NetworkMonitor(context: Context, scope: CoroutineScope) {
    val conditions: StateFlow<NetworkConditions> = MutableStateFlow(NetworkConditions(true, Transport.WIFI, false, false, false))
}
