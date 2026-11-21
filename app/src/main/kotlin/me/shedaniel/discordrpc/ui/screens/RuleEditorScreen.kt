package me.shedaniel.discordrpc.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.LinearScale
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.shedaniel.discordrpc.R
import me.shedaniel.discordrpc.core.model.ActivityType
import me.shedaniel.discordrpc.core.model.AppSignal
import me.shedaniel.discordrpc.core.model.DiscordUser
import me.shedaniel.discordrpc.core.model.MediaKind
import me.shedaniel.discordrpc.core.model.MediaSignal
import me.shedaniel.discordrpc.core.model.PresenceActivity
import me.shedaniel.discordrpc.core.model.Signal
import me.shedaniel.discordrpc.core.model.SignalOrigin
import me.shedaniel.discordrpc.core.model.StatusDisplay
import me.shedaniel.discordrpc.core.rules.ActivityTemplate
import me.shedaniel.discordrpc.core.rules.ButtonTemplate
import me.shedaniel.discordrpc.core.rules.DefaultRules
import me.shedaniel.discordrpc.core.rules.ImageSource
import me.shedaniel.discordrpc.core.rules.Rule
import me.shedaniel.discordrpc.core.rules.RulesEngine
import me.shedaniel.discordrpc.core.rules.TimestampMode
import me.shedaniel.discordrpc.core.rules.Trigger
import me.shedaniel.discordrpc.graph
import me.shedaniel.discordrpc.ui.components.AppIcon
import me.shedaniel.discordrpc.ui.components.Avatar
import me.shedaniel.discordrpc.ui.components.Choice
import me.shedaniel.discordrpc.ui.components.ChoiceRow
import me.shedaniel.discordrpc.ui.components.Glyphs
import me.shedaniel.discordrpc.ui.components.PresenceCard
import me.shedaniel.discordrpc.ui.components.ShapeGlyph
import me.shedaniel.discordrpc.ui.components.glyphIcon
import me.shedaniel.discordrpc.ui.components.typeIcon
import me.shedaniel.discordrpc.ui.components.verbRes
import me.shedaniel.discordrpc.ui.nav.LocalNavigator
import me.shedaniel.discordrpc.ui.nav.Route
import me.shedaniel.discordrpc.ui.theme.rememberHaptics
import java.util.UUID

private fun newRule(preset: String?): Rule {
    val id = "user." + UUID.randomUUID().toString().take(8)
    return when (preset) {
        "app" -> Rule(id, "", trigger = Trigger.App(), template = DefaultRules.games.template, glyph = "game")
        "always" -> Rule(
            id, "", trigger = Trigger.Always, glyph = "always",
            template = ActivityTemplate(type = ActivityType.PLAYING, name = "", details = "", largeImage = ImageSource.NONE),
        )
        else -> Rule(id, "", trigger = Trigger.Media(), template = DefaultRules.music.template.copy(autoType = true), glyph = "music")
    }
}

