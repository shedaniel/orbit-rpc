package me.shedaniel.discordrpc.net

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// STUB — implemented by the discord/net agent.
class NetworkMeter(context: Context, scope: CoroutineScope, monitor: NetworkMonitor) {
    /** Last 30 days, oldest first, including today. */
    val history: StateFlow<List<DailyUsage>> = MutableStateFlow(emptyList())
    fun record(category: TrafficCategory, rx: Long, tx: Long, requests: Long = 1) {}
}
