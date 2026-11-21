package me.shedaniel.discordrpc.net

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.shedaniel.discordrpc.core.util.EventLog

/**
 * Observes the system default network and Data Saver.
 *
 * [NetworkConditions.dataSaver] is only true when Data Saver restricts *this* app
 * ([ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED]) and the network is metered, since Data Saver
 * has no effect on unmetered networks.
 */
class NetworkMonitor(context: Context, @Suppress("unused") scope: CoroutineScope) {
    private val app = context.applicationContext
    private val cm = app.getSystemService(ConnectivityManager::class.java)

    @Volatile private var caps: NetworkCapabilities? = runCatching { cm.getNetworkCapabilities(cm.activeNetwork) }.getOrNull()

    private val _conditions = MutableStateFlow(compute())
    val conditions: StateFlow<NetworkConditions> = _conditions.asStateFlow()

    init {
        try {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    caps = networkCapabilities
                    publish()
                }

                override fun onLost(network: Network) {
                    caps = null
                    publish()
                }
            })
        } catch (e: Exception) {
            EventLog.w("Network", "Could not register network callback", e)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) = publish()
        }
        ContextCompat.registerReceiver(
            app, receiver, IntentFilter(ConnectivityManager.ACTION_RESTRICT_BACKGROUND_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    private fun publish() {
        val next = compute()
        val prev = _conditions.value
        if (next != prev) {
            _conditions.value = next
            EventLog.d("Network", "Network: ${describe(next)}")
        }
    }

    private fun compute(): NetworkConditions {
        val c = caps ?: return NetworkConditions(connected = false, transport = Transport.NONE, metered = false, roaming = false, dataSaver = false)
        val transport = when {
            c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Transport.WIFI
            c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Transport.CELLULAR
            else -> Transport.OTHER
        }
        val metered = !c.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        val roaming = !c.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING)
        val restricted = runCatching { cm.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED }.getOrDefault(false)
        return NetworkConditions(
            connected = c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
            transport = transport,
            metered = metered,
            roaming = roaming,
            dataSaver = restricted && metered,
        )
    }

    private fun describe(c: NetworkConditions) = if (!c.connected) "offline" else buildString {
        append(c.transport.name.lowercase())
        if (c.metered) append(", metered")
        if (c.roaming) append(", roaming")
        if (c.dataSaver) append(", data saver")
    }
}
