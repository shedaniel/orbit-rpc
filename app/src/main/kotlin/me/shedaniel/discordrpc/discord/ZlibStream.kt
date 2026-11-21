package me.shedaniel.discordrpc.discord

import java.io.ByteArrayOutputStream
import java.util.zip.DataFormatException
import java.util.zip.Inflater

/**
 * Discord `compress=zlib-stream` transport decompression: one zlib context for the whole connection;
 * each logical message ends with a sync flush (`00 00 FF FF`). A message may span several websocket
 * frames, so input is buffered until the suffix shows up.
 *
 * Not thread-safe; feed it from the socket's reader thread only.
 */
class ZlibStreamInflater {
    private val inflater = Inflater()
    private val pending = ByteArrayOutputStream()
    private val out = ByteArrayOutputStream(64 * 1024)
    private val chunk = ByteArray(32 * 1024)

    /** One decoded message plus its compressed size. */
    class Decoded(val text: String, val compressedBytes: Int, val uncompressedBytes: Int)

    /** Feeds one websocket binary message. Returns the decoded message, or null when more data is needed. */
    @Throws(DataFormatException::class)
    fun feed(bytes: ByteArray): Decoded? {
        val input: ByteArray
        if (pending.size() == 0 && endsWithFlush(bytes)) {
            input = bytes
        } else {
            pending.write(bytes)
            if (!endsWithFlush(bytes, pending)) return null
            input = pending.toByteArray()
            pending.reset()
        }
        inflater.setInput(input)
        out.reset()
        while (true) {
            val n = inflater.inflate(chunk)
            if (n > 0) {
                out.write(chunk, 0, n)
                continue
            }
            if (inflater.needsInput() || inflater.finished()) break
            if (inflater.needsDictionary()) throw DataFormatException("zlib dictionary required")
        }
        val size = out.size()
        return Decoded(out.toString(Charsets.UTF_8.name()), input.size, size)
    }

    fun close() {
        runCatching { inflater.end() }
    }

    private fun endsWithFlush(last: ByteArray, all: ByteArrayOutputStream? = null): Boolean {
        if (last.size >= 4) {
            val n = last.size
            return last[n - 4] == 0.toByte() && last[n - 3] == 0.toByte() && last[n - 2] == 0xFF.toByte() && last[n - 1] == 0xFF.toByte()
        }
        // Suffix split across frames (tiny trailing frame): check the combined buffer.
        val b = all?.toByteArray() ?: return false
        val n = b.size
        return n >= 4 && b[n - 4] == 0.toByte() && b[n - 3] == 0.toByte() && b[n - 2] == 0xFF.toByte() && b[n - 1] == 0xFF.toByte()
    }
}
