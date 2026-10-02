package com.muir.bear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muir.bear.Graph
import com.muir.bear.data.SessionType
import com.muir.bear.data.Setting
import com.muir.bear.data.Settings
import com.muir.bear.domain.Calc
import com.muir.bear.domain.Insights
import com.muir.bear.domain.Readiness
import com.muir.bear.domain.ProgramReview
import com.muir.bear.sleep.SleepTracker
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private data class Tile(val title: String, val status: String, val screen: Screen, val done: Boolean = false)

@Composable
fun HomeScreen(nav: Nav) {
    val dao = Graph.dao
    val active by dao.activeWorkout().collectAsState(initial = null)
    val workouts by dao.workoutSummaries().collectAsState(initial = emptyList())
    val activities by dao.activities().collectAsState(initial = emptyList())
    val saunas by dao.saunas().collectAsState(initial = emptyList())
    val bodyweights by dao.bodyweights().collectAsState(initial = emptyList())
    val proteins by dao.proteins().collectAsState(initial = emptyList())
    val sleeps by dao.sleeps().collectAsState(initial = emptyList())
    val steps by dao.dailySteps().collectAsState(initial = emptyList())
    val tracking by SleepTracker.state.collectAsState()
    val checkIns by dao.checkIns().collectAsState(initial = emptyList())
    val niggles by dao.niggles().collectAsState(initial = emptyList())
    val gear by dao.gearWithWear().collectAsState(initial = emptyList())
    val supplements by dao.supplements().collectAsState(initial = emptyList())
    val suppLog by dao.supplementLog(today()).collectAsState(initial = emptyList())
    val bwTarget = Calc.parseNumber(dao.settingFlow(Settings.BODYWEIGHT_TARGET).collectAsState(initial = "98").value) ?: 98.0
    val proteinMin = dao.settingFlow(Settings.PROTEIN_MIN).collectAsState(initial = "160").value?.toIntOrNull() ?: 160
    val monthTarget = dao.settingFlow(Settings.MONTHLY_SESSION_TARGET).collectAsState(initial = "7").value?.toIntOrNull() ?: 7
    val neatTarget = dao.settingFlow(Settings.NEAT_WEEKLY_SESSIONS).collectAsState(initial = "3").value?.toIntOrNull() ?: 3
    val programStart = dao.settingFlow(Settings.PROGRAM_START).collectAsState(initial = null).value?.toLongOrNull()
    val reviewWeeks = dao.settingFlow(Settings.PROGRAM_REVIEW_WEEKS).collectAsState(initial = null).value?.toIntOrNull()
    val reviewSnooze = dao.settingFlow(Settings.PROGRAM_REVIEW_SNOOZE).collectAsState(initial = null).value?.toLongOrNull()
    val scope = rememberCoroutineScope()

    val todayDate = LocalDate.now()
    val weekStart = Calc.mondayOf(todayDate).toEpochDay()
    val finished = workouts.map { it.workout }
    val sport = activities.filter { it.type == SessionType.BASKETBALL || it.type == SessionType.CONDITIONING }
    val neat = activities.filter { it.type == SessionType.NEAT }

    // Sessions this month
    val monthSessions = finished.count { it.startedAt.toLocalDate().let { d -> d.year == todayDate.year && d.month == todayDate.month } } +
        sport.count { it.startedAt.toLocalDate().let { d -> d.year == todayDate.year && d.month == todayDate.month } }

    // Weekly load = session RPE × minutes
    val loadItems = trainingLoads(finished, activities)
    val weeks = Calc.weekly(loadItems, todayDate, weeks = 8)
    val form = Insights.fitnessFatigue(loadItems, today()).lastOrNull()

    val todayProtein = proteins.filter { it.day == today() }.sumOf { it.grams }
    val proteinHitDays = (0L..6L).count { off -> proteins.filter { it.day == today() - off }.sumOf { it.grams } >= proteinMin }
    val bwAvg = Calc.rollingAverage(bodyweights.map { it.day to it.kg })
    val lastSleep = sleeps.firstOrNull { it.wakeAt != null }
    val sleepingNow = sleeps.firstOrNull { it.wakeAt == null && System.currentTimeMillis() - it.bedAt < 20 * 3600_000L }
    val todaysCheckIn = checkIns.firstOrNull { it.day == today() }
    val recentNiggles = niggles.filter { it.at >= System.currentTimeMillis() - 14 * 86_400_000L && it.severity >= 3 }
        .distinctBy { it.region + it.side }
    val review = ProgramReview.status(
        programStart, workouts.lastOrNull()?.workout?.startedAt?.toLocalDate()?.toEpochDay(), today(), reviewWeeks, reviewSnooze,
    )
    // Readiness: plain call + reasons, from sleep, check-in, load and niggles.
    val hrv by dao.hrvReadings().collectAsState(initial = emptyList())
    val sleepNeed = Calc.parseNumber(dao.settingFlow(Settings.SLEEP_NEED).collectAsState(initial = null).value) ?: 8.0
    val readiness = run {
        val nights = sleeps.filter { it.wakeAt != null }.sortedByDescending { it.wakeAt }
        val hours = nights.map { (it.wakeAt!! - it.bedAt) / 3_600_000.0 }
        val lastNight = nights.firstOrNull()?.takeIf { it.wakeAt!!.toLocalDate() == todayDate }
        Readiness.assess(
            Readiness.Inputs(
                lastNightHours = lastNight?.let { hours.first() },
                typicalHours = Readiness.median(hours.drop(1).take(14)).takeIf { hours.size >= 4 },
                debtHours = Insights.sleepStats(nights.map { Insights.Night(it.bedAt, it.wakeAt!!) }, sleepNeed)?.debtHours,
                energy = todaysCheckIn?.energy,
                soreness = todaysCheckIn?.soreness,
                formRatio = form?.takeIf { it.fitness >= 1.0 }?.let { it.form / it.fitness },
                hrvToday = hrv.firstOrNull()?.takeIf { it.at.toLocalDate() == todayDate }?.rmssdMs,
                hrvBaseline = hrv.drop(1).filter { it.at >= System.currentTimeMillis() - 30 * 86_400_000L }.map { it.rmssdMs },
                niggles = niggles.filter { it.at >= System.currentTimeMillis() - 7 * 86_400_000L }
                    .sortedByDescending { it.at }.distinctBy { it.region + it.side }
                    .map { (if (it.side == "Centre") "" else it.side + " ").lowercase().replaceFirstChar { c -> c.uppercase() } + it.region.lowercase() to it.severity },
            ),
        )
    }
    val gearNudges = gear.mapNotNull { g -> gearNudge(g)?.let { "${g.gear.name}: $it" } }

    val tiles = listOf(
        Tile("Check-in", todaysCheckIn?.let { "Sl ${it.sleep} · So ${it.soreness} · En ${it.energy}" } ?: "Not done", Screen.CheckIn, todaysCheckIn != null),
        Tile("Sleep", when {
            tracking != null -> "Tracking since ${fmtTime(tracking!!.startedAt)}"
            sleepingNow != null -> "In bed since ${fmtTime(sleepingNow.bedAt)}"
            lastSleep != null -> (lastSleep.score?.let { "Score $it · " } ?: "") +
                "%.1f h".format((lastSleep.wakeAt!! - lastSleep.bedAt) / 3600_000.0) + (lastSleep.quality?.let { " · $it/5" } ?: "")
            else -> "Smart alarm & tracking"
        }, Screen.Sleep, lastSleep?.wakeAt?.toLocalDate() == todayDate),
        Tile("Bodyweight", bodyweights.firstOrNull()?.let { "${Calc.fmt(it.kg)} kg" + if (it.day == today()) " today" else " · ${fmtDay(it.day)}" } ?: "No entries", Screen.Bodyweight, bodyweights.firstOrNull()?.day == today()),
        Tile("Protein", "$todayProtein / $proteinMin g", Screen.Protein, todayProtein >= proteinMin),
        Tile("Supplements", "${suppLog.size} / ${supplements.size}", Screen.Supplements, supplements.isNotEmpty() && suppLog.size >= supplements.size),
        Tile("Sauna", saunas.firstOrNull()?.let { fmtDate(it.at) } ?: "None yet", Screen.Sauna, saunas.firstOrNull()?.at?.toLocalDate() == todayDate),
        Tile("Steps & NEAT", "%,d steps · ".format(steps.firstOrNull { it.day == today() }?.steps ?: 0) +
            "${neat.count { it.startedAt.toLocalDate().toEpochDay() >= weekStart }}/$neatTarget", Screen.Neat),
        Tile("Conditioning", sport.firstOrNull()?.let { "${activityTitle(it).removePrefix("Conditioning · ")} ${fmtDate(it.startedAt)}" } ?: "None yet", Screen.Activity),
        Tile("Niggles", if (recentNiggles.isEmpty()) "None active" else "${recentNiggles.size} active", Screen.Niggle),
        Tile("Gear", if (gearNudges.isEmpty()) "All good" else "${gearNudges.size} to replace", Screen.Gear),
    )

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column {
                BearWordmark(fontSize = 36.sp)
                Gap(2)
                BearMotto()
                Gap(12)
                Text(todayDate.format(DateTimeFormatter.ofPattern("EEEE d MMMM")), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        active?.let { w ->
            item {
                SectionCard("Workout in progress", onClick = { nav.go(Screen.Workout(w.id)) }) {
                    Text(w.name, style = MaterialTheme.typography.titleLarge)
                    Muted("Started ${fmtTime(w.startedAt)} · tap to resume")
                }
            }
        }
        item {
            SectionCard("Readiness", onClick = { nav.go(if (todaysCheckIn == null) Screen.CheckIn else Screen.Insights) }) {
                val r = readiness
                if (r == null) {
                    Muted("Do your morning check-in (and track or log last night's sleep) to see how ready you are today.")
                } else {
                    Text(r.call, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                    Gap(4)
                    r.signals.take(4).forEach { s ->
                        Text(
                            "· ${s.reason}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (s.level == Readiness.Level.POOR) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (hrv.firstOrNull()?.at?.toLocalDate() != todayDate) {
                    TextButton(onClick = { nav.go(Screen.Hrv) }) { Text("Measure morning HRV ›") }
                }
            }
        }
        if (review?.due == true) item {
            SectionCard("Time to review your program") {
                Text("Week ${review.weeksDone + 1} of this block", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                Muted("Running since ${fmtDay(review.startDay)}. After ${review.reviewWeeks} weeks it's worth a look: what's progressing, what's stalled, how you feel.")
                Row {
                    TextButton(onClick = {
                        scope.launch {
                            dao.putSetting(Setting(Settings.PROGRAM_START, today().toString()))
                            dao.putSetting(Setting(Settings.PROGRAM_REVIEW_SNOOZE, ""))
                        }
                    }) { Text("Reviewed · start new block") }
                    TextButton(onClick = {
                        scope.launch { dao.putSetting(Setting(Settings.PROGRAM_REVIEW_SNOOZE, (today() + 7).toString())) }
                    }) { Text("Next week") }
                }
            }
        }
        tiles.chunked(2).forEach { pair ->
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { t -> TileCard(t, Modifier.weight(1f)) { nav.go(t.screen) } }
                    if (pair.size == 1) Column(Modifier.weight(1f)) {}
                }
            }
        }
        if (gearNudges.isNotEmpty()) item {
            SectionCard("Gear to replace", onClick = { nav.go(Screen.Gear) }) {
                gearNudges.forEach { Text("⚠ $it", color = MaterialTheme.colorScheme.error) }
            }
        }
        if (recentNiggles.isNotEmpty()) item {
            SectionCard("Active niggles (last 14 days)", onClick = { nav.go(Screen.Niggle) }) {
                recentNiggles.forEach { Muted("${if (it.side == "Centre") "" else it.side + " "}${it.region}: ${it.severity}/10 · ${fmtDate(it.at)}") }
            }
        }
        item {
            SectionCard("Sessions this month") {
                Text("$monthSessions / $monthTarget", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                LinearProgressIndicator(progress = { (monthSessions.toFloat() / monthTarget).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                Gap(4)
                Muted("Strength workouts + conditioning")
            }
        }
        item {
            SectionCard("Weekly load (RPE × minutes)", onClick = { nav.go(Screen.Insights) }) {
                BarChart(
                    weeks.map { it.value.toFloat() },
                    weeks.map { it.start.format(DateTimeFormatter.ofPattern("d/M")) },
                    weeks.map { false },
                )
                Gap(4)
                weeks.lastOrNull()?.let { Muted("This week: ${it.value.toInt()}") }
                form?.let { Text(Insights.formLabel(it), color = MaterialTheme.colorScheme.primary) }
                Muted("Sessions without an RPE aren't counted. Tap for fitness, fatigue and more in Insights.")
            }
        }
        item {
            SectionCard("This week") {
                StatLine("Sauna", "${saunas.filter { it.at.toLocalDate().toEpochDay() >= weekStart }.sumOf { it.rounds * it.minutesPerRound }} min")
                val neatWeek = neat.filter { it.startedAt.toLocalDate().toEpochDay() >= weekStart }
                StatLine("NEAT", "${neatWeek.size} sessions · ${neatWeek.sumOf { it.durationMin }} min")
                StatLine("Protein target hit", "$proteinHitDays of last 7 days")
                val weekSteps = steps.filter { it.day >= weekStart }.sumOf { it.steps }
                StatLine("Steps", "%,d (avg %,d/day)".format(weekSteps, weekSteps / (today() - weekStart + 1).toInt()))
            }
        }
        item {
            SectionCard("Bodyweight", onClick = { nav.go(Screen.Bodyweight) }) {
                val latest = bwAvg.lastOrNull()
                if (latest == null) Muted("Log your weight to see the trend.") else {
                    StatLine("7-day average", "${Calc.fmt(latest.second)} kg")
                    StatLine("Target", "${Calc.fmt(bwTarget)} kg (${if (latest.second - bwTarget >= 0) "+" else ""}${Calc.fmt(latest.second - bwTarget)})")
                    Gap(4)
                    LineChart(
                        listOf(bwAvg.filter { it.first >= today() - 30 }.map { it.first.toFloat() to it.second.toFloat() }),
                        listOf(MaterialTheme.colorScheme.primary),
                        targetY = bwTarget.toFloat(),
                        showPoints = false,
                    )
                }
            }
        }
        item {
            SectionCard("Sleep vs performance") {
                // Pair each strength session with the sleep that ended the same day.
                val pairs = workouts.mapNotNull { s ->
                    val day = s.workout.startedAt.toLocalDate()
                    val sl = sleeps.firstOrNull { it.wakeAt != null && it.wakeAt.toLocalDate() == day } ?: return@mapNotNull null
                    val vol = s.volume ?: return@mapNotNull null
                    if (vol <= 0) return@mapNotNull null
                    ((sl.wakeAt!! - sl.bedAt) / 3600_000.0).toFloat() to vol.toFloat()
                }
                Muted("Each dot is a workout: hours slept the night before (→) vs volume lifted (↑).")
                ScatterChart(pairs)
                val good = pairs.filter { it.first >= 7f }.map { it.second }
                val poor = pairs.filter { it.first < 7f }.map { it.second }
                if (good.isNotEmpty() && poor.isNotEmpty()) {
                    Muted("Avg volume after ≥7 h: ${Calc.fmt(good.average())} kg · after <7 h: ${Calc.fmt(poor.average())} kg")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TileCard(t: Tile, modifier: Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier.heightIn(min = 84.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (t.done) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text(t.title + if (t.done) " ✓" else "", style = MaterialTheme.typography.titleMedium)
            Muted(t.status)
        }
    }
}
