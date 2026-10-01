@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package io.github.themovementsignal.training.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.themovementsignal.training.Graph
import io.github.themovementsignal.training.data.Activity
import io.github.themovementsignal.training.data.BodyWeight
import io.github.themovementsignal.training.data.CheckIn
import io.github.themovementsignal.training.data.DailySteps
import io.github.themovementsignal.training.data.ConditioningKind
import io.github.themovementsignal.training.data.Niggle
import io.github.themovementsignal.training.data.ProteinEntry
import io.github.themovementsignal.training.data.ProteinPreset
import io.github.themovementsignal.training.data.SaunaSession
import io.github.themovementsignal.training.data.SessionType
import io.github.themovementsignal.training.data.Setting
import io.github.themovementsignal.training.data.Settings
import io.github.themovementsignal.training.data.Sleep
import io.github.themovementsignal.training.data.Supplement
import io.github.themovementsignal.training.data.SupplementLog
import io.github.themovementsignal.training.domain.Calc
import io.github.themovementsignal.training.steps.Steps
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun LogScaffold(title: String, nav: Nav, content: LazyListScope.() -> Unit) {
    Scaffold(topBar = { AppTopBar(title, onBack = { nav.back() }) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

/** − value + stepper with big buttons. */
@Composable
fun Stepper(label: String, value: Int, onChange: (Int) -> Unit, step: Int = 1, range: IntRange = 0..999, suffix: String = "") {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        SmallSquareButton("−") { onChange((value - step).coerceIn(range)) }
        Text("$value$suffix", Modifier.width(84.dp), style = MaterialTheme.typography.titleLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        SmallSquareButton("+") { onChange((value + step).coerceIn(range)) }
    }
}

private fun weekStartMillis(): Long =
    Calc.mondayOf(LocalDate.now()).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

// ---------- Sauna ----------

@Composable
fun SaunaScreen(nav: Nav) {
    val dao = Graph.dao
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val sessions by dao.saunas().collectAsState(initial = emptyList())
    var rounds by remember { mutableIntStateOf(3) }
    var minutes by remember { mutableIntStateOf(15) }
    var temp by remember { mutableIntStateOf(90) }
    var cold by remember { mutableStateOf(true) }
    var coldMin by remember { mutableIntStateOf(2) }
    var notes by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }
    val weekMin = sessions.filter { it.at >= weekStartMillis() }.sumOf { it.rounds * it.minutesPerRound }

    LogScaffold("Sauna", nav) {
        item {
            SectionCard("New session") {
                Stepper("Rounds", rounds, { rounds = it }, range = 1..8)
                Gap(8)
                Stepper("Minutes / round", minutes, { minutes = it }, range = 1..40)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(12, 13, 14, 15).forEach { m -> AssistChip(onClick = { minutes = m }, label = { Text("$m") }) }
                }
                Gap(8)
                Stepper("Temperature", temp, { temp = it }, step = 5, range = 40..120, suffix = "°C")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(80, 85, 90, 95, 100).forEach { t -> AssistChip(onClick = { temp = t }, label = { Text("$t°") }) }
                }
                Gap(8)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Cold contrast", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Switch(checked = cold, onCheckedChange = { cold = it })
                }
                if (cold) Stepper("Cold minutes (total)", coldMin, { coldMin = it }, range = 0..30)
                Gap(8)
                TextInput(notes, { notes = it }, "Notes (optional)")
                Gap(8)
                BigButton(if (saved) "Saved ✓" else "Save ${rounds * minutes} min session", onClick = {
                    focus.clearFocus()
                    scope.launch {
                        val now = System.currentTimeMillis()
                        val id = dao.insertSauna(SaunaSession(at = now, rounds = rounds, minutesPerRound = minutes, tempC = temp, coldContrast = cold, coldMinutes = if (cold) coldMin else 0, notes = notes))
                        Actions.recordGearUsage(SessionType.SAUNA, id, now)
                        notes = ""; saved = true
                    }
                })
            }
        }
        item { Muted("This week: $weekMin min in the sauna") }
        items(sessions, key = { it.id }) { s ->
            DeletableRow(
                "${s.rounds} × ${s.minutesPerRound} min at ${s.tempC}°C" + if (s.coldContrast) " + cold ${s.coldMinutes} min" else "",
                fmtDateTime(s.at) + if (s.notes.isNotBlank()) " · ${s.notes}" else "",
            ) { scope.launch { dao.deleteSauna(s.id); dao.deleteGearUsageForSession(SessionType.SAUNA, s.id) } }
        }
    }
}

