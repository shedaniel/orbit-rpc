package me.shedaniel.discordrpc.discord

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater

/** Server-side zlib-stream: one deflater, SYNC_FLUSH after each message. */
class ZlibStreamDeflater {
    private val deflater = Deflater()
    private val buf = ByteArray(8192)

    fun compress(text: String): ByteArray {
        deflater.setInput(text.encodeToByteArray())
        val out = ByteArrayOutputStream()
        while (true) {
            val n = deflater.deflate(buf, 0, buf.size, Deflater.SYNC_FLUSH)
            out.write(buf, 0, n)
            if (n < buf.size) break
        }
        return out.toByteArray()
    }
}

class ZlibStreamInflaterTest {
    @Test
    fun decodesSequentialMessagesSharingOneContext() {
        val d = ZlibStreamDeflater()
        val inf = ZlibStreamInflater()
        val messages = listOf(
            """{"op":10,"d":{"heartbeat_interval":41250}}""",
            """{"op":11}""",
            """{"op":0,"t":"READY","s":1,"d":{"session_id":"abc","user":{"id":"1","username":"x"}}}""",
            """{"op":11}""", // repeated content compresses to back-references into earlier messages
        )
        val sizes = messages.map { m ->
            val c = d.compress(m)
            val r = inf.feed(c)!!
            assertEquals(m, r.text)
            assertEquals(c.size, r.compressedBytes)
            c.size
        }
        // The second {"op":11} must be no larger than the first thanks to the shared window.
        assertTrue(sizes[3] <= sizes[1])
    }

    @Test
    fun buffersMessagesSplitAcrossFrames() {
        val d = ZlibStreamDeflater()
        val inf = ZlibStreamInflater()
        val big = buildString { repeat(5000) { append("{\"guild\":$it,\"name\":\"g$it\"},") } }
        val c = d.compress(big)
        val parts = listOf(c.copyOfRange(0, c.size / 3), c.copyOfRange(c.size / 3, c.size - 2), c.copyOfRange(c.size - 2, c.size))
        assertNull(inf.feed(parts[0]))
        assertNull(inf.feed(parts[1]))
        val r = inf.feed(parts[2])!!
        assertEquals(big, r.text)
        assertEquals(c.size, r.compressedBytes)
        // And the stream continues to work afterwards.
        assertEquals("""{"op":11}""", inf.feed(d.compress("""{"op":11}"""))!!.text)
    }
}
