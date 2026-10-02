package com.muir.bear.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.muir.bear.R
import com.muir.bear.ui.theme.ArchivoBlack
import com.muir.bear.ui.theme.BearColors
import com.muir.bear.ui.theme.CormorantItalic

/**
 * The logo: "BEAR" in Archivo Black capitals with a small bronze anvil as the full stop.
 * The anvil is full-stop sized (0.38 × the font size wide), sits on the text baseline, with a
 * small gap after the "R". Use this everywhere the logo appears so it's always identical.
 */
@Composable
fun BearWordmark(modifier: Modifier = Modifier, fontSize: TextUnit = 36.sp) {
    val style = remember(fontSize) {
        TextStyle(fontFamily = ArchivoBlack, fontSize = fontSize, color = BearColors.Parchment, letterSpacing = fontSize * 0.02f)
    }
    val density = LocalDensity.current
    val anvilWidth = with(density) { (fontSize * 0.38f).toDp() }
    val gap = with(density) { (fontSize * 0.06f).toDp() }
    Row(modifier.clearAndSetSemantics { contentDescription = "Bear" }) {
        Text("BEAR", style = style, modifier = Modifier.alignByBaseline())
        Spacer(Modifier.width(gap))
        Image(
            painterResource(R.drawable.bear_anvil),
            contentDescription = null,
            // Bottom of the anvil on the text baseline.
            modifier = Modifier.width(anvilWidth).aspectRatio(194f / 110f).alignBy { it.measuredHeight },
        )
    }
}

/** The motto: a quiet line in Cormorant Garamond italic, softened bronze. */
@Composable
fun BearMotto(modifier: Modifier = Modifier, fontSize: TextUnit = 14.sp) {
    Text(
        "Durum patientia frango",
        modifier = modifier,
        style = TextStyle(
            fontFamily = CormorantItalic, fontStyle = FontStyle.Italic, fontSize = fontSize,
            color = BearColors.Bronze.copy(alpha = 0.85f), letterSpacing = fontSize * 0.02f,
        ),
    )
}
