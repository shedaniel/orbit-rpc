package me.shedaniel.discordrpc.sources

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.shedaniel.discordrpc.core.model.AppSignal
import me.shedaniel.discordrpc.core.model.Signal
import me.shedaniel.discordrpc.core.settings.AppSettings
import me.shedaniel.discordrpc.core.util.EventLog

/** Pure: which package is resumed after a batch of usage events. The last resumed activity wins. */
object ForegroundReducer {
    const val RESUMED = 1 // UsageEvents.Event.ACTIVITY_RESUMED == MOVE_TO_FOREGROUND
    const val PAUSED = 2 // UsageEvents.Event.ACTIVITY_PAUSED == MOVE_TO_BACKGROUND

    fun reduce(current: String?, events: List<Pair<Int, String>>): String? {
        var fg = current
        for ((type, pkg) in events) when (type) {
            RESUMED -> fg = pkg
            // Pausing the current app with nothing resumed after it (screen lock, PiP...) = nothing foreground.
            PAUSED -> if (pkg == fg) fg = null
        }
        return fg
    }
}

/**
 * Pure: keeps a watched app "current" for a linger window after it leaves the foreground, and keeps
 * its start time stable across short interruptions.
 */
class ForegroundTracker {
    data class Current(val packageName: String, val since: Long)

    private class Seen(val since: Long, var lastSeen: Long)

    private val recent = LinkedHashMap<String, Seen>()

    /**
     * @param foreground the raw foreground package (anything, including launchers), or null.
     * @param isWatched whether a package should be shown.
     */
    fun update(now: Long, foreground: String?, lingerMs: Long, isWatched: (String) -> Boolean): Current? {
        val fg = foreground?.takeIf(isWatched)
        recent.entries.removeAll { (pkg, s) -> pkg != fg && (now - s.lastSeen > lingerMs || !isWatched(pkg)) }
        if (fg != null) {
            val s = recent[fg]?.also { it.lastSeen = now } ?: Seen(now, now).also { recent[fg] = it }
            return Current(fg, s.since)
        }
        return recent.entries.maxByOrNull { it.value.lastSeen }?.let { Current(it.key, it.value.since) }
    }

    fun clear() = recent.clear()
}

/**
 * The watched app in the foreground, from usage events. Polls only while the screen is interactive
 * and only when some app could match; each poll reads only the events since the previous one.
 */
class ForegroundAppSource(
    context: Context,
    private val catalog: AppCatalog,
    private val settings: StateFlow<AppSettings>,
    private val scope: CoroutineScope,
) : SignalSource {
    private val app = context.applicationContext
    private val usm = app.getSystemService(UsageStatsManager::class.java)
    private val power = app.getSystemService(PowerManager::class.java)

    private val _signals = MutableStateFlow<List<Signal>>(emptyList())
    override val signals: StateFlow<List<Signal>> = _signals.asStateFlow()

    private val interactive = MutableStateFlow(true)
    private val tracker = ForegroundTracker()
    private val lock = Mutex()
    private var job: Job? = null
    private var receiver: BroadcastReceiver? = null

    // Raw usage-event state, guarded by [lock].
    private var rawForeground: String? = null
    private var lastQueryAt = 0L
    private var lastEventTs = 0L

    private data class Gate(val enabled: Boolean, val pollSeconds: Int, val lingerSeconds: Int, val autoGames: Boolean, val forced: Set<String>, val off: Set<String>) {
        val couldMatch get() = enabled && (autoGames || forced.isNotEmpty())
    }

    private fun gate(s: AppSettings) = Gate(
        enabled = s.foreground.enabled,
        pollSeconds = s.foreground.pollSeconds.coerceIn(1, 600),
        lingerSeconds = s.foreground.lingerSeconds.coerceIn(0, 3600),
        autoGames = s.foreground.autoGames,
        forced = s.apps.filterValues { it.foreground == true }.keys,
        off = s.apps.filterValues { it.foreground == false }.keys,
    )

    override fun start() {
        if (job != null) return
        interactive.value = power?.isInteractive ?: true
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                interactive.value = when (i.action) {
                    Intent.ACTION_SCREEN_OFF -> false
                    else -> power?.isInteractive ?: true
                }
            }
        }
        receiver = r
        ContextCompat.registerReceiver(app, r, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        job = scope.launch(Dispatchers.IO) {
            combine(settings.map { gate(it) }.distinctUntilChanged(), interactive) { g, on -> g to on }
                .collectLatest { (g, on) ->
                    if (!on || !g.couldMatch) {
                        lock.withLock { tracker.clear() }
                        publish(null)
                        return@collectLatest
                    }
                    while (true) {
                        poll(g)
                        delay(g.pollSeconds * 1000L)
                    }
                }
        }
    }

    override fun stop() {
        job?.cancel()
        job = null
        receiver?.let { runCatching { app.unregisterReceiver(it) } }
        receiver = null
        scope.launch {
            lock.withLock {
                tracker.clear()
                rawForeground = null
                lastQueryAt = 0
                lastEventTs = 0
            }
            publish(null)
        }
    }

    private suspend fun poll(g: Gate) {
        if (!Permissions.hasUsageAccess(app)) {
            publish(null)
            return
        }
        val current = lock.withLock {
            val now = System.currentTimeMillis()
            rawForeground = readForeground(now)
            tracker.update(now, rawForeground, g.lingerSeconds * 1000L) { pkg -> isWatched(pkg, g) }
        }
        publish(current)
    }

    private fun isWatched(pkg: String, g: Gate): Boolean = when {
        pkg == app.packageName -> false
        pkg in g.forced -> true
        pkg in g.off -> false
        else -> g.autoGames && catalog.get(pkg)?.isGame == true
    }

    /** Reads usage events since the last query (with a small overlap for late writes). */
    private fun readForeground(now: Long): String? {
        val begin = if (lastQueryAt == 0L) now - INITIAL_LOOKBACK_MS else (lastQueryAt - OVERLAP_MS).coerceAtLeast(now - INITIAL_LOOKBACK_MS)
        lastQueryAt = now
        val events = try {
            usm.queryEvents(begin, now)
        } catch (t: Throwable) {
            EventLog.w("Foreground", "queryEvents failed", t)
            return rawForeground
        } ?: return rawForeground
        val batch = ArrayList<Pair<Int, String>>()
        val e = UsageEvents.Event()
        val after = lastEventTs
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.timeStamp <= after) continue
            val t = e.eventType
            if (t == UsageEvents.Event.ACTIVITY_RESUMED || t == UsageEvents.Event.ACTIVITY_PAUSED) {
                batch += t to e.packageName
                if (e.timeStamp > lastEventTs) lastEventTs = e.timeStamp
            }
        }
        return ForegroundReducer.reduce(rawForeground, batch)
    }

    private fun publish(c: ForegroundTracker.Current?) {
        val next: List<Signal> = if (c == null) emptyList() else listOf(
            AppSignal(
                id = "app:${c.packageName}",
                packageName = c.packageName,
                appLabel = catalog.label(c.packageName),
                since = c.since,
                isGame = catalog.get(c.packageName)?.isGame == true,
            )
        )
        if (next != _signals.value) {
            _signals.value = next
            EventLog.i("Foreground", if (c == null) "No watched app" else "Foreground: ${c.packageName} since ${c.since}")
        }
    }

    private companion object {
        const val INITIAL_LOOKBACK_MS = 60 * 60_000L
        const val OVERLAP_MS = 3_000L
    }
}
