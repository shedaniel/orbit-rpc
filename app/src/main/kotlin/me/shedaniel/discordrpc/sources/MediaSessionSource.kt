package me.shedaniel.discordrpc.sources

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import me.shedaniel.discordrpc.core.model.Signal
import me.shedaniel.discordrpc.core.settings.AppSettings

// STUB — implemented by the sources agent.
class MediaSessionSource(context: Context, catalog: AppCatalog, settings: StateFlow<AppSettings>, scope: CoroutineScope) : SignalSource {
    override val signals: StateFlow<List<Signal>> = MutableStateFlow(emptyList())
    override fun start() {}
    override fun stop() {}
}
