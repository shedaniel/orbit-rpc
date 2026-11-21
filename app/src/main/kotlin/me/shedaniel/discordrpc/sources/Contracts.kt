package me.shedaniel.discordrpc.sources

import kotlinx.coroutines.flow.StateFlow
import me.shedaniel.discordrpc.core.model.MediaKind
import me.shedaniel.discordrpc.core.model.Signal

/** A source of live signals. Must be cheap while idle (no polling with the screen off). */
interface SignalSource {
    val signals: StateFlow<List<Signal>>
    fun start()
    fun stop()
}

/** Rough category of an installed app, used for defaults and classification. */
enum class AppCategory { MUSIC, VIDEO, PODCAST, GAME, OTHER }

data class InstalledApp(
    val packageName: String,
    val label: String,
    val category: AppCategory,
    val isGame: Boolean,
    val isSystem: Boolean,
    val versionCode: Long,
) {
    val defaultKind: MediaKind
        get() = when (category) {
            AppCategory.MUSIC -> MediaKind.MUSIC
            AppCategory.VIDEO -> MediaKind.VIDEO
            AppCategory.PODCAST -> MediaKind.PODCAST
            else -> MediaKind.UNKNOWN
        }
}