@Composable
fun DeletableRow(title: String, subtitle: String, onDelete: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Muted(subtitle)
            }
            TextButton(onClick = { confirm = true }) { Text("✕", color = MaterialTheme.colorScheme.error) }
        }
    }
    if (confirm) ConfirmDialog("Delete entry?", title, onConfirm = onDelete, onDismiss = { confirm = false })
}

// ---------- NEAT / treadmill ----------

@Composable
fun NeatScreen(nav: Nav) {
    val dao = Graph.dao
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val activities by dao.activities().collectAsState(initial = emptyList())
    val timerStart by dao.settingFlow(Settings.NEAT_TIMER_START).collectAsState(initial = null)
    val targetSessions by dao.settingFlow(Settings.NEAT_WEEKLY_SESSIONS).collectAsState(initial = "3")
    val targetMin by dao.settingFlow(Settings.NEAT_SESSION_MIN).collectAsState(initial = "60")
    var manual by remember { mutableStateOf("") }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(timerStart) { while (timerStart != null) { now = System.currentTimeMillis(); delay(1000) } }

    val neat = activities.filter { it.type == SessionType.NEAT }
    val week = neat.filter { it.startedAt >= weekStartMillis() }
    val sessionsTarget = targetSessions?.toIntOrNull() ?: 3
    val minTarget = targetMin?.toIntOrNull() ?: 60
    val start = timerStart?.toLongOrNull()

    val steps by dao.dailySteps().collectAsState(initial = emptyList())
    val context = LocalContext.current
    LogScaffold("NEAT · treadmill", nav) {
        item { StepsCard(steps, Steps.available(context)) }
        item {
            SectionCard("This week") {
                Text("${week.size} of $sessionsTarget–${sessionsTarget + 1} sessions · ${week.sumOf { it.durationMin }} min",
                    style = MaterialTheme.typography.titleLarge)
                Gap(6)
                LinearProgressIndicator(progress = { (week.size.toFloat() / sessionsTarget).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                Gap(4)
                Muted("Target: $sessionsTarget–${sessionsTarget + 1} sessions × $minTarget min")
            }
        }
        item {
            SectionCard("Treadmill timer") {
                if (start != null) {
                    Text(fmtClock((now - start) / 1000), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                    Gap(8)
                    BigButton("Stop & save", onClick = {
                        scope.launch {
                            val min = ((System.currentTimeMillis() - start) / 60_000).toInt().coerceAtLeast(1)
                            val id = dao.insertActivity(Activity(type = SessionType.NEAT, startedAt = start, durationMin = min))
                            Actions.recordGearUsage(SessionType.NEAT, id, start)
                            dao.deleteSetting(Settings.NEAT_TIMER_START)
                        }
                    })
                    DangerTextButton("Cancel timer") { scope.launch { dao.deleteSetting(Settings.NEAT_TIMER_START) } }
                } else {
                    BigButton("Start timer", onClick = {
                        scope.launch { dao.putSetting(Setting(Settings.NEAT_TIMER_START, System.currentTimeMillis().toString())) }
                    })
                    Muted("The timer keeps running even if you close the app.")
                }
            }
        }
        item {
            SectionCard("Manual entry") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NumberField(manual, { manual = it }, "Minutes", Modifier.weight(1f), decimal = false)
                    HGap()
                    Button(onClick = {
                        focus.clearFocus()
                        val min = manual.toIntOrNull() ?: return@Button
                        scope.launch {
                            val at = System.currentTimeMillis() - min * 60_000L
                            val id = dao.insertActivity(Activity(type = SessionType.NEAT, startedAt = at, durationMin = min))
                            Actions.recordGearUsage(SessionType.NEAT, id, at)
                            manual = ""
                        }
                    }) { Text("Add") }
                }
            }
        }
        items(neat, key = { it.id }) { a ->
            DeletableRow("${a.durationMin} min", fmtDateTime(a.startedAt)) {
                scope.launch { dao.deleteActivity(a.id); dao.deleteGearUsageForSession(SessionType.NEAT, a.id) }
            }
        }
    }
}

// ---------- Conditioning ----------

@Composable
fun ActivityScreen(nav: Nav) {
    val dao = Graph.dao
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val activities by dao.activities().collectAsState(initial = emptyList())
    var kind by remember { mutableStateOf(ConditioningKind.all.first()) }
    var kindMenu by remember { mutableStateOf(false) }
    var duration by remember { mutableStateOf("30") }
    var rpe by remember { mutableStateOf<Int?>(null) }
    var notes by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }
    val list = activities.filter { it.type == SessionType.BASKETBALL || it.type == SessionType.CONDITIONING }

    LogScaffold("Conditioning", nav) {
        item {
            SectionCard("New session") {
                Text("What did you do?")
                Gap(4)
                Box {
                    OutlinedButton(onClick = { kindMenu = true }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                        Text(kind, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        Text("▾")
                    }
                    DropdownMenu(expanded = kindMenu, onDismissRequest = { kindMenu = false }) {
                        ConditioningKind.all.forEach { k ->
                            DropdownMenuItem(text = { Text(k) }, onClick = { kind = k; kindMenu = false; saved = false })
                        }
                    }
                }
                Gap(8)
                NumberField(duration, { duration = it }, "Duration (minutes)", Modifier.fillMaxWidth(), decimal = false)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(30, 45, 60, 90, 120).forEach { m -> AssistChip(onClick = { duration = "$m" }, label = { Text("$m") }) }
                }
                Gap(8)
                Text("Session RPE (1 = very easy, 10 = maximal)")
                Gap(4)
                RatingRow(rpe, { rpe = it }, 1..5)
                Gap(4)
                RatingRow(rpe, { rpe = it }, 6..10)
                Gap(8)
                TextInput(notes, { notes = it }, "Notes (optional)")
                Gap(8)
                val min = duration.toIntOrNull()
                BigButton(if (saved) "Saved ✓" else "Save", enabled = min != null && min > 0 && rpe != null, onClick = {
                    focus.clearFocus()
                    scope.launch {
                        val m = min ?: return@launch
                        val at = System.currentTimeMillis() - m * 60_000L
                        val id = dao.insertActivity(Activity(type = SessionType.CONDITIONING, startedAt = at, durationMin = m, rpe = rpe, notes = notes, kind = kind))
                        Actions.recordGearUsage(SessionType.CONDITIONING, id, at)
                        notes = ""; rpe = null; saved = true
                    }
                })
                if (rpe != null && min != null) Muted("Load: ${rpe!! * min} (RPE × minutes)")
            }
        }
        items(list, key = { it.id }) { a ->
            DeletableRow(
                activityTitle(a) + " · ${a.durationMin} min" + (a.rpe?.let { " · RPE $it · load ${it * a.durationMin}" } ?: ""),
                fmtDateTime(a.startedAt) + if (a.notes.isNotBlank()) " · ${a.notes}" else "",
            ) { scope.launch { dao.deleteActivity(a.id); dao.deleteGearUsageForSession(a.type, a.id) } }
        }
    }
}

