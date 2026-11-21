package me.shedaniel.discordrpc.discord

import android.os.Build
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import me.shedaniel.discordrpc.core.model.DiscordUser
import me.shedaniel.discordrpc.core.settings.DiscordSettings
import me.shedaniel.discordrpc.core.settings.OnlineStatus
import me.shedaniel.discordrpc.core.util.EventLog
import me.shedaniel.discordrpc.net.Http
import me.shedaniel.discordrpc.net.NetworkConditions
import me.shedaniel.discordrpc.net.TrafficCategory
import me.shedaniel.discordrpc.net.TrafficRecorder
import me.shedaniel.discordrpc.net.WireEstimates
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.random.Random

/**
 * A data-frugal user-token gateway session.
 *
 * What makes it cheap compared to typical Android RPC apps:
 *  - `compress=zlib-stream` transport compression (one inflater for the connection's lifetime),
 *  - `intents: 0` + READY-shrinking capabilities in IDENTIFY ("lean"), with an automatic one-time fallback
 *    if the gateway rejects them (close 4013/4014/4012/4002 before READY),
 *  - never subscribes to guilds (no op 14/37), ignores every dispatch except READY/RESUMED,
 *  - the initial presence rides along in IDENTIFY,
 *  - RESUME instead of IDENTIFY whenever we have a session (also after our own [disconnect]): we close with
 *    code 4000 rather than 1000/1001, because a 1000/1001 close invalidates the session server-side while any
 *    other code keeps it resumable. A failed resume costs ~1 KB (INVALID_SESSION then IDENTIFY on the same
 *    socket), a skipped one costs a whole READY, so we try generously ([resumeWindowMs]),
 *  - identical presences are never re-sent; updates are rate limited (5 / 20 s) and coalesced to the latest,
 *  - backoff waits for connectivity, and switching networks resumes on the new one so a dead Wi-Fi socket
 *    doesn't linger and a cellular socket doesn't keep running after Wi-Fi comes back.
 *
 * All state is confined to one actor coroutine; socket callbacks post events into it.
 */
