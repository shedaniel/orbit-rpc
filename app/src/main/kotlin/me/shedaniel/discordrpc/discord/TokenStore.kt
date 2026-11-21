package me.shedaniel.discordrpc.discord

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.shedaniel.discordrpc.core.model.DiscordUser
import me.shedaniel.discordrpc.core.settings.AppJson
import me.shedaniel.discordrpc.core.util.EventLog
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Holds the Discord token, encrypted at rest with a non-exportable Android Keystore AES-256/GCM key,
 * plus the cached [DiscordUser] (plain JSON; not secret).
 */
class TokenStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("discord_auth", Context.MODE_PRIVATE)

    private val _token = MutableStateFlow(load())
    val token: StateFlow<String?> = _token.asStateFlow()

    private val _user = MutableStateFlow(if (_token.value != null) loadUser() else null)
    val user: StateFlow<DiscordUser?> = _user.asStateFlow()

    /** Stores a new token (null = log out, which also forgets the user). */
    fun setToken(token: String?) {
        val t = token?.trim()?.removeSurrounding("\"")?.takeIf { it.isNotEmpty() }
        if (t == null) {
            prefs.edit().remove(KEY_TOKEN).remove(KEY_USER).apply()
            _user.value = null
            _token.value = null
            return
        }
        if (t != _token.value) {
            prefs.edit().putString(KEY_TOKEN, encrypt(t)).remove(KEY_USER).apply()
            _user.value = null
            _token.value = t
        }
    }

    fun setUser(user: DiscordUser?) {
        if (user == _user.value) return
        _user.value = user
        if (user == null) prefs.edit().remove(KEY_USER).apply()
        else prefs.edit().putString(KEY_USER, AppJson.encodeToString(DiscordUser.serializer(), user)).apply()
    }

    private fun load(): String? {
        val enc = prefs.getString(KEY_TOKEN, null) ?: return null
        return try {
            decrypt(enc)
        } catch (e: Exception) {
            EventLog.w("Auth", "Stored token could not be decrypted; please log in again", e)
            prefs.edit().remove(KEY_TOKEN).remove(KEY_USER).apply()
            null
        }
    }

    private fun loadUser(): DiscordUser? = prefs.getString(KEY_USER, null)?.let {
        runCatching { AppJson.decodeFromString(DiscordUser.serializer(), it) }.getOrNull()
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    private fun encrypt(plain: String): String {
        val c = Cipher.getInstance(TRANSFORMATION)
        c.init(Cipher.ENCRYPT_MODE, key())
        val iv = c.iv
        val ct = c.doFinal(plain.encodeToByteArray())
        return Base64.encodeToString(byteArrayOf(iv.size.toByte()) + iv + ct, Base64.NO_WRAP)
    }

    private fun decrypt(enc: String): String {
        val raw = Base64.decode(enc, Base64.NO_WRAP)
        val ivLen = raw[0].toInt()
        val iv = raw.copyOfRange(1, 1 + ivLen)
        val c = Cipher.getInstance(TRANSFORMATION)
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        return c.doFinal(raw, 1 + ivLen, raw.size - 1 - ivLen).decodeToString()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "orbit_discord_token"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_TOKEN = "token"
        const val KEY_USER = "user"
    }
}
