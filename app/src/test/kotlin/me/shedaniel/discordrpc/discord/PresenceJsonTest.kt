package me.shedaniel.discordrpc.discord

import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import me.shedaniel.discordrpc.core.model.ActivityType
import me.shedaniel.discordrpc.core.model.PresenceActivity
import me.shedaniel.discordrpc.core.model.PresenceButton
import me.shedaniel.discordrpc.core.model.StatusDisplay
import me.shedaniel.discordrpc.core.settings.OnlineStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PresenceJsonTest {
    private fun act(
        name: String = "Spotify",
        details: String? = null,
        state: String? = null,
        type: ActivityType = ActivityType.LISTENING,
        buttons: List<PresenceButton> = emptyList(),
        start: Long? = null,
        end: Long? = null,
        largeText: String? = null,
        url: String? = null,
        statusDisplay: StatusDisplay = StatusDisplay.NAME,
    ) = PresenceActivity(
        ruleId = "r", signalId = "s", type = type, name = name, details = details, state = state, largeText = largeText,
        startMs = start, endMs = end, buttons = buttons, url = url, statusDisplay = statusDisplay,
    )

    @Test
    fun dropsBlankAndNullFields() {
        val o = PresenceJson.activity(ResolvedActivity(act(details = "  ", state = null, largeText = "Album"), null, null), "")
        assertEquals(setOf("name", "type"), o.keys)
        assertEquals(2, o["type"]!!.jsonPrimitive.int)
    }

    @Test
    fun enforcesLimitsAndPads() {
        val long = "x".repeat(300)
        val o = PresenceJson.activity(ResolvedActivity(act(details = long, state = "a", largeText = "b"), "mp:external/abc", null), "962990036020756480")
        val details = o["details"]!!.jsonPrimitive.content
        assertEquals(128, details.length)
        assertTrue(details.endsWith("…"))
        assertEquals(2, o["state"]!!.jsonPrimitive.content.length)
        val assets = o["assets"]!!.jsonObject
        assertEquals("mp:external/abc", assets["large_image"]!!.jsonPrimitive.content)
        assertEquals(2, assets["large_text"]!!.jsonPrimitive.content.length)
        assertEquals("962990036020756480", o["application_id"]!!.jsonPrimitive.content)
    }

    @Test
    fun truncationDoesNotSplitSurrogates() {
        val s = "a".repeat(126) + "😀😀"
        val t = PresenceJson.text(s)!!
        assertTrue(t.length <= 128)
        assertTrue(t.endsWith("…"))
        assertFalse(Character.isHighSurrogate(t[t.length - 2]))
    }

    @Test
    fun buttonsTimestampsAndStatusDisplay() {
        val o = PresenceJson.activity(
            ResolvedActivity(
                act(
                    details = "Song", state = "Artist", start = 1000, end = 500, statusDisplay = StatusDisplay.STATE,
                    buttons = listOf(
                        PresenceButton("Listen on Spotify with a very long label indeed", "https://open.spotify.com/track/1"),
                        PresenceButton("bad", "javascript:alert(1)"),
                        PresenceButton("Two", "https://b"),
                        PresenceButton("Three", "https://c"),
                    ),
                ),
                null, null,
            ),
            "not-a-snowflake",
        )
        assertNull(o["application_id"])
        assertEquals(1, o["status_display_type"]!!.jsonPrimitive.int)
        val ts = o["timestamps"]!!.jsonObject
        assertEquals(1000L, ts["start"]!!.jsonPrimitive.long)
        assertNull(ts["end"]) // end before start is dropped
        val labels = o["buttons"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertEquals(2, labels.size)
        assertEquals(32, labels[0].length)
        assertEquals("Two", labels[1])
        val urls = o["metadata"]!!.jsonObject["button_urls"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertEquals(listOf("https://open.spotify.com/track/1", "https://b"), urls)
    }

    @Test
    fun streamingUrlOnlyForStreaming() {
        val s = PresenceJson.activity(ResolvedActivity(act(type = ActivityType.STREAMING, url = "https://twitch.tv/x"), null, null), "")
        assertEquals("https://twitch.tv/x", s["url"]!!.jsonPrimitive.content)
        val p = PresenceJson.activity(ResolvedActivity(act(type = ActivityType.PLAYING, url = "https://twitch.tv/x"), null, null), "")
        assertNull(p["url"])
    }

    @Test
    fun presenceEnvelope() {
        val p = PresenceJson.presence(emptyList(), OnlineStatus.DND, true, "")
        assertEquals("""{"since":0,"activities":[],"status":"dnd","afk":true}""", p.toString())
    }

    @Test
    fun tokenResultParsing() {
        assertEquals("abcdefghijklmnopqrstuvwxyz.123", parseTokenResult("\"\\\"abcdefghijklmnopqrstuvwxyz.123\\\"\""))
        assertEquals("abcdefghijklmnopqrstuvwxyz.123", parseTokenResult("\"abcdefghijklmnopqrstuvwxyz.123\""))
        assertNull(parseTokenResult("null"))
        assertNull(parseTokenResult(null))
    }
}
