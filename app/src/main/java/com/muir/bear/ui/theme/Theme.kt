@file:OptIn(ExperimentalTextApi::class)

package com.muir.bear.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.muir.bear.R

// Bear palette: near-black, warm off-white text and a single muted bronze accent (the anvil's bronze).
// Quiet and warm; every role is set explicitly so no default (purple) Material colours leak through.
object BearColors {
    val Bronze = Color(0xFFA8875A)
    /** Wordmark and main text colour: a warm off-white. */
    val Parchment = Color(0xFFE9E3D7)
    val BronzeDeep = Color(0xFF3A2F20)
    val BronzeSoft = Color(0xFFE6D3B3)
    val Stone = Color(0xFFC9B79A)
    val Ink = Color(0xFF121212)
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
    onBackground = BearColors.Parchment,
    surface = BearColors.Ink,
    onSurface = BearColors.Parchment,
    surfaceVariant = Color(0xFF2B2A28),
    onSurfaceVariant = Color(0xFFB3AFA7),
    surfaceTint = Color(0xFF8E8E8E),
    inverseSurface = BearColors.Parchment,
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

// Fonts are bundled in res/font (SIL Open Font License, see /licenses). Missing glyphs such as
// emoji or arrows fall back to the phone's system font automatically.

/** Archivo Black: the wordmark only. */
val ArchivoBlack = FontFamily(Font(R.font.archivo_black, FontWeight.Normal))

private fun archivo(weight: Int) =
    Font(R.font.archivo, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

/** Archivo: the app's everyday typeface (one variable font file, several weights). */
val Archivo = FontFamily(archivo(400), archivo(500), archivo(600), archivo(700))

/** Cormorant Garamond italic: the motto line only. */
val CormorantItalic = FontFamily(
    Font(
        R.font.cormorant_garamond_italic, FontWeight.Medium, FontStyle.Italic,
        variationSettings = FontVariation.Settings(FontVariation.weight(500)),
    ),
)

private val base = Typography()

private fun TextStyle.archivo(weight: FontWeight? = null) = copy(fontFamily = Archivo, fontWeight = weight ?: fontWeight)

private val BearTypography = Typography(
    displayLarge = base.displayLarge.archivo(FontWeight.SemiBold),
    displayMedium = base.displayMedium.archivo(FontWeight.SemiBold),
    displaySmall = base.displaySmall.archivo(FontWeight.SemiBold),
    headlineLarge = base.headlineLarge.archivo(FontWeight.SemiBold),
    headlineMedium = base.headlineMedium.archivo(FontWeight.SemiBold),
    headlineSmall = base.headlineSmall.archivo(FontWeight.SemiBold),
    titleLarge = base.titleLarge.archivo(FontWeight.SemiBold),
    titleMedium = base.titleMedium.archivo(FontWeight.SemiBold),
    titleSmall = base.titleSmall.archivo(FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.archivo(),
    bodyMedium = base.bodyMedium.archivo(),
    bodySmall = base.bodySmall.archivo(),
    labelLarge = base.labelLarge.archivo(FontWeight.SemiBold),
    labelMedium = base.labelMedium.archivo(),
    labelSmall = base.labelSmall.archivo(),
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
