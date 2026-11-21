package me.shedaniel.discordrpc.core.util

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** In-memory ring buffer of notable events, shown on the activity log screen. */
object EventLog {
    enum class Level { DEBUG, INFO, WARN, ERROR }
    data class Entry(val timeMs: Long, val level: Level, val tag: String, val message: String)

    private const val MAX = 400
    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    fun d(tag: String, msg: String) = add(Level.DEBUG, tag, msg)
    fun i(tag: String, msg: String) = add(Level.INFO, tag, msg)
    fun w(tag: String, msg: String, t: Throwable? = null) = add(Level.WARN, tag, msg + (t?.let { ": ${it.message}" } ?: ""))
    fun e(tag: String, msg: String, t: Throwable? = null) = add(Level.ERROR, tag, msg + (t?.let { ": ${it.message}" } ?: ""))

    private fun add(level: Level, tag: String, msg: String) {
        when (level) {
            Level.DEBUG -> Log.d("Orbit/$tag", msg)
            Level.INFO -> Log.i("Orbit/$tag", msg)
            Level.WARN -> Log.w("Orbit/$tag", msg)
            Level.ERROR -> Log.e("Orbit/$tag", msg)
        }
        val e = Entry(System.currentTimeMillis(), level, tag, msg)
        _entries.update { (it + e).takeLast(MAX) }
    }

    fun clear() = _entries.update { emptyList() }

    /** Puts [previous] back in front of anything logged since (undo for [clear]). */
    fun restore(previous: List<Entry>) = _entries.update { (previous + it).takeLast(MAX) }
}
