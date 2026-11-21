package me.shedaniel.discordrpc.sources

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// STUB — implemented by the sources agent.
class AppCatalog(context: Context, scope: CoroutineScope) {
    /** Launchable apps, sorted by label. Refreshes on package changes. */
    val apps: StateFlow<List<InstalledApp>> = MutableStateFlow(emptyList())
    /** Packages that have ever published a media session (persisted). */
    val mediaApps: StateFlow<Set<String>> = MutableStateFlow(emptySet())
    fun markMediaApp(packageName: String) {}
    fun label(packageName: String): String = packageName
    fun get(packageName: String): InstalledApp? = null
    fun icon(packageName: String, sizePx: Int): Bitmap? = null
}
