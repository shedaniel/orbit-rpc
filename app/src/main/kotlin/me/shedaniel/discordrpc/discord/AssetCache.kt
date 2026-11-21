package me.shedaniel.discordrpc.discord

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * One cached lookup. [v] is the value (an `mp:` key or a public URL, depending on the key's namespace),
 * null for a negative/failed result. [src] is the public URL behind an `mp:` key (for previews).
 * [exp] is the expiry (epoch ms).
 */
@Serializable
data class CachedAsset(val v: String? = null, val src: String? = null, val exp: Long = Long.MAX_VALUE) {
    fun fresh(now: Long) = now < exp
}

/**
 * A small LRU (access-ordered) map persisted as JSON. Thread-safe. Writes are requested by the owner
 * (debounced there) through [snapshotJson]/[save].
 */
class AssetCache(private val maxEntries: Int = 600) {
    private val map = object : LinkedHashMap<String, CachedAsset>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedAsset>?) = size > maxEntries
    }

    @Synchronized
    operator fun get(key: String): CachedAsset? = map[key]

    /** Like [get] but doesn't count as an access (for previews). */
    @Synchronized
    fun peek(key: String): CachedAsset? = map.entries.firstOrNull { it.key == key }?.value

    @Synchronized
    operator fun set(key: String, value: CachedAsset) {
        map[key] = value
    }

    @Synchronized
    fun remove(key: String) {
        map.remove(key)
    }

    @Synchronized
    fun size() = map.size

    @Synchronized
    fun keys(): List<String> = map.keys.toList()

    @Synchronized
    fun clear() = map.clear()

    @Synchronized
    fun snapshotJson(now: Long = System.currentTimeMillis()): String =
        json.encodeToString(serializer, map.filterValues { it.fresh(now) || it.v != null })

    fun load(file: File) {
        if (!file.exists()) return
        val loaded = runCatching { json.decodeFromString(serializer, file.readText()) }.getOrNull() ?: return
        synchronized(this) { loaded.forEach { (k, v) -> map[k] = v } }
    }

    fun save(file: File) {
        val text = snapshotJson()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = false }
        val serializer = MapSerializer(String.serializer(), CachedAsset.serializer())
    }
}
