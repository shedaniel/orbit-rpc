package me.shedaniel.discordrpc.sources

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import androidx.core.content.edit
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.shedaniel.discordrpc.core.util.EventLog
import me.shedaniel.discordrpc.net.Http
import me.shedaniel.discordrpc.net.TrafficCategory
import okhttp3.FormBody
import okhttp3.Request
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom

data class SpotifyAccount(val id: String, val displayName: String, val imageUrl: String?)

/**
 * Spotify Web API login with PKCE (no client secret). The user supplies their own developer app
 * client id, whose redirect URI must be [REDIRECT_URI]. Tokens live in private SharedPreferences.
 */
class SpotifyAuth(context: Context, private val http: Http, private val scope: CoroutineScope) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("spotify_auth", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    private val _account = MutableStateFlow(loadAccount())
    val account: StateFlow<SpotifyAccount?> = _account.asStateFlow()

    private val _loggingIn = MutableStateFlow(false)
    /** True while the code exchange after the redirect is running. */
    val loggingIn: StateFlow<Boolean> = _loggingIn.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    /** Human-readable reason the last login / refresh failed. */
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _loggedIn = MutableStateFlow(prefs.getString(K_REFRESH, null) != null)
    /** Holds a refresh token (the account details may still be loading). */
    val loggedIn: StateFlow<Boolean> = _loggedIn.asStateFlow()

    /** Opens the browser for PKCE login. */
    fun beginLogin(activity: Activity, clientId: String) {
        val id = clientId.trim()
        if (id.isEmpty()) {
            _lastError.value = "Set a Spotify client id first"
            return
        }
        val verifier = randomUrlSafe(64)
        val state = randomUrlSafe(16)
        prefs.edit {
            putString(K_VERIFIER, verifier)
            putString(K_STATE, state)
            putString(K_PENDING_CLIENT, id)
        }
        _lastError.value = null
        val uri = Uri.Builder().scheme("https").authority("accounts.spotify.com").path("/authorize")
            .appendQueryParameter("client_id", id)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", challenge(verifier))
            .appendQueryParameter("scope", SCOPES)
            .appendQueryParameter("state", state)
            .build()
        runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
            .onFailure { _lastError.value = "No browser available"; EventLog.w("Spotify", "Couldn't open the browser", it) }
    }

    /** Called from the redirect activity. */
    fun handleRedirect(uri: Uri) {
        if (uri.scheme != REDIRECT_URI.toUri().scheme || uri.host != REDIRECT_URI.toUri().host) return
        val expectedState = prefs.getString(K_STATE, null)
        val verifier = prefs.getString(K_VERIFIER, null)
        val clientId = prefs.getString(K_PENDING_CLIENT, null)
        prefs.edit { remove(K_STATE); remove(K_VERIFIER); remove(K_PENDING_CLIENT) }
        uri.getQueryParameter("error")?.let {
            _lastError.value = if (it == "access_denied") "Login cancelled" else "Spotify refused the login: $it"
            EventLog.w("Spotify", "Login failed: $it")
            return
        }
        val code = uri.getQueryParameter("code")
        if (code == null || verifier == null || clientId == null || uri.getQueryParameter("state") != expectedState) {
            _lastError.value = "Login expired, try again"
            EventLog.w("Spotify", "Unexpected redirect (state mismatch or no pending login)")
            return
        }
        _loggingIn.value = true
        scope.launch(Dispatchers.IO) {
            try {
                val body = FormBody.Builder()
                    .add("grant_type", "authorization_code")
                    .add("code", code)
                    .add("redirect_uri", REDIRECT_URI)
                    .add("client_id", clientId)
                    .add("code_verifier", verifier)
                    .build()
                val t = tokenRequest(body) ?: return@launch
                mutex.withLock { saveTokens(t, clientId) }
                fetchAccount(t.accessToken)
                _lastError.value = null
                EventLog.i("Spotify", "Logged in as ${_account.value?.displayName ?: "?"}")
            } catch (e: IOException) {
                _lastError.value = "Network error during login"
                EventLog.w("Spotify", "Login failed", e)
            } finally {
                _loggingIn.value = false
            }
        }
    }

    fun logout() {
        prefs.edit { clear() }
        _account.value = null
        _loggedIn.value = false
        EventLog.i("Spotify", "Logged out")
    }

    /** Forces the next [accessToken] call to refresh (after a 401). */
    fun invalidate() = prefs.edit { putLong(K_EXPIRES, 0) }

    /** A valid access token, refreshing if needed. Null when logged out or on network failure. */
    suspend fun accessToken(clientId: String): String? = mutex.withLock {
        val refresh = prefs.getString(K_REFRESH, null) ?: return null
        val access = prefs.getString(K_ACCESS, null)
        if (access != null && System.currentTimeMillis() < prefs.getLong(K_EXPIRES, 0) - 60_000) return access
        // A token is tied to the client id that issued it; prefer that one.
        val cid = prefs.getString(K_CLIENT, null) ?: clientId
        withContext(Dispatchers.IO) {
            try {
                val body = FormBody.Builder()
                    .add("grant_type", "refresh_token")
                    .add("refresh_token", refresh)
                    .add("client_id", cid)
                    .build()
                val t = tokenRequest(body, logoutOnInvalid = true) ?: return@withContext null
                saveTokens(t, cid)
                if (_account.value == null) runCatching { fetchAccount(t.accessToken) }
                t.accessToken
            } catch (e: IOException) {
                EventLog.w("Spotify", "Token refresh failed", e)
                null
            }
        }
    }

    private fun tokenRequest(body: FormBody, logoutOnInvalid: Boolean = false): TokenResponse? {
        val req = Request.Builder().url("https://accounts.spotify.com/api/token").post(body).build()
        http.client(TrafficCategory.SPOTIFY).newCall(req).execute().use { r ->
            val text = r.body.string()
            if (!r.isSuccessful) {
                val err = runCatching { json.decodeFromString(ErrorResponse.serializer(), text) }.getOrNull()
                EventLog.w("Spotify", "Token request failed: HTTP ${r.code} ${err?.error ?: ""} ${err?.description ?: ""}")
                if (logoutOnInvalid && r.code == 400 && err?.error == "invalid_grant") {
                    _lastError.value = "Spotify session expired, log in again"
                    logout()
                } else _lastError.value = "Spotify login failed (${err?.description ?: "HTTP ${r.code}"})"
                return null
            }
            return json.decodeFromString(TokenResponse.serializer(), text)
        }
    }

    private fun saveTokens(t: TokenResponse, clientId: String) {
        prefs.edit {
            putString(K_ACCESS, t.accessToken)
            putLong(K_EXPIRES, System.currentTimeMillis() + t.expiresIn * 1000L)
            t.refreshToken?.let { putString(K_REFRESH, it) } // refresh responses may omit it
            putString(K_CLIENT, clientId)
        }
        _loggedIn.value = true
    }

    private fun fetchAccount(token: String) {
        val req = Request.Builder().url("https://api.spotify.com/v1/me").header("Authorization", "Bearer $token").build()
        http.client(TrafficCategory.SPOTIFY).newCall(req).execute().use { r ->
            if (!r.isSuccessful) {
                EventLog.w("Spotify", "/v1/me failed: HTTP ${r.code}")
                return
            }
            val me = json.decodeFromString(MeResponse.serializer(), r.body.string())
            val acc = SpotifyAccount(me.id, me.displayName ?: me.id, me.images.maxByOrNull { it.width ?: 0 }?.url)
            prefs.edit {
                putString(K_ACC_ID, acc.id)
                putString(K_ACC_NAME, acc.displayName)
                putString(K_ACC_IMG, acc.imageUrl)
            }
            _account.value = acc
        }
    }

    private fun loadAccount(): SpotifyAccount? {
        if (prefs.getString(K_REFRESH, null) == null) return null
        val id = prefs.getString(K_ACC_ID, null) ?: return null
        return SpotifyAccount(id, prefs.getString(K_ACC_NAME, null) ?: id, prefs.getString(K_ACC_IMG, null))
    }

    @Serializable
    private class TokenResponse(
        @SerialName("access_token") val accessToken: String,
        @SerialName("expires_in") val expiresIn: Long = 3600,
        @SerialName("refresh_token") val refreshToken: String? = null,
    )

    @Serializable
    private class ErrorResponse(val error: String? = null, @SerialName("error_description") val description: String? = null)

    @Serializable
    private class MeResponse(
        val id: String,
        @SerialName("display_name") val displayName: String? = null,
        val images: List<SpotifyImage> = emptyList(),
    )

    companion object {
        const val REDIRECT_URI = "orbit-rpc://spotify-callback"
        const val SCOPES = "user-read-playback-state user-read-currently-playing"

        private const val K_ACCESS = "access"
        private const val K_REFRESH = "refresh"
        private const val K_EXPIRES = "expires"
        private const val K_CLIENT = "client"
        private const val K_VERIFIER = "pkce_verifier"
        private const val K_STATE = "pkce_state"
        private const val K_PENDING_CLIENT = "pkce_client"
        private const val K_ACC_ID = "acc_id"
        private const val K_ACC_NAME = "acc_name"
        private const val K_ACC_IMG = "acc_img"

        private val random = SecureRandom()

        private fun randomUrlSafe(bytes: Int): String {
            val b = ByteArray(bytes).also { random.nextBytes(it) }
            return Base64.encodeToString(b, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        }

        private fun challenge(verifier: String): String {
            val d = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
            return Base64.encodeToString(d, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        }
    }
}
