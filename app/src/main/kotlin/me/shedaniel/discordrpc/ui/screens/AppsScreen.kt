package me.shedaniel.discordrpc.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.shedaniel.discordrpc.R
import me.shedaniel.discordrpc.core.model.MediaKind
import me.shedaniel.discordrpc.core.settings.AppProfile
import me.shedaniel.discordrpc.core.settings.AppSettings
import me.shedaniel.discordrpc.graph
import me.shedaniel.discordrpc.sources.AppCategory
import me.shedaniel.discordrpc.sources.InstalledApp
import me.shedaniel.discordrpc.ui.components.AppIcon
import me.shedaniel.discordrpc.ui.components.Choice
import me.shedaniel.discordrpc.ui.components.ChoiceRow
import me.shedaniel.discordrpc.ui.components.ChoiceStyle
import me.shedaniel.discordrpc.ui.components.ShapeGlyph
import me.shedaniel.discordrpc.ui.theme.NumberFamily
import me.shedaniel.discordrpc.ui.theme.rememberHaptics

private enum class AppFilter(val label: Int) { RELEVANT(R.string.apps_filter_relevant), MEDIA(R.string.apps_filter_media), GAMES(R.string.apps_filter_games), SHOWN(R.string.apps_filter_shown), ALL(R.string.apps_filter_all) }

private fun AppSettings.showsWhenOpen(app: InstalledApp): Boolean {
    val p = apps[app.packageName]
    return p?.foreground ?: (foreground.autoGames && app.isGame)
}

private fun isMediaApp(app: InstalledApp, mediaApps: Set<String>) =
    app.packageName in mediaApps || app.category in setOf(AppCategory.MUSIC, AppCategory.VIDEO, AppCategory.PODCAST)

fun MediaKind.icon(): ImageVector = when (this) {
    MediaKind.MUSIC -> Icons.Rounded.MusicNote
    MediaKind.VIDEO -> Icons.Rounded.LiveTv
    MediaKind.PODCAST, MediaKind.AUDIOBOOK -> Icons.Rounded.Podcasts
    MediaKind.UNKNOWN -> Icons.Rounded.Headphones
}


