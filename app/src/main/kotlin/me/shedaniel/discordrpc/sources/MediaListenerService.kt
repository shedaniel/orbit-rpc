package me.shedaniel.discordrpc.sources

import android.service.notification.NotificationListenerService
import me.shedaniel.discordrpc.graph

/**
 * Exists so the user can grant notification access, which unlocks MediaSessionManager.getActiveSessions.
 * We never read notification content.
 */
class MediaListenerService : NotificationListenerService() {
    /** Bound by the system right after access is granted (and after boot): pick up sessions now. */
    override fun onListenerConnected() {
        super.onListenerConnected()
        applicationContext.graph.mediaSource.refresh()
    }
}
