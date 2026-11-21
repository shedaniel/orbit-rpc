package me.shedaniel.discordrpc.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import me.shedaniel.discordrpc.core.settings.UiSettings

// Fallback palette (seed ≈ #6750F0, a violet that sits next to Discord's blurple without copying it).
private val LightFallback = lightColorScheme(
    primary = Color(0xFF5B4AC8), onPrimary = Color.White,
    primaryContainer = Color(0xFFE5DEFF), onPrimaryContainer = Color(0xFF1A0063),
    secondary = Color(0xFF605B71), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6DFF9), onSecondaryContainer = Color(0xFF1D192B),
    tertiary = Color(0xFF9A4050), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD9DD), onTertiaryContainer = Color(0xFF3F0013),
    background = Color(0xFFFCF8FF), onBackground = Color(0xFF1C1B20),
    surface = Color(0xFFFCF8FF), onSurface = Color(0xFF1C1B20),
    surfaceVariant = Color(0xFFE5E0EC), onSurfaceVariant = Color(0xFF48454F),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF6F2FA),
    surfaceContainer = Color(0xFFF0ECF4), surfaceContainerHigh = Color(0xFFEAE6EF),
    surfaceContainerHighest = Color(0xFFE5E1E9), outline = Color(0xFF79757F), outlineVariant = Color(0xFFC9C4D0),
)
private val DarkFallback = darkColorScheme(
    primary = Color(0xFFC8BFFF), onPrimary = Color(0xFF2C1A98),
    primaryContainer = Color(0xFF4331AF), onPrimaryContainer = Color(0xFFE5DEFF),
    secondary = Color(0xFFC9C3DC), onSecondary = Color(0xFF322E41),
    secondaryContainer = Color(0xFF484459), onSecondaryContainer = Color(0xFFE6DFF9),
    tertiary = Color(0xFFFFB2BC), onTertiary = Color(0xFF5F1225),
    tertiaryContainer = Color(0xFF7C2A3A), onTertiaryContainer = Color(0xFFFFD9DD),
    background = Color(0xFF141318), onBackground = Color(0xFFE5E1E9),
    surface = Color(0xFF141318), onSurface = Color(0xFFE5E1E9),
    surfaceVariant = Color(0xFF48454F), onSurfaceVariant = Color(0xFFC9C4D0),
    surfaceContainerLowest = Color(0xFF0F0E13), surfaceContainerLow = Color(0xFF1C1B20),
    surfaceContainer = Color(0xFF201F24), surfaceContainerHigh = Color(0xFF2B292F),
    surfaceContainerHighest = Color(0xFF36343A), outline = Color(0xFF938F99), outlineVariant = Color(0xFF48454F),
)

val OrbitShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
    largeIncreased = RoundedCornerShape(28.dp),
    extraLargeIncreased = RoundedCornerShape(40.dp),
    extraExtraLarge = RoundedCornerShape(48.dp),
)

private fun ColorScheme.amoled() = copy(
    background = Color.Black, surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0B0B0D),
    surfaceContainer = Color(0xFF111114),
    surfaceContainerHigh = Color(0xFF19191D),
    surfaceContainerHighest = Color(0xFF222227),
)

/** True when the active scheme is dark; used by components that need to pick their own tints. */
val LocalDarkTheme = staticCompositionLocalOf { false }

@Composable
fun OrbitTheme(ui: UiSettings, content: @Composable () -> Unit) {
    val dark = when (ui.themeMode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    val dynamic = ui.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    var scheme = when {
        dynamic && dark -> dynamicDarkColorScheme(context)
        dynamic -> dynamicLightColorScheme(context)
        dark -> DarkFallback
        else -> LightFallback
    }
    if (dark && ui.amoled) scheme = scheme.amoled()
    CompositionLocalProvider(LocalDarkTheme provides dark) {
        MaterialExpressiveTheme(
            colorScheme = scheme,
            motionScheme = MotionScheme.expressive(),
            shapes = OrbitShapes,
            typography = OrbitTypography,
            content = content,
        )
    }
}
