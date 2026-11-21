package me.shedaniel.discordrpc.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.shedaniel.discordrpc.core.model.ConnectionState
import me.shedaniel.discordrpc.core.model.MediaSignal
import me.shedaniel.discordrpc.core.model.PauseReason
import me.shedaniel.discordrpc.core.model.PresenceActivity
import me.shedaniel.discordrpc.core.model.Signal
import me.shedaniel.discordrpc.core.rules.RuleSet
import me.shedaniel.discordrpc.core.rules.RulesEngine
import me.shedaniel.discordrpc.core.settings.AppSettings
import me.shedaniel.discordrpc.core.settings.NetworkProfile
import me.shedaniel.discordrpc.core.settings.NetworkSettings
import me.shedaniel.discordrpc.core.util.EventLog
import me.shedaniel.discordrpc.discord.AssetResolver
import me.shedaniel.discordrpc.discord.DiscordDefaults
import me.shedaniel.discordrpc.discord.DiscordSession
import me.shedaniel.discordrpc.discord.GatewayStatus
import me.shedaniel.discordrpc.discord.ResolvedActivity
import me.shedaniel.discordrpc.discord.TokenStore
import me.shedaniel.discordrpc.net.NetworkConditions
import me.shedaniel.discordrpc.net.Transport
import me.shedaniel.discordrpc.sources.SignalSource
import kotlin.math.abs

/** Everything the UI needs to know about what's being broadcast. */
data class EngineState(
    val running: Boolean = false,
    val connection: ConnectionState = ConnectionState.Idle,
    /** All live signals (after per-app filtering). */
    val signals: List<Signal> = emptyList(),
    /** What we want Discord to show right now (unresolved images). */
    val activities: List<PresenceActivity> = emptyList(),
    /** What was last sent. */
    val sent: List<ResolvedActivity> = emptyList(),
    val lastSentAtMs: Long? = null,
    val profile: NetworkProfile? = null,
    val transport: Transport = Transport.NONE,
)

/** Picks the network profile for the current conditions, or a reason to pause. */
fun NetworkSettings.profileFor(c: NetworkConditions): Pair<NetworkProfile?, PauseReason?> {
    if (!c.connected) return null to PauseReason.NO_NETWORK
    if (c.roaming && pauseWhenRoaming) return null to PauseReason.ROAMING_BLOCKED
    val p = if (c.transport == Transport.CELLULAR || (c.metered && c.transport != Transport.WIFI)) cellular else wifi
    if (!p.enabled) return null to if (p === cellular) PauseReason.CELLULAR_BLOCKED else PauseReason.DISABLED
    if (c.dataSaver && respectDataSaver) {
        // Presence only: nothing that downloads or uploads images.
        return p.copy(artworkLookup = false, uploadImages = false, appIcons = false, spotifyConnect = false, stayConnected = false) to null
    }
    return p to null
}

/**
 * Glues sources → rules → asset resolution → gateway, with debouncing, idle disconnects and
 * network policy. Lives for the app process; [start]/[stop] are driven by the foreground service.
 */
