@file:OptIn(ExperimentalTextApi::class)

package com.muir.bear.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

// Bear palette: near-black with a single muted bronze accent (the same bronze as the stone icon).
// Quiet and warm; every role is set explicitly so no default (purple) Material colours leak through.
object BearColors {
    val Bronze = Color(0xFFA8875A)
    val BronzeDeep = Color(0xFF3A2F20)
    val BronzeSoft = Color(0xFFE6D3B3)
    val Stone = Color(0xFFC9B79A)
    val Ink = Color(0xFF121212)
    val Bone = Color(0xFFECEAE6)
    val Red = Color(0xFFE5776A)
}

private val BearDark = darkColorScheme(
    primary = BearColors.Bronze,
    onPrimary = Color(0xFF1A1408),
    primaryContainer = BearColors.BronzeDeep,
    onPrimaryContainer = BearColors.BronzeSoft,
    inversePrimary = Color(0xFF6E5432),
    secondary = Color(0xFFBFAE90),
    onSecondary = Color(0xFF261E10),
    secondaryContainer = Color(0xFF332B1F),
    onSecondaryContainer = Color(0xFFEADFCB),
    tertiary = BearColors.Stone,
    onTertiary = Color(0xFF2A2214),
    tertiaryContainer = Color(0xFF3D3428),
    onTertiaryContainer = Color(0xFFEFE3CF),
    background = BearColors.Ink,
    onBackground = BearColors.Bone,
    surface = BearColors.Ink,
    onSurface = BearColors.Bone,
    surfaceVariant = Color(0xFF2B2A28),
    onSurfaceVariant = Color(0xFFB3AFA7),
    surfaceTint = Color(0xFF8E8E8E),
    inverseSurface = BearColors.Bone,
    inverseOnSurface = Color(0xFF1A1A1A),
    error = BearColors.Red,
    onError = Color(0xFF3D0A05),
    errorContainer = Color(0xFF5C1A12),
    onErrorContainer = Color(0xFFFFDAD4),
    outline = Color(0xFF6A6761),
    outlineVariant = Color(0xFF3A3936),
    scrim = Color.Black,
    surfaceBright = Color(0xFF353432),
    surfaceContainer = Color(0xFF1C1C1B),
    surfaceContainerHigh = Color(0xFF242423),
    surfaceContainerHighest = Color(0xFF2D2D2B),
    surfaceContainerLow = Color(0xFF181818),
    surfaceContainerLowest = Color(0xFF0C0C0C),
    surfaceDim = BearColors.Ink,
)

/** Built-in condensed sans (Roboto Condensed on most phones) for a scoreboard feel. Falls back to the default font. */
val Condensed = FontFamily(
    Font(DeviceFontFamilyName("sans-serif-condensed"), FontWeight.Normal),
    Font(DeviceFontFamilyName("sans-serif-condensed"), FontWeight.Medium),
    Font(DeviceFontFamilyName("sans-serif-condensed"), FontWeight.SemiBold),
    Font(DeviceFontFamilyName("sans-serif-condensed"), FontWeight.Bold),
)

private val base = Typography()

private val BearTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = Condensed, fontWeight = FontWeight.Bold),
    displayMedium = base.displayMedium.copy(fontFamily = Condensed, fontWeight = FontWeight.Bold),
    displaySmall = base.displaySmall.copy(fontFamily = Condensed, fontWeight = FontWeight.Bold),
    headlineLarge = base.headlineLarge.copy(fontFamily = Condensed, fontWeight = FontWeight.Bold),
    headlineMedium = base.headlineMedium.copy(fontFamily = Condensed, fontWeight = FontWeight.Bold),
    headlineSmall = base.headlineSmall.copy(fontFamily = Condensed, fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontFamily = Condensed, fontWeight = FontWeight.Bold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge,
    bodyMedium = base.bodyMedium,
    bodySmall = base.bodySmall,
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium,
    labelSmall = base.labelSmall,
)

// Dark theme only (gym floor).
@Composable
fun BearTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BearDark,
        typography = BearTypography,
        content = content,
    )
}
