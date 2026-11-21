package me.shedaniel.discordrpc.sources

import android.app.Activity
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import me.shedaniel.discordrpc.net.Http

data class SpotifyAccount(val id: String, val displayName: String, val imageUrl: String?)

// STUB — implemented by the sources agent.
class SpotifyAuth(context: Context, http: Http, scope: CoroutineScope) {
    val account: StateFlow<SpotifyAccount?> = MutableStateFlow(null)
    /** Opens the browser for PKCE login. */
    fun beginLogin(activity: Activity, clientId: String) {}
    /** Called from the redirect activity. */
    fun handleRedirect(uri: Uri) {}
    fun logout() {}
    /** A valid access token, refreshing if needed. Null when logged out. */
    suspend fun accessToken(clientId: String): String? = null

    companion object {
        const val REDIRECT_URI = "orbit-rpc://spotify-callback"
    }
}