@Composable
fun StepsCard(steps: List<DailySteps>, sensor: Boolean) {
    SectionCard("Steps") {
        val today = steps.firstOrNull { it.day == today() }?.steps ?: 0
        Text("%,d today".format(today), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        val days = (6 downTo 0).map { today() - it }
        val values = days.map { d -> (steps.firstOrNull { it.day == d }?.steps ?: 0).toFloat() }
        Muted("7-day average: %,d".format(values.average().toInt()))
        Gap(6)
        BarChart(values, days.map { LocalDate.ofEpochDay(it).dayOfWeek.name.take(2).lowercase().replaceFirstChar { c -> c.uppercase() } }, values.map { false })
        if (!sensor) Muted("This phone has no step counter.")
        else Muted("Counted by your phone while you carry it (needs the Physical activity permission).")
    }
}

/** "Conditioning · Rower", or "Basketball" for older entries. */
fun activityTitle(a: Activity): String =
    if (a.type == SessionType.CONDITIONING && a.kind.isNotBlank()) "Conditioning · ${a.kind}" else SessionType.label(a.type)

// ---------- Morning check-in ----------

@Composable
fun CheckInScreen(nav: Nav) {
    val dao = Graph.dao
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val checkIns by dao.checkIns().collectAsState(initial = emptyList())
    val todays = checkIns.firstOrNull { it.day == today() }
    var sleep by remember(todays) { mutableStateOf(todays?.sleep) }
    var soreness by remember(todays) { mutableStateOf(todays?.soreness) }
    var energy by remember(todays) { mutableStateOf(todays?.energy) }

    LogScaffold("Morning check-in", nav) {
        item {
            SectionCard("Today") {
                Text("Sleep (1 = terrible, 5 = great)"); Gap(4)
                RatingRow(sleep, { sleep = it }); Gap(12)
                Text("Soreness (1 = none, 5 = very sore)"); Gap(4)
                RatingRow(soreness, { soreness = it }); Gap(12)
                Text("Energy (1 = flat, 5 = great)"); Gap(4)
                RatingRow(energy, { energy = it }); Gap(12)
                BigButton(if (todays != null) "Update" else "Save", enabled = sleep != null && soreness != null && energy != null, onClick = {
                    scope.launch {
                        dao.upsertCheckIn(CheckIn(id = todays?.id ?: 0, day = today(), sleep = sleep!!, soreness = soreness!!, energy = energy!!))
                        nav.back()
                    }
                })
            }
        }
        items(checkIns, key = { it.id }) { c ->
            DeletableRow("Sleep ${c.sleep} · Soreness ${c.soreness} · Energy ${c.energy}", fmtDay(c.day)) {
                scope.launch { dao.deleteCheckIn(c.id) }
            }
        }
    }
}

// ---------- Niggles ----------

private data class Region(val name: String, val x: Float, val y: Float, val side: String)

private val regions = listOf(
    Region("Head / neck", 0.5f, 0.07f, "Centre"),
    Region("Shoulder", 0.27f, 0.19f, "Left"), Region("Shoulder", 0.73f, 0.19f, "Right"),
    Region("Upper back / chest", 0.5f, 0.24f, "Centre"),
    Region("Elbow", 0.17f, 0.35f, "Left"), Region("Elbow", 0.83f, 0.35f, "Right"),
    Region("Lower back", 0.5f, 0.38f, "Centre"),
    Region("Wrist / hand", 0.1f, 0.49f, "Left"), Region("Wrist / hand", 0.9f, 0.49f, "Right"),
    Region("Hip", 0.36f, 0.48f, "Left"), Region("Hip", 0.64f, 0.48f, "Right"),
    Region("Groin / adductor", 0.5f, 0.53f, "Centre"),
    Region("Thigh / hamstring", 0.38f, 0.62f, "Left"), Region("Thigh / hamstring", 0.62f, 0.62f, "Right"),
    Region("Knee", 0.39f, 0.72f, "Left"), Region("Knee", 0.61f, 0.72f, "Right"),
    Region("Calf / Achilles", 0.39f, 0.83f, "Left"), Region("Calf / Achilles", 0.61f, 0.83f, "Right"),
    Region("Ankle / foot", 0.39f, 0.95f, "Left"), Region("Ankle / foot", 0.61f, 0.95f, "Right"),
)

@Composable
private fun BodyMap(selected: Region?, onSelect: (Region) -> Unit) {
    val body = MaterialTheme.colorScheme.surfaceVariant
    val dot = MaterialTheme.colorScheme.primary
    val sel = MaterialTheme.colorScheme.error
    BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(0.62f)) {
        val w = maxWidth
        val h = maxHeight
        Canvas(Modifier.fillMaxSize()) {
            val W = size.width; val H = size.height
            drawCircle(body, radius = W * 0.08f, center = Offset(W * 0.5f, H * 0.07f))
            drawRoundRect(body, Offset(W * 0.32f, H * 0.14f), Size(W * 0.36f, H * 0.36f), CornerRadius(30f, 30f))
            drawRoundRect(body, Offset(W * 0.14f, H * 0.16f), Size(W * 0.14f, H * 0.36f), CornerRadius(30f, 30f))
            drawRoundRect(body, Offset(W * 0.72f, H * 0.16f), Size(W * 0.14f, H * 0.36f), CornerRadius(30f, 30f))
            drawRoundRect(body, Offset(W * 0.33f, H * 0.5f), Size(W * 0.15f, H * 0.48f), CornerRadius(30f, 30f))
            drawRoundRect(body, Offset(W * 0.52f, H * 0.5f), Size(W * 0.15f, H * 0.48f), CornerRadius(30f, 30f))
        }
        regions.forEach { r ->
            val isSel = selected == r
            Box(
                Modifier
                    .offset(x = w * r.x - 18.dp, y = h * r.y - 18.dp)
                    .size(36.dp)
                    .background(if (isSel) sel else dot.copy(alpha = 0.55f), CircleShape)
                    .clickable { onSelect(r) },
            )
        }
        Text("L", Modifier.offset(x = 4.dp, y = 4.dp), style = MaterialTheme.typography.titleMedium)
        Text("R", Modifier.offset(x = w - 20.dp, y = 4.dp), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun NiggleScreen(nav: Nav) {
    val dao = Graph.dao
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val niggles by dao.niggles().collectAsState(initial = emptyList())
    var region by remember { mutableStateOf<Region?>(null) }
    var severity by remember { mutableStateOf(3f) }
    var notes by remember { mutableStateOf("") }

    LogScaffold("Niggles", nav) {
        item {
            SectionCard("Where? (mirror view: your left is on the left)") {
                BodyMap(region) { region = it }
                Gap(8)
                Text(region?.let { "${it.side} ${it.name}".removePrefix("Centre ") } ?: "Tap a dot", style = MaterialTheme.typography.titleLarge)
                if (region != null && region!!.side != "Centre") {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Left", "Right", "Both").forEach { side ->
                            AssistChip(onClick = { region = region!!.copy(side = side) }, label = { Text(side) })
                        }
                    }
                }
                Gap(8)
                Text("Severity: ${severity.toInt()}/10")
                Slider(value = severity, onValueChange = { severity = it }, valueRange = 0f..10f, steps = 9)
                TextInput(notes, { notes = it }, "Notes (what aggravates it?)", singleLine = false)
                Gap(8)
                BigButton("Save", enabled = region != null, onClick = {
                    focus.clearFocus()
                    val r = region ?: return@BigButton
                    scope.launch {
                        dao.insertNiggle(Niggle(at = System.currentTimeMillis(), region = r.name, side = r.side, severity = severity.toInt(), notes = notes))
                        notes = ""; region = null
                    }
                })
            }
        }
        items(niggles, key = { it.id }) { n ->
            DeletableRow(
                "${if (n.side == "Centre") "" else n.side + " "}${n.region} · ${n.severity}/10",
                fmtDateTime(n.at) + if (n.notes.isNotBlank()) " · ${n.notes}" else "",
            ) { scope.launch { dao.deleteNiggle(n.id) } }
        }
    }
}

// ---------- Bodyweight ----------

@Composable
fun BodyweightScreen(nav: Nav) {
    val dao = Graph.dao
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val entries by dao.bodyweights().collectAsState(initial = emptyList())
    val targetStr by dao.settingFlow(Settings.BODYWEIGHT_TARGET).collectAsState(initial = "98")
    val target = Calc.parseNumber(targetStr) ?: 98.0
    var input by remember(entries.firstOrNull()?.id) { mutableStateOf(entries.firstOrNull()?.kg?.let { Calc.fmt(it) } ?: "") }
    val points = entries.map { it.day to it.kg }
    val avg = Calc.rollingAverage(points)
    val latestAvg = avg.lastOrNull()?.second

    LogScaffold("Bodyweight", nav) {
        item {
            SectionCard("Today") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NumberField(input, { input = it }, "kg", Modifier.weight(1f))
                    HGap()
                    Button(onClick = {
                        focus.clearFocus()
                        val kg = Calc.parseNumber(input) ?: return@Button
                        scope.launch {
                            val existing = dao.bodyweight(today())
                            dao.upsertBodyweight(BodyWeight(id = existing?.id ?: 0, day = today(), kg = kg))
                        }
                    }, modifier = Modifier.height(56.dp)) { Text("Save") }
                }
            }
        }
        item {
            SectionCard("Trend") {
                if (latestAvg != null) {
                    StatLine("7-day average", "${Calc.fmt(latestAvg)} kg")
                    StatLine("Target", "${Calc.fmt(target)} kg")
                    StatLine("To go", "${Calc.fmt(latestAvg - target)} kg")
                    val weekAgo = avg.lastOrNull { it.first <= today() - 7 }?.second
                    if (weekAgo != null) StatLine("Change vs last week", "${if (latestAvg - weekAgo >= 0) "+" else ""}${Calc.fmt(latestAvg - weekAgo)} kg")
                    Gap(8)
                }
                val recent = points.filter { it.first >= today() - 90 }
                LineChart(
                    listOf(recent.map { it.first.toFloat() to it.second.toFloat() }, avg.filter { it.first >= today() - 90 }.map { it.first.toFloat() to it.second.toFloat() }),
                    listOf(MaterialTheme.colorScheme.outline, MaterialTheme.colorScheme.primary),
                    targetY = target.toFloat(),
                    showPoints = true,
                )
                Muted("Grey: daily · Colour: 7-day average · Dashed: target (last 90 days)")
            }
        }
        items(entries, key = { it.id }) { b ->
            DeletableRow("${Calc.fmt(b.kg)} kg", fmtDay(b.day)) { scope.launch { dao.deleteBodyweight(b.id) } }
        }
    }
}

