package me.shedaniel.discordrpc.sources

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ForegroundTrackerTest {
    private val games = setOf("game.a", "game.b")
    private val watched: (String) -> Boolean = { it in games }
    private val linger = 15_000L

    @Test fun lingersAfterLeavingAndKeepsSince() {
        val t = ForegroundTracker()
        assertEquals(ForegroundTracker.Current("game.a", 0), t.update(0, "game.a", linger, watched))
        assertEquals(ForegroundTracker.Current("game.a", 0), t.update(5_000, "game.a", linger, watched))
        // Briefly opened a chat app, then the launcher.
        assertEquals(ForegroundTracker.Current("game.a", 0), t.update(10_000, "com.whatsapp", linger, watched))
        assertEquals(ForegroundTracker.Current("game.a", 0), t.update(20_000, "com.android.launcher", linger, watched))
        // Back within the window: same session.
        assertEquals(ForegroundTracker.Current("game.a", 0), t.update(25_000, "game.a", linger, watched))
    }

    @Test fun expiresAfterLinger() {
        val t = ForegroundTracker()
        t.update(0, "game.a", linger, watched)
        assertEquals("game.a", t.update(15_000, null, linger, watched)?.packageName)
        assertNull(t.update(15_001, null, linger, watched))
        // Coming back later is a new session.
        assertEquals(ForegroundTracker.Current("game.a", 40_000), t.update(40_000, "game.a", linger, watched))
    }

    @Test fun switchingGamesShowsTheForegroundOne() {
        val t = ForegroundTracker()
        t.update(0, "game.a", linger, watched)
        assertEquals(ForegroundTracker.Current("game.b", 5_000), t.update(5_000, "game.b", linger, watched))
        assertEquals(ForegroundTracker.Current("game.a", 0), t.update(8_000, "game.a", linger, watched))
    }

    @Test fun unwatchedIsDroppedImmediately() {
        val t = ForegroundTracker()
        t.update(0, "game.a", linger, watched)
        assertNull(t.update(1_000, "x", linger) { false })
    }

    @Test fun reducerLastResumedWins() {
        val r = ForegroundReducer.RESUMED
        val p = ForegroundReducer.PAUSED
        assertEquals("b", ForegroundReducer.reduce("a", listOf(p to "a", r to "b")))
        // Late pause of the previous app doesn't clear the new one.
        assertEquals("b", ForegroundReducer.reduce("a", listOf(r to "b", p to "a")))
        assertNull(ForegroundReducer.reduce("a", listOf(p to "a")))
        assertEquals("a", ForegroundReducer.reduce("a", emptyList()))
        assertEquals("launcher", ForegroundReducer.reduce("game", listOf(p to "game", r to "launcher")))
    }
}
