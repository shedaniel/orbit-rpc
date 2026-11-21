package me.shedaniel.discordrpc.sources

import android.service.notification.NotificationListenerService

/**
 * Exists so the user can grant notification access, which unlocks MediaSessionManager.getActiveSessions.
 * We never read notification content.
 */
class MediaListenerService : NotificationListenerService()
