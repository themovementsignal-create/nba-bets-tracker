package io.github.themovementsignal.training.ui

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.themovementsignal.training.ErrorLog
import io.github.themovementsignal.training.Graph
import io.github.themovementsignal.training.data.JumpTest
import io.github.themovementsignal.training.domain.Calc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private class VideoInfo(val retriever: MediaMetadataRetriever, val frameCount: Int, val fileFps: Double, val captureFps: Double?)

@Composable
fun JumpTestScreen(nav: Nav) {
    val context = LocalContext.current
    val dao = Graph.dao
    val scope = rememberCoroutineScope()
    val tests by dao.jumpTests().collectAsState(initial = emptyList())
    var uri by remember { mutableStateOf<Uri?>(null) }
    var info by remember { mutableStateOf<VideoInfo?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var frame by remember { mutableIntStateOf(0) }
    var scrub by remember { mutableStateOf(0f) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var takeoff by remember { mutableStateOf<Int?>(null) }
    var landing by remember { mutableStateOf<Int?>(null) }
    var fpsText by remember { mutableStateOf("") }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u -> if (u != null) uri = u }

    LaunchedEffect(uri) {
        val u = uri ?: return@LaunchedEffect
        info?.retriever?.release()
        info = null; bitmap = null; takeoff = null; landing = null; frame = 0; error = null
        try {
            info = withContext(Dispatchers.IO) {
                val r = MediaMetadataRetriever()
                r.setDataSource(context, u)
                val count = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)?.toIntOrNull() ?: 0
                val durMs = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toDoubleOrNull() ?: 0.0
                val capture = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toDoubleOrNull()
                VideoInfo(r, count, if (durMs > 0) count / (durMs / 1000.0) else 30.0, capture)
            }
            val i = info!!
            if (i.frameCount <= 0) error = "Couldn't read frames from this video."
            fpsText = ((i.captureFps?.takeIf { it > i.fileFps + 1 } ?: i.fileFps)).roundToInt().toString()
        } catch (e: Exception) {
            ErrorLog.log("JUMP", "Could not open video", e)
            error = "Couldn't open this video: ${e.message}"
        }
    }

    LaunchedEffect(info, frame) {
        val i = info ?: return@LaunchedEffect
        if (i.frameCount <= 0) return@LaunchedEffect
        bitmap = withContext(Dispatchers.IO) {
            runCatching { i.retriever.getFrameAtIndex(frame.coerceIn(0, i.frameCount - 1)) }.getOrNull()
        }
    }

    DisposableEffect(Unit) { onDispose { info?.retriever?.release() } }

    LogScaffold("Jump height test", nav) {
        item {
            SectionCard("How it works") {
                Muted("1. Record a slow-motion video of your jump in the camera app (side-on, feet in view).\n" +
                    "2. Pick it here and step frame by frame.\n3. Mark the last frame your feet touch the ground (take-off) and the first frame they touch again (landing).\n" +
                    "Height = g·t²/8, where t is the flight time.")
                Gap(8)
                BigButton(if (info == null) "Choose video…" else "Choose another video…", onClick = { pick.launch(arrayOf("video/*")) })
            }
        }
        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        info?.takeIf { it.frameCount > 0 }?.let { i ->
            item {
                SectionCard {
                    bitmap?.let {
                        Image(it.asImageBitmap(), contentDescription = "Video frame", modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp), contentScale = ContentScale.Fit)
                    }
                    Text("Frame ${frame + 1} / ${i.frameCount}", style = MaterialTheme.typography.titleMedium)
                    Slider(
                        value = scrub,
                        onValueChange = { scrub = it },
                        onValueChangeFinished = { frame = (scrub * (i.frameCount - 1)).roundToInt() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf(-10, -1, 1, 10).forEach { d ->
                            FilledTonalButton(onClick = {
                                frame = (frame + d).coerceIn(0, i.frameCount - 1)
                                scrub = frame.toFloat() / (i.frameCount - 1).coerceAtLeast(1)
                            }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text(if (d > 0) "+$d" else "$d") }
                        }
                    }
                    Gap(8)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { takeoff = frame }, modifier = Modifier.weight(1f)) { Text("Take-off" + (takeoff?.let { " (${it + 1})" } ?: "")) }
                        Button(onClick = { landing = frame }, modifier = Modifier.weight(1f)) { Text("Landing" + (landing?.let { " (${it + 1})" } ?: "")) }
                    }
                }
            }
            item {
                SectionCard("Result") {
                    NumberField(fpsText, { fpsText = it }, "Capture frame rate (fps)", Modifier.fillMaxWidth(), decimal = false)
                    Muted("File: ${i.fileFps.roundToInt()} fps" + (i.captureFps?.let { " · recorded at ${it.roundToInt()} fps" } ?: "") +
                        ". For slow-mo, use the recording rate (e.g. 120 or 240).")
                    val fps = fpsText.toDoubleOrNull()
                    val t0 = takeoff; val t1 = landing
                    if (fps != null && fps > 0 && t0 != null && t1 != null && t1 > t0) {
                        val flight = (t1 - t0) / fps
                        val h = Calc.jumpHeightCm(flight)
                        Gap(8)
                        Text("${"%.1f".format(h)} cm", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                        Muted("Flight time ${(flight * 1000).roundToInt()} ms (${t1 - t0} frames)")
                        Gap(8)
                        BigButton("Save result", onClick = {
                            scope.launch { dao.insertJumpTest(JumpTest(at = System.currentTimeMillis(), flightMs = (flight * 1000).roundToInt(), heightCm = Calc.round2(h))) }
                        })
                    } else {
                        Muted("Mark take-off and landing to see the height.")
                    }
                }
            }
        }
        if (tests.isNotEmpty()) item {
            SectionCard("Progress") {
                LineChart(listOf(tests.map { it.at.toLocalDate().toEpochDay().toFloat() to it.heightCm.toFloat() }), listOf(MaterialTheme.colorScheme.primary))
            }
        }
        tests.forEach { t ->
            item(key = t.id) {
                DeletableRow("${"%.1f".format(t.heightCm)} cm · ${t.flightMs} ms", fmtDateTime(t.at)) { scope.launch { dao.deleteJumpTest(t.id) } }
            }
        }
    }
}
