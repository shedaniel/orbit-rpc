package me.shedaniel.discordrpc.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.shedaniel.discordrpc.R
import me.shedaniel.discordrpc.graph
import me.shedaniel.discordrpc.sources.AppCategory
import me.shedaniel.discordrpc.ui.components.AppIcon
import me.shedaniel.discordrpc.ui.theme.rememberHaptics

/** Multi-select app picker. Media apps (or games) float to the top. */
@Composable
fun AppPickerSheet(selected: Set<String>, preferMedia: Boolean, onDismiss: () -> Unit, onChange: (Set<String>) -> Unit) {
    val graph = LocalContext.current.graph
    val haptics = rememberHaptics()
    val apps by graph.catalog.apps.collectAsStateWithLifecycle()
    val mediaApps by graph.catalog.mediaApps.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var current by remember { mutableStateOf(selected) }
    val sorted = remember(apps, mediaApps, query, preferMedia) {
        apps.filter { query.isBlank() || it.label.contains(query, true) || it.packageName.contains(query, true) }
            .sortedWith(
                compareByDescending<me.shedaniel.discordrpc.sources.InstalledApp> { it.packageName in selected }
                    .thenByDescending {
                        if (preferMedia) it.packageName in mediaApps || it.category in setOf(AppCategory.MUSIC, AppCategory.VIDEO, AppCategory.PODCAST)
                        else it.isGame
                    }
                    .thenBy { it.label.lowercase() }
            )
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                placeholder = { Text(stringResource(R.string.apps_search)) },
                singleLine = true, shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            LazyColumn(Modifier.fillMaxWidth().height(520.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                items(sorted, key = { it.packageName }) { app ->
                    val checked = app.packageName in current
                    ListItem(
                        checked = checked,
                        onCheckedChange = {
                            haptics.toggle(it)
                            current = if (it) current + app.packageName else current - app.packageName
                            onChange(current)
                        },
                        leadingContent = { AppIcon(app.packageName, Modifier.size(40.dp).clip(CircleShape)) },
                        trailingContent = { Checkbox(checked = checked, onCheckedChange = null) },
                        supportingContent = { Text(app.packageName, maxLines = 1, style = MaterialTheme.typography.bodySmall) },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    ) { Text(app.label) }
                }
            }
        }
    }
}