// ---------- Protein ----------

@Composable
fun ProteinScreen(nav: Nav) {
    val dao = Graph.dao
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val entries by dao.proteins().collectAsState(initial = emptyList())
    val presets by dao.proteinPresets().collectAsState(initial = emptyList())
    val minStr by dao.settingFlow(Settings.PROTEIN_MIN).collectAsState(initial = "160")
    val maxStr by dao.settingFlow(Settings.PROTEIN_MAX).collectAsState(initial = "175")
    val min = minStr?.toIntOrNull() ?: 160
    val max = maxStr?.toIntOrNull() ?: 175
    val todays = entries.filter { it.day == today() }
    val total = todays.sumOf { it.grams }
    var custom by remember { mutableStateOf("") }
    var editPresets by remember { mutableStateOf(false) }
    var newLabel by remember { mutableStateOf("") }
    var newGrams by remember { mutableStateOf("") }

    fun add(grams: Int, label: String) {
        scope.launch { dao.insertProtein(ProteinEntry(day = today(), at = System.currentTimeMillis(), grams = grams, label = label)) }
    }

    LogScaffold("Protein", nav) {
        item {
            SectionCard {
                Text("$total g", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                Muted(if (total >= min) "Target hit ($min–$max g) ✓" else "${min - total} g to go (target $min–$max g)")
                Gap(6)
                LinearProgressIndicator(progress = { (total.toFloat() / min).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            }
        }
        item {
            SectionCard("Quick add") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    presets.forEach { p ->
                        Button(onClick = { add(p.grams, p.label) }) { Text("${p.label} · ${p.grams}g") }
                    }
                }
                Gap(8)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NumberField(custom, { custom = it }, "Grams", Modifier.weight(1f), decimal = false)
                    HGap()
                    OutlinedButton(onClick = { focus.clearFocus(); custom.toIntOrNull()?.let { add(it, "") }; custom = "" }, modifier = Modifier.height(56.dp)) { Text("Add") }
                }
                TextButton(onClick = { editPresets = !editPresets }) { Text(if (editPresets) "Done editing presets" else "Edit presets") }
                if (editPresets) {
                    presets.forEach { p ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${p.label} · ${p.grams}g", Modifier.weight(1f))
                            DangerTextButton("Remove") { scope.launch { dao.deleteProteinPreset(p.id) } }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.OutlinedTextField(newLabel, { newLabel = it }, label = { Text("Label") }, singleLine = true, modifier = Modifier.weight(1f))
                        HGap(4)
                        NumberField(newGrams, { newGrams = it }, "g", Modifier.width(80.dp), decimal = false)
                    }
                    TextButton(enabled = newLabel.isNotBlank() && newGrams.toIntOrNull() != null, onClick = {
                        focus.clearFocus()
                        scope.launch {
                            dao.insertProteinPreset(ProteinPreset(label = newLabel.trim(), grams = newGrams.toInt(), sortOrder = presets.size))
                            newLabel = ""; newGrams = ""
                        }
                    }) { Text("+ Add preset") }
                }
            }
        }
        item {
            val last7 = (0L..6L).map { today() - it }
            val hit = last7.count { d -> entries.filter { it.day == d }.sumOf { it.grams } >= min }
            Muted("Last 7 days: target hit on $hit of 7 days")
        }
        items(todays, key = { it.id }) { e ->
            DeletableRow("${e.grams} g" + if (e.label.isNotBlank()) " · ${e.label}" else "", fmtTime(e.at)) {
                scope.launch { dao.deleteProtein(e.id) }
            }
        }
    }
}