class GatewayClient(
    http: Http,
    private val meter: TrafficRecorder,
    private val scope: CoroutineScope,
    private val settings: () -> DiscordSettings,
    private val conditions: StateFlow<NetworkConditions>? = null,
    private val gatewayUrl: String = DEFAULT_GATEWAY,
    private val device: String = defaultDevice(),
    private val resumeWindowMs: Long = RESUME_WINDOW_MS,
    private val random: Random = Random.Default,
) : DiscordSession {

    private val client: OkHttpClient = http.client(TrafficCategory.GATEWAY).newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // heartbeats do liveness
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(0, TimeUnit.MILLISECONDS)
        .build()

    private val _status = MutableStateFlow<GatewayStatus>(GatewayStatus.Closed)
    override val status: StateFlow<GatewayStatus> = _status.asStateFlow()

    private val events = Channel<Ev>(Channel.UNLIMITED)

    // ---- actor state (only touched from the actor coroutine) ----
    private var token: String? = null
    private var fatalToken: String? = null
    private var desired: String = EMPTY_PRESENCE
    private var desiredHasActivities = false
    private var desiredSummary = ""
    private var lastSent: String? = null
    private var lastSentHasActivities = false
    private var wanted = false

    private var ws: WebSocket? = null
    private var gen = 0
    private var opened = false
    private var awaitingReady = false
    private var resuming = false
    private var identifiedLean = false
    private var leanRejected = false
    private var leanInvalidSessions = 0
    private var session: Session? = null
    private var user: DiscordUser? = null

    private var heartbeatJob: Job? = null
    private var heartbeatAcked = true
    private var connectTimeoutJob: Job? = null
    private var reconnectJob: Job? = null
    private var flushJob: Job? = null
    private var attempts = 0
    private val sendTimes = ArrayDeque<Long>()
    private var lastConditions: NetworkConditions? = conditions?.value
    private var otherDispatches = 0
    private var otherBytes = 0L
    @Volatile private var socketRx = 0L

    private class Session(val id: String, val resumeUrl: String?, val token: String, var seq: Long?, var closedAt: Long?)

    init {
        scope.launch {
            for (ev in events) {
                try {
                    handle(ev)
                } catch (e: Exception) {
                    EventLog.e(TAG, "Gateway error", e)
                }
            }
        }
        if (conditions != null) scope.launch { conditions.collect { events.trySend(Ev.Net(it)) } }
    }

    override suspend fun setActivities(token: String, activities: List<ResolvedActivity>, status: OnlineStatus, afk: Boolean, applicationId: String) {
        val payload = PresenceJson.presence(activities, status, afk, applicationId).toString()
        val summary = activities.joinToString { "${it.activity.type.name.lowercase()} ${it.activity.name}" }.ifEmpty { "cleared" }
        val done = CompletableDeferred<Unit>()
        events.send(Ev.Set(token, payload, activities.isNotEmpty(), summary, done))
        done.await()
    }

    override suspend fun disconnect() {
        val done = CompletableDeferred<Unit>()
        events.send(Ev.Disconnect(done))
        done.await()
    }

    // ------------------------------------------------------------------------------------------------

    private sealed interface Ev {
        class Set(val token: String, val payload: String, val hasActivities: Boolean, val summary: String, val done: CompletableDeferred<Unit>) : Ev
        class Disconnect(val done: CompletableDeferred<Unit>) : Ev
        class Opened(val gen: Int, val extensions: String?) : Ev
        class Msg(val gen: Int, val m: Incoming) : Ev
        class Closed(val gen: Int, val code: Int?, val reason: String) : Ev
        class HeartbeatDue(val gen: Int) : Ev
        class IdentifyNow(val gen: Int) : Ev
        class ConnectTimeout(val gen: Int) : Ev
        data object ReconnectNow : Ev
        data object Flush : Ev
        class Net(val c: NetworkConditions) : Ev
    }

    private sealed interface Incoming {
        class Hello(val intervalMs: Long) : Incoming
        data object Ack : Incoming
        data object HeartbeatRequest : Incoming
        data object Reconnect : Incoming
        class InvalidSession(val resumable: Boolean) : Incoming
        class Ready(val seq: Long?, val d: ReadyD, val compressed: Int, val inflated: Int) : Incoming
        class Resumed(val seq: Long?) : Incoming
        class Dispatch(val seq: Long?, val type: String?, val bytes: Int) : Incoming
    }

    private suspend fun handle(ev: Ev) {
        when (ev) {
            is Ev.Set -> {
                onSet(ev)
                ev.done.complete(Unit)
            }
            is Ev.Disconnect -> {
                onDisconnect()
                ev.done.complete(Unit)
            }
            is Ev.Opened -> if (ev.gen == gen) {
                opened = true
                connectTimeoutJob?.cancel()
                if (ev.extensions != null) EventLog.d(TAG, "Server negotiated websocket extensions: ${ev.extensions}")
            }
            is Ev.Msg -> if (ev.gen == gen) onMessage(ev.m)
            is Ev.Closed -> if (ev.gen == gen) onSocketGone(ev.code, ev.reason)
            is Ev.HeartbeatDue -> if (ev.gen == gen && ws != null) {
                if (!heartbeatAcked) {
                    EventLog.w(TAG, "No heartbeat ACK, reconnecting (zombie connection)")
                    dropSocket(cancel = true)
                    scheduleReconnect(null, "Connection timed out")
                } else {
                    heartbeatAcked = false
                    sendHeartbeat()
                }
            }
            is Ev.IdentifyNow -> if (ev.gen == gen && ws != null) sendIdentify()
            is Ev.ConnectTimeout -> if (ev.gen == gen && !opened) {
                EventLog.w(TAG, "Gateway handshake timed out")
                dropSocket(cancel = true)
                scheduleReconnect(null, "Handshake timed out")
            }
            Ev.ReconnectNow -> {
                reconnectJob = null
                if (wanted && ws == null && token != null && token != fatalToken) connect()
            }
            Ev.Flush -> {
                flushJob = null
                flush()
            }
            is Ev.Net -> onNetwork(ev.c)
        }
    }

    private fun onSet(ev: Ev.Set) {
        if (ev.token != token) {
            if (token != null) {
                // Another account: the old session is useless; close it for good.
                ws?.let { EventLog.i(TAG, "Token changed, closing the old session") }
                dropSocket(code = 1000)
                session = null
                user = null
            }
            token = ev.token
            if (fatalToken != null && fatalToken != ev.token) fatalToken = null
            if (_status.value is GatewayStatus.Failed) _status.value = GatewayStatus.Closed
        }
        desired = ev.payload
        desiredHasActivities = ev.hasActivities
        desiredSummary = ev.summary
        if (ev.token == fatalToken) return // rejected token; wait for a new one
        if (ws == null) {
            if (!ev.hasActivities && !wanted) return // nothing to show, stay closed
            wanted = true
            if (reconnectJob?.isActive == true) return // backoff pending; it'll pick up [desired]
            connect()
        } else {
            wanted = true
            flush()
        }
    }

    private fun onDisconnect() {
        wanted = false
        reconnectJob?.cancel(); reconnectJob = null
        flushJob?.cancel(); flushJob = null
        if (ws != null) {
            if (_status.value is GatewayStatus.Ready && lastSentHasActivities) {
                sendText("""{"op":3,"d":${clearedPresence()}}""")
            }
            EventLog.i(TAG, "Closing gateway (session kept for resume)")
            dropSocket(code = RESUMABLE_CLOSE)
        }
        lastSent = null
        lastSentHasActivities = false
        val st = _status.value
        if (!(st is GatewayStatus.Failed && st.fatal)) _status.value = GatewayStatus.Closed
    }

    private fun onNetwork(c: NetworkConditions) {
        val prev = lastConditions
        lastConditions = c
        if (!c.connected || prev == null) return
        if (!prev.connected) {
            // Connectivity is back: don't sit out the rest of a backoff.
            if (ws == null && wanted && reconnectJob?.isActive == true) {
                reconnectJob?.cancel(); reconnectJob = null
                if (token != fatalToken) connect()
            }
            return
        }
        if (prev.transport != c.transport && ws != null && _status.value is GatewayStatus.Ready) {
            EventLog.i(TAG, "Network changed to ${c.transport.name.lowercase()}, resuming on it")
            dropSocket(code = RESUMABLE_CLOSE)
            attempts = 0
            scheduleReconnect(null, "Network changed")
        }
    }

    // ---- connection ---------------------------------------------------------------------------------

    private fun canResume(s: Session? = session): Boolean =
        s != null && s.token == token && (s.closedAt == null || System.currentTimeMillis() - s.closedAt!! < resumeWindowMs)

    private fun connect() {
        if (token == null) return
        reconnectJob?.cancel(); reconnectJob = null
        gen++
        val myGen = gen
        val compress = settings().compression
        val resume = canResume()
        val base = (if (resume) session?.resumeUrl else null) ?: gatewayUrl
        val url = buildUrl(base, compress)
        opened = false
        awaitingReady = false
        resuming = false
        heartbeatAcked = true
        otherDispatches = 0
        otherBytes = 0
        socketRx = 0
        if (_status.value !is GatewayStatus.Connecting) _status.value = GatewayStatus.Connecting
        EventLog.i(TAG, "Connecting (${if (resume) "resume" else "identify"}${if (compress) ", zlib" else ""})")
        val request = Request.Builder().url(url).header("User-Agent", Http.BROWSER_USER_AGENT).build()
        ws = client.newWebSocket(request, Listener(myGen, compress))
        connectTimeoutJob?.cancel()
        connectTimeoutJob = scope.launch {
            delay(CONNECT_TIMEOUT_MS)
            events.send(Ev.ConnectTimeout(myGen))
        }
    }

    /** Forgets the current socket (its later callbacks are ignored). */
    private fun dropSocket(code: Int = RESUMABLE_CLOSE, cancel: Boolean = false) {
        val s = ws ?: return
        if (cancel) s.cancel() else runCatching { s.close(code, null) }.onFailure { s.cancel() }
        meter.record(TrafficCategory.GATEWAY, 0, 12, 0) // close frame
        socketGone()
        if (code == 1000 || code == 1001) session = null
    }

    private fun socketGone() {
        ws = null
        gen++
        heartbeatJob?.cancel(); heartbeatJob = null
        connectTimeoutJob?.cancel(); connectTimeoutJob = null
        flushJob?.cancel(); flushJob = null
        session?.closedAt = System.currentTimeMillis()
        if (otherDispatches > 0) EventLog.d(TAG, "Socket closed; ignored $otherDispatches events (${otherBytes / 1024} KB inflated), ${socketRx / 1024} KB received")
        lastSent = null
        lastSentHasActivities = false
    }

    private fun onSocketGone(code: Int?, reason: String) {
        val wasIdentifying = awaitingReady
        val wasLean = identifiedLean
        socketGone()
        EventLog.w(TAG, "Gateway closed" + (code?.let { " ($it${if (reason.isNotBlank()) " $reason" else ""})" } ?: ": $reason"))
        if (!wanted) {
            val st = _status.value
            if (!(st is GatewayStatus.Failed && st.fatal)) _status.value = GatewayStatus.Closed
            return
        }
        if (code in LEAN_REJECT_CODES && wasIdentifying && wasLean && !leanRejected) {
            leanRejected = true
            session = null
            EventLog.w(TAG, "Gateway rejected lean identify ($code); retrying without intents for this session")
            connect()
            return
        }
        when (code) {
            4004 -> {
                fatalToken = token
                session = null
                EventLog.e(TAG, "Discord rejected the token (4004)")
                _status.value = GatewayStatus.Failed(4004, "Authentication failed", fatal = true, retryAtMs = null)
            }
            4010, 4011, 4012, 4013, 4014 -> {
                session = null
                attempts = maxOf(attempts, 6)
                scheduleReconnect(code, "Gateway refused the session ($code)")
            }
            4003, 4007, 4009 -> {
                session = null
                scheduleReconnect(code, "Session expired")
            }
            4008 -> {
                attempts += 2
                scheduleReconnect(code, "Rate limited by Discord")
            }
            1000, 1001 -> {
                session = null
                scheduleReconnect(code, "Closed by Discord")
            }
            else -> scheduleReconnect(code, if (code == null) reason.ifBlank { "Connection lost" } else "Disconnected ($code)")
        }
    }

    private fun scheduleReconnect(code: Int?, message: String) {
        reconnectJob?.cancel()
        val d = backoffMs(attempts++)
        val at = System.currentTimeMillis() + d
        _status.value = if (d < 2_000) GatewayStatus.Connecting else GatewayStatus.Failed(code, message, fatal = false, retryAtMs = at)
        if (d >= 2_000) EventLog.i(TAG, "Reconnecting in ${d / 1000}s")
        reconnectJob = scope.launch {
            delay(d)
            conditions?.first { it.connected }
            events.send(Ev.ReconnectNow)
        }
    }

    private fun backoffMs(n: Int): Long {
        if (n <= 0) return 250L + random.nextLong(1_000)
        val cap = min(MAX_BACKOFF_MS, 2_000L shl min(n - 1, 10))
        return cap / 2 + random.nextLong(cap / 2 + 1)
    }

    // ---- protocol -----------------------------------------------------------------------------------

    private fun onMessage(m: Incoming) {
        when (m) {
            is Incoming.Hello -> {
                startHeartbeat(m.intervalMs)
                if (canResume()) sendResume() else sendIdentify()
            }
            Incoming.Ack -> heartbeatAcked = true
            Incoming.HeartbeatRequest -> sendHeartbeat()
            Incoming.Reconnect -> {
                EventLog.i(TAG, "Discord asked us to reconnect")
                dropSocket(code = RESUMABLE_CLOSE)
                scheduleReconnect(null, "Reconnecting")
            }
            is Incoming.InvalidSession -> onInvalidSession(m.resumable)
            is Incoming.Ready -> {
                val d = m.d
                val u = DiscordUser(d.user.id, d.user.username, d.user.globalName, d.user.avatar)
                session = Session(d.sessionId, d.resumeUrl, token!!, m.seq, null)
                user = u
                awaitingReady = false
                leanInvalidSessions = 0
                attempts = 0
                EventLog.i(TAG, "READY as ${u.username}: ${kb(m.compressed)} on the wire, ${kb(m.inflated)} inflated${if (identifiedLean) ", lean" else ""}")
                _status.value = GatewayStatus.Ready(u)
                flush()
            }
            is Incoming.Resumed -> {
                m.seq?.let { session?.seq = it }
                resuming = false
                attempts = 0
                val idle = session?.closedAt?.let { (System.currentTimeMillis() - it) / 1000 }
                session?.closedAt = null
                EventLog.i(TAG, "Resumed session" + (idle?.let { " after ${it}s" } ?: "") + if (otherDispatches > 0) ", $otherDispatches events replayed" else "")
                lastSent = null // Discord may have dropped it; always re-send after a resume
                val u = user
                _status.value = if (u != null) GatewayStatus.Ready(u) else GatewayStatus.Connecting
                flush()
            }
            is Incoming.Dispatch -> {
                m.seq?.let { session?.seq = it }
                otherDispatches++
                otherBytes += m.bytes
            }
        }
    }

    private fun onInvalidSession(resumable: Boolean) {
        if (resumable) {
            EventLog.i(TAG, "Session invalidated (resumable)")
            dropSocket(code = RESUMABLE_CLOSE)
            scheduleReconnect(null, "Reconnecting")
            return
        }
        if (resuming) {
            val idle = session?.closedAt?.let { (System.currentTimeMillis() - it) / 1000 }
            EventLog.i(TAG, "Resume rejected" + (idle?.let { " (session idle ${it}s)" } ?: "") + ", identifying")
        } else {
            EventLog.w(TAG, "Session invalidated, identifying again")
            if (awaitingReady && identifiedLean && ++leanInvalidSessions >= 2 && !leanRejected) {
                leanRejected = true
                EventLog.w(TAG, "Identify keeps being invalidated; disabling lean mode for this session")
            }
        }
        session = null
        resuming = false
        val g = gen
        scope.launch {
            delay(1_000 + random.nextLong(4_000)) // Discord asks for a 1-5 s wait
            events.send(Ev.IdentifyNow(g))
        }
    }

    private fun startHeartbeat(intervalMs: Long) {
        heartbeatJob?.cancel()
        val g = gen
        heartbeatAcked = true
        heartbeatJob = scope.launch {
            delay((intervalMs * random.nextDouble()).toLong())
            while (true) {
                events.send(Ev.HeartbeatDue(g))
                delay(intervalMs)
            }
        }
    }

    private fun sendHeartbeat() {
        sendText("""{"op":1,"d":${session?.seq ?: "null"}}""")
    }

    private fun sendResume() {
        val s = session ?: return sendIdentify()
        resuming = true
        val d = buildJsonObject {
            put("token", s.token)
            put("session_id", s.id)
            put("seq", s.seq)
        }
        sendText("""{"op":6,"d":$d}""")
    }

    private fun sendIdentify() {
        val tok = token ?: return
        val lean = settings().leanGateway && !leanRejected
        session = null
        identifiedLean = lean
        awaitingReady = true
        val text = identifyPayload(tok, desired, lean, device)
        lastSent = desired
        lastSentHasActivities = desiredHasActivities
        sendText(text)
    }

    private fun flush() {
        val socket = ws ?: return
        if (_status.value !is GatewayStatus.Ready) return
        if (desired == lastSent) return
        val now = System.currentTimeMillis()
        while (sendTimes.isNotEmpty() && now - sendTimes.first() >= RATE_WINDOW_MS) sendTimes.removeFirst()
        if (sendTimes.size >= RATE_MAX) {
            if (flushJob?.isActive != true) {
                val wait = RATE_WINDOW_MS - (now - sendTimes.first()) + 50
                flushJob = scope.launch {
                    delay(wait)
                    events.send(Ev.Flush)
                }
            }
            return
        }
        if (sendText("""{"op":3,"d":$desired}""", socket)) {
            sendTimes.addLast(now)
            lastSent = desired
            lastSentHasActivities = desiredHasActivities
            EventLog.d(TAG, "Presence: $desiredSummary")
        }
    }

    private fun clearedPresence(): String {
        // Keep status/afk of the last payload, drop activities.
        val o = runCatching { json.parseToJsonElement(desired) as JsonObject }.getOrNull()
        return buildJsonObject {
            put("since", 0)
            put("activities", kotlinx.serialization.json.JsonArray(emptyList()))
            put("status", o?.get("status") ?: kotlinx.serialization.json.JsonPrimitive("online"))
            put("afk", o?.get("afk") ?: kotlinx.serialization.json.JsonPrimitive(true))
        }.toString()
    }

    private fun sendText(text: String, socket: WebSocket? = ws): Boolean {
        val s = socket ?: return false
        val ok = s.send(text)
        if (ok) meter.record(TrafficCategory.GATEWAY, 0, wireSize(text.encodeToByteArray().size, masked = true), 0)
        return ok
    }

    // ---- socket listener (OkHttp reader thread) -----------------------------------------------------

    private inner class Listener(private val g: Int, compress: Boolean) : WebSocketListener() {
        private val inflater = if (compress) ZlibStreamInflater() else null

        override fun onOpen(webSocket: WebSocket, response: Response) {
            meter.record(
                TrafficCategory.GATEWAY,
                WireEstimates.TCP_HANDSHAKE_RX + WireEstimates.tlsRx(response.handshake) + WireEstimates.responseHead(response),
                WireEstimates.TCP_HANDSHAKE_TX + WireEstimates.TLS_HANDSHAKE_TX + WireEstimates.requestHead(response.request),
                1,
            )
            events.trySend(Ev.Opened(g, response.header("Sec-WebSocket-Extensions")))
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            meter.record(TrafficCategory.GATEWAY, wireSize(bytes.size, masked = false), 0, 0)
            val inf = inflater ?: return
            val d = try {
                inf.feed(bytes.toByteArray()) ?: return
            } catch (e: Exception) {
                EventLog.e(TAG, "Corrupt compressed stream", e)
                webSocket.cancel()
                events.trySend(Ev.Closed(g, null, "Corrupt stream"))
                return
            }
            parse(d.text, d.compressedBytes, d.uncompressedBytes)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val n = text.encodeToByteArray().size
            meter.record(TrafficCategory.GATEWAY, wireSize(n, masked = false), 0, 0)
            parse(text, n, n)
        }

        private fun parse(text: String, compressed: Int, inflated: Int) {
            socketRx += compressed
            val m = try {
                decodeIncoming(text, compressed, inflated)
            } catch (e: Exception) {
                EventLog.w(TAG, "Unparseable gateway message", e)
                null
            } ?: return
            events.trySend(Ev.Msg(g, m))
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            runCatching { webSocket.close(RESUMABLE_CLOSE, null) }
            events.trySend(Ev.Closed(g, code, reason))
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            inflater?.close()
            events.trySend(Ev.Closed(g, code, reason))
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            inflater?.close()
            val why = response?.let { "HTTP ${it.code}" } ?: (t.message ?: t.javaClass.simpleName)
            events.trySend(Ev.Closed(g, null, why))
        }
    }

    private fun decodeIncoming(text: String, compressed: Int, inflated: Int): Incoming? {
        val env = json.decodeFromString(Envelope.serializer(), text)
        return when (env.op) {
            0 -> when (env.t) {
                "READY" -> Incoming.Ready(env.s, json.decodeFromString(ReadyEnvelope.serializer(), text).d, compressed, inflated)
                "RESUMED" -> Incoming.Resumed(env.s)
                else -> Incoming.Dispatch(env.s, env.t, inflated)
            }
            1 -> Incoming.HeartbeatRequest
            7 -> Incoming.Reconnect
            9 -> Incoming.InvalidSession(json.decodeFromString(InvalidSessionEnvelope.serializer(), text).d == true)
            10 -> Incoming.Hello(json.decodeFromString(HelloEnvelope.serializer(), text).d.heartbeatInterval)
            11 -> Incoming.Ack
            else -> null
        }
    }

    // ---- wire models (only the fields we need; everything else is skipped by the lexer) -------------

    @Serializable
    private class Envelope(val op: Int, val s: Long? = null, val t: String? = null)

    @Serializable
    private class HelloEnvelope(val d: HelloD)

    @Serializable
    private class HelloD(@SerialName("heartbeat_interval") val heartbeatInterval: Long)

    @Serializable
    private class InvalidSessionEnvelope(val d: Boolean? = null)

    @Serializable
    private class ReadyEnvelope(val d: ReadyD)

    @Serializable
    class ReadyD(
        @SerialName("session_id") val sessionId: String,
        @SerialName("resume_gateway_url") val resumeUrl: String? = null,
        val user: UserD,
    )

    @Serializable
    class UserD(val id: String, val username: String, @SerialName("global_name") val globalName: String? = null, val avatar: String? = null)

    companion object {
        private const val TAG = "Gateway"
        const val DEFAULT_GATEWAY = "wss://gateway.discord.gg"

        /** Any code other than 1000/1001 keeps the session resumable. */
        const val RESUMABLE_CLOSE = 4000

        /** How long after a close we still try RESUME first (unverified upper bound; a failed try is cheap). */
        const val RESUME_WINDOW_MS = 30 * 60_000L
        const val RATE_MAX = 5
        const val RATE_WINDOW_MS = 20_000L
        const val MAX_BACKOFF_MS = 5 * 60_000L
        const val CONNECT_TIMEOUT_MS = 30_000L

        /** user notes lazily | no affine user ids | dedupe user objects | settings as proto: shrink READY. */
        const val LEAN_CAPABILITIES = 1 or 2 or 16 or 512

        /** Close codes that, right after a lean IDENTIFY, mean "don't do that": retry once without intents. */
        private val LEAN_REJECT_CODES = setOf(4002, 4012, 4013, 4014)

        internal val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

        private val EMPTY_PRESENCE = PresenceJson.presence(emptyList(), OnlineStatus.ONLINE, true, "").toString()

        fun buildUrl(base: String, compress: Boolean): String {
            val b = base.trimEnd('/').substringBefore('?')
            return "$b/?v=10&encoding=json" + if (compress) "&compress=zlib-stream" else ""
        }

        fun identifyPayload(token: String, presence: String, lean: Boolean, device: String): String {
            val d = buildJsonObject {
                put("token", token)
                if (lean) put("capabilities", LEAN_CAPABILITIES)
                putJsonObject("properties") {
                    put("os", "Android")
                    put("browser", "Discord Android")
                    put("device", device)
                    put("system_locale", java.util.Locale.getDefault().toLanguageTag())
                }
                put("presence", json.parseToJsonElement(presence))
                put("compress", false)
                put("large_threshold", 50)
                if (lean) put("intents", 0)
            }
            return """{"op":2,"d":$d}"""
        }

        /** Frame header (+ client mask) + TLS 1.3 record overhead per 16 KB record. */
        fun wireSize(payload: Int, masked: Boolean): Long {
            val header = when {
                payload < 126 -> 2
                payload < 65_536 -> 4
                else -> 10
            } + if (masked) 4 else 0
            val records = (payload + header) / 16_384 + 1
            return payload.toLong() + header + records * 22L
        }

        private fun kb(n: Int) = if (n < 1024) "$n B" else "%.1f KB".format(n / 1024.0)

        fun defaultDevice(): String = runCatching { Build.MODEL }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Android"
    }
}
