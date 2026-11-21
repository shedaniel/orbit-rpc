package me.shedaniel.discordrpc.net

import android.content.Context
import android.net.TrafficStats
import android.os.Process
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import me.shedaniel.discordrpc.core.settings.AppJson
import me.shedaniel.discordrpc.core.util.EventLog
import java.io.File
import java.time.LocalDate

/** Something that can be told about bytes we moved. Implemented by [NetworkMeter]; tests use fakes. */
interface TrafficRecorder {
    fun record(category: TrafficCategory, rx: Long, tx: Long, requests: Long = 1)
}

/**
 * Per-day, per-transport, per-category byte accounting, persisted to `traffic.json` (30 days).
 *
 * Besides our own accounting (fed by [Http]'s event listener and the gateway's frame metering) it samples
 * the kernel's per-UID totals ([TrafficStats.getUidRxBytes]) and attributes the deltas to the transport that
 * was active, into [DailyUsage.systemRx]/[DailyUsage.systemTx], so the UI can cross-check.
 */
class NetworkMeter(context: Context, private val scope: CoroutineScope, private val monitor: NetworkMonitor) : TrafficRecorder {
    private val file = File(context.filesDir, "traffic.json")
    private val tmp = File(context.filesDir, "traffic.json.tmp")
    private val lock = Any()
    private val days: MutableMap<String, DailyUsage> = LinkedHashMap()
    private val uid = Process.myUid()
    private var lastUidRx = -1L
    private var lastUidTx = -1L

    /** The transport the kernel counters are currently attributed to (updated *after* sampling on a change). */
    private var sysTransport: Transport = monitor.conditions.value.transport

    private val _history = MutableStateFlow<List<DailyUsage>>(emptyList())

    /** Last 30 days, oldest first, including today (even if empty). */
    val history: StateFlow<List<DailyUsage>> = _history.asStateFlow()

    private var writeJob: Job? = null

    init {
        load()
        synchronized(lock) {
            lastUidRx = uidRx()
            lastUidTx = uidTx()
            publishLocked()
        }
        scope.launch {
            monitor.conditions.drop(1).collect { c ->
                synchronized(lock) {
                    if (c.transport != sysTransport) {
                        sampleLocked() // attribute what happened so far to the old transport
                        sysTransport = c.transport
                        publishLocked()
                    }
                }
                scheduleWrite()
            }
        }
    }

    /** Today's usage (empty if nothing was recorded yet). */
    fun today(): DailyUsage = synchronized(lock) { days[today_()] ?: DailyUsage(today_()) }

    override fun record(category: TrafficCategory, rx: Long, tx: Long, requests: Long) {
        if (rx == 0L && tx == 0L && requests == 0L) return
        val transport = monitor.conditions.value.transport
        synchronized(lock) {
            val key = today_()
            val d = days[key] ?: DailyUsage(key)
            val perT = d.usage[transport].orEmpty()
            val cur = perT[category] ?: TrafficCounter()
            val next = cur + TrafficCounter(rx.coerceAtLeast(0), tx.coerceAtLeast(0), requests.coerceAtLeast(0))
            days[key] = d.copy(usage = d.usage + (transport to (perT + (category to next))))
            sampleLocked()
            publishLocked()
        }
        scheduleWrite()
    }

    /** Forces a kernel counter sample (e.g. from the UI before showing numbers). */
    fun sample() {
        synchronized(lock) {
            sampleLocked()
            publishLocked()
        }
        scheduleWrite()
    }

    fun clear() {
        synchronized(lock) {
            days.clear()
            publishLocked()
        }
        scheduleWrite()
    }

    private fun sampleLocked() {
        val rx = uidRx()
        val tx = uidTx()
        if (rx < 0 || tx < 0) return
        val dRx = if (lastUidRx in 0..rx) rx - lastUidRx else 0
        val dTx = if (lastUidTx in 0..tx) tx - lastUidTx else 0
        lastUidRx = rx
        lastUidTx = tx
        if (dRx == 0L && dTx == 0L) return
        val key = today_()
        val d = days[key] ?: DailyUsage(key)
        val t = sysTransport
        days[key] = d.copy(
            systemRx = d.systemRx + (t to ((d.systemRx[t] ?: 0) + dRx)),
            systemTx = d.systemTx + (t to ((d.systemTx[t] ?: 0) + dTx)),
        )
    }

    private fun publishLocked() {
        val key = today_()
        val cutoff = LocalDate.now().minusDays(KEEP_DAYS - 1L).toString()
        days.keys.removeAll { it < cutoff }
        if (key !in days) days[key] = DailyUsage(key)
        _history.value = days.values.sortedBy { it.day }
    }

    private fun scheduleWrite() {
        synchronized(lock) {
            if (writeJob?.isActive == true) return
            writeJob = scope.launch(Dispatchers.IO) {
                delay(WRITE_DEBOUNCE_MS)
                write()
            }
        }
    }

    private fun load() {
        try {
            if (!file.exists()) return
            val list = AppJson.decodeFromString(ListSerializer(DailyUsage.serializer()), file.readText())
            synchronized(lock) { list.forEach { days[it.day] = it } }
        } catch (e: Exception) {
            EventLog.w("Meter", "Could not read traffic history", e)
        }
    }

    private fun write() {
        val snapshot = synchronized(lock) { days.values.sortedBy { it.day } }
        try {
            tmp.writeText(AppJson.encodeToString(ListSerializer(DailyUsage.serializer()), snapshot))
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        } catch (e: Exception) {
            EventLog.w("Meter", "Could not write traffic history", e)
        }
    }

    private fun uidRx() = runCatching { TrafficStats.getUidRxBytes(uid) }.getOrDefault(-1L).let { if (it == TrafficStats.UNSUPPORTED.toLong()) -1 else it }
    private fun uidTx() = runCatching { TrafficStats.getUidTxBytes(uid) }.getOrDefault(-1L).let { if (it == TrafficStats.UNSUPPORTED.toLong()) -1 else it }

    @Suppress("FunctionName")
    private fun today_() = LocalDate.now().toString()

    private companion object {
        const val KEEP_DAYS = 30
        const val WRITE_DEBOUNCE_MS = 5_000L
    }
}
