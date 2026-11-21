package me.shedaniel.discordrpc.core

import me.shedaniel.discordrpc.core.model.ActivityType
import me.shedaniel.discordrpc.core.model.AppSignal
import me.shedaniel.discordrpc.core.model.ImageRef
import me.shedaniel.discordrpc.core.model.MediaKind
import me.shedaniel.discordrpc.core.model.MediaSignal
import me.shedaniel.discordrpc.core.model.PauseReason
import me.shedaniel.discordrpc.core.model.SignalOrigin
import me.shedaniel.discordrpc.core.rules.DefaultRules
import me.shedaniel.discordrpc.core.rules.RulesEngine
import me.shedaniel.discordrpc.core.rules.Template
import me.shedaniel.discordrpc.core.rules.Trigger
import me.shedaniel.discordrpc.core.settings.NetworkSettings
import me.shedaniel.discordrpc.net.NetworkConditions
import me.shedaniel.discordrpc.net.Transport
import me.shedaniel.discordrpc.service.profileFor
import me.shedaniel.discordrpc.service.sameAs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RulesTest {
    private fun media(kind: MediaKind, playing: Boolean = true, pkg: String = "com.spotify.music", since: Long = 1000) = MediaSignal(
        id = "media:$pkg", origin = SignalOrigin.MEDIA_SESSION, packageName = pkg, appLabel = "Spotify", since = since,
        title = "Song", artist = "Artist", album = null, artwork = null, durationMs = 200_000, positionMs = 50_000,
        positionAtMs = 100_000, playing = playing, kind = kind,
    )

    @Test fun templatePlaceholdersAndOptionalSections() {
        val v = mapOf("title" to "Song", "artist" to "Artist", "album" to null)
        assertEquals("Song by Artist", Template.render("{title} [by {artist}]", v))
        assertEquals("Song", Template.render("{title} [on {album}]", v))
        assertEquals("[x]", Template.render("\\[x\\]", v))
        assertEquals("", Template.render("{album}", v))
        assertEquals("a b", Template.render("  a   b ", v))
    }

    @Test fun musicGoesToListeningVideoToWatching() {
        val m = RulesEngine.evaluate(DefaultRules.all, listOf(media(MediaKind.MUSIC)), 1).single()
        assertEquals(DefaultRules.MUSIC, m.rule.id)
        val a = RulesEngine.render(m.rule, m.signal, null, 0)!!
        assertEquals(ActivityType.LISTENING, a.type)
        assertEquals("Spotify", a.name)
        assertEquals("Song", a.details)
        assertEquals(200_000L, a.endMs!! - a.startMs!!) // progress window = duration
        assertEquals(50_000L, 100_000L - a.startMs!!)
        assertTrue(a.largeImage is ImageRef.Media)

        val v = RulesEngine.evaluate(DefaultRules.all, listOf(media(MediaKind.VIDEO, pkg = "com.google.android.youtube")), 1).single()
        assertEquals(ActivityType.WATCHING, RulesEngine.render(v.rule, v.signal, null, 0)!!.type)
    }

    @Test fun pausedMediaDoesNotMatchByDefault() {
        assertTrue(RulesEngine.evaluate(DefaultRules.all, listOf(media(MediaKind.MUSIC, playing = false)), 1).isEmpty())
    }

    @Test fun priorityOrderWinsAndMultipleActivitiesUseDistinctSignals() {
        val game = AppSignal("app:g", "com.game", "Game", 5, isGame = true)
        val music = media(MediaKind.MUSIC)
        val one = RulesEngine.evaluate(DefaultRules.all, listOf(music, game), 1)
        assertEquals(DefaultRules.GAMES, one.single().rule.id)
        val two = RulesEngine.evaluate(DefaultRules.all, listOf(music, game), 4)
        assertEquals(listOf(DefaultRules.GAMES, DefaultRules.MUSIC), two.map { it.rule.id })
    }

    @Test fun alwaysRuleIsAFallback() {
        val always = DefaultRules.games.copy(id = "a", trigger = Trigger.Always, template = DefaultRules.games.template.copy(name = "Vibing"))
        val rules = listOf(always) + DefaultRules.all
        assertEquals("a", RulesEngine.evaluate(rules, emptyList(), 1).single().rule.id)
        assertEquals(DefaultRules.MUSIC, RulesEngine.evaluate(rules, listOf(media(MediaKind.MUSIC)), 1).single().rule.id)
    }

    @Test fun emptyNameIsRejected() {
        val r = DefaultRules.games.copy(template = DefaultRules.games.template.copy(name = "{title}"))
        assertNull(RulesEngine.render(r, AppSignal("x", "p", "P", 0, true), null, 0))
    }

    @Test fun timestampJitterIsIgnored() {
        val m = RulesEngine.evaluate(DefaultRules.all, listOf(media(MediaKind.MUSIC)), 1).single()
        val a = RulesEngine.render(m.rule, m.signal, null, 0)!!
        assertTrue(a.sameAs(a.copy(startMs = a.startMs!! + 900, endMs = a.endMs!! + 900)))
        assertFalse(a.sameAs(a.copy(startMs = a.startMs!! + 5000)))
        assertFalse(a.sameAs(a.copy(details = "Other")))
    }

    @Test fun networkProfiles() {
        val n = NetworkSettings()
        val wifi = NetworkConditions(true, Transport.WIFI, false, false, false)
        val cell = NetworkConditions(true, Transport.CELLULAR, true, false, false)
        assertEquals(n.wifi, n.profileFor(wifi).first)
        assertEquals(n.cellular, n.profileFor(cell).first)
        assertEquals(PauseReason.ROAMING_BLOCKED, n.profileFor(cell.copy(roaming = true)).second)
        assertEquals(PauseReason.CELLULAR_BLOCKED, n.copy(cellular = n.cellular.copy(enabled = false)).profileFor(cell).second)
        val saver = n.profileFor(cell.copy(dataSaver = true)).first!!
        assertFalse(saver.artworkLookup || saver.uploadImages || saver.appIcons || saver.spotifyConnect)
        assertEquals(PauseReason.NO_NETWORK, n.profileFor(wifi.copy(connected = false)).second)
    }
}