class PresenceEngine(
    private val scope: CoroutineScope,
    private val settings: StateFlow<AppSettings>,
    private val rules: StateFlow<RuleSet>,
    private val tokens: TokenStore,
    private val conditions: StateFlow<NetworkConditions>,
    private val sources: List<SignalSource>,
    private val session: DiscordSession,
    private val resolver: AssetResolver,
) {
    private val _state = MutableStateFlow(EngineState())
    val state: StateFlow<EngineState> = _state.asStateFlow()

    /** Current network profile (null = paused). Exposed for sources that need it (Spotify). */
    val profile: StateFlow<NetworkProfile?> = combine(settings, conditions) { s, c -> s.network.profileFor(c).first }
        .stateIn(scope, SharingStarted.Eagerly, null)

    private var job: Job? = null
    private val pausedAt = mutableMapOf<String, Long>()

    fun start() {
        if (job != null) return
        EventLog.i("Engine", "Starting")
        sources.forEach { it.start() }
        _state.update { it.copy(running = true) }
        job = scope.launch {
            launch { observeGateway() }
            launch { drive() }
        }
    }

    fun stop() {
        val j = job ?: return
        job = null
        EventLog.i("Engine", "Stopping")
        j.cancel()
        sources.forEach { it.stop() }
        scope.launch { session.disconnect() }
        _state.update { EngineState(running = false, connection = if (tokens.token.value == null) ConnectionState.LoggedOut else ConnectionState.Paused(PauseReason.DISABLED)) }
    }

    private suspend fun observeGateway() {
        session.status.collect { st ->
            if (st is GatewayStatus.Ready) tokens.setUser(st.user)
            if (st is GatewayStatus.Failed && st.fatal) {
                EventLog.e("Engine", "Gateway rejected the session: ${st.message}")
            }
        }
    }

    private fun allSignals() = combine(sources.map { it.signals }) { arr -> arr.flatMap { it } }

    /** Inputs, combined. */
    private data class Inputs(
        val settings: AppSettings,
        val rules: RuleSet,
        val token: String?,
        val conditions: NetworkConditions,
        val signals: List<Signal>,
    )

    private suspend fun drive() {
        val signalsFlow = allSignals()
        combine(settings, rules, tokens.token, conditions, signalsFlow) { s, r, t, c, sig -> Inputs(s, r, t, c, sig) }
            .map { compute(it) }
            // The UI always sees the freshest view; the network side only reacts to meaningful changes.
            .onEach { p -> _state.update { it.copy(signals = p.signals, activities = p.activities, profile = p.profile, transport = p.transport) } }
            .distinctUntilChanged { a, b -> a.equivalent(b) }
            .collectLatest { plan -> apply(plan) }
    }

    /** What should happen, derived purely from inputs. */
    private data class Plan(
        val token: String?,
        val pause: PauseReason?,
        val profile: NetworkProfile?,
        val transport: Transport,
        val signals: List<Signal>,
        val activities: List<PresenceActivity>,
        val settings: AppSettings,
    ) {
        fun equivalent(o: Plan): Boolean =
            token == o.token && pause == o.pause && profile == o.profile && transport == o.transport &&
                settings == o.settings && signals.map { it.id } == o.signals.map { it.id } &&
                activities.size == o.activities.size && activities.zip(o.activities).all { (a, b) -> a.sameAs(b) }
    }

    private fun compute(i: Inputs): Plan {
        val now = System.currentTimeMillis()
        val s = i.settings
        val (profile, reason) = s.network.profileFor(i.conditions)
        val pause = when {
            !s.enabled -> PauseReason.DISABLED
            else -> reason
        }
        val signals = filterSignals(i.signals, s, now)
        val max = if (s.discord.multipleActivities) 4 else 1
        val activities = RulesEngine.evaluate(i.rules.rules, signals, max).mapNotNull { m ->
            val custom = m.signal?.packageName?.let { s.apps[it]?.customName }
            RulesEngine.render(m.rule, m.signal, custom, now)
        }
        return Plan(i.token, pause, profile, i.conditions.transport, signals, activities, s)
    }

    private fun filterSignals(raw: List<Signal>, s: AppSettings, now: Long): List<Signal> {
        val out = mutableListOf<Signal>()
        val live = mutableSetOf<String>()
        for (sig in raw) {
            live += sig.id
            val pkg = sig.packageName
            val profile = pkg?.let { s.app(it) }
            if (sig is MediaSignal) {
                if (!s.media.enabled) continue
                if (profile != null && !profile.media) continue
                val d = sig.durationMs
                if (s.media.minDurationSeconds > 0 && d != null && d in 1 until s.media.minDurationSeconds * 1000L) continue
                var m: MediaSignal = sig
                profile?.kind?.let { m = m.copy(kind = it) }
                if (!m.playing) {
                    val at = pausedAt.getOrPut(m.id) { now }
                    val grace = s.media.pausedGraceSeconds * 1000L
                    // Within the grace window keep it "playing" without a progress bar.
                    if (grace > 0 && now - at < grace) m = m.copy(playing = true, positionMs = null, durationMs = null)
                } else pausedAt.remove(m.id)
                out += m
            } else {
                if (!s.foreground.enabled) continue
                out += sig
            }
        }
        pausedAt.keys.retainAll(live)
        return out
    }

    private suspend fun apply(plan: Plan) {
        val token = plan.token
        val base = _state.value
        if (token == null) {
            _state.value = base.copy(connection = ConnectionState.LoggedOut, sent = emptyList())
            session.disconnect()
            return
        }
        if (plan.pause != null) {
            _state.value = base.copy(connection = ConnectionState.Paused(plan.pause), sent = emptyList())
            session.disconnect()
            return
        }
        val profile = plan.profile ?: return
        val ds = plan.settings.discord
        val appId = ds.applicationId.ifBlank { DiscordDefaults.APPLICATION_ID }
        _state.value = base.copy(connection = connectionFrom(session.status.value))

        if (plan.activities.isEmpty()) {
            if (_state.value.sent.isNotEmpty() || session.status.value is GatewayStatus.Ready) {
                session.setActivities(token, emptyList(), ds.status, ds.afk, appId)
                _state.update { it.copy(sent = emptyList(), lastSentAtMs = System.currentTimeMillis()) }
            }
            if (!profile.stayConnected) {
                // collectLatest cancels this wait if something starts playing again.
                delay(ds.idleDisconnectSeconds * 1000L)
                EventLog.i("Engine", "Idle for ${ds.idleDisconnectSeconds}s, closing the gateway to save data")
                session.disconnect()
            }
            _state.update { it.copy(connection = ConnectionState.Idle) }
            return
        }

        // Coalesce bursts (track skips, seek storms).
        val since = System.currentTimeMillis() - (_state.value.lastSentAtMs ?: 0)
        if (since < ds.updateDebounceMs) delay(ds.updateDebounceMs - since)

        val resolved = plan.activities.map { a ->
            ResolvedActivity(
                activity = a,
                largeImage = a.largeImage?.let { runCatching { resolver.resolve(it, profile, token, appId) }.getOrNull() },
                smallImage = a.smallImage?.let { runCatching { resolver.resolve(it, profile, token, appId) }.getOrNull() },
            )
        }
        session.setActivities(token, resolved, ds.status, ds.afk, appId)
        _state.update { it.copy(sent = resolved, lastSentAtMs = System.currentTimeMillis(), connection = connectionFrom(session.status.value)) }
        // Keep connection state fresh while this plan is active.
        session.status.collect { st -> _state.update { it.copy(connection = connectionFrom(st)) } }
    }

    private fun connectionFrom(st: GatewayStatus): ConnectionState = when (st) {
        GatewayStatus.Closed -> ConnectionState.Idle
        GatewayStatus.Connecting -> ConnectionState.Connecting
        is GatewayStatus.Ready -> ConnectionState.Connected(st.user)
        is GatewayStatus.Failed -> if (st.fatal) ConnectionState.Paused(PauseReason.TOKEN_INVALID) else ConnectionState.Error(st.message, st.retryAtMs)
    }
}

/** Equality that ignores timestamp jitter below 2s, so position updates don't spam Discord. */
fun PresenceActivity.sameAs(o: PresenceActivity): Boolean {
    fun near(a: Long?, b: Long?) = (a == null && b == null) || (a != null && b != null && abs(a - b) < 2000)
    return copy(startMs = null, endMs = null) == o.copy(startMs = null, endMs = null) && near(startMs, o.startMs) && near(endMs, o.endMs)
}
