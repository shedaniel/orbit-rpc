package me.shedaniel.discordrpc.discord

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import me.shedaniel.discordrpc.core.model.DiscordUser

// STUB — implemented by the discord/net agent.
class TokenStore(context: Context) {
    val token: StateFlow<String?> = MutableStateFlow(null)
    val user: StateFlow<DiscordUser?> = MutableStateFlow(null)
    fun setToken(token: String?) {}
    fun setUser(user: DiscordUser?) {}
}
