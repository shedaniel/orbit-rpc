package me.shedaniel.discordrpc.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.shedaniel.discordrpc.R
import me.shedaniel.discordrpc.core.rules.Rule
import me.shedaniel.discordrpc.graph
import me.shedaniel.discordrpc.ui.components.Glyphs
import me.shedaniel.discordrpc.ui.components.ShapeGlyph
import me.shedaniel.discordrpc.ui.components.glyphIcon
import me.shedaniel.discordrpc.ui.components.verbRes
import me.shedaniel.discordrpc.ui.nav.LocalNavigator
import me.shedaniel.discordrpc.ui.nav.Route
import me.shedaniel.discordrpc.ui.theme.NumberFamily
import me.shedaniel.discordrpc.ui.theme.rememberHaptics
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun RulesScreen(contentPadding: PaddingValues) {
    val graph = LocalContext.current.graph
    val nav = LocalNavigator.current
    val haptics = rememberHaptics()
    val ruleSet by graph.rulesStore.state.collectAsStateWithLifecycle()
    val engine by graph.engine.state.collectAsStateWithLifecycle()
    val activeRules = engine.activities.map { it.ruleId }.toSet()
    val apps by graph.catalog.apps.collectAsStateWithLifecycle()
    val labelOf: (String) -> String = { pkg -> apps.firstOrNull { it.packageName == pkg }?.label ?: graph.catalog.label(pkg) }

    // Local copy while dragging so the list feels instant; persisted on drop.
    var rules by remember(ruleSet) { mutableStateOf(ruleSet.rules) }
    val listState = rememberLazyListState()
    val reorder = rememberReorderableLazyListState(listState) { from, to ->
        rules = rules.toMutableList().apply { add(to.index, removeAt(from.index)) }
        haptics.frequentTick()
    }

    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var fabExpanded by rememberSaveable { mutableStateOf(false) }
    BackHandler(fabExpanded) { fabExpanded = false }
    val fabVisible by remember { derivedStateOf { !listState.canScrollForward || listState.firstVisibleItemIndex == 0 } }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.rules_title)) },
                scrollBehavior = scroll,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface, scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer),
            )
        },
    ) { inner ->
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(
                    top = inner.calculateTopPadding() + 4.dp,
                    bottom = contentPadding.calculateBottomPadding() + 96.dp,
                    start = 16.dp, end = 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(rules, key = { it.id }) { rule ->
                    ReorderableItem(reorder, key = rule.id) { dragging ->
                        val index = rules.indexOf(rule)
                        RuleCard(
                            rule = rule,
                            priority = index + 1,
                            active = rule.id in activeRules,
                            dragging = dragging,
                            summary = describeTrigger(rule, labelOf),
                            onClick = { haptics.press(); nav.push(Route.RuleEditor(rule.id)) },
                            onToggle = { on ->
                                haptics.toggle(on)
                                graph.rulesStore.update { rs -> rs.copy(rules = rs.rules.map { if (it.id == rule.id) it.copy(enabled = on) else it }) }
                            },
                            handle = {
                                Icon(
                                    Icons.Rounded.DragIndicator,
                                    contentDescription = stringResource(R.string.rules_reorder),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .draggableHandle(
                                            onDragStarted = { haptics.dragStart() },
                                            onDragStopped = {
                                                haptics.gestureEnd()
                                                val order = rules
                                                graph.rulesStore.update { it.copy(rules = order) }
                                            },
                                        )
                                        .padding(8.dp),
                                )
                            },
                        )
                    }
                }
            }

            FloatingActionButtonMenu(
                modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = contentPadding.calculateBottomPadding()),
                expanded = fabExpanded,
                button = {
                    ToggleFloatingActionButton(
                        modifier = Modifier.animateFloatingActionButton(visible = fabVisible || fabExpanded, alignment = Alignment.BottomEnd),
                        checked = fabExpanded,
                        onCheckedChange = { haptics.toggle(it); fabExpanded = it },
                    ) {
                        val icon by remember { derivedStateOf { if (checkedProgress > 0.5f) Icons.Rounded.Close else Icons.Rounded.Add } }
                        Icon(rememberVectorPainter(icon), stringResource(R.string.rules_new), Modifier.animateIcon({ checkedProgress }))
                    }
                },
            ) {
                listOf(
                    Triple("media", Icons.Rounded.MusicNote, R.string.rules_new_media),
                    Triple("app", Icons.Rounded.SportsEsports, R.string.rules_new_app),
                    Triple("always", Icons.Rounded.AutoAwesome, R.string.rules_new_always),
                ).forEach { (preset, icon, label) ->
                    FloatingActionButtonMenuItem(
                        onClick = {
                            haptics.press()
                            fabExpanded = false
                            nav.push(Route.RuleEditor(null, preset))
                        },
                        icon = { Icon(icon, null) },
                        text = { Text(stringResource(label)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RuleCard(
    rule: Rule,
    priority: Int,
    active: Boolean,
    dragging: Boolean,
    summary: String,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    handle: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val elevation by animateDpAsState(if (dragging) 8.dp else 0.dp, label = "elev")
    val container by animateColorAsState(
        when {
            dragging -> scheme.surfaceContainerHighest
            active -> scheme.secondaryContainer
            else -> scheme.surfaceContainer
        }, label = "container",
    )
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        color = container,
        shadowElevation = elevation,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 14.dp, top = 14.dp, bottom = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                ShapeGlyph(
                    glyphIcon(rule.glyph), Glyphs.polygon(rule.glyph), size = 52.dp,
                    container = if (rule.enabled) scheme.primaryContainer else scheme.surfaceContainerHighest,
                    content = if (rule.enabled) scheme.onPrimaryContainer else scheme.outline,
                )
                // Priority, by position.
                Surface(shape = CircleShape, color = if (rule.enabled) scheme.primary else scheme.outline, modifier = Modifier.align(Alignment.BottomEnd).size(20.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            priority.toString(),
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = NumberFamily),
                            color = scheme.onPrimary,
                        )
                    }
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(rule.name, style = MaterialTheme.typography.titleMediumEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(rule.template.type.verbRes(), rule.template.name.ifBlank { "…" }),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(summary, style = MaterialTheme.typography.labelMedium, color = scheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Switch(checked = rule.enabled, onCheckedChange = onToggle)
            handle()
        }
    }
}