@Composable
fun AppsScreen(contentPadding: PaddingValues) {
    val graph = LocalContext.current.graph
    val haptics = rememberHaptics()
    val apps by graph.catalog.apps.collectAsStateWithLifecycle()
    val mediaApps by graph.catalog.mediaApps.collectAsStateWithLifecycle()
    val settings by graph.settings.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(AppFilter.RELEVANT) }
    var open by rememberSaveable { mutableStateOf<String?>(null) }

    val visible = remember(apps, mediaApps, settings, query, filter) {
        apps.filter { a ->
            (query.isBlank() || a.label.contains(query, true) || a.packageName.contains(query, true)) && when (filter) {
                AppFilter.ALL -> true
                AppFilter.RELEVANT -> isMediaApp(a, mediaApps) || a.isGame || a.packageName in settings.apps
                AppFilter.MEDIA -> isMediaApp(a, mediaApps)
                AppFilter.GAMES -> a.isGame
                AppFilter.SHOWN -> settings.showsWhenOpen(a) || (isMediaApp(a, mediaApps) && settings.app(a.packageName).media)
            }
        }
    }

    // Same header as Rules and Data: a large flexible bar that collapses as the list scrolls.
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.tab_apps)) },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
    ) { inner ->
        LazyColumn(
            state = rememberLazyListState(),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = inner.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding() + 16.dp),
        ) {
            item(key = "search") {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.apps_search)) },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = {
                        AnimatedVisibility(query.isNotEmpty(), enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                            IconButton(onClick = { haptics.press(); query = "" }) { Icon(Icons.Rounded.Close, stringResource(R.string.action_clear)) }
                        }
                    },
                    singleLine = true,
                    shape = CircleShape,
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            item(key = "filters") {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AppFilter.entries.forEach { f ->
                        FilterChip(
                            selected = filter == f,
                            onClick = { haptics.tick(); filter = f },
                            label = { Text(stringResource(f.label)) },
                            leadingIcon = if (filter == f) { { Icon(Icons.Rounded.Check, null, Modifier.size(FilterChipDefaults.IconSize)) } } else null,
                        )
                    }
                }
            }

            when {
                apps.isEmpty() -> item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(top = 96.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
                }
                visible.isEmpty() -> item(key = "empty") {
                    Box(Modifier.fillMaxWidth().padding(top = 72.dp).animateItem(), contentAlignment = Alignment.Center) {
                        ShapeGlyph(
                            Icons.Rounded.Search, MaterialShapes.Cookie9Sided, size = 96.dp,
                            container = MaterialTheme.colorScheme.surfaceContainerHigh, content = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
                else -> itemsIndexed(visible, key = { _, a -> a.packageName }) { i, app ->
                    val profile = settings.app(app.packageName)
                    val media = isMediaApp(app, mediaApps)
                    val kind = profile.kind ?: app.defaultKind
                    SegmentedListItem(
                        onClick = { haptics.press(); open = app.packageName },
                        shapes = ListItemDefaults.segmentedShapes(i, visible.size),
                        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        modifier = Modifier
                            .animateItem()
                            .padding(horizontal = 16.dp)
                            .padding(top = if (i == 0) 0.dp else ListItemDefaults.SegmentedGap),
                        leadingContent = { AppIcon(app.packageName, Modifier.size(44.dp).clip(CircleShape)) },
                        supportingContent = {
                            val parts = buildList {
                                if (media) add(stringResource(if (profile.media) kind.labelRes() else R.string.apps_media_off))
                                if (settings.showsWhenOpen(app)) add(stringResource(if (app.isGame) R.string.apps_shown_game else R.string.apps_shown_open))
                            }
                            if (parts.isNotEmpty()) Text(parts.joinToString(" · "), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        trailingContent = {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (media && profile.media) StatusGlyph(kind.icon(), MaterialShapes.Cookie9Sided)
                                if (settings.showsWhenOpen(app)) StatusGlyph(Icons.Rounded.SportsEsports, MaterialShapes.Clover4Leaf)
                            }
                        },
                    ) { Text(profile.customName ?: app.label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
    }

    val current = open?.let { pkg -> apps.firstOrNull { it.packageName == pkg } }
    if (current != null) {
        AppSheet(current, settings, onDismiss = { open = null }) { transform ->
            graph.settingsStore.update { s ->
                val p = transform(s.app(current.packageName))
                s.copy(apps = s.apps + (current.packageName to p))
            }
        }
    }
}

@Composable
private fun StatusGlyph(icon: ImageVector, polygon: androidx.graphics.shapes.RoundedPolygon) {
    ShapeGlyph(icon, polygon, size = 28.dp, container = MaterialTheme.colorScheme.secondaryContainer, content = MaterialTheme.colorScheme.onSecondaryContainer)
}

private fun AppCategory.icon(): ImageVector = when (this) {
    AppCategory.MUSIC -> Icons.Rounded.MusicNote
    AppCategory.VIDEO -> Icons.Rounded.LiveTv
    AppCategory.PODCAST -> Icons.Rounded.Podcasts
    AppCategory.GAME -> Icons.Rounded.SportsEsports
    AppCategory.OTHER -> Icons.Rounded.Apps
}

private fun AppCategory.labelRes(): Int = when (this) {
    AppCategory.MUSIC -> R.string.apps_cat_music
    AppCategory.VIDEO -> R.string.apps_cat_video
    AppCategory.PODCAST -> R.string.apps_cat_podcast
    AppCategory.GAME -> R.string.apps_cat_game
    AppCategory.OTHER -> R.string.apps_cat_other
}

@Composable
private fun AppSheet(app: InstalledApp, settings: AppSettings, onDismiss: () -> Unit, update: ((AppProfile) -> AppProfile) -> Unit) {
    val profile = settings.app(app.packageName)
    val haptics = rememberHaptics()
    val scheme = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Scrim follows the sheet: its alpha tracks how much of the sheet is on screen, so it fades in and out with
    // the slide (and with a drag) instead of on its own timer.
    var shown by remember { mutableFloatStateOf(0f) }
    val scrim = BottomSheetDefaults.ScrimColor
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = scheme.surfaceContainerLow,
        scrimColor = scrim.copy(alpha = scrim.alpha * shown),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .onGloballyPositioned { c ->
                    val rootHeight = c.findRootCoordinates().size.height.toFloat()
                    val top = c.positionInRoot().y
                    val h = c.size.height.toFloat()
                    if (h > 0f) shown = ((rootHeight - top) / h).coerceIn(0f, 1f)
                }
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            // Header: name over the detected category; long-press reveals the package name.
            var showPackage by remember(app.packageName) { mutableStateOf(false) }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.pointerInput(app.packageName) {
                    detectTapGestures(onLongPress = { haptics.longPress(); showPackage = !showPackage })
                },
            ) {
                AppIcon(app.packageName, Modifier.size(64.dp).clip(CircleShape))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(app.label, style = MaterialTheme.typography.headlineSmallEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(6.dp))
                    val motion = MaterialTheme.motionScheme
                    AnimatedContent(
                        showPackage,
                        transitionSpec = { fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) },
                        contentAlignment = Alignment.CenterStart,
                        label = "header",
                    ) { pkg ->
                        if (pkg) {
                            Text(
                                app.packageName, style = MaterialTheme.typography.labelMedium.copy(fontFamily = NumberFamily),
                                color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.height(24.dp).wrapContentHeight(Alignment.CenterVertically),
                            )
                        } else {
                            Surface(shape = CircleShape, color = scheme.secondaryContainer, contentColor = scheme.onSecondaryContainer) {
                                Row(
                                    Modifier.height(24.dp).padding(start = 8.dp, end = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(app.category.icon(), null, Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(app.category.labelRes()), style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }

            SheetLabel(stringResource(R.string.apps_sheet_media))
            ChoiceRow(
                listOf(
                    Choice<MediaKind?>(
                        MediaKind.UNKNOWN, stringResource(R.string.editor_type_auto), Icons.Rounded.AutoAwesome,
                        detail = stringResource(app.defaultKind.labelRes()),
                    ),
                    Choice<MediaKind?>(MediaKind.MUSIC, stringResource(R.string.kind_music), MediaKind.MUSIC.icon()),
                    Choice<MediaKind?>(MediaKind.VIDEO, stringResource(R.string.kind_video), MediaKind.VIDEO.icon()),
                    Choice<MediaKind?>(MediaKind.PODCAST, stringResource(R.string.kind_podcast), MediaKind.PODCAST.icon()),
                    Choice<MediaKind?>(null, stringResource(R.string.apps_off), Icons.Rounded.Block),
                ),
                selected = when {
                    !profile.media -> null
                    profile.kind == null -> MediaKind.UNKNOWN
                    else -> profile.kind
                },
                onSelect = { v ->
                    update {
                        when (v) {
                            null -> it.copy(media = false)
                            MediaKind.UNKNOWN -> it.copy(media = true, kind = null)
                            else -> it.copy(media = true, kind = v)
                        }
                    }
                },
                style = ChoiceStyle.Stacked,
            )

            SheetLabel(stringResource(R.string.apps_sheet_open))
            val autoShown = app.isGame && settings.foreground.autoGames
            ChoiceRow(
                listOf(
                    Choice<Boolean?>(
                        null, stringResource(R.string.editor_type_auto), Icons.Rounded.AutoAwesome,
                        detail = stringResource(if (autoShown) R.string.apps_open_auto_shown else R.string.apps_open_auto_hidden),
                    ),
                    Choice<Boolean?>(true, stringResource(R.string.apps_show), Icons.Rounded.Visibility),
                    Choice<Boolean?>(false, stringResource(R.string.apps_off), Icons.Rounded.Block),
                ),
                selected = profile.foreground,
                onSelect = { v -> update { it.copy(foreground = v) } },
                style = ChoiceStyle.Stacked,
            )

            SheetLabel(stringResource(R.string.apps_sheet_name))
            var name by remember(app.packageName) { mutableStateOf(profile.customName ?: "") }
            OutlinedTextField(
                value = name,
                onValueChange = { v -> name = v.take(64); update { it.copy(customName = v.take(64).ifBlank { null }) } },
                placeholder = { Text(app.label) },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SheetLabel(text: String) {
    Text(
        text, style = MaterialTheme.typography.labelLargeEmphasized, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 24.dp, bottom = 10.dp),
    )
}
