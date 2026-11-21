package me.shedaniel.discordrpc.sources

import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri

/**
 * Special-access checks and the settings screens that grant them.
 *
 * Android 13+ blocks notification access (and accessibility) for apps sideloaded from a file
 * ("restricted settings"). The toggle is greyed out until the user opens the app info page, taps
 * the overflow menu and picks "Allow restricted settings". [mayBeRestricted] detects that case and
 * [appDetailsIntent] opens the right page.
 */
object Permissions {
    fun hasNotificationAccess(context: Context): Boolean =
        context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

    fun hasUsageAccess(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return if (mode == AppOpsManager.MODE_DEFAULT) {
            context.checkCallingOrSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else mode == AppOpsManager.MODE_ALLOWED
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

    fun listenerComponent(context: Context) = ComponentName(context, MediaListenerService::class.java)

    /** The per-app notification listener page, falling back to the list of listeners. */
    fun notificationAccessIntent(context: Context): Intent {
        val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, listenerComponent(context).flattenToString())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return if (detail.resolveActivity(context.packageManager) != null) detail
        else Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** Usage access for this app directly where the platform supports it, else the list. */
    fun usageAccessIntent(context: Context): Intent {
        val direct = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, "package:${context.packageName}".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return if (direct.resolveActivity(context.packageManager) != null) direct
        else Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** System dialog asking to exempt us from battery optimizations (permission is declared). */
    @SuppressLint("BatteryLife")
    fun batteryOptimizationIntent(context: Context): Intent {
        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:${context.packageName}".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return if (direct.resolveActivity(context.packageManager) != null) direct
        else Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** App info page; where "Allow restricted settings" lives (overflow menu) on Android 13+. */
    fun appDetailsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * True when Android may be blocking notification access because the app was sideloaded
     * (Android 13+ restricted settings). Only meaningful while access is not yet granted.
     */
    fun mayBeRestricted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        // The appop backing "Allow restricted settings" is not public; try it, then fall back to the install source.
        val ops = context.getSystemService(AppOpsManager::class.java)
        runCatching {
            val mode = ops.unsafeCheckOpNoThrow("android:access_restricted_settings", Process.myUid(), context.packageName)
            return mode != AppOpsManager.MODE_ALLOWED && mode != AppOpsManager.MODE_DEFAULT
        }
        return runCatching {
            val src = context.packageManager.getInstallSourceInfo(context.packageName).packageSource
            src == PackageInstaller.PACKAGE_SOURCE_LOCAL_FILE || src == PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE
        }.getOrDefault(false)
    }
}
