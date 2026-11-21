package me.shedaniel.discordrpc.net

import okhttp3.OkHttpClient

// STUB — implemented by the discord/net agent.
class Http(meter: NetworkMeter) {
    val base: OkHttpClient = OkHttpClient()
    fun client(category: TrafficCategory): OkHttpClient = base
}
