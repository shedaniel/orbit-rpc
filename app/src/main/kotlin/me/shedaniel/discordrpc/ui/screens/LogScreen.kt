package me.shedaniel.discordrpc.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.shedaniel.discordrpc.R
import me.shedaniel.discordrpc.core.util.EventLog
import me.shedaniel.discordrpc.ui.components.PolygonShape
import me.shedaniel.discordrpc.ui.components.ShapeGlyph
import me.shedaniel.discordrpc.ui.components.rememberSpin
import me.shedaniel.discordrpc.ui.nav.LocalNavigator
import me.shedaniel.discordrpc.ui.theme.NumberFamily
import me.shedaniel.discordrpc.ui.theme.rememberHaptics
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class Keyed(val key: String, val entry: EventLog.Entry)

private fun levelLabel(l: EventLog.Level) = when (l) {
    EventLog.Level.DEBUG -> R.string.log_level_debug
    EventLog.Level.INFO -> R.string.log_level_info
    EventLog.Level.WARN -> R.string.log_level_warn
    EventLog.Level.ERROR -> R.string.log_level_error
}

private fun levelShape(l: EventLog.Level): RoundedPolygon = when (l) {
    EventLog.Level.DEBUG -> MaterialShapes.Circle
    EventLog.Level.INFO -> MaterialShapes.Cookie4Sided
    EventLog.Level.WARN -> MaterialShapes.Triangle
    EventLog.Level.ERROR -> MaterialShapes.SoftBurst
}

@Composable
private fun levelColor(l: EventLog.Level): Color = when (l) {
    EventLog.Level.DEBUG -> MaterialTheme.colorScheme.outlineVariant
    EventLog.Level.INFO -> MaterialTheme.colorScheme.primary
    EventLog.Level.WARN -> MaterialTheme.colorScheme.tertiary
    EventLog.Level.ERROR -> MaterialTheme.colorScheme.error
}

@Composable
private fun LevelGlyph(level: EventLog.Level, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 12.dp) {
    Box(modifier.size(size).clip(remember(level) { PolygonShape(levelShape(level)) }).background(levelColor(level)))
}

