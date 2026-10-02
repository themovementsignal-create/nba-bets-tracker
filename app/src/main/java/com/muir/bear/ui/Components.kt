@file:OptIn(ExperimentalMaterial3Api::class)

package com.muir.bear.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// ---------- Formatting ----------

private val dayFmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
private val dayYearFmt = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.getDefault())
private val timeFmt = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

fun Long.toLocalDateTime() = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDateTime()
fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

fun fmtDate(millis: Long): String {
    val d = millis.toLocalDate()
    return if (d.year == LocalDate.now().year) d.format(dayFmt) else d.format(dayYearFmt)
}

fun fmtDay(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).let {
    if (it.year == LocalDate.now().year) it.format(dayFmt) else it.format(dayYearFmt)
}

fun fmtTime(millis: Long): String = millis.toLocalDateTime().format(timeFmt)
fun fmtDateTime(millis: Long): String = "${fmtDate(millis)}, ${fmtTime(millis)}"

fun fmtMinutes(min: Long): String = if (min >= 60) "${min / 60}h ${min % 60}m" else "${min}m"

fun fmtClock(totalSeconds: Long): String {
    val s = totalSeconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

fun today(): Long = LocalDate.now().toEpochDay()

// ---------- Layout pieces ----------

@Composable
fun AppTopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            if (onBack != null) TextButton(onClick = onBack) { Text("‹ Back", style = MaterialTheme.typography.titleMedium) }
        },
        actions = actions,
    )
}

@Composable
fun SectionCard(
    title: String? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    val inner: @Composable () -> Unit = {
        Column(Modifier.padding(16.dp)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
            }
            content()
        }
    }
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier.fillMaxWidth(), colors = colors) { inner() }
    } else {
        Card(modifier = modifier.fillMaxWidth(), colors = colors) { inner() }
    }
}

@Composable
fun Muted(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    decimal: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onValueChange(v.filter { it.isDigit() || it == '.' || it == ',' || it == '-' }) },
        label = if (label.isNotEmpty()) ({ Text(label, maxLines = 1) }) else null,
        placeholder = if (placeholder.isNotEmpty()) ({ Text(placeholder, maxLines = 1) }) else null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier,
    )
}

@Composable
fun TextInput(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, singleLine: Boolean = true) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        modifier = modifier.fillMaxWidth(),
    )
}

/** Row of big buttons 1..max (or from min) for ratings like soreness or RPE. */
@Composable
fun RatingRow(value: Int?, onChange: (Int) -> Unit, range: IntRange = 1..5, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (i in range) {
            val selected = value == i
            if (selected) {
                Button(
                    onClick = { onChange(i) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    contentPadding = PaddingValues(0.dp),
                ) { Text("$i") }
            } else {
                OutlinedButton(
                    onClick = { onChange(i) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    contentPadding = PaddingValues(0.dp),
                ) { Text("$i") }
            }
        }
    }
}

@Composable
fun BigButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, secondary: Boolean = false) {
    if (secondary) {
        OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(text, style = MaterialTheme.typography.titleMedium)
        }
    } else {
        Button(onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(text, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String = "Delete", onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(confirm, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ListRow(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    Row(modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Muted(subtitle)
        }
        trailing()
    }
}

@Composable
fun StatLine(label: String, value: String, valueColor: Color = Color.Unspecified) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold, color = valueColor)
    }
}

// ---------- Charts (drawn by hand to avoid a chart dependency) ----------

/**
 * Simple line chart. [series] are lists of (x, y); all share the same scale.
 * [targetY] draws a dashed horizontal line.
 */
