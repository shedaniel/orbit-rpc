package me.shedaniel.discordrpc.sources

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.shedaniel.discordrpc.core.model.MediaKind

@Serializable
class SpotifyImage(val url: String, val width: Int? = null, val height: Int? = null)

/** What `GET /v1/me/player` says is playing, flattened. */
data class SpotifyPlayback(
    val itemId: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val imageUrl: String?,
    val link: String?,
    val durationMs: Long?,
    val progressMs: Long?,
    val isPlaying: Boolean,
    val deviceName: String?,
    val deviceType: String?,
    val kind: MediaKind,
)

/** Pure parser for the Spotify player endpoint. */
object SpotifyPlayerParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    @Serializable
    private class Player(
        val device: Device? = null,
        @SerialName("progress_ms") val progressMs: Long? = null,
        @SerialName("is_playing") val isPlaying: Boolean = false,
        val item: Item? = null,
        @SerialName("currently_playing_type") val type: String? = null,
    )

    @Serializable
    private class Device(
        val name: String? = null,
        val type: String? = null,
        @SerialName("is_private_session") val privateSession: Boolean = false,
    )

    @Serializable
    private class Named(val name: String? = null, val publisher: String? = null, val images: List<SpotifyImage> = emptyList())

    @Serializable
    private class Item(
        val id: String? = null,
        val uri: String? = null,
        val name: String? = null,
        val type: String? = null,
        @SerialName("duration_ms") val durationMs: Long? = null,
        val artists: List<Named> = emptyList(),
        val album: Named? = null,
        val show: Named? = null,
        val images: List<SpotifyImage> = emptyList(),
        @SerialName("external_urls") val externalUrls: Map<String, String> = emptyMap(),
    )

    /** Null when nothing identifiable is playing (ads, private session, unknown item). */
    fun parse(body: String): SpotifyPlayback? {
        val p = runCatching { json.decodeFromString(Player.serializer(), body) }.getOrNull() ?: return null
        val item = p.item ?: return null
        if (p.device?.privateSession == true) return null
        val title = item.name?.takeIf { it.isNotBlank() } ?: return null
        val episode = item.type == "episode" || p.type == "episode"
        val artist: String?
        val album: String?
        val images: List<SpotifyImage>
        if (episode) {
            artist = item.show?.name ?: item.show?.publisher
            album = null
            images = item.images.ifEmpty { item.show?.images.orEmpty() }
        } else {
            artist = item.artists.mapNotNull { it.name?.takeIf(String::isNotBlank) }.joinToString(", ").ifBlank { null }
            album = item.album?.name?.takeIf { it.isNotBlank() }
            images = item.album?.images.orEmpty()
        }
        return SpotifyPlayback(
            itemId = item.id ?: item.uri ?: title,
            title = title,
            artist = artist,
            album = album,
            imageUrl = pickImage(images),
            link = item.externalUrls["spotify"],
            durationMs = item.durationMs?.takeIf { it > 0 },
            progressMs = p.progressMs?.takeIf { it >= 0 },
            isPlaying = p.isPlaying,
            deviceName = p.device?.name,
            deviceType = p.device?.type,
            kind = if (episode) MediaKind.PODCAST else MediaKind.MUSIC,
        )
    }

    /** Smallest image at least 300px wide (Spotify gives 64/300/640), else the largest. */
    fun pickImage(images: List<SpotifyImage>): String? {
        val sized = images.filter { (it.width ?: 0) >= 300 }.minByOrNull { it.width ?: 0 }
        return (sized ?: images.maxByOrNull { it.width ?: 0 })?.url
    }

    /**
     * Delay until the next poll: the regular cadence while playing, but no later than just after
     * the current item should end, so a track change is noticed promptly.
     */
    fun nextPollDelayMs(p: SpotifyPlayback?, pollMs: Long, idleMs: Long): Long {
        if (p == null || !p.isPlaying) return idleMs
        val d = p.durationMs ?: return pollMs
        val pos = p.progressMs ?: return pollMs
        val remaining = (d - pos).coerceAtLeast(0) + 1500
        return minOf(pollMs, remaining).coerceAtLeast(3000)
    }
}
