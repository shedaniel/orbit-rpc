package me.shedaniel.discordrpc.net

import kotlinx.serialization.Serializable

/** What a network call was for. Used for metering and the data usage screen. */
@Serializable
enum class TrafficCategory { GATEWAY, DISCORD_API, ARTWORK_LOOKUP, IMAGE_UPLOAD, APP_ICON, SPOTIFY, OTHER }

@Serializable
enum class Transport { WIFI, CELLULAR, OTHER, NONE }

data class NetworkConditions(
    val connected: Boolean,
    val transport: Transport,
    val metered: Boolean,
    val roaming: Boolean,
    /** Android Data Saver is on and this app is not allowlisted. */
    val dataSaver: Boolean,
)

@Serializable
data class TrafficCounter(val rx: Long = 0, val tx: Long = 0, val requests: Long = 0) {
    val total get() = rx + tx
    operator fun plus(o: TrafficCounter) = TrafficCounter(rx + o.rx, tx + o.tx, requests + o.requests)
}

/** One day of usage. Key format "yyyy-MM-dd". */
@Serializable
data class DailyUsage(
    val day: String,
    /** transport -> category -> counter */
    val usage: Map<Transport, Map<TrafficCategory, TrafficCounter>> = emptyMap(),
    /** Whole-app bytes as reported by the kernel (TrafficStats), per transport, for cross-checking. */
    val systemRx: Map<Transport, Long> = emptyMap(),
    val systemTx: Map<Transport, Long> = emptyMap(),
) {
    fun total(transport: Transport? = null): TrafficCounter =
        usage.filterKeys { transport == null || it == transport }.values.flatMap { it.values }.fold(TrafficCounter()) { a, b -> a + b }

    fun byCategory(transport: Transport? = null): Map<TrafficCategory, TrafficCounter> =
        usage.filterKeys { transport == null || it == transport }.values
            .flatMap { it.entries }
            .groupBy({ it.key }, { it.value })
            .mapValues { (_, v) -> v.fold(TrafficCounter()) { a, b -> a + b } }
}