@Composable
fun LineChart(
    series: List<List<Pair<Float, Float>>>,
    colors: List<Color>,
    modifier: Modifier = Modifier,
    targetY: Float? = null,
    showPoints: Boolean = true,
) {
    val all = series.flatten()
    if (all.isEmpty()) {
        Muted("No data yet", modifier.padding(vertical = 16.dp))
        return
    }
    val xs = all.map { it.first }
    val ys = all.map { it.second } + listOfNotNull(targetY)
    val minX = xs.min()
    val maxX = xs.max()
    var minY = ys.min()
    var maxY = ys.max()
    if (maxY - minY < 1e-3f) { minY -= 1f; maxY += 1f }
    val pad = (maxY - minY) * 0.1f
    minY -= pad; maxY += pad
    val grid = MaterialTheme.colorScheme.outlineVariant
    val targetColor = MaterialTheme.colorScheme.tertiary
    Column(modifier) {
        Row {
            Muted(fmtAxis(maxY), Modifier.weight(1f))
        }
        Canvas(Modifier.fillMaxWidth().height(160.dp)) {
            fun px(x: Float) = if (maxX - minX < 1e-6f) size.width / 2 else (x - minX) / (maxX - minX) * size.width
            fun py(y: Float) = size.height - (y - minY) / (maxY - minY) * size.height
            drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), 2f)
            if (targetY != null) {
                drawLine(
                    targetColor, Offset(0f, py(targetY)), Offset(size.width, py(targetY)), 3f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)),
                )
            }
            series.forEachIndexed { si, pts ->
                val c = colors.getOrElse(si) { Color.Gray }
                val sorted = pts.sortedBy { it.first }
                if (sorted.size > 1) {
                    val path = Path()
                    sorted.forEachIndexed { i, (x, y) -> if (i == 0) path.moveTo(px(x), py(y)) else path.lineTo(px(x), py(y)) }
                    drawPath(path, c, style = Stroke(width = 5f))
                }
                if (showPoints || sorted.size == 1) sorted.forEach { (x, y) -> drawCircle(c, 7f, Offset(px(x), py(y))) }
            }
        }
        Muted(fmtAxis(minY))
    }
}

private fun fmtAxis(v: Float): String = if (kotlin.math.abs(v) >= 100) "%.0f".format(v) else "%.1f".format(v)

/** Vertical bar chart with optional highlighted bars and labels underneath. */
@Composable
fun BarChart(values: List<Float>, labels: List<String>, highlight: List<Boolean>, modifier: Modifier = Modifier) {
    if (values.isEmpty() || values.all { it <= 0f }) {
        Muted("No data yet", modifier.padding(vertical = 16.dp))
        return
    }
    val maxV = values.max().coerceAtLeast(1f)
    val normal = MaterialTheme.colorScheme.primary
    val warn = MaterialTheme.colorScheme.error
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val slot = size.width / values.size
            val barW = slot * 0.6f
            values.forEachIndexed { i, v ->
                val h = v / maxV * size.height
                drawRect(
                    color = if (highlight.getOrElse(i) { false }) warn else normal,
                    topLeft = Offset(i * slot + (slot - barW) / 2, size.height - h),
                    size = androidx.compose.ui.geometry.Size(barW, h),
                )
            }
        }
        Row(Modifier.fillMaxWidth()) {
            labels.forEach { Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, maxLines = 1) }
        }
    }
}

/** Scatter plot of (x, y) points. */
@Composable
fun ScatterChart(points: List<Pair<Float, Float>>, modifier: Modifier = Modifier) {
    if (points.size < 2) {
        Muted("Need a few more nights of sleep + sessions to plot this.", modifier.padding(vertical = 8.dp))
        return
    }
    val c = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val minX = points.minOf { it.first }; val maxX = points.maxOf { it.first }
    val minY = points.minOf { it.second }; val maxY = points.maxOf { it.second }
    Box(modifier) {
        Canvas(Modifier.fillMaxWidth().height(140.dp)) {
            fun px(x: Float) = if (maxX - minX < 1e-6f) size.width / 2 else (x - minX) / (maxX - minX) * (size.width - 20) + 10
            fun py(y: Float) = if (maxY - minY < 1e-6f) size.height / 2 else size.height - 10 - (y - minY) / (maxY - minY) * (size.height - 20)
            drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), 2f)
            drawLine(grid, Offset(0f, 0f), Offset(0f, size.height), 2f)
            points.forEach { (x, y) -> drawCircle(c, 9f, Offset(px(x), py(y))) }
        }
    }
}

@Composable
fun Gap(h: Int = 12) = Spacer(Modifier.height(h.dp))

@Composable
fun HGap(w: Int = 8) = Spacer(Modifier.width(w.dp))

@Composable
fun DangerTextButton(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
        Text(text)
    }
}

@Composable
fun SmallSquareButton(text: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(48.dp)) { Text(text) }
}
