package me.shedaniel.discordrpc.discord

import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.scale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.shedaniel.discordrpc.core.model.ImageRef
import me.shedaniel.discordrpc.core.model.MediaKind
import me.shedaniel.discordrpc.core.settings.ArtworkLookup
import me.shedaniel.discordrpc.core.settings.ImageHost
import me.shedaniel.discordrpc.core.settings.ImageSettings
import me.shedaniel.discordrpc.core.settings.NetworkProfile
import me.shedaniel.discordrpc.core.util.EventLog
import me.shedaniel.discordrpc.net.Http
import me.shedaniel.discordrpc.net.TrafficCategory
import me.shedaniel.discordrpc.sources.AppCatalog
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.Locale

/**
 * Turns [ImageRef]s into Discord `mp:` asset keys. Cache-first and persisted (`assets_cache.json`), so a
 * lookup or upload is never repeated:
 *
 *  - `x:<url>`               external-assets result for a public URL (7 days, then refreshed lazily)
 *  - `play:<pkg>`            Play Store icon URL for a package (forever; misses retried after 30 days)
 *  - `art:<provider>:<norm>` catalog artwork URL for (artist, title, album) (90 days; misses 7 days)
 *  - `up:<host>:<key>`       our upload of a local artwork bitmap (host lifetime)
 *  - `iconup:<host>:<pkg>`   our upload of a local app icon (host lifetime)
 *
 * Network failures are cached for [FAIL_TTL] so a flaky network can't cause request storms; concurrent
 * requests for the same key share one flight. The network profile gates only *network* work: anything
 * already cached is always used.
 */
