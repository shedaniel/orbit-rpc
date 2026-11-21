package me.shedaniel.discordrpc.ui.components

import java.util.Locale

fun formatBytes(bytes: Long): String {
    val b = bytes.coerceAtLeast(0).toDouble()
    return when {
        b < 1000 -> "${bytes.coerceAtLeast(0)} B"
        b < 1000 * 1000 -> String.format(Locale.US, if (b < 10_000) "%.1f kB" else "%.0f kB", b / 1000)
        b < 1000.0 * 1000 * 1000 -> String.format(Locale.US, if (b < 10_000_000) "%.2f MB" else "%.1f MB", b / 1e6)
        else -> String.format(Locale.US, "%.2f GB", b / 1e9)
    }
}
