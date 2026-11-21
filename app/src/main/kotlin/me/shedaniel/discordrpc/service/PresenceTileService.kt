package me.shedaniel.discordrpc.service

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import me.shedaniel.discordrpc.R
import me.shedaniel.discordrpc.graph

class PresenceTileService : TileService() {
    override fun onStartListening() = refresh()

    override fun onClick() {
        val g = graph
        g.settingsStore.update { it.copy(enabled = !it.enabled) }
        if (g.settingsStore.value.enabled) PresenceService.start(this) else PresenceService.stop(this)
        refresh()
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val on = graph.settings.value.enabled
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.app_name)
        tile.subtitle = getString(if (on) R.string.tile_on else R.string.tile_off)
        tile.updateTile()
    }
}