@Composable
fun LogScreen() {
    val context = LocalContext.current
    val nav = LocalNavigator.current
    val haptics = rememberHaptics()
    val entries by EventLog.entries.collectAsStateWithLifecycle()
    var showFilter by rememberSaveable { mutableStateOf(false) }
    var hidden by rememberSaveable { mutableStateOf(setOf<EventLog.Level>()) }
    var expanded by rememberSaveable { mutableStateOf(setOf<String>()) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clearedLabel = stringResource(R.string.log_cleared)
    val undoLabel = stringResource(R.string.log_undo)

    // Newest first, with stable keys (time + content, disambiguated for repeats).
    val keyed = remember(entries) {
        val seen = HashMap<String, Int>()
        entries.map { e ->
            val base = "${e.timeMs}:${e.level}:${e.tag}:${e.message.hashCode()}"
            val n = seen.merge(base, 1, Int::plus)!!
            Keyed("$base:$n", e)
        }.asReversed()
    }
    val visible = remember(keyed, hidden) { if (hidden.isEmpty()) keyed else keyed.filter { it.entry.level !in hidden } }
    val listState = rememberLazyListState()
    val newest = visible.firstOrNull()?.key
    LaunchedEffect(newest) {
        // Follow new entries only while the reader is at the top.
        if (newest != null && listState.firstVisibleItemIndex <= 1) listState.animateScrollToItem(0)
    }
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Column {
                MediumFlexibleTopAppBar(
                    title = { Text(stringResource(R.string.log_title)) },
                    navigationIcon = {
                        Tip(stringResource(R.string.screen_back)) {
                            IconButton(onClick = { haptics.press(); nav.pop() }) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.screen_back))
                            }
                        }
                    },
                    actions = {
                        Tip(stringResource(R.string.log_filter)) {
                            IconToggleButton(checked = showFilter || hidden.isNotEmpty(), onCheckedChange = { haptics.toggle(it); showFilter = !showFilter }) {
                                Icon(Icons.Rounded.FilterList, stringResource(R.string.log_filter))
                            }
                        }
                        Tip(stringResource(R.string.log_share)) {
                        IconButton(
                            onClick = {
                                haptics.press()
                                val text = entries.joinToString("\n") { e ->
                                    "${timeFormat.format(Date(e.timeMs))} ${e.level.name.first()}/${e.tag}: ${e.message}"
                                }
                                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                                runCatching { context.startActivity(Intent.createChooser(send, null)) }
                            },
                            enabled = entries.isNotEmpty(),
                        ) { Icon(Icons.Rounded.Share, stringResource(R.string.log_share)) }
                        }
                        Tip(stringResource(R.string.log_clear)) {
                            IconButton(
                                onClick = {
                                    haptics.confirm()
                                    val previous = entries
                                    EventLog.clear()
                                    expanded = emptySet()
                                    scope.launch {
                                        snackbar.currentSnackbarData?.dismiss()
                                        val result = snackbar.showSnackbar(clearedLabel, undoLabel, duration = SnackbarDuration.Short)
                                        if (result == SnackbarResult.ActionPerformed) { haptics.tick(); EventLog.restore(previous) }
                                    }
                                },
                                enabled = entries.isNotEmpty(),
                            ) { Icon(Icons.Rounded.DeleteSweep, stringResource(R.string.log_clear)) }
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
                AnimatedVisibility(showFilter, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(if (scrollBehavior.state.overlappedFraction > 0.01f) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surface)
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        EventLog.Level.entries.forEach { l ->
                            val on = l !in hidden
                            FilterChip(
                                selected = on,
                                onClick = { haptics.tick(); hidden = if (on) hidden + l else hidden - l },
                                label = { Text(stringResource(levelLabel(l))) },
                                leadingIcon = { LevelGlyph(l, size = 14.dp) },
                                shape = CircleShape,
                            )
                        }
                    }
                }
            }
        },
    ) { inner ->
        AnimatedContent(
            visible.isEmpty(),
            transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.9f)) togetherWith fadeOut() },
            label = "empty",
            modifier = Modifier.fillMaxSize(),
        ) { empty ->
            if (empty) {
                Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) {
                    val spin by rememberSpin(40_000)
                    ShapeGlyph(
                        Icons.AutoMirrored.Rounded.ReceiptLong, MaterialShapes.Cookie9Sided, size = 132.dp,
                        container = MaterialTheme.colorScheme.surfaceContainerHigh,
                        content = MaterialTheme.colorScheme.outline,
                        rotation = spin,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = inner.calculateTopPadding() + 4.dp,
                        bottom = inner.calculateBottomPadding() + 16.dp,
                    ),
                ) {
                    items(visible, key = { it.key }) { k ->
                        val open = k.key in expanded
                        LogRow(
                            k.entry, timeFormat, open,
                            onToggle = { haptics.tick(); expanded = if (open) expanded - k.key else expanded + k.key },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

/** Icon button label shown on long-press, for the top bar's glyph-only actions. */
@Composable
private fun Tip(label: String, content: @Composable () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
        content = content,
    )
}

private val Bullet = Regex("""^[▶■●◆►•→*>\s-]+""")
private val Bracketed = Regex("""\[[A-Z0-9_]+]""")
private val KeyValue = Regex("""(^|\s)[\w.]+=\S+""")
private val Numeric = Regex("""(^|\s)(@?\d+(\.\d+)?(ms|s)?|\d+x\d+)(?=\s|$)""")
private val Spaces = Regex("""\s+""")

/** The human part of a log line: first line, minus bullets, [TAGS], key=value pairs and bare numbers. */
internal fun cleanLogLine(message: String): String {
    val first = message.lineSequence().firstOrNull().orEmpty()
    val t = first
        .replace(Bullet, "")
        .replace(Bracketed, " ")
        .replace(KeyValue, " ")
        .replace(Numeric, " ")
        .replace(Spaces, " ")
        .trim()
        .trimEnd(':', ',', ';')
    return t.ifBlank { first.trim() }
}

@Composable
private fun LogRow(e: EventLog.Entry, timeFormat: SimpleDateFormat, expanded: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val error = e.level == EventLog.Level.ERROR
    val clean = remember(e.message) { cleanLogLine(e.message) }
    val hasDetails = clean != e.message.trim()
    val container by animateColorAsState(
        when {
            error -> scheme.errorContainer.copy(alpha = if (expanded) 0.7f else 0.45f)
            expanded -> scheme.surfaceContainer
            else -> scheme.surfaceContainer.copy(alpha = 0f)
        },
        label = "row",
    )
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(MaterialTheme.shapes.large)
            .background(container)
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        LevelGlyph(e.level, Modifier.padding(top = 4.dp), size = 14.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = scheme.secondaryContainer, contentColor = scheme.onSecondaryContainer) {
                    Text(
                        e.tag,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 1.dp),
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    remember(e.timeMs) { timeFormat.format(Date(e.timeMs)) },
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = NumberFamily),
                    color = scheme.onSurfaceVariant,
                )
            }
            Text(
                clean,
                style = MaterialTheme.typography.bodyMedium,
                color = if (e.level == EventLog.Level.DEBUG) scheme.onSurfaceVariant else scheme.onSurface,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
            AnimatedVisibility(
                expanded && hasDetails,
                enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
                exit = shrinkVertically(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
            ) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = scheme.surfaceContainerHighest,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    SelectionContainer {
                        Text(
                            e.message,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = scheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}
