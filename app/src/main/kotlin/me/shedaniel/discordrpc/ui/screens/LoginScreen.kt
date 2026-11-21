package me.shedaniel.discordrpc.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.shedaniel.discordrpc.R
import me.shedaniel.discordrpc.core.util.EventLog
import me.shedaniel.discordrpc.discord.DiscordLoginWebView
import me.shedaniel.discordrpc.graph
import me.shedaniel.discordrpc.ui.nav.LocalNavigator
import me.shedaniel.discordrpc.ui.theme.rememberHaptics

@Composable
fun LoginScreen() {
    val graph = LocalContext.current.graph
    val nav = LocalNavigator.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var verifying by remember { mutableStateOf(false) }
    var manual by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val invalidText = stringResource(R.string.login_invalid)

    fun accept(token: String) {
        val t = token.trim().trim('"')
        if (t.isBlank() || verifying) return
        verifying = true
        scope.launch {
            val result = graph.rest.me(t)
            val failure = result.exceptionOrNull()
            // Only reject when Discord says the token is bad; network trouble shouldn't block sign-in.
            if (failure != null && (failure.message?.contains("401") == true)) {
                haptics.reject()
                error = invalidText
                verifying = false
                return@launch
            }
            graph.tokens.setToken(t)
            result.getOrNull()?.let { graph.tokens.setUser(it) }
            EventLog.i("Login", "Signed in" + (result.getOrNull()?.let { " as ${it.username}" } ?: ""))
            haptics.confirm()
            nav.pop()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.login_title)) },
                navigationIcon = {
                    IconButton(onClick = { nav.pop() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back)) }
                },
                actions = {
                    TextButton(onClick = { haptics.press(); manual = true }) {
                        Icon(Icons.Rounded.Key, null)
                        Text(stringResource(R.string.login_use_token), Modifier.padding(start = 6.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            DiscordLoginWebView(
                modifier = Modifier.fillMaxSize(),
                onToken = ::accept,
                onLoadingChange = { loading = it },
            )
            AnimatedVisibility(loading || verifying, enter = fadeIn(), exit = fadeOut()) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = if (verifying) 0.92f else 0.6f)), contentAlignment = Alignment.Center) {
                    LoadingIndicator()
                }
            }
        }
    }

    if (manual) {
        var value by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { manual = false },
            icon = { Icon(Icons.Rounded.Key, null) },
            title = { Text(stringResource(R.string.login_token_title)) },
            text = {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it; error = null },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = { TextButton(onClick = { accept(value) }, enabled = value.isNotBlank()) { Text(stringResource(R.string.login_sign_in)) } },
            dismissButton = { TextButton(onClick = { manual = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

