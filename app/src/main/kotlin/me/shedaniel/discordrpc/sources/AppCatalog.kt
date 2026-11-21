package me.shedaniel.discordrpc.sources

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import me.shedaniel.discordrpc.core.util.EventLog
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Installed apps: launchable list with categories, labels, icons, and which apps have ever
 * published a media session. Loads off the main thread and refreshes on package changes.
 */
class AppCatalog(context: Context, private val scope: CoroutineScope) {
    private val app = context.applicationContext
    private val pm: PackageManager = app.packageManager

    private val _apps = MutableStateFlow<List<InstalledApp>>(emptyList())
    /** Launchable apps, sorted by label. Refreshes on package changes. */
    val apps: StateFlow<List<InstalledApp>> = _apps.asStateFlow()

    private val _mediaApps = MutableStateFlow<Set<String>>(emptySet())
    /** Packages that have ever published a media session (persisted). */
    val mediaApps: StateFlow<Set<String>> = _mediaApps.asStateFlow()

    @Volatile private var byPackage: Map<String, InstalledApp> = emptyMap()
    private val labels = ConcurrentHashMap<String, String>()
    private val icons = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    private val mediaFile = File(app.filesDir, "media_apps.json")
    private val mediaMutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private var reloadJob: Job? = null

    init {
        scope.launch(Dispatchers.IO) {
            loadMediaApps()
            reload()
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(app, object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                i.data?.schemeSpecificPart?.let { pkg ->
                    labels.remove(pkg)
                    synchronized(icons) { icons.snapshot().keys.filter { it.startsWith("$pkg@") }.forEach { icons.remove(it) } }
                }
                scheduleReload()
            }
        }, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    /** Coalesces bursts (an update fires REMOVED + ADDED + REPLACED). */
    private fun scheduleReload() {
        reloadJob?.cancel()
        reloadJob = scope.launch(Dispatchers.IO) {
            delay(500)
            reload()
        }
    }

    fun refresh() = scheduleReload()

    private fun reload() {
        try {
            val versions = runCatching {
                pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0)).associate { it.packageName to it.longVersionCode }
            }.getOrDefault(emptyMap())
            val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val list = pm.queryIntentActivities(launcher, PackageManager.ResolveInfoFlags.of(0))
                .map { it.activityInfo.applicationInfo }
                .distinctBy { it.packageName }
                .filter { it.packageName != app.packageName }
                .map { ai ->
                    val label = ai.loadLabel(pm).toString().ifBlank { ai.packageName }
                    labels[ai.packageName] = label
                    toInstalled(ai, label, versions[ai.packageName] ?: 0L)
                }
                .sortedBy { it.label.lowercase() }
            byPackage = list.associateBy { it.packageName }
            _apps.value = list
        } catch (t: Throwable) {
            EventLog.w("Catalog", "Couldn't list apps", t)
        }
    }

    private fun toInstalled(ai: ApplicationInfo, label: String, version: Long): InstalledApp {
        @Suppress("DEPRECATION")
        val flagGame = ai.flags and ApplicationInfo.FLAG_IS_GAME != 0
        val declared = when (ai.category) {
            ApplicationInfo.CATEGORY_AUDIO -> AppCategory.MUSIC
            ApplicationInfo.CATEGORY_VIDEO -> AppCategory.VIDEO
            ApplicationInfo.CATEGORY_GAME -> AppCategory.GAME
            else -> if (flagGame) AppCategory.GAME else AppCategory.OTHER
        }
        val known = KnownApps.category(ai.packageName)
        val category = known ?: declared
        return InstalledApp(
            packageName = ai.packageName,
            label = label,
            category = category,
            isGame = category == AppCategory.GAME,
            isSystem = ai.flags and ApplicationInfo.FLAG_SYSTEM != 0,
            versionCode = version,
        )
    }

    fun get(packageName: String): InstalledApp? = byPackage[packageName]

    /** User-visible label of any installed package (launchable or not); the package name if unknown. */
    fun label(packageName: String): String {
        labels[packageName]?.let { return it }
        val l = try {
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            return packageName // not cached: it may get installed later
        }.ifBlank { packageName }
        labels[packageName] = l
        return l
    }

    /**
     * Launcher icon rendered to a [sizePx] square bitmap. Adaptive icons are drawn with their full
     * background + foreground layers and no mask. Cached; call off the main thread when possible.
     */
    fun icon(packageName: String, sizePx: Int): Bitmap? {
        val key = "$packageName@$sizePx"
        synchronized(icons) { icons.get(key) }?.let { return it }
        val d = try {
            pm.getApplicationIcon(packageName)
        } catch (_: PackageManager.NameNotFoundException) {
            return null
        }
        val bmp = runCatching { render(d, sizePx.coerceIn(16, 1024)) }.getOrNull() ?: return null
        synchronized(icons) { icons.put(key, bmp) }
        return bmp
    }

    private fun render(d: Drawable, size: Int): Bitmap {
        if (d is BitmapDrawable && d.bitmap != null && d.bitmap.width == size && d.bitmap.height == size) return d.bitmap
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        if (d is AdaptiveIconDrawable) {
            for (layer in listOfNotNull(d.background, d.foreground)) {
                layer.setBounds(0, 0, size, size)
                layer.draw(c)
            }
        } else {
            d.setBounds(0, 0, size, size)
            d.draw(c)
        }
        return bmp
    }

    fun markMediaApp(packageName: String) {
        if (packageName in _mediaApps.value) return
        _mediaApps.update { it + packageName }
        scope.launch(Dispatchers.IO) { saveMediaApps() }
    }

    private suspend fun loadMediaApps() = mediaMutex.withLock {
        val loaded = runCatching {
            if (mediaFile.exists()) json.decodeFromString(SetSerializer(String.serializer()), mediaFile.readText()) else emptySet()
        }.getOrElse {
            EventLog.w("Catalog", "media_apps.json unreadable, starting fresh", it)
            emptySet()
        }
        _mediaApps.update { it + loaded }
    }

    private suspend fun saveMediaApps() = mediaMutex.withLock {
        runCatching {
            val tmp = File(mediaFile.parentFile, mediaFile.name + ".tmp")
            tmp.writeText(json.encodeToString(SetSerializer(String.serializer()), _mediaApps.value))
            tmp.renameTo(mediaFile)
        }.onFailure { EventLog.w("Catalog", "Couldn't save media apps", it) }
    }
}
