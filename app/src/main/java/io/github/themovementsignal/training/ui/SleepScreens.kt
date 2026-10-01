package io.github.themovementsignal.training.ui

import android.Manifest
import android.app.NotificationManager
import android.app.Activity
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.themovementsignal.training.Graph
import io.github.themovementsignal.training.data.Setting
import io.github.themovementsignal.training.data.Settings
import io.github.themovementsignal.training.data.Sleep
import io.github.themovementsignal.training.domain.Calc
import io.github.themovementsignal.training.domain.SleepAnalysis
import io.github.themovementsignal.training.domain.SleepAnalysis.Stage
import io.github.themovementsignal.training.sleep.SleepTracker
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

private fun nextAlarmMillis(hhmm: String): Long {
    val t = runCatching { LocalTime.parse(hhmm) }.getOrDefault(LocalTime.of(6, 30))
    val zone = ZoneId.systemDefault()
    var at = LocalDate.now().atTime(t).atZone(zone).toInstant().toEpochMilli()
    if (at <= System.currentTimeMillis()) at += 86_400_000L
    return at
}

private fun fmtHours(min: Long): String = "${min / 60}h ${min % 60}m"

@Composable
fun SleepScreen(nav: Nav) {
    val dao = Graph.dao
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val live by SleepTracker.state.collectAsState()
    val justFinished by SleepTracker.justFinished.collectAsState()
    val sleeps by dao.sleeps().collectAsState(initial = emptyList())
    val alarmTime = dao.settingFlow(Settings.ALARM_TIME).collectAsState(initial = null).value ?: "06:30"
    val alarmOn = (dao.settingFlow(Settings.ALARM_ON).collectAsState(initial = null).value ?: "1") == "1"
    val window = dao.settingFlow(Settings.ALARM_WINDOW).collectAsState(initial = null).value?.toIntOrNull() ?: 30
    val snoreOn = (dao.settingFlow(Settings.SNORE_ON).collectAsState(initial = null).value ?: "1") == "1"
    val alarmSound by dao.settingFlow(Settings.ALARM_SOUND).collectAsState(initial = null)
    var showManual by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }

    fun put(key: String, value: String) = scope.launch { dao.putSetting(Setting(key, value)) }

    if (pickTime) AlarmTimeDialog(alarmTime, onDismiss = { pickTime = false }) { hhmm ->
        pickTime = false
        put(Settings.ALARM_TIME, hhmm)
        put(Settings.ALARM_ON, "1")
        // Already tracking tonight? Move tonight's alarm too.
        SleepTracker.setAlarm(context, nextAlarmMillis(hhmm), window)
    }

    fun start(listen: Boolean) {
        SleepTracker.start(context, if (alarmOn) nextAlarmMillis(alarmTime) else null, window, listen)
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> start(granted) }

    LogScaffold("Sleep", nav) {
        val l = live
        if (l != null) {
            item { TrackingCard(l, onChangeAlarm = { pickTime = true }) }
        } else {
            val rateId = justFinished
            val toRate = rateId?.let { id -> sleeps.firstOrNull { it.id == id && it.quality == null } }
            if (toRate != null) item {
                SectionCard("Good morning — how did you sleep?") {
                    toRate.score?.let { Text("Sleep score $it", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary) }
                    Muted("1 = terrible, 5 = great")
                    Gap(4)
                    RatingRow(null, { q ->
                        scope.launch { dao.updateSleep(toRate.copy(quality = q)) }
                        SleepTracker.justFinished.value = null
                    })
                    TextButton(onClick = { nav.go(Screen.SleepNight(toRate.id)) }) { Text("See last night ›") }
                }
            }
            item {
                SectionCard("Smart alarm") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).clickable { pickTime = true }.testTag("alarmTime")) {
                            Text(if (alarmOn) alarmTime else "Off", style = MaterialTheme.typography.displaySmall, color = if (alarmOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            if (alarmOn && window > 0) Muted("Wakes you in light sleep from ${runCatching { LocalTime.parse(alarmTime).minusMinutes(window.toLong()).toString() }.getOrDefault("")}")
                        }
                        Switch(checked = alarmOn, onCheckedChange = { put(Settings.ALARM_ON, if (it) "1" else "0") })
                    }
                    if (alarmOn) {
                        TextButton(onClick = { pickTime = true }) { Text("Change time") }
                        Text("Wake-up window (minutes)")
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(0, 10, 20, 30, 45).forEach { w ->
                                val label = if (w == 0) "Exact" else "$w"
                                val pad = PaddingValues(horizontal = 4.dp)
                                if (w == window) Button(onClick = { }, Modifier.weight(1f), contentPadding = pad) { Text(label, maxLines = 1) }
                                else OutlinedButton(onClick = { put(Settings.ALARM_WINDOW, w.toString()) }, Modifier.weight(1f), contentPadding = pad) { Text(label, maxLines = 1) }
                            }
                        }
                        Gap(8)
                        AlarmSoundRow(alarmSound) { put(Settings.ALARM_SOUND, it) }
                    }
                    Gap(8)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Snore detection")
                            Muted("Listens with the microphone; sounds are classified on the phone and never recorded.")
                        }
                        Switch(checked = snoreOn, onCheckedChange = { put(Settings.SNORE_ON, if (it) "1" else "0") })
                    }
                    if (Build.VERSION.SDK_INT >= 34 && alarmOn &&
                        !context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
                    ) {
                        TextButton(onClick = {
                            context.startActivity(
                                Intent(AndroidSettings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}"))
                            )
                        }) { Text("Allow the alarm to show on the lock screen ›") }
                    }
                    Gap(12)
                    BigButton("🌙 Start sleep tracking", onClick = {
                        if (snoreOn && ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                            micPermission.launch(Manifest.permission.RECORD_AUDIO)
                        } else {
                            start(snoreOn)
                        }
                    })
                    Gap(4)
                    Muted("Put the phone face down on the mattress near your pillow, ideally plugged in.")
                }
            }
            item {
                TextButton(onClick = { showManual = !showManual }) { Text(if (showManual) "Hide manual log" else "Forgot to track? Log a night manually") }
                if (showManual) ManualSleepCard()
            }
        }
        val nights = sleeps.filter { it.wakeAt != null }
        if (nights.isNotEmpty()) item { Text("Nights", style = MaterialTheme.typography.titleMedium) }
        nights.forEach { s -> item(key = s.id) { NightRow(s) { nav.go(Screen.SleepNight(s.id)) } } }
    }
}

