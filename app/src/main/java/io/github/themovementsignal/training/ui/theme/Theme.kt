@file:OptIn(ExperimentalTextApi::class)

package io.github.themovementsignal.training.ui.theme

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

// Agon palette: near-black with a championship-gold accent and bronze highlights.
// Every role is set explicitly so no default (purple) Material colours leak through.
object AgonColors {
    val Gold = Color(0xFFF5C542)
    val GoldDeep = Color(0xFF3B2F00)
    val GoldSoft = Color(0xFFFFE39A)
    val Bronze = Color(0xFFE8955A)
    val Ink = Color(0xFF0D0D0E)
    val Bone = Color(0xFFECEAE6)
    val Red = Color(0xFFFF6B5E)
}

private val AgonDark = darkColorScheme(
    primary = AgonColors.Gold,
    onPrimary = Color(0xFF1B1500),
    primaryContainer = AgonColors.GoldDeep,
    onPrimaryContainer = AgonColors.GoldSoft,
    inversePrimary = Color(0xFF7A5C00),
    secondary = Color(0xFFD4BC7A),
    onSecondary = Color(0xFF2A2100),
    secondaryContainer = Color(0xFF3A3220),
    onSecondaryContainer = Color(0xFFF2E2B8),
    tertiary = AgonColors.Bronze,
    onTertiary = Color(0xFF3A1A00),
    tertiaryContainer = Color(0xFF5A3418),
    onTertiaryContainer = Color(0xFFFFD9C0),
    background = AgonColors.Ink,
    onBackground = AgonColors.Bone,
    surface = AgonColors.Ink,
    onSurface = AgonColors.Bone,
    surfaceVariant = Color(0xFF2A2A2C),
    onSurfaceVariant = Color(0xFFB3B0A8),
    surfaceTint = Color(0xFF8E8E8E),
    inverseSurface = AgonColors.Bone,
    inverseOnSurface = Color(0xFF1A1A1A),
    error = AgonColors.Red,
    onError = Color(0xFF3D0A05),
    errorContainer = Color(0xFF5C1A12),
    onErrorContainer = Color(0xFFFFDAD4),
    outline = Color(0xFF6A6862),
    outlineVariant = Color(0xFF3A3936),
    scrim = Color.Black,
    surfaceBright = Color(0xFF333336),
    surfaceContainer = Color(0xFF18181A),
    surfaceContainerHigh = Color(0xFF212123),
    surfaceContainerHighest = Color(0xFF2A2A2D),
    surfaceContainerLow = Color(0xFF121213),
    surfaceContainerLowest = Color(0xFF080809),
    surfaceDim = AgonColors.Ink,
)

/** Built-in condensed sans (Roboto Condensed on most phones) for a scoreboard feel. Falls back to the default font. */
val Condensed = FontFamily(
    Font(DeviceFontFamilyName("sans-serif-condensed"), FontWeight.Normal),
    Font(DeviceFontFamilyName("sans-serif-condensed"), FontWeight.Medium),
    Font(DeviceFontFamilyName("sans-serif-condensed"), FontWeight.SemiBold),
    Font(DeviceFontFamilyName("sans-serif-condensed"), FontWeight.Bold),
)

private val base = Typography()

private val AgonTypography = Typography(
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
fun TrainingTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AgonDark,
        typography = AgonTypography,
        content = content,
    )
}
