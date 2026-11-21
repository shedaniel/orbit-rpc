package me.shedaniel.discordrpc.discord

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import me.shedaniel.discordrpc.core.model.ActivityType
import me.shedaniel.discordrpc.core.model.PresenceActivity
import me.shedaniel.discordrpc.core.settings.DiscordSettings
import me.shedaniel.discordrpc.core.settings.OnlineStatus
import me.shedaniel.discordrpc.net.Http
import me.shedaniel.discordrpc.net.TrafficCategory
import me.shedaniel.discordrpc.net.TrafficRecorder
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class GatewayClientTest {
    private lateinit var server: MockWebServer
    private lateinit var scope: CoroutineScope
    private val rx = AtomicLong()
    private val tx = AtomicLong()
    private val recorder = object : TrafficRecorder {
        override fun record(category: TrafficCategory, rx: Long, tx: Long, requests: Long) {
            assertEquals(TrafficCategory.GATEWAY, category)
            this@GatewayClientTest.rx.addAndGet(rx)
            this@GatewayClientTest.tx.addAndGet(tx)
        }
    }

    /** Messages the fake gateway received: (connection index, payload). */
    private val received = LinkedBlockingQueue<Pair<Int, JsonObject>>()
    private val closes = LinkedBlockingQueue<Pair<Int, Int>>()

    /** Fake Discord gateway speaking zlib-stream. */
    private inner class FakeGateway(
        private val index: Int,
        private val onIdentify: ((WebSocket, JsonObject) -> Unit)? = null,
    ) : WebSocketListener() {
        private val deflater = ZlibStreamDeflater()

        fun send(ws: WebSocket, text: String) {
            ws.send(deflater.compress(text).toByteString())
        }

        fun ready(ws: WebSocket) {
            val resume = server.url("/").toString().replace("http://", "ws://")
            send(ws, """{"op":0,"t":"READY","s":1,"d":{"v":10,"session_id":"sess-1","resume_gateway_url":"$resume","user":{"id":"42","username":"tester","global_name":"Test","avatar":null},"guilds":[]}}""")
        }

        override fun onOpen(webSocket: WebSocket, response: Response) {
            send(webSocket, """{"op":10,"s":null,"t":null,"d":{"heartbeat_interval":45000}}""")
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val o = Json.parseToJsonElement(text).jsonObject
            val op = o["op"]!!.jsonPrimitive.int
            if (op == 1) {
                send(webSocket, """{"op":11}""")
                return
            }
            received.add(index to o)
            when (op) {
                2 -> onIdentify?.invoke(webSocket, o) ?: ready(webSocket)
                6 -> send(webSocket, """{"op":0,"t":"RESUMED","s":3,"d":{}}""")
            }
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) = error("client must send text")

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            closes.add(index to code)
            webSocket.close(code, null)
        }
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @After
    fun tearDown() {
        scope.cancel()
        server.shutdown()
    }

    private fun client(settings: DiscordSettings = DiscordSettings()) = GatewayClient(
        http = Http(recorder),
        meter = recorder,
        scope = scope,
        settings = { settings },
        gatewayUrl = server.url("/").toString().replace("http://", "ws://"),
        device = "Test Phone",
    )

    private fun activity(name: String) = ResolvedActivity(
        PresenceActivity(ruleId = "r", signalId = "s", type = ActivityType.LISTENING, name = name, details = "Song $name", state = "Artist"),
        largeImage = "mp:external/x", smallImage = null,
    )

    private fun next(timeoutS: Long = 10): Pair<Int, JsonObject> = received.poll(timeoutS, TimeUnit.SECONDS) ?: error("timed out waiting for a gateway message")

    private fun JsonObject.op() = this["op"]!!.jsonPrimitive.int
    private fun JsonObject.d() = this["d"]!!.jsonObject
    private fun JsonObject.activityNames() = d()["activities"]!!.jsonArray.map { it.jsonObject["name"]!!.jsonPrimitive.content }

    @Test
    fun identifyPresenceThenResumeAfterDisconnect() = runBlocking {
        server.enqueue(MockResponse().withWebSocketUpgrade(FakeGateway(0)))
        server.enqueue(MockResponse().withWebSocketUpgrade(FakeGateway(1)))
        val gw = client()

        gw.setActivities("tok", listOf(activity("A")), OnlineStatus.ONLINE, true, "962990036020756480")
        val (c0, identify) = next()
        assertEquals(0, c0)
        assertEquals(2, identify.op())
        val d = identify.d()
        assertEquals("tok", d["token"]!!.jsonPrimitive.content)
        assertEquals(0, d["intents"]!!.jsonPrimitive.int)
        assertEquals(false, d["compress"]!!.jsonPrimitive.content.toBoolean())
        assertEquals(50, d["large_threshold"]!!.jsonPrimitive.int)
        assertEquals("Discord Android", d["properties"]!!.jsonObject["browser"]!!.jsonPrimitive.content)
        // The first activity rides along in IDENTIFY.
        assertEquals(listOf("A"), d["presence"]!!.jsonObject["activities"]!!.jsonArray.map { it.jsonObject["name"]!!.jsonPrimitive.content })

        val st = withTimeout(10_000) { gw.status.first { it is GatewayStatus.Ready } } as GatewayStatus.Ready
        assertEquals("tester", st.user.username)
        assertEquals("Test", st.user.globalName)

        // Same presence again: nothing is sent. A different one: op 3.
        gw.setActivities("tok", listOf(activity("A")), OnlineStatus.ONLINE, true, "962990036020756480")
        gw.setActivities("tok", listOf(activity("B")), OnlineStatus.ONLINE, true, "962990036020756480")
        val (_, upd) = next()
        assertEquals(3, upd.op())
        assertEquals(listOf("B"), upd.activityNames())
        assertEquals("962990036020756480", upd.d()["activities"]!!.jsonArray[0].jsonObject["application_id"]!!.jsonPrimitive.content)

        // Disconnect clears the presence, then closes with a resumable code.
        gw.disconnect()
        val (_, cleared) = next()
        assertEquals(3, cleared.op())
        assertEquals(emptyList<String>(), cleared.activityNames())
        assertEquals(0 to 4000, closes.poll(10, TimeUnit.SECONDS))
        assertEquals(GatewayStatus.Closed, gw.status.value)

        // Next activity: a new socket that RESUMEs (no IDENTIFY, no READY download) and re-sends the presence.
        gw.setActivities("tok", listOf(activity("C")), OnlineStatus.ONLINE, true, "962990036020756480")
        val (c1, resume) = next()
        assertEquals(1, c1)
        assertEquals(6, resume.op())
        assertEquals("sess-1", resume.d()["session_id"]!!.jsonPrimitive.content)
        assertEquals(1L, resume.d()["seq"]!!.jsonPrimitive.long)
        val (_, again) = next()
        assertEquals(3, again.op())
        assertEquals(listOf("C"), again.activityNames())
        withTimeout(10_000) { gw.status.first { it is GatewayStatus.Ready } }

        // No further traffic (in particular no identify) on the resumed socket.
        delay(300)
        assertNull(received.poll())
        assertEquals(2, server.requestCount)
        assertTrue("wire bytes metered", rx.get() > 0 && tx.get() > 0)
    }

    @Test
    fun authFailureIsFatalUntilTokenChanges() = runBlocking {
        server.enqueue(MockResponse().withWebSocketUpgrade(FakeGateway(0) { ws, _ -> ws.close(4004, "Authentication failed.") }))
        val gw = client()
        gw.setActivities("bad", listOf(activity("A")), OnlineStatus.ONLINE, true, "")
        assertEquals(2, next().second.op())
        val st = withTimeout(10_000) { gw.status.first { it is GatewayStatus.Failed } } as GatewayStatus.Failed
        assertEquals(4004, st.code)
        assertTrue(st.fatal)
        // Same token: ignored, no new connection.
        gw.setActivities("bad", listOf(activity("B")), OnlineStatus.ONLINE, true, "")
        delay(500)
        assertEquals(1, server.requestCount)
        gw.disconnect()
        assertTrue((gw.status.value as GatewayStatus.Failed).fatal)
    }

    @Test
    fun rejectedLeanIdentifyFallsBackOnce() = runBlocking {
        server.enqueue(MockResponse().withWebSocketUpgrade(FakeGateway(0) { ws, _ -> ws.close(4014, "Disallowed intent(s).") }))
        server.enqueue(MockResponse().withWebSocketUpgrade(FakeGateway(1)))
        val gw = client()
        gw.setActivities("tok", listOf(activity("A")), OnlineStatus.ONLINE, true, "")
        val (_, first) = next()
        assertEquals(0, first.d()["intents"]!!.jsonPrimitive.int)
        val (c, second) = next()
        assertEquals(1, c)
        assertEquals(2, second.op())
        assertFalse("intents" in second.d())
        withTimeout(10_000) { gw.status.first { it is GatewayStatus.Ready } }
        gw.disconnect()
    }

    @Test
    fun emptyActivitiesDoNotOpenASocket() = runBlocking {
        val gw = client()
        gw.setActivities("tok", emptyList(), OnlineStatus.ONLINE, true, "")
        delay(300)
        assertEquals(0, server.requestCount)
        assertEquals(GatewayStatus.Closed, gw.status.value)
    }
}
