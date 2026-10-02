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
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.muir.bear.R
import com.muir.bear.ui.theme.ArchivoBlack
import com.muir.bear.ui.theme.BearColors
import com.muir.bear.ui.theme.CormorantItalic

/**
 * The logo: lowercase "bear" in Archivo Black with a bronze anvil as the full stop.
 * The anvil is as wide as the letter "a", sits on the text baseline, with a small gap after the "r".
 * Use this everywhere the logo appears so it's always identical.
 */
@Composable
fun BearWordmark(modifier: Modifier = Modifier, fontSize: TextUnit = 40.sp) {
    val style = remember(fontSize) { TextStyle(fontFamily = ArchivoBlack, fontSize = fontSize, color = BearColors.Parchment) }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val anvilWidth = with(density) { measurer.measure("a", style).size.width.toDp() }
    val gap = with(density) { (fontSize * 0.08f).toDp() }
    Row(modifier.clearAndSetSemantics { contentDescription = "bear" }) {
        Text("bear", style = style, modifier = Modifier.alignByBaseline())
        Spacer(Modifier.width(gap))
        Image(
            painterResource(R.drawable.bear_anvil),
            contentDescription = null,
            // Bottom of the anvil on the text baseline.
            modifier = Modifier.width(anvilWidth).aspectRatio(194f / 110f).alignBy { it.measuredHeight },
        )
    }
}

/** The motto, set in Cormorant Garamond italic, bronze. */
@Composable
fun BearMotto(modifier: Modifier = Modifier, fontSize: TextUnit = 17.sp) {
    Text(
        "Durum patientia frango",
        modifier = modifier,
        style = TextStyle(fontFamily = CormorantItalic, fontStyle = FontStyle.Italic, fontSize = fontSize, color = BearColors.Bronze),
    )
}