@Composable
private fun TrackingCard(l: SleepTracker.Live, onChangeAlarm: () -> Unit) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    SectionCard {
        Text(if (l.ringing) "Good morning ☀" else "Tracking your sleep 🌙", style = MaterialTheme.typography.headlineSmall)
        Muted("Since ${fmtTime(l.startedAt)} · ${fmtHours((now - l.startedAt) / 60_000)}")
        Gap(8)
        StatLine("Alarm", l.alarmAt?.let { fmtTime(it) + if (l.windowMin > 0) " (window ${l.windowMin} min)" else "" } ?: "Off")
        l.snoozedUntil?.let { StatLine("Snoozed until", fmtTime(it)) }
        if (!l.ringing) TextButton(onClick = onChangeAlarm) { Text(if (l.alarmAt == null) "Set an alarm" else "Change alarm time") }
        StatLine("Snore detection", when {
            !l.listening -> "Off"
            l.modelReady -> "On · ${l.snoreSec / 60} min so far"
            else -> "Loudness only"
        })
        Gap(12)
        if (l.ringing) {
            BigButton("I'm up", onClick = { SleepTracker.stop(context) })
            Gap(8)
            BigButton("Snooze 9 min", onClick = { SleepTracker.snooze(context) }, secondary = true)
        } else {
            BigButton("Stop · I'm awake", onClick = { SleepTracker.stop(context) })
        }
    }
}

