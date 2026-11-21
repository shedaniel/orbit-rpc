package me.shedaniel.discordrpc.core.settings

import android.util.AtomicFile
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.File

val AppJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
    explicitNulls = false
    coerceInputValues = true
}

/**
 * A tiny reactive JSON-file store. Reads synchronously once, writes are debounced and atomic.
 */
@OptIn(FlowPreview::class)
class JsonStore<T>(
    file: File,
    private val serializer: KSerializer<T>,
    default: () -> T,
    scope: CoroutineScope,
    writeDebounceMs: Long = 300,
) {
    private val atomic = AtomicFile(file)
    private val _state = MutableStateFlow(read() ?: default())
    val state: StateFlow<T> = _state.asStateFlow()
    val value: T get() = _state.value

    init {
        // Skip only the value we just read. (`drop(1)` would also swallow an update made before the collector
        // subscribes, e.g. a tile toggle in a cold process, and that change would never be written.)
        val initial = _state.value
        _state.dropWhile { it === initial }.debounce(writeDebounceMs).onEach { write(it) }
            .launchIn(CoroutineScope(scope.coroutineContext + Dispatchers.IO))
    }

    fun update(transform: (T) -> T) = _state.update(transform)

    private fun read(): T? = try {
        if (!atomic.baseFile.exists()) null
        else AppJson.decodeFromString(serializer, atomic.readFully().decodeToString())
    } catch (e: Exception) {
        Log.w("JsonStore", "Failed to read ${atomic.baseFile}", e)
        null
    }

    private fun write(value: T) {
        val out = atomic.startWrite()
        try {
            out.write(AppJson.encodeToString(serializer, value).encodeToByteArray())
            atomic.finishWrite(out)
        } catch (e: Exception) {
            atomic.failWrite(out)
            Log.w("JsonStore", "Failed to write ${atomic.baseFile}", e)
        }
    }
}