class DiscordAssetResolver(
    context: Context,
    http: Http,
    private val catalog: AppCatalog,
    private val scope: CoroutineScope,
    private val rest: DiscordRest = DiscordRest(http),
    private val imageSettings: () -> ImageSettings,
) : AssetResolver {
    private val file = File(context.filesDir, "assets_cache.json")
    private val cache = AssetCache()
    private val lookupClient = http.client(TrafficCategory.ARTWORK_LOOKUP)
    private val iconClient = http.client(TrafficCategory.APP_ICON)
    private val uploadClient = http.client(TrafficCategory.IMAGE_UPLOAD)

    private val inflight = HashMap<String, Deferred<String?>>()
    private var saveJob: Job? = null

    /** Resolving before the persisted cache is loaded would repeat lookups/uploads that are already cached. */
    private val loaded = scope.async(Dispatchers.IO) { runCatching { cache.load(file) } }

    override suspend fun resolve(ref: ImageRef, profile: NetworkProfile, token: String, applicationId: String): String? {
        loaded.await()
        return resolve(ref, profile, token, applicationId, depth = 0)
    }

    private suspend fun resolve(ref: ImageRef, profile: NetworkProfile, token: String, appId: String, depth: Int): String? {
        if (depth > 3) return null
        return when (ref) {
            is ImageRef.Raw -> ref.key.trim().takeIf { it.isNotEmpty() }
            is ImageRef.Url -> external(ref.url, token, appId, URL_TTL)
            is ImageRef.AppIcon -> appIcon(ref.packageName, profile, token, appId)
            is ImageRef.Media -> media(ref, profile, token, appId) ?: ref.fallback?.let { resolve(it, profile, token, appId, depth + 1) }
        }
    }

    override fun previewUrl(ref: ImageRef): String? = when (ref) {
        is ImageRef.Raw -> ref.key.takeIf { it.startsWith("mp:") }?.let { "https://media.discordapp.net/" + it.removePrefix("mp:") }
        is ImageRef.Url -> ref.url.takeIf { isHttp(it) }
        is ImageRef.AppIcon -> cache.peek("play:${ref.packageName}")?.v
            ?: ImageHost.entries.firstNotNullOfOrNull { cache.peek("iconup:${it.name}:${ref.packageName}")?.v }
        is ImageRef.Media -> ref.artwork?.url?.takeIf { isHttp(it) }
            ?: ArtworkLookup.entries.firstNotNullOfOrNull { cache.peek("art:${it.name}:${ArtworkMatch.cacheKey(ref.title, ref.artist, ref.album)}")?.v }
            ?: ref.artwork?.let { a -> ImageHost.entries.firstNotNullOfOrNull { cache.peek("up:${it.name}:${a.key}")?.v } }
            ?: ref.fallback?.let { previewUrl(it) }
    }

    /** Drops everything cached (for a "clear image cache" button). */
    fun clearCache() {
        cache.clear()
        scheduleSave()
    }

    val cachedEntries: Int get() = cache.size()

    // ---- pipelines ----------------------------------------------------------------------------------

    private suspend fun appIcon(pkg: String, profile: NetworkProfile, token: String, appId: String): String? {
        val playKey = "play:$pkg"
        val now = System.currentTimeMillis()
        val cached = cache[playKey]
        val iconUrl = when {
            cached != null && cached.fresh(now) -> cached.v
            profile.appIcons -> once(playKey) { fetchPlayIcon(pkg) }
            else -> cached?.v
        }
        if (iconUrl != null) external(iconUrl, token, appId, URL_TTL)?.let { return it }

        // Not on Play (or Play failed): upload the launcher icon if allowed.
        val host = imageSettings().host
        if (host == ImageHost.NONE) return null
        val upKey = "iconup:${host.name}:$pkg"
        val up = cachedOrNull(upKey) ?: run {
            if (!profile.appIcons || !profile.uploadImages) return null
            if (cache[upKey]?.fresh(System.currentTimeMillis()) == true) return null // recent miss/failure
            once(upKey) {
                val bmp = catalog.icon(pkg, 512) ?: return@once Outcome(null, NEGATIVE_TTL)
                Outcome(upload(host, encode(bmp, png = true), "icon.png", "image/png"), hostTtl(host))
            }
        } ?: return null
        return external(up, token, appId, minOf(URL_TTL, hostTtl(host)))
    }

    private suspend fun media(ref: ImageRef.Media, profile: NetworkProfile, token: String, appId: String): String? {
        val art = ref.artwork
        // (a) the player gave us a public URL
        art?.url?.takeIf { isHttp(it) }?.let { u -> external(u, token, appId, URL_TTL)?.let { return it } }

        // (b) public catalog lookup
        val lookup = imageSettings().lookup
        if (lookup != ArtworkLookup.NONE && ref.kind in LOOKUP_KINDS && ref.title.isNotBlank()) {
            val key = "art:${lookup.name}:${ArtworkMatch.cacheKey(ref.title, ref.artist, ref.album)}"
            val url = cachedOrNull(key) ?: if (profile.artworkLookup && cache[key]?.fresh(System.currentTimeMillis()) != true) {
                once(key) { lookupArtwork(lookup, ref) }
            } else null
            if (url != null) external(url, token, appId, URL_TTL)?.let { return it }
        }

        // (c) upload the local bitmap
        val host = imageSettings().host
        if (art != null && host != ImageHost.NONE) {
            val upKey = "up:${host.name}:${art.key}"
            val url = cachedOrNull(upKey) ?: run {
                val bmp = art.bitmap
                if (bmp != null && profile.uploadImages && cache[upKey]?.fresh(System.currentTimeMillis()) != true) {
                    once(upKey) { Outcome(upload(host, encode(bmp, png = false), "art.jpg", "image/jpeg"), hostTtl(host)) }
                } else null
            }
            if (url != null) external(url, token, appId, minOf(URL_TTL, hostTtl(host)))?.let { return it }
        }
        return null // (d) caller resolves the fallback
    }

    /** `x:` — public URL to `mp:` key via Discord's external-assets endpoint. */
    private suspend fun external(url: String, token: String, appId: String, ttl: Long): String? {
        if (url.startsWith("mp:")) return url
        if (!isHttp(url)) return null
        val key = "x:$url"
        val now = System.currentTimeMillis()
        val c = cache[key]
        if (c != null) {
            if (c.fresh(now)) return c.v
            if (c.v != null) {
                // Stale but usable (mp keys are derived from the URL): refresh in the background.
                scope.launch { runCatching { once(key) { externalNow(url, token, appId, ttl) } } }
                return c.v
            }
        }
        return once(key) { externalNow(url, token, appId, ttl) }
    }

    private suspend fun externalNow(url: String, token: String, appId: String, ttl: Long): Outcome {
        val r = rest.externalAssets(token, appId, listOf(url)).firstOrNull()
        return if (r != null) Outcome(r, ttl, src = url) else Outcome(null, MISS_RETRY_TTL)
    }

    // ---- network steps ------------------------------------------------------------------------------

    /** Streams the (small) Play data-safety page and stops reading as soon as og:image shows up. */
    private suspend fun fetchPlayIcon(pkg: String): Outcome = withContext(Dispatchers.IO) {
        val url = "https://play.google.com/store/apps/datasafety".toHttpUrl().newBuilder()
            .addQueryParameter("id", pkg).addQueryParameter("hl", "en").build()
        val req = Request.Builder().url(url).header("User-Agent", Http.BROWSER_USER_AGENT).header("Accept-Language", "en").build()
        iconClient.newCall(req).execute().use { r ->
            if (r.code == 404) return@withContext Outcome(null, PLAY_MISS_TTL)
            if (!r.isSuccessful) throw IOException("Play HTTP ${r.code}")
            val source = r.body.source()
            val buf = StringBuilder()
            val chunk = okio.Buffer()
            var scanned = 0
            while (source.read(chunk, 8192) != -1L) {
                buf.append(chunk.readUtf8())
                val from = (scanned - ArtworkMatch.OG_IMAGE.length - 600).coerceAtLeast(0)
                ArtworkMatch.playIconFrom(buf.subSequence(from, buf.length))?.let { icon ->
                    EventLog.d("Assets", "Play icon for $pkg found after ${buf.length / 1024} KB")
                    return@withContext Outcome(icon, Long.MAX_VALUE) // closing the body cancels the rest
                }
                scanned = buf.length
                // Keep memory bounded: only a tail is needed to catch a split match.
                if (buf.length > 64 * 1024) {
                    buf.delete(0, buf.length - 2048)
                    scanned = buf.length
                }
            }
            EventLog.d("Assets", "No Play listing for $pkg")
            Outcome(null, PLAY_MISS_TTL)
        }
    }

    private suspend fun lookupArtwork(provider: ArtworkLookup, ref: ImageRef.Media): Outcome = withContext(Dispatchers.IO) {
        val podcast = ref.kind == MediaKind.PODCAST
        val candidates: List<ArtworkCandidate>
        val url = when (provider) {
            ArtworkLookup.ITUNES -> {
                val b = "https://itunes.apple.com/search".toHttpUrl().newBuilder()
                if (podcast) {
                    b.addQueryParameter("term", ref.artist ?: ref.album ?: ref.title).addQueryParameter("media", "podcast").addQueryParameter("entity", "podcast")
                } else {
                    b.addQueryParameter("term", ArtworkMatch.searchTerm(ref.title, ref.artist)).addQueryParameter("media", "music").addQueryParameter("entity", "song")
                }
                Locale.getDefault().country.takeIf { it.length == 2 }?.let { b.addQueryParameter("country", it) }
                b.addQueryParameter("limit", "3").build()
            }
            ArtworkLookup.DEEZER -> {
                if (podcast) return@withContext Outcome(null, NEGATIVE_TTL)
                "https://api.deezer.com/search".toHttpUrl().newBuilder()
                    .addQueryParameter("q", ArtworkMatch.searchTerm(ref.title, ref.artist)).addQueryParameter("limit", "3").build()
            }
            ArtworkLookup.NONE -> return@withContext Outcome(null, NEGATIVE_TTL)
        }
        val body = lookupClient.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("Lookup HTTP ${r.code}")
            r.body.string()
        }
        candidates = if (provider == ArtworkLookup.ITUNES) ArtworkMatch.parseItunes(body, podcast) else ArtworkMatch.parseDeezer(body)
        val hit = if (podcast) ArtworkMatch.pickPodcast(candidates, ref.artist ?: ref.album ?: ref.title)
        else ArtworkMatch.pick(candidates, ref.title, ref.artist, ref.album)
        if (hit == null) EventLog.d("Assets", "No confident ${provider.name.lowercase()} match for “${ref.title}”")
        if (hit != null) Outcome(hit.artworkUrl, ART_TTL) else Outcome(null, NEGATIVE_TTL)
    }

    private suspend fun upload(host: ImageHost, bytes: ByteArray, name: String, mime: String): String = withContext(Dispatchers.IO) {
        val (endpoint, extra) = when (host) {
            ImageHost.LITTERBOX -> "https://litterbox.catbox.moe/resources/internals/api.php" to mapOf("time" to "72h")
            ImageHost.CATBOX -> "https://catbox.moe/user/api.php" to emptyMap()
            ImageHost.NONE -> throw IllegalStateException("No image host")
        }
        val body = MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("reqtype", "fileupload").apply {
            extra.forEach { (k, v) -> addFormDataPart(k, v) }
            addFormDataPart("fileToUpload", name, bytes.toRequestBody(mime.toMediaType()))
        }.build()
        uploadClient.newCall(Request.Builder().url(endpoint).post(body).build()).execute().use { r ->
            val text = r.body.string().trim()
            if (!r.isSuccessful || !text.startsWith("https://")) throw IOException("Upload failed (${r.code}): ${text.take(100)}")
            EventLog.i("Assets", "Uploaded ${bytes.size / 1024} KB image to ${host.name.lowercase()}")
            text
        }
    }

    private fun encode(bmp: Bitmap, png: Boolean): ByteArray {
        val max = 512
        val scaled = if (bmp.width > max || bmp.height > max) {
            val f = max.toFloat() / maxOf(bmp.width, bmp.height)
            bmp.scale((bmp.width * f).toInt().coerceAtLeast(1), (bmp.height * f).toInt().coerceAtLeast(1))
        } else bmp
        val out = ByteArrayOutputStream()
        scaled.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 85, out)
        return out.toByteArray()
    }

    // ---- cache plumbing -----------------------------------------------------------------------------

    private class Outcome(val value: String?, val ttl: Long, val src: String? = null)

    /** A fresh positive cached value, or null. */
    private fun cachedOrNull(key: String): String? = cache[key]?.takeIf { it.fresh(System.currentTimeMillis()) }?.v

    /**
     * Single-flight: one network flight per key; the result (or failure) is cached. Runs in [scope] so a
     * cancelled caller doesn't waste a half-finished request.
     */
    private suspend fun once(key: String, block: suspend () -> Outcome): String? {
        val d = synchronized(inflight) {
            inflight[key] ?: scope.async(Dispatchers.IO) {
                try {
                    val o = block()
                    put(key, o)
                    o.value
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    EventLog.w("Assets", "Lookup failed for ${key.substringBefore(':')}", e)
                    // Keep a stale positive value if we have one; otherwise remember the failure briefly.
                    val old = cache[key]
                    cache[key] = CachedAsset(old?.v, old?.src, System.currentTimeMillis() + FAIL_TTL)
                    scheduleSave()
                    old?.v
                } finally {
                    synchronized(inflight) { inflight.remove(key) }
                }
            }.also { inflight[key] = it }
        }
        return d.await()
    }

    private fun put(key: String, o: Outcome) {
        val exp = if (o.ttl == Long.MAX_VALUE) Long.MAX_VALUE else System.currentTimeMillis() + o.ttl
        cache[key] = CachedAsset(o.value, o.src, exp)
        scheduleSave()
    }

    private fun scheduleSave() {
        synchronized(this) {
            if (saveJob?.isActive == true) return
            saveJob = scope.launch(Dispatchers.IO) {
                delay(2_000)
                runCatching { cache.save(file) }.onFailure { EventLog.w("Assets", "Could not save image cache", it) }
            }
        }
    }

    private fun isHttp(u: String) = u.startsWith("https://", true) || u.startsWith("http://", true)

    private fun hostTtl(host: ImageHost) = when (host) {
        ImageHost.LITTERBOX -> 70 * HOUR // files live 72 h; leave a margin
        ImageHost.CATBOX -> 365 * DAY
        ImageHost.NONE -> 0
    }

    private companion object {
        const val HOUR = 3_600_000L
        const val DAY = 24 * HOUR
        const val URL_TTL = 7 * DAY
        const val ART_TTL = 90 * DAY
        const val NEGATIVE_TTL = 7 * DAY
        const val PLAY_MISS_TTL = 30 * DAY
        const val MISS_RETRY_TTL = HOUR
        const val FAIL_TTL = 10 * 60_000L
        val LOOKUP_KINDS = setOf(MediaKind.MUSIC, MediaKind.PODCAST, MediaKind.UNKNOWN)
    }
}