@Composable
private fun NightRow(s: Sleep, onClick: () -> Unit) {
    val minutes = ((s.wakeAt ?: s.bedAt) - s.bedAt) / 60_000
    SectionCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    (s.score?.let { "Score $it · " } ?: "") + fmtHours(minutes) + (s.quality?.let { " · felt $it/5" } ?: ""),
                    style = MaterialTheme.typography.titleMedium,
                )
                Muted("${fmtDateTime(s.bedAt)} → ${fmtTime(s.wakeAt ?: s.bedAt)}" + (s.snoreMin?.takeIf { it > 0 }?.let { " · snoring $it min" } ?: ""))
            }
            Text("›", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun ManualSleepCard() {
    val dao = Graph.dao
    val scope = rememberCoroutineScope()
    var hours by remember { mutableStateOf("") }
    var quality by remember { mutableStateOf<Int?>(null) }
    SectionCard("Log last night") {
        NumberField(hours, { hours = it }, "Hours slept", Modifier.fillMaxWidth())
        Gap(4)
        RatingRow(quality, { quality = it })
        Gap(8)
        BigButton("Save", secondary = true, enabled = Calc.parseNumber(hours) != null, onClick = {
            val h = Calc.parseNumber(hours) ?: return@BigButton
            scope.launch {
                val wake = LocalDate.now().atTime(7, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                dao.insertSleep(Sleep(bedAt = wake - (h * 3600_000).toLong(), wakeAt = wake, quality = quality))
                hours = ""; quality = null
            }
        })
    }
}

@Composable
fun SleepNightScreen(id: Long, nav: Nav) {
    val dao = Graph.dao
    val scope = rememberCoroutineScope()
    val sleep by dao.sleepFlow(id).collectAsState(initial = null)
    val samples by dao.sleepSamplesFlow(id).collectAsState(initial = emptyList())
    var confirm by remember { mutableStateOf(false) }
    val s = sleep
    LogScaffold("Night", nav) {
        if (s == null) return@LogScaffold
        val minutes = ((s.wakeAt ?: System.currentTimeMillis()) - s.bedAt) / 60_000
        item {
            SectionCard {
                Text(fmtDate(s.bedAt), style = MaterialTheme.typography.titleMedium)
                Muted("${fmtTime(s.bedAt)} → ${s.wakeAt?.let { fmtTime(it) } ?: "now"} · ${fmtHours(minutes)}")
                Gap(8)
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    s.score?.let { Big("$it", "Score") }
                    s.snoreMin?.let { Big("$it min", "Snoring") }
                    s.quality?.let { Big("$it/5", "Felt") }
                }
            }
        }
        if (samples.isNotEmpty()) {
            val stages = SleepAnalysis.stages(samples.map { it.movement })
            item {
                SectionCard("Sleep stages (estimated)") {
                    Hypnogram(stages)
                    Gap(8)
                    StatLine("Deep", fmtHours((s.deepMin ?: stages.count { it == Stage.DEEP }).toLong()))
                    StatLine("REM", fmtHours((s.remMin ?: stages.count { it == Stage.REM }).toLong()))
                    StatLine("Light", fmtHours((s.lightMin ?: stages.count { it == Stage.LIGHT }).toLong()))
                    StatLine("Awake", fmtHours((s.awakeMin ?: stages.count { it == Stage.AWAKE }).toLong()))
                    Gap(4)
                    Muted("Estimated from your movement, like Sleep Cycle. Good for trends, not a medical measurement.")
                }
            }
            item {
                SectionCard("Snoring & noise") {
                    SnoreBars(samples.map { it.snoreSec }, samples.map { it.noiseDb })
                    Muted("Bars: seconds of snoring per minute · line: loudness")
                }
            }
        } else if (!s.tracked) {
            item { Muted("Logged manually, so there's no overnight graph.") }
        }
        item { DangerTextButton("Delete this night") { confirm = true } }
    }
    if (confirm) ConfirmDialog("Delete this night?", "Its graph and score are deleted too.", onConfirm = {
        scope.launch {
            dao.deleteSleepSamples(id)
            dao.deleteSleepRow(id)
            nav.back()
        }
    }, onDismiss = { confirm = false })
}

@Composable
private fun Big(value: String, label: String) {
    Column {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Muted(label)
    }
}

/** Classic hypnogram: awake at the top, deep at the bottom, one block per minute. */
@Composable
private fun Hypnogram(stages: List<Stage>) {
    if (stages.isEmpty()) return
    val colors = mapOf(
        Stage.AWAKE to MaterialTheme.colorScheme.error,
        Stage.REM to MaterialTheme.colorScheme.tertiary,
        Stage.LIGHT to MaterialTheme.colorScheme.secondary,
        Stage.DEEP to MaterialTheme.colorScheme.primary,
    )
    val rows = listOf(Stage.AWAKE, Stage.REM, Stage.LIGHT, Stage.DEEP)
    Row {
        Column(Modifier.height(140.dp), verticalArrangement = Arrangement.SpaceBetween) {
            rows.forEach { Text(it.name.lowercase().replaceFirstChar { c -> c.uppercase() }, style = MaterialTheme.typography.labelSmall, color = colors.getValue(it)) }
        }
        Canvas(Modifier.fillMaxWidth().height(140.dp)) {
            val w = size.width / stages.size
            val rowH = size.height / rows.size
            stages.forEachIndexed { i, st ->
                val r = rows.indexOf(st)
                drawRect(colors.getValue(st), Offset(i * w, r * rowH + rowH * 0.15f), Size(w.coerceAtLeast(1f), rowH * 0.7f))
            }
        }
    }
}

@Composable
private fun SnoreBars(snoreSec: List<Int>, noise: List<Float>) {
    val bar = MaterialTheme.colorScheme.tertiary
    val line = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(Modifier.fillMaxWidth().height(90.dp)) {
        if (snoreSec.isEmpty()) return@Canvas
        val w = size.width / snoreSec.size
        snoreSec.forEachIndexed { i, s ->
            val h = (s / 60f).coerceIn(0f, 1f) * size.height
            if (h > 0) drawRect(bar, Offset(i * w, size.height - h), Size(w.coerceAtLeast(1f), h))
        }
        val maxDb = (noise.maxOrNull() ?: 0f).coerceAtLeast(1f)
        var prev: Offset? = null
        noise.forEachIndexed { i, db ->
            val p = Offset(i * w + w / 2, size.height - (db / maxDb) * size.height)
            prev?.let { drawLine(line.copy(alpha = 0.6f), it, p, 2f) }
            prev = p
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlarmTimeDialog(current: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val t = runCatching { LocalTime.parse(current) }.getOrDefault(LocalTime.of(6, 30))
    val state = rememberTimePickerState(initialHour = t.hour, initialMinute = t.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Wake me at") },
        text = { TimePicker(state = state) },
        confirmButton = { TextButton(onClick = { onPick("%02d:%02d".format(state.hour, state.minute)) }) { Text("Set alarm") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun soundTitle(context: Context, picked: String?): String {
    if (picked.isNullOrBlank()) return "Phone's default alarm"
    return runCatching { RingtoneManager.getRingtone(context, Uri.parse(picked))?.getTitle(context) }.getOrNull() ?: "Custom sound"
}

/** Pick the alarm sound from the phone's own alarm tones, and preview it at alarm volume. */
@Composable
private fun AlarmSoundRow(picked: String?, onPicked: (String) -> Unit) {
    val context = LocalContext.current
    var preview by remember { mutableStateOf<MediaPlayer?>(null) }
    DisposableEffect(Unit) { onDispose { preview?.release() } }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            @Suppress("DEPRECATION")
            val uri = r.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            onPicked(uri?.toString() ?: "")
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Alarm sound")
            Muted(soundTitle(context, picked) + " · plays at alarm volume, getting louder")
        }
    }
    Row {
        TextButton(onClick = {
            picker.launch(
                Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                    .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                    .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Alarm sound")
                    .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                    .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                    .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, SleepTracker.alarmSound(context, picked))
            )
        }) { Text("Change sound") }
        TextButton(onClick = {
            preview?.release()
            val uri = SleepTracker.alarmSound(context, picked)
            preview = uri?.let {
                runCatching {
                    MediaPlayer().apply {
                        setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                        setDataSource(context, it)
                        prepare()
                        start()
                    }
                }.getOrNull()
            }
        }) { Text("Test sound") }
        if (preview != null) TextButton(onClick = { preview?.release(); preview = null }) { Text("Stop") }
    }
    // Stop the preview by itself after 6 seconds.
    LaunchedEffect(preview) {
        val p = preview ?: return@LaunchedEffect
        delay(6_000)
        if (preview === p) { p.release(); preview = null }
    }
}
