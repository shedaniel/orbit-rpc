package me.shedaniel.discordrpc.discord

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import me.shedaniel.discordrpc.core.model.ImageRef
import me.shedaniel.discordrpc.core.settings.ImageSettings
import me.shedaniel.discordrpc.core.settings.NetworkProfile
import me.shedaniel.discordrpc.net.Http
import me.shedaniel.discordrpc.sources.AppCatalog

// STUB — implemented by the discord/net agent.
class DiscordAssetResolver(
    context: Context,
    http: Http,
    catalog: AppCatalog,
    scope: CoroutineScope,
    imageSettings: () -> ImageSettings,
) : AssetResolver {
    override suspend fun resolve(ref: ImageRef, profile: NetworkProfile, token: String, applicationId: String): String? = null
    override fun previewUrl(ref: ImageRef): String? = null
}
