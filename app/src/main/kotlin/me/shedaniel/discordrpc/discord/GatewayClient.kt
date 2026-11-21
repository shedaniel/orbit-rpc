package me.shedaniel.discordrpc.discord

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import me.shedaniel.discordrpc.core.settings.DiscordSettings
import me.shedaniel.discordrpc.core.settings.OnlineStatus
import me.shedaniel.discordrpc.net.Http
import me.shedaniel.discordrpc.net.NetworkMeter

// STUB — implemented by the discord/net agent.
class GatewayClient(http: Http, meter: NetworkMeter, scope: CoroutineScope, settings: () -> DiscordSettings) : DiscordSession {
    override val status: StateFlow<GatewayStatus> = MutableStateFlow(GatewayStatus.Closed)
    override suspend fun setActivities(token: String, activities: List<ResolvedActivity>, status: OnlineStatus, afk: Boolean, applicationId: String) {}
    override suspend fun disconnect() {}
}