// ---------- Supplements ----------

@Composable
fun SupplementsScreen(nav: Nav) {
    val dao = Graph.dao
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val supplements by dao.supplements().collectAsState(initial = emptyList())
    val log by dao.supplementLog(today()).collectAsState(initial = emptyList())
    val taken = log.map { it.supplementId }.toSet()
    var edit by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    LogScaffold("Supplements", nav) {
        item { Muted("Today: ${taken.size} of ${supplements.size} taken") }
        items(supplements, key = { it.id }) { s ->
            SectionCard(onClick = {
                scope.launch {
                    val l = SupplementLog(s.id, today())
                    if (s.id in taken) dao.deleteSupplementLog(l) else dao.insertSupplementLog(l)
                }
            }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = s.id in taken, onCheckedChange = null)
                    Text(s.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    if (edit) DangerTextButton("Remove") { scope.launch { dao.updateSupplement(s.copy(active = false)) } }
                }
            }
        }
        item {
            TextButton(onClick = { edit = !edit }) { Text(if (edit) "Done editing" else "Edit list") }
            if (edit) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.OutlinedTextField(newName, { newName = it }, label = { Text("New supplement") }, singleLine = true, modifier = Modifier.weight(1f))
                    HGap()
                    Button(enabled = newName.isNotBlank(), onClick = {
                        focus.clearFocus()
                        scope.launch { dao.insertSupplement(Supplement(name = newName.trim(), sortOrder = supplements.size)); newName = "" }
                    }) { Text("Add") }
                }
            }
        }
    }
}
