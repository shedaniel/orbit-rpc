package me.shedaniel.discordrpc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import me.shedaniel.discordrpc.service.PresenceService
import me.shedaniel.discordrpc.ui.OrbitApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (graph.settings.value.onboarded) PresenceService.sync(this)
        setContent { OrbitApp() }
    }

    override fun onResume() {
        super.onResume()
        // Permissions may have been granted in system settings meanwhile.
        graph.mediaSource.refresh()
    }
}
