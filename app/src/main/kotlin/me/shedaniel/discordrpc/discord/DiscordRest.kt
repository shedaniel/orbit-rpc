package me.shedaniel.discordrpc.discord

import me.shedaniel.discordrpc.core.model.DiscordUser
import me.shedaniel.discordrpc.net.Http

// STUB — implemented by the discord/net agent.
class DiscordRest(http: Http) {
    suspend fun me(token: String): Result<DiscordUser> = Result.failure(NotImplementedError())
}
