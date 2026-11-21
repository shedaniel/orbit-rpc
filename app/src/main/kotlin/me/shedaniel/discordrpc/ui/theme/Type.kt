package me.shedaniel.discordrpc.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import me.shedaniel.discordrpc.R

private fun flex(weight: Int, round: Float, width: Float = 100f) = Font(
    R.font.google_sans_flex,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight),
        FontVariation.width(width),
        FontVariation.Setting("ROND", round),
    ),
)

/** Text face: Google Sans Flex, slightly rounded. */
val SansFamily = FontFamily(listOf(300, 400, 500, 600, 700, 800).map { flex(it, 20f) })

/** Display face: fully rounded, used for emphasized and display styles. */
val RoundFamily = FontFamily(listOf(400, 500, 600, 700, 800, 900).map { flex(it, 100f) })

/** Condensed rounded face for big numbers. */
val NumberFamily = FontFamily(listOf(500, 700, 800).map { flex(it, 100f, 80f) })

private fun TextStyle.sans() = copy(fontFamily = SansFamily)
private fun TextStyle.round(weight: Int) = copy(fontFamily = RoundFamily, fontWeight = FontWeight(weight))

private val base = Typography()

val OrbitTypography = Typography(
    displayLarge = base.displayLarge.round(500).copy(letterSpacing = (-0.02).em),
    displayMedium = base.displayMedium.round(500).copy(letterSpacing = (-0.02).em),
    displaySmall = base.displaySmall.round(500),
    headlineLarge = base.headlineLarge.round(500),
    headlineMedium = base.headlineMedium.round(500),
    headlineSmall = base.headlineSmall.round(500),
    titleLarge = base.titleLarge.round(500),
    titleMedium = base.titleMedium.sans().copy(fontWeight = FontWeight(600)),
    titleSmall = base.titleSmall.sans().copy(fontWeight = FontWeight(600)),
    bodyLarge = base.bodyLarge.sans(),
    bodyMedium = base.bodyMedium.sans(),
    bodySmall = base.bodySmall.sans(),
    labelLarge = base.labelLarge.sans().copy(fontWeight = FontWeight(600)),
    labelMedium = base.labelMedium.sans().copy(fontWeight = FontWeight(600)),
    labelSmall = base.labelSmall.sans().copy(fontWeight = FontWeight(600)),
    displayLargeEmphasized = base.displayLargeEmphasized.round(800).copy(letterSpacing = (-0.03).em),
    displayMediumEmphasized = base.displayMediumEmphasized.round(800).copy(letterSpacing = (-0.03).em),
    displaySmallEmphasized = base.displaySmallEmphasized.round(800),
    headlineLargeEmphasized = base.headlineLargeEmphasized.round(750),
    headlineMediumEmphasized = base.headlineMediumEmphasized.round(750),
    headlineSmallEmphasized = base.headlineSmallEmphasized.round(700),
    titleLargeEmphasized = base.titleLargeEmphasized.round(700),
    titleMediumEmphasized = base.titleMediumEmphasized.round(700),
    titleSmallEmphasized = base.titleSmallEmphasized.round(700),
    bodyLargeEmphasized = base.bodyLargeEmphasized.sans().copy(fontWeight = FontWeight(600)),
    bodyMediumEmphasized = base.bodyMediumEmphasized.sans().copy(fontWeight = FontWeight(600)),
    bodySmallEmphasized = base.bodySmallEmphasized.sans().copy(fontWeight = FontWeight(600)),
    labelLargeEmphasized = base.labelLargeEmphasized.round(700),
    labelMediumEmphasized = base.labelMediumEmphasized.round(700),
    labelSmallEmphasized = base.labelSmallEmphasized.round(700),
)
