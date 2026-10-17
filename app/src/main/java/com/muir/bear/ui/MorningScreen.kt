package com.muir.bear.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.muir.bear.Graph
import com.muir.bear.data.BodyWeight
import com.muir.bear.data.CheckIn
import com.muir.bear.data.Settings
import com.muir.bear.data.SupplementLog
import com.muir.bear.domain.Calc
import com.muir.bear.domain.Dashboard
import com.muir.bear.sleep.SleepTracker
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * Opens after you turn the wake-up alarm off: your morning check-ins on one screen, each ticked
 * once done. Which ones appear follows your Today ticks; HRV is always offered.
 */
@Composable
fun MorningScreen(nav: Nav) {
    val dao = Graph.dao
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val ticksSaved by dao.settingFlow(Settings.DASH_TICKS).collectAsState(initial = null)
    val ticks = Dashboard.parseTicks(ticksSaved)
    val sleeps by dao.sleeps().collectAsState(initial = emptyList())
    val checkIns by dao.checkIns().collectAsState(initial = emptyList())
    val weights by dao.bodyweights().collectAsState(initial = emptyList())
    val supplements by dao.supplements().collectAsState(initial = emptyList())
    val suppLog by dao.supplementLog(today()).collectAsState(initial = emptyList())
    val readings by dao.hrvReadings().collectAsState(initial = emptyList())

    val now = System.currentTimeMillis()
    val lastNight = sleeps.firstOrNull { (it.wakeAt ?: now) > now - 12 * 3_600_000L && it.bedAt > now - 24 * 3_600_000L }
    val todaysCheckIn = checkIns.firstOrNull { it.day == today() }
    val todaysWeight = weights.firstOrNull { it.day == today() }
    val taken = suppLog.map { it.supplementId }.toSet()
    val zone = ZoneId.systemDefault()
    val todaysHrv = readings.firstOrNull { Instant.ofEpochMilli(it.at).atZone(zone).toLocalDate().toEpochDay() == today() }

    var sleepQ by remember(todaysCheckIn) { mutableStateOf(todaysCheckIn?.sleep) }
    var soreness by remember(todaysCheckIn) { mutableStateOf(todaysCheckIn?.soreness) }
    var energy by remember(todaysCheckIn) { mutableStateOf(todaysCheckIn?.energy) }
    var kg by remember(todaysWeight?.id, weights.firstOrNull()?.id) {
        mutableStateOf((todaysWeight ?: weights.firstOrNull())?.kg?.let { Calc.fmt(it) } ?: "")
    }

    fun title(name: String, done: Boolean) = if (done) "$name ✓" else name

    LogScaffold("Good morning", nav) {
        item { Muted(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM"))) }
        lastNight?.let { s ->
            item {
                SectionCard("Last night", onClick = { nav.go(Screen.SleepNight(s.id)) }) {
                    val end = s.wakeAt ?: now
                    val min = (end - s.bedAt) / 60_000
                    Text("${min / 60} h ${min % 60} min", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                    s.score?.let { Muted("Sleep score $it") }
                    if (s.wakeAt == null) Muted("Finishing last night's analysis…")
                }
            }
        }
        if (Dashboard.Tick.CHECKIN in ticks) item {
            SectionCard(title("Check-in", todaysCheckIn != null)) {
                Text("Sleep (1 = terrible, 5 = great)"); Gap(4)
                RatingRow(sleepQ, { sleepQ = it }); Gap(12)
                Text("Soreness (1 = none, 5 = very sore)"); Gap(4)
                RatingRow(soreness, { soreness = it }); Gap(12)
                Text("Energy (1 = flat, 5 = great)"); Gap(4)
                RatingRow(energy, { energy = it }); Gap(12)
                BigButton(if (todaysCheckIn != null) "Update" else "Save", enabled = sleepQ != null && soreness != null && energy != null, onClick = {
                    val q = sleepQ!!
                    scope.launch {
                        dao.upsertCheckIn(CheckIn(id = todaysCheckIn?.id ?: 0, day = today(), sleep = q, soreness = soreness!!, energy = energy!!))
                        // One question, not two: your sleep rating also rates last night.
                        lastNight?.let { s -> if (s.quality == null && s.wakeAt != null) dao.updateSleep(s.copy(quality = q)) }
                        SleepTracker.justFinished.value = null
                    }
                })
            }
        }
        if (Dashboard.Tick.BODYWEIGHT in ticks) item {
            SectionCard(title("Bodyweight", todaysWeight != null)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NumberField(kg, { kg = it }, "kg", Modifier.weight(1f))
                    HGap()
                    Button(onClick = {
                        focus.clearFocus()
                        val value = Calc.parseNumber(kg) ?: return@Button
                        scope.launch { dao.upsertBodyweight(BodyWeight(id = todaysWeight?.id ?: 0, day = today(), kg = value)) }
                    }, modifier = Modifier.height(56.dp)) { Text(if (todaysWeight != null) "Update" else "Save") }
                }
            }
        }
        if (Dashboard.Tick.SUPPLEMENTS in ticks && supplements.isNotEmpty()) item {
            SectionCard(title("Supplements ${taken.size}/${supplements.size}", taken.size >= supplements.size)) {
                supplements.forEach { s ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            scope.launch {
                                val l = SupplementLog(s.id, today())
                                if (s.id in taken) dao.deleteSupplementLog(l) else dao.insertSupplementLog(l)
                            }
                        }.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = s.id in taken, onCheckedChange = null)
                        HGap()
                        Text(s.name)
                    }
                }
                TextButton(onClick = { nav.go(Screen.Supplements) }) { Text("Reminder times ›") }
            }
        }
        item {
            SectionCard(title("Morning HRV", todaysHrv != null)) {
                if (todaysHrv != null) {
                    Text("${todaysHrv.rmssdMs.roundToInt()} ms · ${todaysHrv.heartRate.roundToInt()} bpm", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    TextButton(onClick = { nav.go(Screen.Hrv) }) { Text("See readings ›") }
                } else {
                    Muted("One minute, fingertip on the camera, before you get up.")
                    Gap(8)
                    BigButton("Measure HRV", onClick = { nav.go(Screen.Hrv) }, secondary = true)
                }
            }
        }
        item {
            BigButton("Done", onClick = { nav.home() })
        }
    }
}
