package me.shedaniel.discordrpc.ui.components

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import me.shedaniel.discordrpc.R
import me.shedaniel.discordrpc.core.model.ActivityType
import me.shedaniel.discordrpc.core.model.ImageRef
import me.shedaniel.discordrpc.core.model.PresenceActivity
import me.shedaniel.discordrpc.ui.theme.NumberFamily

@StringRes
fun ActivityType.verbRes(): Int = when (this) {
    ActivityType.PLAYING -> R.string.verb_playing
    ActivityType.LISTENING -> R.string.verb_listening
    ActivityType.WATCHING -> R.string.verb_watching
    ActivityType.STREAMING -> R.string.verb_streaming
    ActivityType.COMPETING -> R.string.verb_competing
}

/** Wall clock that ticks every [periodMs] while composed. */
@Composable
fun rememberNow(periodMs: Long = 1000): State<Long> = produceState(System.currentTimeMillis()) {
    while (true) {
        value = System.currentTimeMillis()
        delay(periodMs - (value % periodMs))
    }
}

fun formatDuration(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/**
 * How an activity will look on a profile. Not a pixel copy of Discord: it speaks the app's own
 * visual language while keeping the same information hierarchy.
 */
@Composable
fun PresenceCard(
    activity: PresenceActivity,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    previewUrl: (ImageRef) -> String? = { null },
    live: Boolean = true,
) {
    Surface(modifier, shape = MaterialTheme.shapes.extraLarge, color = container) {
        Column(Modifier.padding(16.dp)) {
            val fin = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
            val fout = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
            AnimatedContent(
                targetState = stringResource(activity.type.verbRes(), activity.name),
                transitionSpec = { fadeIn(fin) togetherWith fadeOut(fout) },
                label = "verb",
            ) { verb ->
                Text(
                    verb,
                    style = MaterialTheme.typography.labelLargeEmphasized,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ActivityArt(activity, previewUrl)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (activity.details == null && activity.state == null) {
                        Text(
                            activity.name,
                            style = MaterialTheme.typography.titleMediumEmphasized,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    activity.details?.let {
                        Text(it, style = MaterialTheme.typography.titleMediumEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    activity.state?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    TimeLine(activity, live)
                }
            }
            AnimatedVisibility(activity.buttons.isNotEmpty()) {
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    activity.buttons.forEach { b ->
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                b.label,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(vertical = 10.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityArt(activity: PresenceActivity, previewUrl: (ImageRef) -> String?) {
    Box(Modifier.size(88.dp)) {
        if (activity.largeImage != null) {
            RefImage(activity.largeImage, Modifier.size(80.dp).clip(RoundedCornerShape(18.dp)), previewUrl)
        } else {
            // No image: the activity is still recognisable by shape alone.
            ShapeGlyph(
                icon = typeIcon(activity.type),
                polygon = androidx.compose.material3.MaterialShapes.Cookie9Sided,
                size = 80.dp,
            )
        }
        if (activity.smallImage != null) {
            val ring = MaterialTheme.colorScheme.surfaceContainerHigh
            RefImage(
                activity.smallImage,
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 2.dp, y = 2.dp)
                    .size(30.dp)
                    .border(3.dp, ring, CircleShape)
                    .clip(CircleShape),
                previewUrl,
            )
        }
    }
}

@Composable
private fun TimeLine(activity: PresenceActivity, live: Boolean) {
    val start = activity.startMs ?: return
    val now by rememberNow()
    val end = activity.endMs
    Spacer(Modifier.height(6.dp))
    if (end != null && end > start) {
        val total = end - start
        val pos = if (live) (now - start).coerceIn(0, total) else total / 3
        LinearWavyProgressIndicator(
            progress = { pos.toFloat() / total },
            modifier = Modifier.fillMaxWidth().height(10.dp),
        )
        Spacer(Modifier.height(4.dp))
        Row {
            Text(formatDuration(pos), style = MaterialTheme.typography.labelSmall.copy(fontFamily = NumberFamily), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text(formatDuration(total), style = MaterialTheme.typography.labelSmall.copy(fontFamily = NumberFamily), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        Text(
            stringResource(R.string.card_elapsed, formatDuration(if (live) now - start else 754_000)),
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = NumberFamily, fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
