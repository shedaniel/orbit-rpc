package me.shedaniel.discordrpc.discord

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.shedaniel.discordrpc.core.model.ActivityType
import me.shedaniel.discordrpc.core.model.PresenceActivity
import me.shedaniel.discordrpc.core.settings.DiscordSettings
import me.shedaniel.discordrpc.core.settings.OnlineStatus
import me.shedaniel.discordrpc.net.Http
import me.shedaniel.discordrpc.net.TrafficCategory
import me.shedaniel.discordrpc.net.TrafficRecorder
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Talks to the real Discord gateway with an invalid token (costs a few KB). Opt-in:
 * `ORBIT_LIVE=1 ./gradlew :app:testDebugUnitTest --tests '*LiveGatewaySanityTest*'`
 */
class LiveGatewaySanityTest {
    private fun live() = assumeTrue(System.getenv("ORBIT_LIVE") == "1")

    @Test
    fun rawHelloIsZlibStreamAndBadTokenGets4004() {
        live()
        val done = CountDownLatch(1)
        val inflater = ZlibStreamInflater()
        val log = StringBuilder()
        var rxPayload = 0
        var closeCode = -1
        val client = OkHttpClient()
        val req = Request.Builder().url(GatewayClient.buildUrl(GatewayClient.DEFAULT_GATEWAY, true)).header("User-Agent", Http.BROWSER_USER_AGENT).build()
        client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                log.appendLine("handshake: HTTP ${response.code}, Sec-WebSocket-Extensions=${response.header("Sec-WebSocket-Extensions")}, protocol=${response.protocol}")
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                rxPayload += bytes.size
                val d = inflater.feed(bytes.toByteArray())
                log.appendLine("binary frame ${bytes.size} B -> ${d?.uncompressedBytes} B: ${d?.text?.take(120)}")
                if (d != null && d.text.contains("\"op\":10")) {
                    val identify = GatewayClient.identifyPayload("invalid.token.value", PresenceJson.presence(emptyList(), OnlineStatus.ONLINE, true, "").toString(), true, "Test")
                    log.appendLine("sent identify ${identify.length} B")
                    webSocket.send(identify)
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                log.appendLine("TEXT frame (unexpected): ${text.take(100)}")
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                closeCode = code
                log.appendLine("close $code $reason")
                webSocket.close(4000, null)
                done.countDown()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                log.appendLine("failure $t")
                done.countDown()
            }
        })
        done.await(20, TimeUnit.SECONDS)
        println(log)
        println("total binary payload received: $rxPayload B")
        client.dispatcher.executorService.shutdown()
        assertEquals(4004, closeCode)
    }

    @Test
    fun clientReportsFatalOnBadToken() = runBlocking {
        live()
        val rxTotal = AtomicLong()
        val txTotal = AtomicLong()
        val rec = object : TrafficRecorder {
            override fun record(category: TrafficCategory, rx: Long, tx: Long, requests: Long) {
                rxTotal.addAndGet(rx)
                txTotal.addAndGet(tx)
            }
        }
        val rx = rxTotal
        val tx = txTotal
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val gw = GatewayClient(Http(rec), rec, scope, { DiscordSettings() }, device = "Test")
        val act = ResolvedActivity(PresenceActivity("r", "s", ActivityType.PLAYING, "Test"), null, null)
        gw.setActivities("invalid.token.value", listOf(act), OnlineStatus.ONLINE, true, "")
        val st = withTimeout(20_000) { gw.status.first { it is GatewayStatus.Failed } } as GatewayStatus.Failed
        println("status=$st metered rx=${rx.get()} B tx=${tx.get()} B (incl. TCP/TLS/handshake estimates)")
        assertEquals(4004, st.code)
        assertTrue(st.fatal)
        scope.cancel()
    }
}
