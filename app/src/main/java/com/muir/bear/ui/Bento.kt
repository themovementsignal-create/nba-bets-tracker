package com.muir.bear.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

// The soft "bento" look shared by every screen: tinted glow, hairline edge, gentle motion.

/** Corner glow tints, all warm and muted: bronze, sage-grey, sand. */
private val GlowTints = listOf(Color(0xFFA8875A), Color(0xFF8C9687), Color(0xFFBEAA8C))

/** A stable tint per card title, so the same card always looks the same. */
fun glowFor(key: String?): Color = GlowTints[((key?.hashCode() ?: 0) and 0x7fffffff) % GlowTints.size]

/** Card surface: rounded, faint glow from the top-right corner, hairline edge, optional tap. */
@Composable
fun Modifier.bentoSurface(glow: Color, onClick: (() -> Unit)? = null, glowAlpha: Float = 0.14f): Modifier {
    val shape = MaterialTheme.shapes.medium
    val base = MaterialTheme.colorScheme.surfaceContainer
    return this
        .riseIn()
        .clip(shape)
        .background(base)
        .drawBehind {
            drawRect(
                Brush.radialGradient(
                    listOf(glow.copy(alpha = glowAlpha), Color.Transparent),
                    center = Offset(size.width, 0f),
                    radius = size.maxDimension * 0.9f,
                ),
            )
        }
        .border(1.dp, Color(0x10E9E3D7), shape)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
}

/**
 * Fades and lifts a card into place, but only in the moment a screen opens (never when you scroll
 * back to it), with a small stagger from top to bottom.
 */
@Composable
fun Modifier.riseIn(): Modifier {
    val animate = remember { Motion.justOpened() }
    if (!animate) return this
    val order = remember { Motion.risen++ }
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay((order * 45L).coerceAtMost(270L))
        p.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
    }
    return this.graphicsLayer {
        alpha = p.value
        translationY = (1f - p.value) * 14.dp.toPx()
    }
}

/** A card's quiet label, with a small ↗ when tapping the card opens more. */
@Composable
fun CardLabel(text: String, arrow: Boolean = false, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text,
            Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
        )
        if (arrow) ArrowUpRight()
    }
}

@Composable
fun ArrowUpRight() {
    val c = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(Modifier.size(14.dp)) {
        val w = size.width
        val stroke = w * 0.12f
        drawLine(c, Offset(w * 0.22f, w * 0.78f), Offset(w * 0.78f, w * 0.22f), stroke, StrokeCap.Round)
        drawLine(c, Offset(w * 0.36f, w * 0.22f), Offset(w * 0.78f, w * 0.22f), stroke, StrokeCap.Round)
        drawLine(c, Offset(w * 0.78f, w * 0.22f), Offset(w * 0.78f, w * 0.64f), stroke, StrokeCap.Round)
    }
}

/**
 * A number that counts to its value when the screen opens, starting from [from] (e.g. yesterday's
 * value, so the motion itself shows the change). Otherwise it just shows the value.
 */
@Composable
fun CountUp(value: Double, format: (Double) -> String, style: TextStyle, from: Double? = null, color: Color = Color.Unspecified) {
    val anim = remember { Animatable(value.toFloat()) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(value) {
        if (first && from != null && Motion.justOpened(2_000)) {
            anim.snapTo(from.toFloat())
            anim.animateTo(value.toFloat(), tween(900, easing = FastOutSlowInEasing))
        } else {
            anim.snapTo(value.toFloat())
        }
        first = false
    }
    Text(format(anim.value.toDouble()), style = style, color = color)
}

/** 0 → 1 once when the screen opens (for charts that draw themselves in); 1 otherwise. */
@Composable
fun rememberDrawIn(durationMs: Int = 800, delayMs: Long = 150): Float {
    val animate = remember { Motion.justOpened(2_000) }
    val p = remember { Animatable(if (animate) 0f else 1f) }
    if (animate) LaunchedEffect(Unit) {
        delay(delayMs)
        p.animateTo(1f, tween(durationMs, easing = FastOutSlowInEasing))
    }
    return p.value
}

/** A slow, faint breathing (0.45–1), for the one card that sums up today. Still in tests. */
@Composable
fun rememberBreath(): Float {
    if (!Motion.animateLists) return 1f
    val t = rememberInfiniteTransition(label = "breath")
    val v by t.animateFloat(0.45f, 1f, infiniteRepeatable(tween(6_000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "glow")
    return v
}

/** Half-circle gauge filled to [fraction] (0–1), with a dot at the end. */
@Composable
fun HalfGauge(fraction: Float, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val fill = MaterialTheme.colorScheme.primary
    val dot = MaterialTheme.colorScheme.onSurface
    val shown = fraction.coerceIn(0f, 1f) * rememberDrawIn(900)
    Canvas(modifier) {
        val stroke = size.width * 0.055f
        val d = size.width - stroke
        val topLeft = Offset(stroke / 2, stroke / 2)
        val arc = Size(d, d)
        drawArc(track, 180f, 180f, false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Round))
        drawArc(fill, 180f, 180f * shown, false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Round))
        val angle = Math.toRadians((180.0 + 180.0 * shown))
        val r = d / 2
        val c = Offset(size.width / 2, size.width / 2)
        if (shown > 0.02f) drawCircle(dot, stroke * 0.85f, Offset(c.x + (r * kotlin.math.cos(angle)).toFloat(), c.y + (r * kotlin.math.sin(angle)).toFloat()))
    }
}
