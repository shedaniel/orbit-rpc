package me.shedaniel.discordrpc.core.rules

import me.shedaniel.discordrpc.core.model.ActivityType
import me.shedaniel.discordrpc.core.model.MediaKind
import me.shedaniel.discordrpc.core.model.StatusDisplay

object DefaultRules {
    const val GAMES = "builtin.games"
    const val MUSIC = "builtin.music"
    const val VIDEO = "builtin.video"
    const val PODCAST = "builtin.podcast"
    const val APPS = "builtin.apps"

    val games = Rule(
        id = GAMES,
        name = "Games",
        builtIn = true,
        glyph = "game",
        trigger = Trigger.App(games = true),
        template = ActivityTemplate(
            type = ActivityType.PLAYING,
            name = "{app}",
            largeImage = ImageSource.APP_ICON,
            largeText = "{app}",
            timestamps = TimestampMode.ELAPSED,
        ),
    )

    val music = Rule(
        id = MUSIC,
        name = "Music",
        builtIn = true,
        glyph = "music",
        trigger = Trigger.Media(kinds = setOf(MediaKind.MUSIC, MediaKind.UNKNOWN)),
        template = ActivityTemplate(
            type = ActivityType.LISTENING,
            name = "{app}",
            details = "{title}",
            state = "{artist}",
            largeImage = ImageSource.ARTWORK,
            largeText = "{album}",
            smallImage = ImageSource.APP_ICON,
            smallText = "{app}",
            timestamps = TimestampMode.PROGRESS,
            statusDisplay = StatusDisplay.DETAILS,
        ),
    )

    val podcast = Rule(
        id = PODCAST,
        name = "Podcasts",
        builtIn = true,
        glyph = "podcast",
        trigger = Trigger.Media(kinds = setOf(MediaKind.PODCAST, MediaKind.AUDIOBOOK)),
        template = ActivityTemplate(
            type = ActivityType.LISTENING,
            name = "{app}",
            details = "{title}",
            state = "{artist}",
            largeImage = ImageSource.ARTWORK,
            largeText = "{album}",
            smallImage = ImageSource.APP_ICON,
            smallText = "{app}",
            timestamps = TimestampMode.PROGRESS,
            statusDisplay = StatusDisplay.NAME,
        ),
    )

    val video = Rule(
        id = VIDEO,
        name = "Video",
        builtIn = true,
        glyph = "video",
        trigger = Trigger.Media(kinds = setOf(MediaKind.VIDEO)),
        template = ActivityTemplate(
            type = ActivityType.WATCHING,
            name = "{app}",
            details = "{title}",
            state = "{artist}",
            largeImage = ImageSource.ARTWORK,
            largeText = "{title}",
            smallImage = ImageSource.APP_ICON,
            smallText = "{app}",
            timestamps = TimestampMode.PROGRESS,
            statusDisplay = StatusDisplay.NAME,
        ),
    )

    /** Apps the user explicitly picked to show while open (non-games). */
    val apps = Rule(
        id = APPS,
        name = "Apps",
        builtIn = true,
        glyph = "app",
        trigger = Trigger.App(),
        template = ActivityTemplate(
            type = ActivityType.PLAYING,
            name = "{app}",
            largeImage = ImageSource.APP_ICON,
            largeText = "{app}",
            timestamps = TimestampMode.ELAPSED,
        ),
    )

    /** Priority order: first wins. */
    val all = listOf(games, music, podcast, video, apps)
}
