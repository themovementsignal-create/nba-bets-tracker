package com.muir.bear.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.muir.bear.Graph
import com.muir.bear.data.HrvReading
import com.muir.bear.domain.Ppg
import com.muir.bear.domain.Readiness
import com.muir.bear.hrv.PulseCamera
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Finger on: the camera adjusts for a few seconds, then brightness is locked, then a short wait. */
private const val LOCK_AT_S = 4.0
private const val FIRST_SETTLE_S = 8.0
/** After a slip, a shorter wait before counting again. */
private const val RESETTLE_S = 3.0
/** Seconds of clean, finger-on recording needed. */
private const val MEASURE_S = 60.0
/** Window for the live heart rate and signal bar. */
private const val LIVE_S = 8.0
/** Shorter flickers off the lens don't count as a slip. */
private const val SLIP_S = 0.4
/** No finger for this long: stop, so the torch isn't left burning. */
private const val GIVE_UP_S = 60.0

/** What the recording looks like right now, for the screen. */
private data class Snapshot(
    val now: Double,
    val covered: Boolean,
    val coveredFor: Double,
    val uncoveredFor: Double,
    val counting: Boolean,
    val measured: Double,
)

/**
 * Collects samples from the camera thread. Only finger-on samples after the settle time are kept,
 * in separate stretches: when the finger slips the current stretch ends and the clock pauses.
 */
private class Recording(private val onLock: () -> Unit) {
    private val lock = Any()
    private val segments = mutableListOf<Pair<ArrayList<Double>, ArrayList<Double>>>()
    private var current: Pair<ArrayList<Double>, ArrayList<Double>>? = null
    private val recentT = ArrayDeque<Double>()
    private val recentV = ArrayDeque<Double>()
    private var origin = Double.NaN
    private var now = 0.0
    private var covered = false
    private var coveredSince = Double.NaN
    private var uncoveredSince = Double.NaN
    private var offSince = Double.NaN
    private var settle = FIRST_SETTLE_S
    private var lockAsked = false
    private var measured = 0.0

    fun add(time: Double, red: Double, isCovered: Boolean): Unit = synchronized(lock) {
        if (origin.isNaN()) origin = time
        val x = time - origin
        now = x
        if (!isCovered) {
            if (offSince.isNaN()) offSince = x
            if (covered && x - offSince < SLIP_S) return@synchronized // a flicker: skip the frame
            if (covered || uncoveredSince.isNaN()) uncoveredSince = offSince
            covered = false
            coveredSince = Double.NaN
            current = null
            recentT.clear(); recentV.clear()
            return@synchronized
        }
        offSince = Double.NaN
        if (!covered) { coveredSince = x; uncoveredSince = Double.NaN }
        covered = true
        recentT.addLast(x); recentV.addLast(red)
        while (recentT.first() < x - LIVE_S) { recentT.removeFirst(); recentV.removeFirst() }
        val on = x - coveredSince
        if (!lockAsked && on >= LOCK_AT_S) { lockAsked = true; onLock() }
        if (on < settle) return@synchronized
        settle = RESETTLE_S
        val seg = current ?: (ArrayList<Double>() to ArrayList<Double>()).also { current = it; segments += it }
        seg.first.lastOrNull()?.let { measured += x - it }
        seg.first += x; seg.second += red
    }

    fun snapshot(): Snapshot = synchronized(lock) {
        Snapshot(
            now = now,
            covered = covered,
            coveredFor = if (covered) now - coveredSince else 0.0,
            uncoveredFor = if (uncoveredSince.isNaN()) 0.0 else now - uncoveredSince,
            counting = current != null,
            measured = measured,
        )
    }

    fun recent(): Pair<DoubleArray, DoubleArray> = synchronized(lock) { recentT.toDoubleArray() to recentV.toDoubleArray() }

    fun segments(): List<Pair<DoubleArray, DoubleArray>> = synchronized(lock) {
        segments.map { it.first.toDoubleArray() to it.second.toDoubleArray() }
    }
}

/** Short buzzes you can feel with your eyes closed. [timings] alternate on/off in milliseconds. */
private fun buzz(context: Context, vararg timings: Long) {
    runCatching {
        val v: Vibrator = if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, *timings), -1))
    }
}

