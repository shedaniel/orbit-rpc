package me.shedaniel.discordrpc.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import me.shedaniel.discordrpc.MainActivity
import me.shedaniel.discordrpc.R
import me.shedaniel.discordrpc.core.model.ActivityType
import me.shedaniel.discordrpc.core.model.ConnectionState
import me.shedaniel.discordrpc.core.model.PauseReason
import me.shedaniel.discordrpc.core.util.EventLog
import me.shedaniel.discordrpc.graph

/**
 * Keeps the process alive while broadcasting is enabled and shows what's being shared.
 * The heavy lifting is in [PresenceEngine]; this only owns its lifecycle.
 */
class PresenceService : LifecycleService() {

    override fun onCreate() {
        super.onCreate()
        ensureChannel(this)
        startInForeground(buildNotification(getString(R.string.notif_idle_title), null))
        val g = graph
        g.engine.start()
        lifecycleScope.launch {
            g.engine.state.map { notificationContent(it) }.distinctUntilChanged().collect { (title, text) ->
                getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(title, text))
            }
        }
        lifecycleScope.launch {
            g.settings.map { it.enabled }.distinctUntilChanged().collect { enabled ->
                if (!enabled) stopSelf()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            graph.settingsStore.update { it.copy(enabled = false) }
            stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        graph.engine.stop()
        super.onDestroy()
    }

    private fun startInForeground(n: Notification) {
        ServiceCompat.startForeground(this, NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
    }

    private fun notificationContent(s: EngineState): Pair<String, String?> {
        val a = s.activities.firstOrNull()
        return when {
            a != null && s.connection is ConnectionState.Connected || a != null && s.connection is ConnectionState.Connecting -> {
                val verb = getString(
                    when (a!!.type) {
                        ActivityType.LISTENING -> R.string.verb_listening
                        ActivityType.WATCHING -> R.string.verb_watching
                        ActivityType.STREAMING -> R.string.verb_streaming
                        ActivityType.COMPETING -> R.string.verb_competing
                        ActivityType.PLAYING -> R.string.verb_playing
                    }, a.name
                )
                verb to listOfNotNull(a.details, a.state).joinToString(" · ").ifBlank { null }
            }
            s.connection is ConnectionState.Paused -> getString(R.string.notif_paused_title) to getString(pauseText((s.connection as ConnectionState.Paused).reason))
            s.connection is ConnectionState.Error -> getString(R.string.notif_error_title) to (s.connection as ConnectionState.Error).message
            s.connection is ConnectionState.LoggedOut -> getString(R.string.notif_logged_out) to null
            else -> getString(R.string.notif_idle_title) to null
        }
    }

    private fun pauseText(r: PauseReason) = when (r) {
        PauseReason.DISABLED -> R.string.pause_disabled
        PauseReason.NO_NETWORK -> R.string.pause_no_network
        PauseReason.CELLULAR_BLOCKED -> R.string.pause_cellular
        PauseReason.ROAMING_BLOCKED -> R.string.pause_roaming
        PauseReason.DATA_SAVER -> R.string.pause_data_saver
        PauseReason.TOKEN_INVALID -> R.string.pause_token
    }

    private fun buildNotification(title: String, text: String?): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, PresenceService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_orbit)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setContentIntent(open)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, getString(R.string.notif_action_stop), stop)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "presence"
        const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "me.shedaniel.discordrpc.STOP"

        fun ensureChannel(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, context.getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW).apply {
                        setShowBadge(false)
                    }
                )
            }
        }

        /** Returns false when the system refused (e.g. background FGS start restrictions on Android 12+). */
        fun start(context: Context): Boolean = try {
            ContextCompat.startForegroundService(context, Intent(context, PresenceService::class.java))
            true
        } catch (e: Exception) {
            EventLog.w("Service", "Could not start the foreground service", e)
            false
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PresenceService::class.java))
        }

        /** Start or stop the service to match the master switch. */
        fun sync(context: Context) {
            if (context.graph.settings.value.enabled) start(context) else stop(context)
        }
    }
}
