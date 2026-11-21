package me.shedaniel.discordrpc.discord

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonArray
import me.shedaniel.discordrpc.core.model.DiscordUser
import me.shedaniel.discordrpc.net.Http
import me.shedaniel.discordrpc.net.TrafficCategory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/** Thrown for non-2xx Discord responses. [code] 401 means the token is invalid. */
class DiscordHttpException(val code: Int, message: String) : IOException(message)

/** The handful of Discord REST calls we need. */
class DiscordRest(http: Http, private val baseUrl: String = "https://discord.com") {
    private val client = http.client(TrafficCategory.DISCORD_API)
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    /** GET /users/@me — validates a token and returns its user. */
    suspend fun me(token: String): Result<DiscordUser> = runCatching {
        val body = call(Request.Builder().url("$baseUrl/api/v10/users/@me").header("Authorization", token).get().build())
        val u = json.decodeFromString(UserDto.serializer(), body)
        DiscordUser(u.id, u.username, u.globalName, u.avatar)
    }

    /**
     * Proxies public image URLs through Discord's media proxy. Returns, per input url (same order), an
     * asset key `mp:<external_asset_path>` or null when Discord didn't return one for it.
     */
    suspend fun externalAssets(token: String, applicationId: String, urls: List<String>): List<String?> {
        if (urls.isEmpty()) return emptyList()
        val payload = buildJsonObject { putJsonArray("urls") { urls.forEach { add(it) } } }.toString()
        val body = call(
            Request.Builder()
                .url("$baseUrl/api/v9/applications/$applicationId/external-assets")
                .header("Authorization", token)
                .post(payload.toRequestBody(JSON))
                .build(),
        )
        val items = json.decodeFromString(ListSerializer(ExternalAssetDto.serializer()), body)
        val byUrl = items.filter { it.url != null }.associateBy { it.url }
        return urls.mapIndexed { i, u ->
            val item = byUrl[u] ?: items.getOrNull(i)?.takeIf { it.url == null || items.size == urls.size }
            item?.path?.takeIf { it.isNotBlank() }?.let { "mp:$it" }
        }
    }

    private suspend fun call(request: Request): String = withContext(Dispatchers.IO) {
        val req = request.newBuilder()
            .header("User-Agent", Http.BROWSER_USER_AGENT)
            .header("Accept", "application/json")
            .build()
        client.newCall(req).execute().use { r ->
            val text = r.body.string()
            if (!r.isSuccessful) throw DiscordHttpException(r.code, "Discord ${r.code}: ${text.take(200)}")
            text
        }
    }

    @Serializable
    private class UserDto(val id: String, val username: String, @SerialName("global_name") val globalName: String? = null, val avatar: String? = null)

    @Serializable
    private class ExternalAssetDto(val url: String? = null, @SerialName("external_asset_path") val path: String? = null)

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}

/** Checks a token against `/users/@me`. */
suspend fun validateToken(rest: DiscordRest, token: String): Result<DiscordUser> {
    val t = token.trim().removeSurrounding("\"")
    if (t.isEmpty()) return Result.failure(IllegalArgumentException("Empty token"))
    return rest.me(t)
}