@Composable
fun HrvScreen(nav: Nav) {
    val context = LocalContext.current
    val view = LocalView.current
    val dao = Graph.dao
    val scope = rememberCoroutineScope()
    val readings by dao.hrvReadings().collectAsState(initial = emptyList())
    var measuring by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var camera by remember { mutableStateOf<PulseCamera?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<Ppg.Result?>(null) }
    var failed by remember { mutableStateOf<String?>(null) }
    var snap by remember { mutableStateOf<Snapshot?>(null) }
    var live by remember { mutableStateOf(Ppg.Live(null, Ppg.Signal.NONE)) }
    var trace by remember { mutableStateOf<List<Pair<Float, Float>>>(emptyList()) }
    var lastPulseAt by remember { mutableStateOf(0.0) }
    var showTips by remember { mutableStateOf(false) }

    fun stop() {
        camera?.stop(); camera = null
        measuring = false
        view.keepScreenOn = false
    }

    fun start() {
        error = null; result = null; failed = null; snap = null; trace = emptyList()
        live = Ppg.Live(null, Ppg.Signal.NONE)
        lateinit var cam: PulseCamera
        val rec = Recording(onLock = { cam.lockExposure() })
        cam = PulseCamera(context, onSample = rec::add, onError = { msg -> error = msg })
        recording = rec
        camera = cam
        measuring = true
        view.keepScreenOn = true
        cam.start()
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) start() else error = "Bear needs the camera to see your pulse. Nothing is recorded or saved."
    }

    DisposableEffect(Unit) { onDispose { camera?.stop(); view.keepScreenOn = false } }

    // While measuring: update twice a second; finish once a minute of clean recording is in.
    LaunchedEffect(measuring) {
        var pulseFound = false
        var wasCounting = false
        lastPulseAt = 0.0
        while (measuring) {
            delay(500)
            if (error != null) { stop(); break }
            val rec = recording ?: break
            val s = rec.snapshot()
            snap = s
            if (s.covered) {
                val (t, v) = rec.recent()
                live = Ppg.live(t, v)
                if (t.size > 20) {
                    val y = Ppg.clean(v, (t.size - 1) / (t.last() - t.first()).coerceAtLeast(0.1))
                    trace = t.indices.filter { t[it] >= s.now - 6 }.map { t[it].toFloat() to y[it].toFloat() }
                }
            } else {
                live = Ppg.Live(null, Ppg.Signal.NONE)
                trace = emptyList()
            }
            if (live.signal != Ppg.Signal.NONE) {
                lastPulseAt = s.now
                if (!pulseFound) { pulseFound = true; buzz(context, 60) }
            }
            // Finger slipped while counting: one long buzz, the clock pauses.
            if (wasCounting && !s.covered) buzz(context, 400)
            wasCounting = s.counting
            if (!s.covered && s.uncoveredFor > GIVE_UP_S) {
                stop()
                failed = "Stopped: no finger on the camera for a minute."
                break
            }
            if (s.measured >= MEASURE_S) {
                stop()
                buzz(context, 150, 120, 150)
                val r = Ppg.analyzeSegments(rec.segments())
                when {
                    r == null -> failed = "Couldn't find a steady pulse. Try a lighter touch that covers both the lens and the flash, and rest your hand on something."
                    r.quality == Ppg.Quality.POOR -> failed = "Too many unclear beats (${r.artifactPct.roundToInt()}%). Try again with your hand resting on something, breathing normally."
                    else -> {
                        result = r
                        val seconds = s.measured.roundToInt()
                        Graph.scope.launch {
                            dao.insertHrv(HrvReading(at = System.currentTimeMillis(), rmssdMs = r.rmssdMs, heartRate = r.heartRate, artifactPct = r.artifactPct, seconds = seconds))
                        }
                    }
                }
                break
            }
        }
    }

    LogScaffold("Morning HRV", nav) {
        if (!measuring) {
            result?.let { r ->
                item {
                    SectionCard("Done") {
                        StatLine("HRV (RMSSD)", "${r.rmssdMs.roundToInt()} ms", MaterialTheme.colorScheme.primary)
                        StatLine("Resting heart rate", "${r.heartRate.roundToInt()} bpm")
                        StatLine("Signal", if (r.quality == Ppg.Quality.GOOD) "Good" else "OK (${r.artifactPct.roundToInt()}% unclear beats)")
                        val baseline = readings.drop(1).filter { it.at >= System.currentTimeMillis() - 30 * 86_400_000L }.map { it.rmssdMs }
                        Readiness.hrvSignal(r.rmssdMs, baseline)?.let { Gap(4); Text(it.reason, color = MaterialTheme.colorScheme.primary) }
                            ?: Muted("A few more mornings and Bear will compare this with your normal range.")
                    }
                }
            }
            failed?.let { item { SectionCard("No reading saved") { Text(it) } } }
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            item {
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FingerGuide()
                        HGap(16)
                        Column(Modifier.weight(1f)) {
                            Text("One minute", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Gap(4)
                            Muted("Rest a fingertip lightly over the camera and the flash. The phone buzzes when it finds your pulse and twice when it's done.")
                        }
                    }
                    Gap(12)
                    BigButton(if (result != null || failed != null) "Measure again" else "Start", onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) start()
                        else permission.launch(Manifest.permission.CAMERA)
                    })
                    TextButton(onClick = { showTips = !showTips }) { Text(if (showTips) "Hide tips" else "Tips") }
                    if (showTips) {
                        Muted("• First thing after waking, before coffee, sitting or lying still.")
                        Muted("• Light touch: pressing hard squeezes the blood out and hides the pulse.")
                        Muted("• Rest your hand on something, breathe normally, don't talk.")
                        Muted("• If your finger slips, the clock pauses. Just put it back.")
                        Muted("• Same time and position each day makes the trend meaningful.")
                        Gap(4)
                        Muted("Only the average colour of each frame is used; no image is kept.")
                    }
                }
            }
        } else {
            item {
                val s = snap
                SectionCard {
                    val (headline, warn) = when {
                        s == null -> "Starting the camera…" to false
                        !s.covered && s.measured > 0 -> "Paused at ${s.measured.roundToInt()} s. Put your fingertip back" to true
                        !s.covered -> "Cover the camera and flash with your fingertip" to true
                        !s.counting -> "Finding your pulse…" to false
                        else -> "Hold still · ${(MEASURE_S - s.measured).coerceAtLeast(0.0).roundToInt()} s left" to false
                    }
                    Text(
                        headline,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    )
                    Gap(16)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LiveHeartRate(live.heartRate)
                        Box(Modifier.weight(1f))
                        SignalBars(live.signal)
                    }
                    if (s != null && s.covered && s.coveredFor > FIRST_SETTLE_S && s.now - lastPulseAt > LIVE_S) {
                        Gap(8)
                        Muted("Can't see a pulse yet. Try a lighter touch, and make sure the flash is covered too.")
                    }
                    Gap(12)
                    LinearProgressIndicator(progress = { ((s?.measured ?: 0.0) / MEASURE_S).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    Gap(8)
                    if (trace.size > 20) LineChart(listOf(trace), listOf(MaterialTheme.colorScheme.primary), showPoints = false)
                    Gap(8)
                    BigButton("Cancel", onClick = { stop() }, secondary = true)
                }
            }
        }
        if (readings.isNotEmpty() && !measuring) {
            item {
                SectionCard("Your readings") {
                    val recent = readings.take(30).reversed()
                    if (recent.size >= 2) {
                        LineChart(listOf(recent.map { (it.at / 86_400_000.0).toFloat() to it.rmssdMs.toFloat() }), listOf(MaterialTheme.colorScheme.primary))
                        Gap(4)
                    }
                    readings.take(10).forEach { r ->
                        DeletableRow("${r.rmssdMs.roundToInt()} ms · ${r.heartRate.roundToInt()} bpm", fmtDateTime(r.at)) {
                            scope.launch { dao.deleteHrv(r.id) }
                        }
                    }
                    Muted("HRV (RMSSD) rises with good recovery and falls with stress, poor sleep, illness or heavy training. Compare with yourself, not others.")
                }
            }
        }
    }
}

