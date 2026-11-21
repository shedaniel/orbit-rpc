package me.shedaniel.discordrpc.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import me.shedaniel.discordrpc.graph

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                val s = context.graph.settings.value
                if (s.enabled && s.onboarded) PresenceService.start(context)
            }
        }
    }
}
