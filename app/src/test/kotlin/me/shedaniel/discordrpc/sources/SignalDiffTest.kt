package me.shedaniel.discordrpc.sources

import me.shedaniel.discordrpc.core.model.MediaKind
import me.shedaniel.discordrpc.core.model.MediaSignal
import me.shedaniel.discordrpc.core.model.SignalOrigin
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalDiffTest {
    private fun sig(pos: Long?, at: Long, playing: Boolean = true, title: String = "Song", speed: Float = 1f) = MediaSignal(
        id = "media:x", origin = SignalOrigin.MEDIA_SESSION, packageName = "x", appLabel = "X", since = 1000,
        title = title, artist = "A", album = null, artwork = null, durationMs = 200_000, positionMs = pos,
        positionAtMs = at, playbackSpeed = speed, playing = playing, kind = MediaKind.MUSIC,
    )

    @Test fun continuousPlaybackDoesNotEmit() {
        val a = sig(10_000, 100_000)
        assertFalse(SignalDiff.meaningfullyChanged(a, sig(40_000, 130_000)))
        assertFalse(SignalDiff.meaningfullyChanged(a, sig(41_500, 130_000))) // jitter < 2s
        assertFalse(SignalDiff.meaningfullyChanged(sig(0, 0, speed = 2f), sig(20_000, 10_000, speed = 2f)))
        val prev = listOf(a)
        assertSame(prev, SignalDiff.merge(prev, listOf(sig(70_000, 160_000))))
    }

    @Test fun seekEmits() {
        val a = sig(10_000, 100_000)
        assertTrue(SignalDiff.meaningfullyChanged(a, sig(90_000, 130_000)))
        assertTrue(SignalDiff.meaningfullyChanged(a, sig(5_000, 130_000)))
        assertNotSame(listOf(a), SignalDiff.merge(listOf(a), listOf(sig(90_000, 130_000))))
    }

    @Test fun otherChangesEmit() {
        val a = sig(10_000, 100_000)
        assertTrue(SignalDiff.meaningfullyChanged(a, sig(10_000, 100_000, playing = false)))
        assertTrue(SignalDiff.meaningfullyChanged(a, sig(10_000, 100_000, title = "Other")))
        assertTrue(SignalDiff.meaningfullyChanged(a, sig(null, 100_000)))
        assertTrue(SignalDiff.meaningfullyChanged(null, a))
    }

    @Test fun pausedComparesPosition() {
        val p = sig(10_000, 100_000, playing = false)
        assertFalse(SignalDiff.meaningfullyChanged(p, sig(10_000, 500_000, playing = false)))
        assertTrue(SignalDiff.meaningfullyChanged(p, sig(60_000, 500_000, playing = false)))
    }

    @Test fun removalEmits() {
        val prev = listOf(sig(10_000, 100_000))
        assertTrue(SignalDiff.merge(prev, emptyList()).isEmpty())
    }
}
