package me.shedaniel.discordrpc.sources

import android.content.Context
import android.content.Intent
import android.provider.Settings

// STUB — implemented by the sources agent.
object Permissions {
    fun hasNotificationAccess(context: Context): Boolean = false
    fun hasUsageAccess(context: Context): Boolean = false
    fun notificationAccessIntent(context: Context): Intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    fun usageAccessIntent(context: Context): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
}
