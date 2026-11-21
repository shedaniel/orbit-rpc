package me.shedaniel.discordrpc.discord

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import me.shedaniel.discordrpc.core.util.EventLog
import me.shedaniel.discordrpc.net.Http

private const val LOGIN_URL = "https://discord.com/login"

/**
 * Discord removes `window.localStorage` from the page; a fresh iframe still exposes the origin's storage.
 * The token is stored JSON-encoded (with quotes).
 */
private const val TOKEN_JS =
    "(function(){try{var f=document.createElement('iframe');f.style.display='none';document.body.appendChild(f);" +
        "var t=f.contentWindow.localStorage.getItem('token');f.remove();return t;}catch(e){return null;}})();"

/**
 * One-off Discord login in a WebView. Starts from a clean slate, waits for the web app to navigate to
 * `/app` or `/channels/...`, reads the token, wipes all WebView data again and calls [onToken] once.
 * Stops the page immediately after so the web client doesn't start downloading the app / opening a gateway.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DiscordLoginWebView(modifier: Modifier, onToken: (String) -> Unit, onLoadingChange: (Boolean) -> Unit = {}) {
    val tokenCb by rememberUpdatedState(onToken)
    val loadingCb by rememberUpdatedState(onLoadingChange)
    val state = remember { LoginState() }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            clearWebData()
            WebView(ctx).apply {
                clearCache(true)
                clearHistory()
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.userAgentString = Http.BROWSER_USER_AGENT
                settings.setSupportMultipleWindows(false)
                settings.javaScriptCanOpenWindowsAutomatically = false
                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                        loadingCb(true)
                        check(view, url)
                    }

                    override fun onPageFinished(view: WebView, url: String?) {
                        loadingCb(false)
                        check(view, url)
                    }

                    override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) = check(view, url)

                    private fun check(view: WebView, url: String?) {
                        if (state.done || state.polling || !isLoggedInUrl(url)) return
                        state.polling = true
                        poll(view, attempt = 0)
                    }

                    private fun poll(view: WebView, attempt: Int) {
                        if (state.done) return
                        view.evaluateJavascript(TOKEN_JS) { raw ->
                            if (state.done) return@evaluateJavascript
                            val token = parseTokenResult(raw)
                            if (token != null) {
                                state.done = true
                                view.stopLoading()
                                view.loadUrl("about:blank")
                                view.clearCache(true)
                                view.clearHistory()
                                clearWebData()
                                EventLog.i("Auth", "Logged in through Discord web")
                                tokenCb(token)
                            } else if (attempt < 20) {
                                state.handler.postDelayed({ poll(view, attempt + 1) }, 500)
                            } else {
                                state.polling = false
                                EventLog.w("Auth", "Could not read the token after login")
                            }
                        }
                    }
                }
                loadUrl(LOGIN_URL)
            }
        },
        onRelease = { wv ->
            state.done = true
            state.handler.removeCallbacksAndMessages(null)
            wv.stopLoading()
            wv.destroy()
        },
    )
}

private class LoginState {
    @Volatile var done = false
    var polling = false
    val handler = Handler(Looper.getMainLooper())
}

private fun clearWebData() {
    runCatching {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
    }
}

internal fun isLoggedInUrl(url: String?): Boolean {
    val u = url?.let { runCatching { Uri.parse(it) }.getOrNull() } ?: return false
    val host = u.host ?: return false
    if (host != "discord.com" && !host.endsWith(".discord.com")) return false
    val path = u.path ?: return false
    return path == "/app" || path.startsWith("/app/") || path.startsWith("/channels/")
}

/** Decodes an `evaluateJavascript` result (a JSON value) into a bare token, or null. */
fun parseTokenResult(raw: String?): String? {
    if (raw.isNullOrBlank() || raw == "null" || raw == "undefined") return null
    val outer = runCatching { (Json.parseToJsonElement(raw) as? JsonPrimitive)?.takeIf { it.isString }?.content }.getOrNull() ?: raw
    val t = outer.trim().removeSurrounding("\"").trim()
    return t.takeIf { it.length >= 20 && it.none { c -> c.isWhitespace() || c == '"' } }
}