/** A plausible signal for previews when nothing live matches. */
private fun sampleSignal(rule: Rule, appLabel: (String) -> String): Signal? {
    val now = System.currentTimeMillis()
    return when (val t = rule.trigger) {
        is Trigger.Media -> {
            val pkg = t.packages.firstOrNull()
            val kind = t.kinds.firstOrNull { it != MediaKind.UNKNOWN } ?: MediaKind.MUSIC
            val (title, artist, album) = when (kind) {
                MediaKind.VIDEO -> Triple("Building a tiny synth from scratch", "Studio Hollow", null)
                MediaKind.PODCAST, MediaKind.AUDIOBOOK -> Triple("Episode 42: Slow mornings", "The Long Table", "The Long Table")
                else -> Triple("Golden Hour", "Mara Vey", "Low Tide")
            }
            MediaSignal(
                id = "sample", origin = SignalOrigin.MEDIA_SESSION, packageName = pkg, appLabel = pkg?.let(appLabel) ?: "Music",
                since = now - 64_000, title = title, artist = artist, album = album, artwork = null,
                durationMs = 214_000, positionMs = 64_000, positionAtMs = now, playing = true, kind = kind,
            )
        }
        is Trigger.App -> {
            val pkg = t.packages.firstOrNull() ?: "com.android.settings"
            AppSignal("sample", pkg, t.packages.firstOrNull()?.let(appLabel) ?: "Game", now - 754_000, isGame = true)
        }
        Trigger.Always -> null
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RuleEditorScreen(route: Route.RuleEditor) {
    val graph = LocalContext.current.graph
    val nav = LocalNavigator.current
    val haptics = rememberHaptics()
    val stored = remember(route) { route.id?.let { id -> graph.rulesStore.value.rules.firstOrNull { it.id == id } } }
    val original = remember(route) { stored ?: newRule(route.preset) }
    var draft by remember(route) { mutableStateOf(original) }
    val isNew = stored == null
    val dirty = draft != original
    var confirmDiscard by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var pickApps by rememberSaveable { mutableStateOf(false) }

    val engine by graph.engine.state.collectAsStateWithLifecycle()
    val user by graph.tokens.user.collectAsStateWithLifecycle()
    val apps by graph.catalog.apps.collectAsStateWithLifecycle()
    val labelOf: (String) -> String = { pkg -> apps.firstOrNull { it.packageName == pkg }?.label ?: graph.catalog.label(pkg) }

    val defaultName = when (draft.trigger) {
        is Trigger.Media -> stringResource(R.string.editor_default_name_media)
        is Trigger.App -> stringResource(R.string.editor_default_name_app)
        Trigger.Always -> stringResource(R.string.editor_default_name_always)
    }

    fun save() {
        val r = draft.copy(name = draft.name.ifBlank { defaultName })
        graph.rulesStore.update { rs ->
            if (rs.rules.any { it.id == r.id }) rs.copy(rules = rs.rules.map { if (it.id == r.id) r else it })
            else rs.copy(rules = listOf(r) + rs.rules) // new rules go first: most specific wins
        }
        haptics.confirm()
        nav.pop()
    }

    BackHandler(enabled = dirty) { confirmDiscard = true }

    // Preview: a live matching signal if there is one, else a sample.
    val live = engine.signals.firstOrNull { RulesEngine.matches(draft.trigger, it) }
    val signal = live ?: sampleSignal(draft, labelOf)
    val preview: PresenceActivity? = RulesEngine.render(draft, signal, null, System.currentTimeMillis())

    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(draft.name.ifBlank { defaultName }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { if (dirty) confirmDiscard = true else nav.pop() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (!draft.builtIn && !isNew) {
                        IconButton(onClick = { haptics.press(); confirmDelete = true }) { Icon(Icons.Rounded.Delete, stringResource(R.string.action_delete)) }
                    }
                    Button(onClick = ::save, modifier = Modifier.padding(end = 8.dp)) {
                        Icon(Icons.Rounded.Check, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.action_save))
                    }
                },
                scrollBehavior = scroll,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface, scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer),
            )
        },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = inner.calculateTopPadding())
                .imePadding()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 32.dp),
        ) {
            // Live preview, pinned to the top of the content.
            Column(Modifier.padding(horizontal = 16.dp).animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec())) {
                if (preview != null) {
                    PresenceCard(preview, Modifier.fillMaxWidth(), container = MaterialTheme.colorScheme.surfaceContainerHigh, previewUrl = { graph.assets.previewUrl(it) }, live = live != null)
                    Spacer(Modifier.height(8.dp))
                    MemberRow(user, preview)
                } else {
                    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                            ShapeGlyph(Icons.Rounded.Block, MaterialShapes.Cookie9Sided, size = 48.dp, container = MaterialTheme.colorScheme.surfaceContainerHighest, content = MaterialTheme.colorScheme.outline)
                            Spacer(Modifier.width(16.dp))
                            Text(stringResource(R.string.editor_preview_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Identity: glyph + name.
            Section(stringResource(R.string.editor_section_rule)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("music", "video", "podcast", "game", "app", "always", "spotify").forEach { g ->
                        val selected = draft.glyph == g
                        Surface(
                            onClick = { haptics.tick(); draft = draft.copy(glyph = g) },
                            shape = CircleShape,
                            color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                        ) {
                            ShapeGlyph(
                                glyphIcon(g), Glyphs.polygon(g), size = 44.dp, modifier = Modifier.padding(4.dp),
                                container = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                                content = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { draft = draft.copy(name = it.take(40)) },
                    label = { Text(stringResource(R.string.editor_rule_name)) },
                    placeholder = { Text(defaultName) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                )
            }

            // When.
            Section(stringResource(R.string.editor_section_when)) {
                when (val t = draft.trigger) {
                    is Trigger.Media -> {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(MediaKind.MUSIC, MediaKind.VIDEO, MediaKind.PODCAST, MediaKind.AUDIOBOOK, MediaKind.UNKNOWN).forEach { k ->
                                val sel = k in t.kinds
                                FilterChip(
                                    selected = sel,
                                    onClick = {
                                        haptics.tick()
                                        draft = draft.copy(trigger = t.copy(kinds = if (sel) t.kinds - k else t.kinds + k))
                                    },
                                    label = { Text(stringResource(k.labelRes())) },
                                    leadingIcon = if (sel) { { Icon(Icons.Rounded.Check, null, Modifier.size(FilterChipDefaults.IconSize)) } } else null,
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        AppsField(t.packages, labelOf, anyLabel = stringResource(R.string.trigger_any_app), onPick = { pickApps = true }, onRemove = { p ->
                            draft = draft.copy(trigger = t.copy(packages = t.packages - p))
                        })
                        ToggleRow(stringResource(R.string.editor_include_paused), t.includePaused) { draft = draft.copy(trigger = t.copy(includePaused = it)) }
                        ToggleRow(stringResource(R.string.editor_include_remote), t.includeRemote) { draft = draft.copy(trigger = t.copy(includeRemote = it)) }
                    }
                    is Trigger.App -> {
                        ToggleRow(stringResource(R.string.editor_any_game), t.games && t.packages.isEmpty(), enabled = t.packages.isEmpty()) {
                            draft = draft.copy(trigger = t.copy(games = it))
                        }
                        Spacer(Modifier.height(4.dp))
                        AppsField(t.packages, labelOf, anyLabel = stringResource(if (t.games) R.string.trigger_games else R.string.trigger_chosen_apps), onPick = { pickApps = true }, onRemove = { p ->
                            draft = draft.copy(trigger = t.copy(packages = t.packages - p))
                        })
                    }
                    Trigger.Always -> Text(stringResource(R.string.trigger_always), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // How it reads.
            val tpl = draft.template
            fun setT(f: (ActivityTemplate) -> ActivityTemplate) { draft = draft.copy(template = f(draft.template)) }
            val placeholders = if (draft.trigger is Trigger.Media) listOf("title", "artist", "album", "app", "device") else listOf("app")
            Section(stringResource(R.string.editor_section_show)) {
                val autoAllowed = draft.trigger is Trigger.Media
                val typeChoices = buildList {
                    if (autoAllowed) add(Choice<ActivityType?>(null, stringResource(R.string.editor_type_auto), Icons.Rounded.AutoAwesome))
                    ActivityType.entries.filter { it != ActivityType.STREAMING }.forEach { add(Choice<ActivityType?>(it, stringResource(it.labelRes()), typeIcon(it))) }
                }
                ChoiceRow(typeChoices, if (tpl.autoType && autoAllowed) null else tpl.type, { sel ->
                    setT { if (sel == null) it.copy(autoType = true) else it.copy(autoType = false, type = sel) }
                }, showLabels = false)
                Spacer(Modifier.height(6.dp))
                AnimatedContent(if (tpl.autoType && autoAllowed) null else tpl.type, label = "typeLabel") { sel ->
                    Text(
                        if (sel == null) stringResource(R.string.editor_type_auto_hint) else stringResource(sel.labelRes()),
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
                TemplateField(stringResource(R.string.editor_field_name), tpl.name, placeholders) { v -> setT { it.copy(name = v) } }
                TemplateField(stringResource(R.string.editor_field_details), tpl.details, placeholders) { v -> setT { it.copy(details = v) } }
                TemplateField(stringResource(R.string.editor_field_state), tpl.state, placeholders) { v -> setT { it.copy(state = v) } }
            }

            Section(stringResource(R.string.editor_section_member_list)) {
                ChoiceRow(
                    listOf(
                        Choice(StatusDisplay.NAME, stringResource(R.string.editor_field_name)),
                        Choice(StatusDisplay.DETAILS, stringResource(R.string.editor_field_details)),
                        Choice(StatusDisplay.STATE, stringResource(R.string.editor_field_state)),
                    ),
                    tpl.statusDisplay, { v -> setT { it.copy(statusDisplay = v) } },
                )
            }

            Section(stringResource(R.string.editor_section_images)) {
                ImagePicker(stringResource(R.string.editor_large_image), tpl.largeImage, tpl.largeImageUrl, draft.trigger is Trigger.Media,
                    onSource = { v -> setT { it.copy(largeImage = v) } }, onUrl = { v -> setT { it.copy(largeImageUrl = v) } })
                AnimatedVisibility(tpl.largeImage != ImageSource.NONE, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    TemplateField(stringResource(R.string.editor_hover_text), tpl.largeText, placeholders) { v -> setT { it.copy(largeText = v) } }
                }
                Spacer(Modifier.height(12.dp))
                ImagePicker(stringResource(R.string.editor_small_image), tpl.smallImage, tpl.smallImageUrl, draft.trigger is Trigger.Media,
                    onSource = { v -> setT { it.copy(smallImage = v) } }, onUrl = { v -> setT { it.copy(smallImageUrl = v) } })
                AnimatedVisibility(tpl.smallImage != ImageSource.NONE, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    TemplateField(stringResource(R.string.editor_hover_text), tpl.smallText, placeholders) { v -> setT { it.copy(smallText = v) } }
                }
            }

            Section(stringResource(R.string.editor_section_time)) {
                ChoiceRow(
                    buildList {
                        add(Choice(TimestampMode.NONE, stringResource(R.string.editor_time_none), Icons.Rounded.Block))
                        add(Choice(TimestampMode.ELAPSED, stringResource(R.string.editor_time_elapsed), Icons.Rounded.Timer))
                        if (draft.trigger is Trigger.Media) add(Choice(TimestampMode.PROGRESS, stringResource(R.string.editor_time_progress), Icons.Rounded.LinearScale))
                    },
                    tpl.timestamps, { v -> setT { it.copy(timestamps = v) } },
                )
            }

            Section(stringResource(R.string.editor_section_buttons)) {
                tpl.buttons.forEachIndexed { i, b ->
                    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    b.label, { v -> setT { t -> t.copy(buttons = t.buttons.toMutableList().also { it[i] = b.copy(label = v.take(32)) }) } },
                                    label = { Text(stringResource(R.string.editor_button_label)) }, singleLine = true, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.medium,
                                )
                                IconButton(onClick = { haptics.press(); setT { t -> t.copy(buttons = t.buttons.filterIndexed { j, _ -> j != i }) } }) {
                                    Icon(Icons.Rounded.Close, stringResource(R.string.action_remove))
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            OutlinedTextField(
                                b.url, { v -> setT { t -> t.copy(buttons = t.buttons.toMutableList().also { it[i] = b.copy(url = v.trim()) }) } },
                                label = { Text(stringResource(R.string.editor_button_url)) }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
                                leadingIcon = { Icon(Icons.Rounded.Link, null) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                                isError = b.url.isNotEmpty() && !b.url.startsWith("http") && !b.url.startsWith("{"),
                            )
                        }
                    }
                }
                if (tpl.buttons.size < 2) {
                    OutlinedButton(onClick = { haptics.press(); setT { it.copy(buttons = it.buttons + ButtonTemplate("", "")) } }) {
                        Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.editor_add_button))
                    }
                }
            }
        }
    }

    if (pickApps) {
        val current = when (val t = draft.trigger) {
            is Trigger.Media -> t.packages
            is Trigger.App -> t.packages
            Trigger.Always -> emptySet()
        }
        AppPickerSheet(
            selected = current,
            preferMedia = draft.trigger is Trigger.Media,
            onDismiss = { pickApps = false },
            onChange = { set ->
                draft = when (val t = draft.trigger) {
                    is Trigger.Media -> draft.copy(trigger = t.copy(packages = set))
                    is Trigger.App -> draft.copy(trigger = t.copy(packages = set))
                    Trigger.Always -> draft
                }
            },
        )
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.editor_discard_title)) },
            confirmButton = { TextButton(onClick = { haptics.reject(); confirmDiscard = false; nav.pop() }) { Text(stringResource(R.string.action_discard)) } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false; save() }) { Text(stringResource(R.string.action_save)) } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(Icons.Rounded.Delete, null) },
            title = { Text(stringResource(R.string.editor_delete_title)) },
            confirmButton = {
                TextButton(onClick = {
                    haptics.reject()
                    confirmDelete = false
                    graph.rulesStore.update { rs -> rs.copy(rules = rs.rules.filterNot { it.id == draft.id }) }
                    nav.pop()
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

fun ActivityType.labelRes(): Int = when (this) {
    ActivityType.PLAYING -> R.string.type_playing
    ActivityType.LISTENING -> R.string.type_listening
    ActivityType.WATCHING -> R.string.type_watching
    ActivityType.STREAMING -> R.string.type_streaming
    ActivityType.COMPETING -> R.string.type_competing
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 24.dp)) {
        Text(
            title, style = MaterialTheme.typography.labelLargeEmphasized, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
        )
        content()
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    val haptics = rememberHaptics()
    Surface(
        checked = checked,
        onCheckedChange = { haptics.toggle(it); onChange(it) },
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline)
            androidx.compose.material3.Switch(checked = checked, onCheckedChange = null, enabled = enabled)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppsField(packages: Set<String>, labelOf: (String) -> String, anyLabel: String, onPick: () -> Unit, onRemove: (String) -> Unit) {
    val haptics = rememberHaptics()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.animateContentSize()) {
        if (packages.isEmpty()) {
            SuggestionChip(onClick = { haptics.press(); onPick() }, label = { Text(anyLabel) }, icon = { Icon(Icons.Rounded.Apps, null, Modifier.size(18.dp)) })
        }
        packages.forEach { p ->
            InputChip(
                selected = true,
                onClick = { haptics.press(); onRemove(p) },
                label = { Text(labelOf(p), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                avatar = { AppIcon(p, Modifier.size(24.dp).clip(CircleShape)) },
                trailingIcon = { Icon(Icons.Rounded.Close, null, Modifier.size(16.dp)) },
            )
        }
        SuggestionChip(onClick = { haptics.press(); onPick() }, label = { Text(stringResource(R.string.editor_pick_apps)) }, icon = { Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)) })
    }
}

@Composable
private fun ImagePicker(title: String, source: ImageSource, url: String, media: Boolean, onSource: (ImageSource) -> Unit, onUrl: (String) -> Unit) {
    Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
    ChoiceRow(
        buildList {
            add(Choice(ImageSource.NONE, stringResource(R.string.editor_image_none), Icons.Rounded.Block))
            if (media) add(Choice(ImageSource.ARTWORK, stringResource(R.string.editor_image_artwork), Icons.Rounded.Image))
            add(Choice(ImageSource.APP_ICON, stringResource(R.string.editor_image_app), Icons.Rounded.Apps))
            add(Choice(ImageSource.URL, stringResource(R.string.editor_image_url), Icons.Rounded.Link))
        },
        source, onSource,
    )
    AnimatedVisibility(source == ImageSource.URL, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        OutlinedTextField(
            url, { onUrl(it.trim()) },
            label = { Text(stringResource(R.string.editor_image_url_hint)) },
            singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), shape = MaterialTheme.shapes.large,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
    }
}

private val placeholderLabels = mapOf(
    "title" to R.string.ph_title, "artist" to R.string.ph_artist, "album" to R.string.ph_album,
    "app" to R.string.ph_app, "device" to R.string.ph_device,
)

/** Text field with tap-to-insert placeholder chips, visible while focused. */
@Composable
private fun TemplateField(label: String, value: String, placeholders: List<String>, onChange: (String) -> Unit) {
    val haptics = rememberHaptics()
    var tfv by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    if (tfv.text != value) tfv = TextFieldValue(value, TextRange(value.length))
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        OutlinedTextField(
            value = tfv,
            onValueChange = { tfv = it; onChange(it.text.take(128)) },
            label = { Text(label) },
            singleLine = true,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        )
        AnimatedVisibility(focused, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                placeholders.forEach { key ->
                    SuggestionChip(
                        onClick = {
                            haptics.tick()
                            val token = "{$key}"
                            val sel = tfv.selection
                            val text = tfv.text.replaceRange(sel.min, sel.max, token)
                            tfv = TextFieldValue(text, TextRange(sel.min + token.length))
                            onChange(text.take(128))
                        },
                        label = { Text(stringResource(placeholderLabels[key] ?: R.string.ph_app)) },
                        icon = { Icon(Icons.Rounded.Add, null, Modifier.size(16.dp)) },
                    )
                }
            }
        }
    }
}

/** How the member list shows the activity: avatar, name, one line. */
@Composable
private fun MemberRow(user: DiscordUser?, a: PresenceActivity) {
    val line = when (a.statusDisplay) {
        StatusDisplay.NAME -> a.name
        StatusDisplay.DETAILS -> a.details ?: a.name
        StatusDisplay.STATE -> a.state ?: a.name
    }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                Avatar(user, Modifier.size(36.dp).clip(CircleShape))
                Box(
                    Modifier.align(Alignment.BottomEnd).size(12.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainer, CircleShape).padding(2.dp)
                        .background(androidx.compose.ui.graphics.Color(0xFF3BA55D), CircleShape),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(user?.displayName ?: stringResource(R.string.editor_you), style = MaterialTheme.typography.titleSmall, maxLines = 1)
                AnimatedContent(stringResource(a.type.verbRes(), line), label = "member") {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
