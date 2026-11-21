package me.shedaniel.discordrpc.sources

import me.shedaniel.discordrpc.core.model.MediaSignal
import me.shedaniel.discordrpc.core.model.Signal
import kotlin.math.abs

/** Decides whether a freshly built signal is worth emitting over the previous one. Pure. */
object SignalDiff {
    /** Timeline shifts below this are treated as clock jitter, above as a seek. */
    const val SEEK_TOLERANCE_MS = 2000L

    fun meaningfullyChanged(old: MediaSignal?, new: MediaSignal): Boolean {
        if (old == null) return true
        // Everything except the timeline must match exactly.
        val a = old.copy(positionMs = null, positionAtMs = 0, since = 0)
        val b = new.copy(positionMs = null, positionAtMs = 0, since = 0)
        if (a != b) return true
        if (old.since != new.since) return true
        if ((old.positionMs == null) != (new.positionMs == null)) return true
        if (old.positionMs == null) return false
        return if (new.playing) {
            // Same computed start = continuous playback; a seek shifts it.
            val s1 = old.startedAtMs() ?: return true
            val s2 = new.startedAtMs() ?: return true
            abs(s1 - s2) > SEEK_TOLERANCE_MS
        } else {
            abs(old.positionMs - new.positionMs!!) > SEEK_TOLERANCE_MS
        }
    }

    /**
     * Merges [fresh] into [previous]: unchanged media signals keep their previous instance, so the
     * returned list equals [previous] when nothing meaningful changed (and a StateFlow won't emit).
     */
    fun merge(previous: List<Signal>, fresh: List<Signal>): List<Signal> {
        val byId = previous.associateBy { it.id }
        val merged = fresh.map { n ->
            val o = byId[n.id]
            if (o is MediaSignal && n is MediaSignal && !meaningfullyChanged(o, n)) o else n
        }
        return if (merged == previous) previous else merged
    }
}
