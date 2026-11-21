package me.shedaniel.discordrpc.ui.screens

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Api
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Cable
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.DataSaverOn
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.RoundedPolygon
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.shedaniel.discordrpc.R
import me.shedaniel.discordrpc.core.settings.NetworkProfile
import me.shedaniel.discordrpc.graph
import me.shedaniel.discordrpc.net.DailyUsage
import me.shedaniel.discordrpc.net.TrafficCategory
import me.shedaniel.discordrpc.net.Transport
import me.shedaniel.discordrpc.ui.components.Choice
import me.shedaniel.discordrpc.ui.components.ChoiceRow
import me.shedaniel.discordrpc.ui.components.SettingsGroup
import me.shedaniel.discordrpc.ui.components.ShapeGlyph
import me.shedaniel.discordrpc.ui.components.StackedBar
import me.shedaniel.discordrpc.ui.components.StackedBarChart
import me.shedaniel.discordrpc.ui.components.SwitchItem
import me.shedaniel.discordrpc.ui.components.formatBytes
import me.shedaniel.discordrpc.ui.components.rememberSpin
import me.shedaniel.discordrpc.ui.theme.NumberFamily
import me.shedaniel.discordrpc.ui.theme.rememberHaptics
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private const val DAYS = 14

@Composable
fun DataScreen(contentPadding: PaddingValues) {
    val graph = LocalContext.current.graph
    val haptics = rememberHaptics()
    val history by graph.networkMeter.history.collectAsStateWithLifecycle()
    val conditions by graph.networkMonitor.conditions.collectAsStateWithLifecycle()
    val settings by graph.settings.collectAsStateWithLifecycle()
    // Pull the kernel's counters so the cross-check row is current.
    androidx.compose.runtime.LaunchedEffect(Unit) { graph.networkMeter.sample() }

    // Day keys, oldest -> today. Re-evaluated as history ticks so a midnight rollover is picked up.
    val todayKey = history.lastOrNull()?.day ?: LocalDate.now().toString()
    val days = remember(todayKey) {
        val today = runCatching { LocalDate.parse(todayKey) }.getOrElse { LocalDate.now() }
        (DAYS - 1 downTo 0).map { today.minusDays(it.toLong()) }
    }
    val usages = remember(days, history) {
        val byDay = history.associateBy { it.day }
        days.map { byDay[it.toString()] ?: DailyUsage(it.toString()) }
    }
    var selected by rememberSaveable { mutableIntStateOf(DAYS - 1) }
    var filter by rememberSaveable { mutableStateOf<Transport?>(null) }
    val day = usages[selected.coerceIn(0, DAYS - 1)]
    val isToday = selected == DAYS - 1

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.data_title)) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { inner ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = inner.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding() + 24.dp,
            ),
        ) {
            item(key = "hero") {
                DayHero(
                    day = day,
                    dayLabel = dayLabel(days[selected], isToday),
                    dayKey = selected,
                    live = if (isToday && conditions.connected) conditions.transport else null,
                    filter = filter,
                    onFilter = { t -> haptics.tick(); filter = if (filter == t) null else t },
                )
            }
            item(key = "chart") {
                val labels = remember(days) { days.map { it.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()) } }
                val bars = remember(usages) {
                    usages.map { u ->
                        val cell = u.total(Transport.CELLULAR).total
                        StackedBar(lower = cell, upper = u.total().total - cell)
                    }
                }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    StackedBarChart(
                        bars = bars,
                        labels = labels,
                        selected = selected,
                        onSelect = { if (it != selected) { haptics.tick(); selected = it } },
                        lowerColor = MaterialTheme.colorScheme.primary,
                        upperColor = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp),
                    )
                }
            }
            item(key = "breakdown") {
                Breakdown(day, filter, selected) { haptics.tick(); filter = it }
            }
            item(key = "crosscheck") {
                CrossCheck(day, filter)
            }
            item(key = "policy") {
                Policy(
                    wifi = settings.network.wifi,
                    cellular = settings.network.cellular,
                    initial = if (conditions.transport == Transport.CELLULAR) Transport.CELLULAR else Transport.WIFI,
                    onUpdate = { t, f ->
                        graph.settingsStore.update { s ->
                            s.copy(
                                network = if (t == Transport.WIFI) s.network.copy(wifi = f(s.network.wifi))
                                else s.network.copy(cellular = f(s.network.cellular)),
                            )
                        }
                    },
                )
                SettingsGroup(null, Modifier.padding(top = 12.dp)) {
                    item {
                        SwitchItem(
                            it, stringResource(R.string.data_roaming), settings.network.pauseWhenRoaming,
                            { on -> graph.settingsStore.update { s -> s.copy(network = s.network.copy(pauseWhenRoaming = on)) } },
                            icon = Icons.Rounded.Public,
                        )
                    }
                    item {
                        SwitchItem(
                            it, stringResource(R.string.data_saver), settings.network.respectDataSaver,
                            { on -> graph.settingsStore.update { s -> s.copy(network = s.network.copy(respectDataSaver = on)) } },
                            icon = Icons.Rounded.DataSaverOn,
                            summary = stringResource(R.string.data_saver_summary),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun dayLabel(date: LocalDate, today: Boolean): String {
    if (today) return stringResource(R.string.data_today)
    val pattern = remember { DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMM") }
    return remember(date, pattern) { date.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault())) }
}

// ---- Hero ------------------------------------------------------------------------------------

private fun transportIcon(t: Transport): ImageVector = if (t == Transport.CELLULAR) Icons.Rounded.SignalCellularAlt else Icons.Rounded.Wifi
private fun transportShape(t: Transport): RoundedPolygon = if (t == Transport.CELLULAR) MaterialShapes.Gem else MaterialShapes.Cookie6Sided

@Composable
private fun DayHero(day: DailyUsage, dayLabel: String, dayKey: Int, live: Transport?, filter: Transport?, onFilter: (Transport) -> Unit) {
    val motion = MaterialTheme.motionScheme
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        AnimatedContent(
            dayLabel,
            transitionSpec = { fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) },
            label = "day",
            modifier = Modifier.padding(start = 28.dp, bottom = 10.dp),
        ) {
            Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(Transport.WIFI, Transport.CELLULAR).forEach { t ->
                UsageTile(
                    transport = t,
                    bytes = day.total(t).total,
                    dayKey = dayKey,
                    live = live == t,
                    filtered = filter == t,
                    onClick = { onFilter(t) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun UsageTile(
    transport: Transport,
    bytes: Long,
    dayKey: Int,
    live: Boolean,
    filtered: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val motion = MaterialTheme.motionScheme
    val scheme = MaterialTheme.colorScheme
    val mobile = transport == Transport.CELLULAR
    val accent = if (mobile) scheme.primary else scheme.tertiary
    val onAccent = if (mobile) scheme.onPrimary else scheme.onTertiary
    val accentContainer = if (mobile) scheme.primaryContainer else scheme.tertiaryContainer
    val onAccentContainer = if (mobile) scheme.onPrimaryContainer else scheme.onTertiaryContainer
    val container by animateColorAsState(if (live) accentContainer else scheme.surfaceContainer, label = "tile")
    val textColor by animateColorAsState(if (live) onAccentContainer else scheme.onSurface, label = "tileText")
    val spin by rememberSpin(14_000, enabled = live)

    Surface(onClick = onClick, shape = MaterialTheme.shapes.extraLarge, color = container, modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShapeGlyph(transportIcon(transport), transportShape(transport), size = 44.dp, container = accent, content = onAccent, rotation = spin)
                Spacer(Modifier.weight(1f))
                // Filter mark: a small dot in the tile's accent while the breakdown is filtered to this network.
                AnimatedVisibility(filtered, enter = scaleIn(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeIn(), exit = scaleOut() + fadeOut()) {
                    Box(Modifier.size(10.dp).padding(1.dp)) {
                        Canvas(Modifier.fillMaxSize()) { drawCircle(accent) }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            val (num, unit) = splitBytes(bytes)
            AnimatedContent(
                targetState = dayKey to (num to unit),
                contentKey = { it.first },
                transitionSpec = {
                    val up = targetState.first > initialState.first
                    (slideInVertically(motion.defaultSpatialSpec()) { if (up) it / 2 else -it / 2 } + fadeIn(motion.defaultEffectsSpec())) togetherWith
                        (slideOutVertically(motion.fastSpatialSpec()) { if (up) -it / 2 else it / 2 } + fadeOut(motion.fastEffectsSpec())) using
                        SizeTransform(clip = false)
                },
                label = "bytes",
            ) { (_, value) ->
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        value.first,
                        style = MaterialTheme.typography.displayMediumEmphasized.copy(fontFamily = NumberFamily),
                        color = textColor,
                        maxLines = 1,
                        autoSize = TextAutoSize.StepBased(minFontSize = 24.sp, maxFontSize = MaterialTheme.typography.displayMediumEmphasized.fontSize),
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        value.second,
                        style = MaterialTheme.typography.titleLargeEmphasized.copy(fontFamily = NumberFamily),
                        color = textColor.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
            }
            Text(
                stringResource(if (mobile) R.string.data_mobile else R.string.data_wifi),
                style = MaterialTheme.typography.labelLarge,
                color = textColor.copy(alpha = 0.8f),
            )
        }
    }
}

/** "12.3 MB" -> ("12.3", "MB"). */
private fun splitBytes(bytes: Long): Pair<String, String> {
    val s = formatBytes(bytes)
    val i = s.lastIndexOf(' ')
    return if (i < 0) s to "" else s.substring(0, i) to s.substring(i + 1)
}

// ---- Breakdown -------------------------------------------------------------------------------

private data class CategorySpec(val name: Int, val icon: ImageVector, val shape: RoundedPolygon)

private fun spec(c: TrafficCategory) = when (c) {
    TrafficCategory.GATEWAY -> CategorySpec(R.string.data_cat_gateway, Icons.Rounded.Cable, MaterialShapes.Sunny)
    TrafficCategory.DISCORD_API -> CategorySpec(R.string.data_cat_api, Icons.Rounded.Api, MaterialShapes.Cookie4Sided)
    TrafficCategory.ARTWORK_LOOKUP -> CategorySpec(R.string.data_cat_artwork, Icons.Rounded.Album, MaterialShapes.Cookie9Sided)
    TrafficCategory.IMAGE_UPLOAD -> CategorySpec(R.string.data_cat_upload, Icons.Rounded.CloudUpload, MaterialShapes.Puffy)
    TrafficCategory.APP_ICON -> CategorySpec(R.string.data_cat_icons, Icons.Rounded.Apps, MaterialShapes.Pentagon)
    TrafficCategory.SPOTIFY -> CategorySpec(R.string.data_cat_spotify, Icons.Rounded.Speaker, MaterialShapes.Cookie12Sided)
    TrafficCategory.OTHER -> CategorySpec(R.string.data_cat_other, Icons.Rounded.MoreHoriz, MaterialShapes.SoftBurst)
}

private data class CategoryRow(val category: TrafficCategory, val total: Long, val cellular: Long, val requests: Long)

@Composable
private fun Breakdown(day: DailyUsage, filter: Transport?, dayKey: Int, onFilter: (Transport?) -> Unit) {
    val motion = MaterialTheme.motionScheme
    val rows = remember(day, filter) {
        val cell = day.byCategory(Transport.CELLULAR)
        day.byCategory(filter)
            .filter { it.value.total > 0 }
            .map { (c, v) -> CategoryRow(c, v.total, if (filter == Transport.WIFI) 0 else cell[c]?.total ?: 0, v.requests) }
            .sortedByDescending { it.total }
    }
    val max = rows.maxOfOrNull { it.total }?.coerceAtLeast(1) ?: 1

    Column(Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.data_breakdown),
            style = MaterialTheme.typography.labelLargeEmphasized,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 28.dp, top = 20.dp, bottom = 8.dp),
        )
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf<Transport?>(null, Transport.WIFI, Transport.CELLULAR).forEach { t ->
                val sel = filter == t
                FilterChip(
                    selected = sel,
                    onClick = { if (!sel) onFilter(t) },
                    label = {
                        Text(
                            stringResource(
                                when (t) {
                                    null -> R.string.data_all
                                    Transport.WIFI -> R.string.data_wifi
                                    else -> R.string.data_mobile_short
                                },
                            ),
                        )
                    },
                    leadingIcon = t?.let { { Icon(transportIcon(it), null, Modifier.size(FilterChipDefaults.IconSize)) } },
                    shape = CircleShape,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        AnimatedContent(
            targetState = Triple(dayKey, filter, rows),
            contentKey = { it.first to it.second },
            transitionSpec = {
                fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) using
                    SizeTransform(clip = false) { _, _ -> motion.defaultSpatialSpec() }
            },
            label = "breakdown",
        ) { (_, f, list) ->
            if (list.isEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ShapeGlyph(
                        Icons.Rounded.Sensors, MaterialShapes.Cookie9Sided, size = 40.dp,
                        container = MaterialTheme.colorScheme.surfaceContainerHighest,
                        content = MaterialTheme.colorScheme.outline,
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(stringResource(R.string.data_nothing), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                SettingsGroup(null, Modifier.animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec())) {
                    list.forEach { row -> item { CategoryItem(it, row, max, f) } }
                }
            }
        }
    }
}

@Composable
private fun CategoryItem(shapes: androidx.compose.material3.ListItemShapes, row: CategoryRow, max: Long, filter: Transport?) {
    val scheme = MaterialTheme.colorScheme
    val s = spec(row.category)
    SegmentedListItem(
        shapes = shapes,
        colors = settingsItemColors(),
        leadingContent = {
            ShapeGlyph(s.icon, s.shape, size = 40.dp, container = scheme.secondaryContainer, content = scheme.onSecondaryContainer)
        },
        supportingContent = {
            Column {
                Spacer(Modifier.height(6.dp))
                val lowerColor = if (filter == Transport.WIFI) scheme.tertiary else scheme.primary
                ProportionBar(
                    lower = row.cellular.takeIf { filter != Transport.WIFI } ?: row.total,
                    upper = if (filter == Transport.WIFI) 0 else row.total - row.cellular,
                    max = max,
                    lowerColor = lowerColor,
                    upperColor = scheme.tertiary,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    pluralStringResource(R.plurals.data_requests, row.requests.toInt().coerceAtLeast(0), row.requests.toInt()),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = NumberFamily),
                )
            }
        },
        trailingContent = {
            Text(
                formatBytes(row.total),
                style = MaterialTheme.typography.titleMediumEmphasized.copy(fontFamily = NumberFamily),
                color = scheme.onSurface,
            )
        },
    ) { Text(stringResource(s.name), maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

/** A track with two adjoining pills: [lower] then [upper], scaled to [max]. */
@Composable
private fun ProportionBar(lower: Long, upper: Long, max: Long, lowerColor: Color, upperColor: Color) {
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val lo by animateFloatAsState(lower.toFloat() / max, spec, label = "lo")
    val up by animateFloatAsState(upper.toFloat() / max, spec, label = "up")
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Canvas(Modifier.fillMaxWidth().height(8.dp)) {
        val h = size.height
        val r = CornerRadius(h / 2)
        val gap = 2.dp.toPx()
        drawRoundRect(track, cornerRadius = r)
        var x = 0f
        val loW = size.width * lo.coerceIn(0f, 1f)
        if (lower > 0) {
            drawRoundRect(lowerColor, Offset(0f, 0f), Size(loW.coerceAtLeast(h), h), r)
            x = loW.coerceAtLeast(h) + gap
        }
        val upW = size.width * up.coerceIn(0f, 1f)
        if (upper > 0) drawRoundRect(upperColor, Offset(x, 0f), Size(upW.coerceAtLeast(h).coerceAtMost(size.width - x), h), r)
    }
}

// ---- Cross-check -----------------------------------------------------------------------------

@Composable
private fun CrossCheck(day: DailyUsage, filter: Transport?) {
    val measured = day.total(filter).total
    val system = day.systemRx.filterKeys { filter == null || it == filter }.values.sum() +
        day.systemTx.filterKeys { filter == null || it == filter }.values.sum()
    val scheme = MaterialTheme.colorScheme
    SettingsGroup(null, Modifier.padding(top = 10.dp)) {
        item { shapes ->
            SegmentedListItem(
                shapes = shapes,
                colors = settingsItemColors(),
                leadingContent = {
                    ShapeGlyph(Icons.Rounded.Android, MaterialShapes.Pill, size = 40.dp, container = scheme.surfaceContainerHighest, content = scheme.onSurfaceVariant)
                },
                supportingContent = {
                    Text(stringResource(R.string.data_measured, formatBytes(measured)), style = MaterialTheme.typography.bodyMedium.copy(fontFamily = NumberFamily))
                },
                trailingContent = {
                    Text(
                        if (system > 0) formatBytes(system) else stringResource(R.string.data_unknown),
                        style = MaterialTheme.typography.titleMediumEmphasized.copy(fontFamily = NumberFamily),
                        color = scheme.onSurface,
                    )
                },
            ) { Text(stringResource(R.string.data_android_counted)) }
        }
    }
}

// ---- Policy ----------------------------------------------------------------------------------

@Composable
private fun Policy(
    wifi: NetworkProfile,
    cellular: NetworkProfile,
    initial: Transport,
    onUpdate: (Transport, (NetworkProfile) -> NetworkProfile) -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    var editing by rememberSaveable { mutableStateOf(initial) }
    Column(Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.data_policy),
            style = MaterialTheme.typography.labelLargeEmphasized,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 28.dp, top = 28.dp, bottom = 8.dp),
        )
        ChoiceRow(
            choices = listOf(
                Choice(Transport.WIFI, stringResource(R.string.data_wifi), Icons.Rounded.Wifi),
                Choice(Transport.CELLULAR, stringResource(R.string.data_mobile), Icons.Rounded.SignalCellularAlt),
            ),
            selected = editing,
            onSelect = { editing = it },
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(10.dp))
        AnimatedContent(
            targetState = editing,
            transitionSpec = {
                val forward = targetState == Transport.CELLULAR
                val spatial = motion.defaultSpatialSpec<androidx.compose.ui.unit.IntOffset>()
                (slideInHorizontally(spatial) { if (forward) it / 4 else -it / 4 } + fadeIn(motion.defaultEffectsSpec())) togetherWith
                    (slideOutHorizontally(spatial) { if (forward) -it / 4 else it / 4 } + fadeOut(motion.fastEffectsSpec())) using
                    SizeTransform(clip = false)
            },
            label = "profile",
        ) { t ->
            val p = if (t == Transport.WIFI) wifi else cellular
            val set: ((NetworkProfile) -> NetworkProfile) -> Unit = { f -> onUpdate(t, f) }
            SettingsGroup(null) {
                item { SwitchItem(it, stringResource(R.string.data_p_enabled), p.enabled, { v -> set { it.copy(enabled = v) } }, icon = Icons.Rounded.Sensors) }
                item { SwitchItem(it, stringResource(R.string.data_p_artwork), p.artworkLookup, { v -> set { it.copy(artworkLookup = v) } }, icon = Icons.Rounded.Album, enabled = p.enabled) }
                item { SwitchItem(it, stringResource(R.string.data_p_upload), p.uploadImages, { v -> set { it.copy(uploadImages = v) } }, icon = Icons.Rounded.CloudUpload, enabled = p.enabled) }
                item { SwitchItem(it, stringResource(R.string.data_p_icons), p.appIcons, { v -> set { it.copy(appIcons = v) } }, icon = Icons.Rounded.Apps, enabled = p.enabled) }
                item { SwitchItem(it, stringResource(R.string.data_p_spotify), p.spotifyConnect, { v -> set { it.copy(spotifyConnect = v) } }, icon = Icons.Rounded.Speaker, enabled = p.enabled) }
                item {
                    SwitchItem(
                        it, stringResource(R.string.data_p_stay), p.stayConnected, { v -> set { it.copy(stayConnected = v) } },
                        icon = Icons.Rounded.Link, summary = stringResource(R.string.data_p_stay_summary), enabled = p.enabled,
                    )
                }
            }
        }
    }
}
