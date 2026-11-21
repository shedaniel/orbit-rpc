package me.shedaniel.discordrpc.sources

import android.content.Context
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
import me.shedaniel.discordrpc.core.model.Artwork
import me.shedaniel.discordrpc.core.model.MediaSignal
import me.shedaniel.discordrpc.core.model.Signal
import me.shedaniel.discordrpc.core.model.SignalOrigin
import me.shedaniel.discordrpc.core.settings.AppSettings
import me.shedaniel.discordrpc.core.settings.NetworkProfile
import me.shedaniel.discordrpc.core.settings.SpotifySettings
import me.shedaniel.discordrpc.core.util.EventLog
import me.shedaniel.discordrpc.net.Http
import me.shedaniel.discordrpc.net.TrafficCategory
import okhttp3.Request
import java.io.IOException

/**
 * Spotify playback on other devices (Spotify Connect) via the Web API. Polls adaptively: the
 * regular cadence while playing plus a poll right after the current track should end, the idle
 * cadence otherwise, and nothing at all when disabled, logged out, the network profile forbids it,
 * or Spotify is already playing on this phone.
 */
class SpotifyConnectSource(
    context: Context,
    private val http: Http,
    private val auth: SpotifyAuth,
    private val settings: StateFlow<AppSettings>,
    /** Current network profile, null = paused (no polling). */
    private val profile: StateFlow<NetworkProfile?>,
    /** Local media signals, to skip polling while Spotify plays on this phone. */
    private val local: StateFlow<List<Signal>>,
    private val scope: CoroutineScope,
) : SignalSource {
    private val _signals = MutableStateFlow<List<Signal>>(emptyList())
    override val signals: StateFlow<List<Signal>> = _signals.asStateFlow()

    private var job: Job? = null
    private var etag: String? = null
    private var last: SpotifyPlayback? = null
    private var lastOkAt = 0L
    private var sinceItem: String? = null
    private var sinceAt = 0L

    private sealed interface Result {
        data class Ok(val playback: SpotifyPlayback?) : Result
        data class RateLimited(val retryAfterMs: Long) : Result
        data object Unauthorized : Result
        data object NoToken : Result
        data object Failed : Result
    }

    private fun localSpotifyPlaying(list: List<Signal>) = list.any {
        it is MediaSignal && it.origin == SignalOrigin.MEDIA_SESSION && it.packageName == KnownApps.SPOTIFY && it.playing
    }

    override fun start() {
        if (job != null) return
        job = scope.launch(Dispatchers.IO) {
            combine(
                settings.map { it.spotify }.distinctUntilChanged(),
                auth.loggedIn,
                profile.map { it?.spotifyConnect == true }.distinctUntilChanged(),
                local.map { localSpotifyPlaying(it) }.distinctUntilChanged(),
            ) { s, loggedIn, allowed, localPlaying ->
                s.takeIf { it.enabled && it.clientId.isNotBlank() && loggedIn && allowed && !(it.skipWhenLocal && localPlaying) }
            }.distinctUntilChanged().collectLatest { s ->
                if (s == null) {
                    publish(null)
                    return@collectLatest
                }
                loop(s)
            }
        }
    }

    override fun stop() {
        job?.cancel()
        job = null
        last = null
        etag = null
        publish(null)
    }

    private suspend fun loop(s: SpotifySettings) {
        val pollMs = s.pollSeconds.coerceIn(5, 600) * 1000L
        val idleMs = s.idlePollSeconds.coerceIn(10, 3600) * 1000L
        var backoffMs = 0L
        var unauthorizedStreak = 0
        while (true) {
            val r = pollOnce(s.clientId)
            val wait = when (r) {
                is Result.Ok -> {
                    backoffMs = 0; unauthorizedStreak = 0
                    lastOkAt = System.currentTimeMillis()
                    last = r.playback
                    publish(r.playback)
                    SpotifyPlayerParser.nextPollDelayMs(r.playback, pollMs, idleMs)
                }
                is Result.RateLimited -> {
                    EventLog.w("Spotify", "Rate limited, waiting ${r.retryAfterMs / 1000}s")
                    maxOf(r.retryAfterMs, pollMs)
                }
                Result.Unauthorized -> {
                    auth.invalidate()
                    if (++unauthorizedStreak >= 3) idleMs else 2000L
                }
                Result.NoToken -> idleMs
                Result.Failed -> {
                    backoffMs = if (backoffMs == 0L) pollMs else (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
                    backoffMs
                }
            }
            // Don't show something stale forever when polls keep failing.
            if (r !is Result.Ok && System.currentTimeMillis() - lastOkAt > STALE_MS) publish(null)
            delay(wait)
        }
    }

    private suspend fun pollOnce(clientId: String): Result {
        val token = auth.accessToken(clientId) ?: return Result.NoToken
        val req = Request.Builder()
            .url("https://api.spotify.com/v1/me/player?additional_types=episode")
            .header("Authorization", "Bearer $token")
            .apply { etag?.let { header("If-None-Match", it) } }
            .build()
        return try {
            http.client(TrafficCategory.SPOTIFY).newCall(req).execute().use { r ->
                when (r.code) {
                    200 -> {
                        etag = r.header("ETag")
                        Result.Ok(SpotifyPlayerParser.parse(r.body.string()))
                    }
                    204 -> { etag = null; Result.Ok(null) }
                    304 -> Result.Ok(last)
                    401 -> Result.Unauthorized
                    429 -> Result.RateLimited((r.header("Retry-After")?.toLongOrNull() ?: 30L) * 1000L)
                    else -> {
                        EventLog.w("Spotify", "Player request failed: HTTP ${r.code}")
                        Result.Failed
                    }
                }
            }
        } catch (e: IOException) {
            EventLog.d("Spotify", "Player request failed: ${e.message}")
            Result.Failed
        }
    }

    private fun publish(p: SpotifyPlayback?) {
        val now = System.currentTimeMillis()
        val next: List<Signal> = when {
            p == null -> emptyList()
            // Playing on this phone and already visible as a local session: don't duplicate it.
            local.value.any { it is MediaSignal && it.packageName == KnownApps.SPOTIFY && it.origin == SignalOrigin.MEDIA_SESSION && it.title == p.title } -> emptyList()
            else -> {
                if (sinceItem != p.itemId) {
                    sinceItem = p.itemId
                    sinceAt = now
                }
                listOf(
                    MediaSignal(
                        id = ID,
                        origin = SignalOrigin.SPOTIFY_CONNECT,
                        packageName = KnownApps.SPOTIFY,
                        appLabel = "Spotify",
                        since = sinceAt,
                        title = p.title,
                        artist = p.artist,
                        album = p.album,
                        artwork = p.imageUrl?.let { Artwork(url = it, bitmap = null, key = it) },
                        durationMs = p.durationMs,
                        positionMs = p.progressMs,
                        positionAtMs = now,
                        playing = p.isPlaying,
                        kind = p.kind,
                        deviceName = p.deviceName,
                        link = p.link,
                    )
                )
            }
        }
        val prev = _signals.value
        val merged = SignalDiff.merge(prev, next)
        if (merged !== prev) {
            _signals.value = merged
            EventLog.i("Spotify", merged.firstOrNull()?.let { "Remote: " + MediaSessionSource.describe(it as MediaSignal) } ?: "Nothing playing remotely")
        }
    }

    private companion object {
        const val ID = "spotify:remote"
        const val MAX_BACKOFF_MS = 10 * 60_000L
        const val STALE_MS = 5 * 60_000L
    }
}
