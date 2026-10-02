package com.muir.bear.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import com.muir.bear.Graph
import com.muir.bear.data.HrvReading
import com.muir.bear.domain.Ppg
import com.muir.bear.domain.Readiness
import com.muir.bear.hrv.PulseCamera
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val SETTLE_S = 10.0
private const val MEASURE_S = 120.0

/** Collects samples from the camera thread. */
private class Recording {
    private val lock = Any()
    private val t = ArrayList<Double>(5000)
    private val v = ArrayList<Double>(5000)
    var covered = false
    var start = Double.NaN

    fun add(time: Double, red: Double, isCovered: Boolean) = synchronized(lock) {
        if (start.isNaN()) start = time
        t += time - start; v += red; covered = isCovered
    }

    fun elapsed(): Double = synchronized(lock) { t.lastOrNull() ?: 0.0 }

    /** Samples from [from] seconds onward. */
    fun since(from: Double): Pair<DoubleArray, DoubleArray> = synchronized(lock) {
        val i = t.indexOfFirst { it >= from }.let { if (it < 0) t.size else it }
        t.subList(i, t.size).toDoubleArray() to v.subList(i, v.size).toDoubleArray()
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
    var tick by remember { mutableIntStateOf(0) }

    fun stop() {
        camera?.stop(); camera = null
        measuring = false
        view.keepScreenOn = false
    }

    fun start() {
        error = null; result = null; failed = null
        val rec = Recording()
        recording = rec
        val cam = PulseCamera(context, onSample = rec::add, onError = { msg -> error = msg })
        camera = cam
        measuring = true
        view.keepScreenOn = true
        cam.start()
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) start() else error = "Bear needs the camera to see your pulse. Nothing is recorded or saved."
    }

    DisposableEffect(Unit) { onDispose { camera?.stop(); view.keepScreenOn = false } }

    // While measuring: refresh the screen twice a second, finish after settle + measure time.
    LaunchedEffect(measuring) {
        while (measuring) {
            delay(500)
            tick++
            if (error != null) { stop(); break }
            val rec = recording ?: break
            if (rec.elapsed() >= SETTLE_S + MEASURE_S) {
                stop()
                val (t, v) = rec.since(SETTLE_S)
                val r = Ppg.analyze(t, v)
                when {
                    r == null -> failed = "Couldn't find a steady pulse. Try a lighter touch that covers both the lens and the flash, and keep your hand still."
                    r.quality == Ppg.Quality.POOR -> failed = "Too many unclear beats (${r.artifactPct.roundToInt()}%). Try again: rest your hand on something and breathe normally."
                    else -> {
                        result = r
                        Graph.scope.launch {
                            dao.insertHrv(HrvReading(at = System.currentTimeMillis(), rmssdMs = r.rmssdMs, heartRate = r.heartRate, artifactPct = r.artifactPct, seconds = MEASURE_S.toInt()))
                        }
                    }
                }
            }
        }
    }

    LogScaffold("Morning HRV", nav) {
        if (!measuring) {
            result?.let { r ->
                item {
                    SectionCard("Done") {
                        StatLine("Resting heart rate", "${r.heartRate.roundToInt()} bpm")
                        StatLine("HRV (RMSSD)", "${r.rmssdMs.roundToInt()} ms", MaterialTheme.colorScheme.primary)
                        StatLine("Signal quality", if (r.quality == Ppg.Quality.GOOD) "Good" else "OK (${r.artifactPct.roundToInt()}% unclear beats)")
                        val baseline = readings.drop(1).filter { it.at >= System.currentTimeMillis() - 30 * 86_400_000L }.map { it.rmssdMs }
                        Readiness.hrvSignal(r.rmssdMs, baseline)?.let { Gap(4); Text(it.reason, color = MaterialTheme.colorScheme.primary) }
                            ?: Muted("A few more mornings and Bear will compare this with your normal range.")
                    }
                }
            }
            failed?.let { item { SectionCard("No reading saved") { Text(it) } } }
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            item {
                SectionCard("How to measure") {
                    Muted("• First thing after waking, before coffee, sitting or lying still.")
                    Muted("• Rest a fingertip lightly over the back camera and the flash. Pressing hard blocks the pulse.")
                    Muted("• Rest your hand, breathe normally, don't talk. Takes about 2 minutes.")
                    Muted("• Same time and position each day makes the trend meaningful.")
                    Gap(8)
                    BigButton(if (result != null || failed != null) "Measure again" else "Start measuring", onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) start()
                        else permission.launch(Manifest.permission.CAMERA)
                    })
                    Gap(4)
                    Muted("Only the average colour of each frame is used to find your pulse; no image is kept.")
                }
            }
        } else {
            item {
                val frame = tick // read so this refreshes on each tick
                if (frame < 0) return@item
                val rec = recording
                val elapsed = rec?.elapsed() ?: 0.0
                SectionCard("Measuring") {
                    val settling = elapsed < SETTLE_S
                    Text(
                        when {
                            rec?.covered != true -> "Cover the camera and flash with your fingertip"
                            settling -> "Finding your pulse…"
                            else -> "Hold still. %d s left".format((SETTLE_S + MEASURE_S - elapsed).coerceAtLeast(0.0).roundToInt())
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = if (rec?.covered != true) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    )
                    Gap(8)
                    LinearProgressIndicator(progress = { (elapsed / (SETTLE_S + MEASURE_S)).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    Gap(8)
                    // Live pulse trace: last 6 seconds, cleaned.
                    if (rec != null && elapsed > 3) {
                        val (t, v) = rec.since(elapsed - 6)
                        if (t.size > 20) {
                            val fs = (t.size - 1) / (t.last() - t.first()).coerceAtLeast(0.1)
                            val y = Ppg.clean(v, fs)
                            LineChart(listOf(t.indices.map { t[it].toFloat() to y[it].toFloat() }), listOf(MaterialTheme.colorScheme.primary), showPoints = false)
                        }
                    }
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