/** Big live heart rate with a dot that beats along with it. */
@Composable
private fun LiveHeartRate(bpm: Double?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val primary = MaterialTheme.colorScheme.primary
        if (bpm != null) {
            val period = (60_000 / bpm).roundToInt().coerceIn(300, 2000)
            key(period / 20) {
                val pulse = rememberInfiniteTransition(label = "pulse")
                val scale by pulse.animateFloat(
                    initialValue = 0.7f, targetValue = 1.15f,
                    animationSpec = infiniteRepeatable(tween(period / 2, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                    label = "beat",
                )
                Box(Modifier.size(14.dp).scale(scale).clip(CircleShape).background(primary))
            }
        } else {
            Box(Modifier.size(14.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outlineVariant))
        }
        HGap(12)
        Text(bpm?.roundToInt()?.toString() ?: "–", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Light)
        HGap(6)
        Muted("bpm")
    }
}

/** Three bars: no pulse / weak / good. */
@Composable
private fun SignalBars(signal: Ppg.Signal) {
    val level = when (signal) { Ppg.Signal.NONE -> 0; Ppg.Signal.WEAK -> 2; Ppg.Signal.GOOD -> 3 }
    Column(horizontalAlignment = Alignment.End) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
            (1..3).forEach { i ->
                Box(
                    Modifier.width(8.dp).height((8 + i * 6).dp).clip(RoundedCornerShape(2.dp))
                        .background(if (i <= level) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                )
            }
        }
        Gap(4)
        Muted(when (signal) { Ppg.Signal.NONE -> "No pulse yet"; Ppg.Signal.WEAK -> "Weak signal"; Ppg.Signal.GOOD -> "Good signal" })
    }
}

/** The back of a phone with a fingertip resting over the lens and the flash. */
@Composable
private fun FingerGuide() {
    val outline = MaterialTheme.colorScheme.outline
    val primary = MaterialTheme.colorScheme.primary
    Canvas(Modifier.size(width = 64.dp, height = 96.dp)) {
        val w = size.width
        val h = size.height
        drawRoundRect(outline, Offset(w * 0.06f, h * 0.02f), Size(w * 0.88f, h * 0.96f), CornerRadius(w * 0.16f), style = Stroke(w * 0.04f))
        drawCircle(outline, radius = w * 0.11f, center = Offset(w * 0.32f, h * 0.16f), style = Stroke(w * 0.035f))
        drawCircle(outline, radius = w * 0.05f, center = Offset(w * 0.32f, h * 0.31f))
        // Fingertip covering both.
        drawOval(primary.copy(alpha = 0.35f), Offset(w * 0.12f, h * 0.04f), Size(w * 0.42f, h * 0.40f))
        drawOval(primary, Offset(w * 0.12f, h * 0.04f), Size(w * 0.42f, h * 0.40f), style = Stroke(w * 0.03f))
    }
}
