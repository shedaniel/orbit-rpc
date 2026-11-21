package me.shedaniel.discordrpc.core.rules

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.shedaniel.discordrpc.core.model.ActivityType
import me.shedaniel.discordrpc.core.model.MediaKind
import me.shedaniel.discordrpc.core.model.StatusDisplay

/** What makes a rule fire. */
@Serializable
sealed interface Trigger {
    /** Media playback. Empty [packages] = any included app. */
    @Serializable
    @SerialName("media")
    data class Media(
        val kinds: Set<MediaKind> = emptySet(),
        val packages: Set<String> = emptySet(),
        val includePaused: Boolean = false,
        /** Also match Spotify Connect (remote) playback. */
        val includeRemote: Boolean = true,
    ) : Trigger

    /** An app is in the foreground. Empty [packages] + [games] = any game. */
    @Serializable
    @SerialName("app")
    data class App(
        val packages: Set<String> = emptySet(),
        val games: Boolean = false,
    ) : Trigger

    /** Always on (a custom status that shows whenever nothing else does). */
    @Serializable
    @SerialName("always")
    data object Always : Trigger
}

@Serializable
enum class ImageSource {
    NONE,
    /** Album art / thumbnail, falls back to the app icon. */
    ARTWORK,
    APP_ICON,
    /** The fixed [ActivityTemplate.largeImageUrl] / [ActivityTemplate.smallImageUrl]. */
    URL,
}

@Serializable
enum class TimestampMode {
    NONE,
    /** "00:42 elapsed" since the app / track started. */
    ELAPSED,
    /** A progress bar for media with a known duration (falls back to elapsed). */
    PROGRESS,
}

@Serializable
data class ButtonTemplate(val label: String, val url: String)

/**
 * Text fields accept placeholders: {title} {artist} {album} {app} {device} {kind}.
 * Square brackets mark an optional section that disappears when any placeholder inside is empty,
 * e.g. "[by {artist}]".
 */
@Serializable
data class ActivityTemplate(
    val type: ActivityType = ActivityType.PLAYING,
    /** null = automatic: LISTENING for music/podcasts, WATCHING for video, PLAYING otherwise. */
    val autoType: Boolean = false,
    val name: String = "{app}",
    val details: String = "",
    val state: String = "",
    val largeImage: ImageSource = ImageSource.NONE,
    val largeImageUrl: String = "",
    val largeText: String = "",
    val smallImage: ImageSource = ImageSource.NONE,
    val smallImageUrl: String = "",
    val smallText: String = "",
    val timestamps: TimestampMode = TimestampMode.NONE,
    val buttons: List<ButtonTemplate> = emptyList(),
    val statusDisplay: StatusDisplay = StatusDisplay.NAME,
    /** Link the details line to the media link when one is known. */
    val linkDetails: Boolean = true,
)

@Serializable
data class Rule(
    val id: String,
    val name: String,
    val enabled: Boolean = true,
    val trigger: Trigger,
    val template: ActivityTemplate,
    /** Built-in rules can be edited and disabled, not deleted. */
    val builtIn: Boolean = false,
    /** Icon hint for the UI; one of the RuleGlyph names. */
    val glyph: String = "",
)

@Serializable
data class RuleSet(val rules: List<Rule> = DefaultRules.all)
