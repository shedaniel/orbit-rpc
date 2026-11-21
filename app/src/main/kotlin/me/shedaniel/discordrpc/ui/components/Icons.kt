package me.shedaniel.discordrpc.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.ui.graphics.vector.ImageVector
import me.shedaniel.discordrpc.core.model.ActivityType

fun typeIcon(type: ActivityType): ImageVector = when (type) {
    ActivityType.PLAYING -> Icons.Rounded.SportsEsports
    ActivityType.LISTENING -> Icons.Rounded.Headphones
    ActivityType.WATCHING -> Icons.Rounded.LiveTv
    ActivityType.STREAMING -> Icons.Rounded.Videocam
    ActivityType.COMPETING -> Icons.Rounded.EmojiEvents
}

fun glyphIcon(key: String): ImageVector = when (key) {
    "music" -> Icons.Rounded.MusicNote
    "video" -> Icons.Rounded.LiveTv
    "game" -> Icons.Rounded.SportsEsports
    "podcast" -> Icons.Rounded.Podcasts
    "app" -> Icons.Rounded.Apps
    "spotify" -> Icons.Rounded.Speaker
    "always" -> Icons.Rounded.AutoAwesome
    else -> Icons.Rounded.Star
}
