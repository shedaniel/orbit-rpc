package me.shedaniel.discordrpc.net

import android.net.TrafficStats
import me.shedaniel.discordrpc.BuildConfig
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.Handshake
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.net.InetAddress
import java.net.Socket
import java.net.SocketAddress
import java.util.EnumMap
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory

/** `TrafficStats` tags, so `adb shell dumpsys netstats detail` can split our traffic by purpose. */
object TrafficTags {
    const val GATEWAY = 0x0B17_0001
    const val DISCORD_API = 0x0B17_0002
    const val ARTWORK_LOOKUP = 0x0B17_0003
    const val IMAGE_UPLOAD = 0x0B17_0004
    const val APP_ICON = 0x0B17_0005
    const val SPOTIFY = 0x0B17_0006
    const val OTHER = 0x0B17_000F

    fun of(c: TrafficCategory) = when (c) {
        TrafficCategory.GATEWAY -> GATEWAY
        TrafficCategory.DISCORD_API -> DISCORD_API
        TrafficCategory.ARTWORK_LOOKUP -> ARTWORK_LOOKUP
        TrafficCategory.IMAGE_UPLOAD -> IMAGE_UPLOAD
        TrafficCategory.APP_ICON -> APP_ICON
        TrafficCategory.SPOTIFY -> SPOTIFY
        TrafficCategory.OTHER -> OTHER
    }
}

/** Rough wire-size estimates for things OkHttp doesn't report as byte counts. */
object WireEstimates {
    /** TCP handshake + FIN exchange, IPv4/v6 averaged, both directions. */
    const val TCP_HANDSHAKE_TX = 180L
    const val TCP_HANDSHAKE_RX = 120L

    /** Client side of a TLS 1.3 handshake (ClientHello with key shares + Finished). */
    const val TLS_HANDSHAKE_TX = 650L

    /** Server side without certificates (ServerHello, EncryptedExtensions, CertVerify, Finished, tickets). */
    const val TLS_HANDSHAKE_RX_BASE = 900L

    fun tlsRx(handshake: Handshake?): Long =
        TLS_HANDSHAKE_RX_BASE + (handshake?.peerCertificates?.sumOf { runCatching { it.encoded.size.toLong() }.getOrDefault(1200L) } ?: 3000L)

    /** HTTP/1.1 encoded size of a request head. HTTP/2 (HPACK) is smaller, so this over-estimates slightly. */
    fun requestHead(r: Request): Long = r.method.length + r.url.encodedPath.length + (r.url.encodedQuery?.length?.plus(1) ?: 0) + 12L + r.headers.byteCount() + 2 * r.headers.size + 2

    fun responseHead(r: Response): Long = 13L + r.message.length + r.headers.byteCount() + 2 * r.headers.size + 2

    @Suppress("unused")
    fun headers(h: Headers) = h.byteCount() + 2 * h.size
}

/**
 * Shared HTTP stack. One connection pool / dispatcher for the whole app; [client] hands out per-category
 * clients that meter every byte into [NetworkMeter] and tag their sockets for `dumpsys netstats`.
 */
class Http(private val meter: TrafficRecorder) {
    val base: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addNetworkInterceptor { chain ->
            val req = chain.request()
            chain.proceed(if (req.header("User-Agent") == null) req.newBuilder().header("User-Agent", USER_AGENT).build() else req)
        }
        .build()

    private val clients = EnumMap<TrafficCategory, OkHttpClient>(TrafficCategory::class.java)

    /** A client for [category]. Cached, so connection pooling still works within a category. */
    fun client(category: TrafficCategory): OkHttpClient = synchronized(clients) {
        clients.getOrPut(category) {
            base.newBuilder()
                .socketFactory(TaggingSocketFactory(TrafficTags.of(category)))
                .eventListenerFactory { MeteringListener(category, meter) }
                .build()
        }
    }

    companion object {
        val USER_AGENT = "Orbit/${BuildConfig.VERSION_NAME} (Android; +https://github.com/shedaniel)"

        /** Mobile Chrome UA, used for Discord (REST + gateway + login) and Google Play so we look like a normal browser. */
        const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 15; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Mobile Safari/537.36"
    }
}

/** Records request/response heads, bodies and TLS handshakes as they happen (robust to missing callEnd). */
private class MeteringListener(private val category: TrafficCategory, private val meter: TrafficRecorder) : EventListener() {
    override fun connectEnd(call: Call, inetSocketAddress: java.net.InetSocketAddress, proxy: java.net.Proxy, protocol: okhttp3.Protocol?) {
        meter.record(category, WireEstimates.TCP_HANDSHAKE_RX, WireEstimates.TCP_HANDSHAKE_TX, 0)
    }

    override fun secureConnectEnd(call: Call, handshake: Handshake?) {
        meter.record(category, WireEstimates.tlsRx(handshake), WireEstimates.TLS_HANDSHAKE_TX, 0)
    }

    override fun requestHeadersEnd(call: Call, request: Request) {
        meter.record(category, 0, WireEstimates.requestHead(request), 1)
    }

    override fun requestBodyEnd(call: Call, byteCount: Long) {
        meter.record(category, 0, byteCount, 0)
    }

    override fun responseHeadersEnd(call: Call, response: Response) {
        meter.record(category, WireEstimates.responseHead(response), 0, 0)
    }

    override fun responseBodyEnd(call: Call, byteCount: Long) {
        meter.record(category, byteCount, 0, 0)
    }
}

/**
 * Creates sockets tagged with [tag] (via the thread tag at connect time + an explicit [TrafficStats.tagSocket]).
 */
private class TaggingSocketFactory(private val tag: Int) : SocketFactory() {
    private val delegate = getDefault()

    override fun createSocket(): Socket = TaggedSocket(tag)
    override fun createSocket(host: String?, port: Int): Socket = tagged { delegate.createSocket(host, port) }
    override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket =
        tagged { delegate.createSocket(host, port, localHost, localPort) }

    override fun createSocket(host: InetAddress?, port: Int): Socket = tagged { delegate.createSocket(host, port) }
    override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket =
        tagged { delegate.createSocket(address, port, localAddress, localPort) }

    private inline fun tagged(block: () -> Socket): Socket {
        val old = TrafficStats.getThreadStatsTag()
        TrafficStats.setThreadStatsTag(tag)
        try {
            return block().also { runCatching { TrafficStats.tagSocket(it) } }
        } finally {
            TrafficStats.setThreadStatsTag(old)
        }
    }

    // Equality matters: OkHttp's Address (and so connection pooling) compares socket factories.
    override fun equals(other: Any?) = other is TaggingSocketFactory && other.tag == tag
    override fun hashCode() = tag
}

private class TaggedSocket(private val tag: Int) : Socket() {
    override fun connect(endpoint: SocketAddress?, timeout: Int) {
        val old = TrafficStats.getThreadStatsTag()
        TrafficStats.setThreadStatsTag(tag)
        try {
            // The socket's fd is created (and tagged with the thread tag by the platform) inside connect().
            super.connect(endpoint, timeout)
            runCatching { TrafficStats.tagSocket(this) }
        } finally {
            TrafficStats.setThreadStatsTag(old)
        }
    }
}
