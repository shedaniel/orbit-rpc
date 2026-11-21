package me.shedaniel.discordrpc.sources

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import me.shedaniel.discordrpc.MainActivity
import me.shedaniel.discordrpc.graph

/** Receives the Spotify OAuth redirect and bounces back into the app. */
class SpotifyRedirectActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent?.data?.let { graph.spotifyAuth.handleRedirect(it) }
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        finish()
    }
}
