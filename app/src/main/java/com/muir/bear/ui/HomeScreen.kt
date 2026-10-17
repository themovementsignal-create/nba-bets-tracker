@file:OptIn(ExperimentalLayoutApi::class)

package com.muir.bear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Button
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import com.muir.bear.steps.Steps
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.muir.bear.data.Exercise
import com.muir.bear.data.ExerciseType
import com.muir.bear.data.Template
import com.muir.bear.data.TemplateKind
import com.muir.bear.data.Workout
import com.muir.bear.data.WorkoutSet
import com.muir.bear.domain.Dashboard
import com.muir.bear.domain.Rpe
import kotlin.math.roundToInt
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

/** One bento tile: a big number (or word), its unit, a small chart and a footer line. */
private data class Tile(
    val title: String,
    val screen: Screen,
    val value: Double? = null,
    val decimals: Int = 0,
    /** Where the number counts up from when Today opens (e.g. yesterday's value). */
    val from: Double? = null,
    /** Shown instead of a number when there isn't one. */
    val text: String? = null,
    val unit: String = "",
    val footLeft: String = "",
    val footRight: String? = null,
    val done: Boolean = false,
    val bars: List<Float>? = null,
    /** Counting right now (pedometer): a small pulsing dot. */
    val live: Boolean = false,
)

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
    val loadDays = Insights.fitnessFatigue(loadItems, today())

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
        // Ignore "nights" under an hour (naps, tests, mistakes).
        val nights = sleeps.filter { it.wakeAt != null && it.wakeAt - it.bedAt >= 3_600_000L }.sortedByDescending { it.wakeAt }
        val hours = nights.map { (it.wakeAt!! - it.bedAt) / 3_600_000.0 }
        val lastNight = nights.firstOrNull()?.takeIf { it.wakeAt!!.toLocalDate() == todayDate }
        Readiness.assess(
            Readiness.Inputs(
                lastNightHours = lastNight?.let { hours.first() },
                typicalHours = Readiness.median(hours.drop(1).take(14)).takeIf { hours.size >= 4 },
                debtHours = Insights.sleepStats(nights.map { Insights.Night(it.bedAt, it.wakeAt!!) }, sleepNeed)?.debtHours,
                energy = todaysCheckIn?.energy,
                soreness = todaysCheckIn?.soreness,
                formRatio = Insights.formRatio(loadDays),
                hrvToday = hrv.firstOrNull()?.takeIf { it.at.toLocalDate() == todayDate }?.rmssdMs,
                hrvBaseline = hrv.drop(1).filter { it.at >= System.currentTimeMillis() - 30 * 86_400_000L }.map { it.rmssdMs },
                niggles = niggles.filter { it.at >= System.currentTimeMillis() - 7 * 86_400_000L }
                    .sortedByDescending { it.at }.distinctBy { it.region + it.side }
                    .map { (if (it.side == "Centre") "" else it.side + " ").lowercase().replaceFirstChar { c -> c.uppercase() } + it.region.lowercase() to it.severity },
            ),
        )
    }
    val gearNudges = gear.mapNotNull { g -> gearNudge(g)?.let { "${g.gear.name}: $it" } }

    // Your Today layout (order, on/off), chips and key lifts, from Edit Today.
    val layout = Dashboard.parse(dao.settingFlow(Settings.DASH_LAYOUT).collectAsState(initial = null).value)
    val ticks = Dashboard.parseTicks(dao.settingFlow(Settings.DASH_TICKS).collectAsState(initial = null).value)
    val liftsSetting = dao.settingFlow(Settings.DASH_LIFTS).collectAsState(initial = null).value
    val templates by dao.templates().collectAsState(initial = emptyList())

    // Last 7 days, oldest first, for the small trend bars in tiles.
    val week7 = (6 downTo 0).map { today() - it }
    val sleepBars = week7.map { d ->
        sleeps.filter { it.wakeAt != null && it.wakeAt - it.bedAt >= 3_600_000L && it.wakeAt.toLocalDate().toEpochDay() == d }
            .sumOf { (it.wakeAt!! - it.bedAt) / 3_600_000.0 }.toFloat()
    }
    val stepBars = week7.map { d -> (steps.firstOrNull { it.day == d }?.steps ?: 0).toFloat() }
    val context = LocalContext.current
    val stepSensor = remember { Steps.available(context) }

    fun tile(item: Dashboard.Item): Tile? = when (item) {
        Dashboard.Item.SLEEP -> {
            val nights = sleeps.filter { it.wakeAt != null && it.wakeAt - it.bedAt >= 3_600_000L }
            val hours = nights.map { (it.wakeAt!! - it.bedAt) / 3_600_000.0 }
            when {
                tracking != null -> Tile("Sleep", Screen.Sleep, text = "Tracking", unit = "since ${fmtTime(tracking!!.startedAt)}", footLeft = "Sleep well", bars = sleepBars)
                sleepingNow != null -> Tile("Sleep", Screen.Sleep, text = "In bed", unit = "since ${fmtTime(sleepingNow.bedAt)}", bars = sleepBars)
                hours.isNotEmpty() -> Tile(
                    "Sleep", Screen.Sleep, value = hours.first(), decimals = 1, from = hours.getOrNull(1), unit = "hours",
                    footLeft = nights.first().score?.let { "Score $it" } ?: "Last night",
                    footRight = nights.first().quality?.let { "$it/5" },
                    done = nights.first().wakeAt!!.toLocalDate() == todayDate, bars = sleepBars,
                )
                else -> Tile("Sleep", Screen.Sleep, text = "–", unit = "hours", footLeft = "Track tonight", bars = sleepBars)
            }
        }
        Dashboard.Item.STEPS -> {
            val past = stepBars.dropLast(1).filter { it > 0f }
            Tile(
                "Steps", Screen.Neat, value = (steps.firstOrNull { it.day == today() }?.steps ?: 0).toDouble(), from = 0.0,
                unit = "steps today",
                footLeft = "NEAT ${neat.count { it.startedAt.toLocalDate().toEpochDay() >= weekStart }}/$neatTarget",
                footRight = if (past.isNotEmpty()) "avg %,d".format(past.average().roundToInt()) else null,
                bars = stepBars, live = stepSensor,
            )
        }
        Dashboard.Item.HRV -> {
            val latest = hrv.firstOrNull()
            val baseline = hrv.drop(1).filter { it.at >= System.currentTimeMillis() - 30 * 86_400_000L }.map { it.rmssdMs }
            val measuredToday = latest?.at?.toLocalDate() == todayDate
            if (latest == null) Tile("HRV", Screen.Hrv, text = "–", unit = "ms", footLeft = "Measure ›")
            else Tile(
                "HRV", Screen.Hrv, value = latest.rmssdMs, from = hrv.getOrNull(1)?.rmssdMs, unit = "ms · ${latest.heartRate.roundToInt()} bpm",
                footLeft = if (measuredToday) "This morning" else "Measure ›",
                footRight = if (baseline.size >= 3) (latest.rmssdMs - baseline.average()).roundToInt().let { if (it >= 0) "+$it" else "$it" } else null,
                done = measuredToday, bars = hrv.take(14).reversed().map { it.rmssdMs.toFloat() },
            )
        }
        Dashboard.Item.CONDITIONING -> {
            val week = sport.count { it.startedAt.toLocalDate().toEpochDay() >= weekStart }
            Tile(
                "Conditioning", Screen.Activity, value = week.toDouble(), unit = if (week == 1) "session this week" else "sessions this week",
                footLeft = sport.firstOrNull()?.let { "${activityTitle(it).removePrefix("Conditioning · ")} · ${fmtDate(it.startedAt)}" } ?: "None yet",
                done = sport.firstOrNull()?.startedAt?.toLocalDate() == todayDate,
            )
        }
        Dashboard.Item.NIGGLES -> Tile(
            "Niggles", Screen.Niggle, value = recentNiggles.size.toDouble(), unit = "active",
            footLeft = if (recentNiggles.isEmpty()) "All clear" else "Last 14 days",
        )
        Dashboard.Item.GEAR -> Tile(
            "Gear", Screen.Gear, value = gearNudges.size.toDouble(), unit = "to replace",
            footLeft = if (gearNudges.isEmpty()) "All good" else gearNudges.first().substringBefore(':'),
        )
        else -> null
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(Modifier.padding(top = 6.dp, bottom = 4.dp)) {
                Text(todayDate.format(DateTimeFormatter.ofPattern("EEEE d MMMM")), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Gap(10)
                BearWordmark(fontSize = 36.sp)
                Gap(2)
                BearMotto()
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

        // Tiles that sit next to each other are laid out two per row.
        val pendingTiles = mutableListOf<Tile>()
        fun flushTiles() {
            pendingTiles.chunked(2).forEach { pair ->
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pair.forEach { t -> BentoTile(t, Modifier.weight(1f)) { nav.go(t.screen) } }
                        if (pair.size == 1) Column(Modifier.weight(1f)) {}
                    }
                }
            }
            pendingTiles.clear()
        }
        for (entry in layout.filter { it.shown }) {
            val t = tile(entry.item)
            if (t != null) { pendingTiles += t; continue }
            flushTiles()
            when (entry.item) {
                Dashboard.Item.READINESS -> item {
                    // The one card that sums up today: a slow breathing glow and a gauge that sweeps in.
                    val breath = rememberBreath()
                    Column(
                        Modifier.fillMaxWidth()
                            .bentoSurface(MaterialTheme.colorScheme.primary, { nav.go(if (todaysCheckIn == null) Screen.CheckIn else Screen.Insights) }, glowAlpha = 0.10f + 0.14f * breath)
                            .padding(18.dp),
                    ) {
                        CardLabel("Readiness", arrow = true)
                        val r = readiness
                        if (r == null) {
                            Gap(8)
                            Muted("Do your morning check-in (and track or log last night's sleep) to see how ready you are today.")
                        } else {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(r.call, Modifier.weight(1f).padding(top = 10.dp), style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary)
                                HalfGauge(
                                    when (r.call) { "Ready" -> 0.82f; "Go easy" -> 0.28f; else -> 0.55f },
                                    Modifier.size(width = 108.dp, height = 60.dp),
                                )
                            }
                            Gap(8)
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
                Dashboard.Item.NEXT -> item { NextSessionCard(templates, finished, active != null, nav) }
                Dashboard.Item.TICKS -> if (ticks.isNotEmpty()) item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Dashboard.Tick.entries.filter { it in ticks }.forEach { tk ->
                            when (tk) {
                                Dashboard.Tick.CHECKIN -> TickChip("Check-in", todaysCheckIn?.let { "${it.sleep}·${it.soreness}·${it.energy}" }, todaysCheckIn != null) { nav.go(Screen.CheckIn) }
                                Dashboard.Tick.BODYWEIGHT -> TickChip("Bodyweight", bodyweights.firstOrNull()?.takeIf { it.day == today() }?.let { "${Calc.fmt(it.kg)} kg" }, bodyweights.firstOrNull()?.day == today()) { nav.go(Screen.Bodyweight) }
                                Dashboard.Tick.SUPPLEMENTS -> TickChip("Supplements", "${suppLog.size}/${supplements.size}", supplements.isNotEmpty() && suppLog.size >= supplements.size) { nav.go(Screen.Supplements) }
                                Dashboard.Tick.SAUNA -> TickChip("Sauna", null, saunas.firstOrNull()?.at?.toLocalDate() == todayDate) { nav.go(Screen.Sauna) }
                                Dashboard.Tick.PROTEIN -> TickChip("Protein", "$todayProtein/$proteinMin g", todayProtein >= proteinMin) { nav.go(Screen.Protein) }
                            }
                        }
                    }
                }
                Dashboard.Item.LIFTS -> item { KeyLiftsCard(liftsSetting, workouts.size, nav) }
                Dashboard.Item.MONTH -> item {
                    SectionCard("Sessions this month") {
                        Text("$monthSessions / $monthTarget", style = MaterialTheme.typography.displaySmall)
                        Gap(8)
                        LinearProgressIndicator(progress = { (monthSessions.toFloat() / monthTarget).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                        Gap(4)
                        Muted("Strength workouts + conditioning")
                    }
                }
                Dashboard.Item.LOAD -> item {
                    SectionCard("Weekly load (RPE × minutes)", onClick = { nav.go(Screen.Insights) }) {
                        BarChart(
                            weeks.map { it.value.toFloat() },
                            weeks.map { it.start.format(DateTimeFormatter.ofPattern("d/M")) },
                            weeks.map { false },
                        )
                        Gap(4)
                        weeks.lastOrNull()?.let { Muted("This week: ${it.value.toInt()}") }
                        if (loadDays.isNotEmpty()) Text(Insights.formLabel(loadDays), color = MaterialTheme.colorScheme.primary)
                        Muted("Sessions without an RPE aren't counted. Tap for fitness, fatigue and more in Insights.")
                    }
                }
                Dashboard.Item.WEEK -> item {
                    SectionCard("This week") {
                        StatLine("Sauna", "${saunas.filter { it.at.toLocalDate().toEpochDay() >= weekStart }.sumOf { it.rounds * it.minutesPerRound }} min")
                        val neatWeek = neat.filter { it.startedAt.toLocalDate().toEpochDay() >= weekStart }
                        StatLine("NEAT", "${neatWeek.size} ${if (neatWeek.size == 1) "session" else "sessions"} · ${neatWeek.sumOf { it.durationMin }} min")
                        if (Dashboard.Tick.PROTEIN in ticks) StatLine("Protein target hit", "$proteinHitDays of last 7 days")
                        val weekSteps = steps.filter { it.day >= weekStart }.sumOf { it.steps }
                        StatLine("Steps", "%,d (avg %,d/day)".format(weekSteps, weekSteps / (today() - weekStart + 1).toInt()))
                    }
                }
                Dashboard.Item.BODYWEIGHT -> item {
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
                Dashboard.Item.SLEEP_PERF -> item {
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
                else -> {}
            }
        }
        flushTiles()

        // Things that need attention always show, whatever the layout.
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
            TextButton(onClick = { nav.go(Screen.EditToday) }, modifier = Modifier.fillMaxWidth()) { Text("Edit Today") }
        }
    }
}

/** The session due next in your rotation, one tap to start. */
@Composable
private fun NextSessionCard(templates: List<Template>, finished: List<Workout>, workoutActive: Boolean, nav: Nav) {
    val scope = rememberCoroutineScope()
    val strength = templates.filter { it.kind == TemplateKind.STRENGTH }
    val done = finished.filter { it.templateId != null && it.endedAt != null }
    val nextId = Dashboard.nextTemplate(strength.map { it.id }, done.map { it.templateId!! to it.endedAt!! }) ?: return
    val next = strength.firstOrNull { it.id == nextId } ?: return
    val lastOfIt = done.filter { it.templateId == nextId }.maxOfOrNull { it.startedAt }
    val lastAny = finished.maxByOrNull { it.startedAt }
    SectionCard("Next session") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(next.name, style = MaterialTheme.typography.titleLarge)
                Muted(
                    (lastOfIt?.let { "Last done ${fmtDate(it)}" } ?: "Not done yet") +
                        (lastAny?.let { " · last workout ${fmtDate(it.startedAt)}" } ?: ""),
                )
            }
            if (!workoutActive) {
                HGap(12)
                Button(
                    onClick = {
                        scope.launch {
                            val id = Actions.startWorkout(next.id)
                            nav.go(Screen.Workout(id))
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                ) { Text("Start", style = MaterialTheme.typography.titleMedium) }
            }
        }
        if (workoutActive) {
            Gap(8)
            Muted("A workout is in progress: finish it first, or resume it above.")
        }
    }
}

/** A small daily log chip: bronze once done today. */
@Composable
private fun TickChip(label: String, value: String?, done: Boolean, onClick: () -> Unit) {
    val shape = MaterialTheme.shapes.small
    val m = if (done) Modifier.background(MaterialTheme.colorScheme.secondaryContainer, shape)
    else Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
    Row(
        m
            .clip(shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label + if (done) " ✓" else "", style = MaterialTheme.typography.labelLarge, color = if (done) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface)
        value?.let { Text("  $it", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/** Seven small bars, oldest to newest, for a quiet trend inside a tile. */
@Composable
private fun MiniBars(values: List<Float>) {
    val color = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.surfaceVariant
    val grow = rememberDrawIn()
    Canvas(Modifier.fillMaxWidth().height(28.dp)) {
        val max = (values.maxOrNull() ?: 0f).coerceAtLeast(0.0001f)
        val gap = 4.dp.toPx()
        val w = (size.width - gap * (values.size - 1)) / values.size
        values.forEachIndexed { i, v ->
            // Bars grow in left to right as Today opens.
            val g = ((grow * (values.size + 3) - i) / 4f).coerceIn(0f, 1f)
            val h = if (v <= 0f) 2.dp.toPx() else (v / max) * size.height * g
            drawRoundRect(
                color = if (v <= 0f) empty else color.copy(alpha = if (i == values.lastIndex) 1f else 0.55f),
                topLeft = Offset(i * (w + gap), size.height - h),
                size = Size(w, h),
                cornerRadius = CornerRadius(2.dp.toPx()),
            )
        }
    }
}

/**
 * Estimated 1RM over 12 weeks for up to three lifts you pick in Edit Today (or, by default,
 * the two you've trained most). RPE-adjusted where sets were rated.
 */
@Composable
private fun KeyLiftsCard(liftsSetting: String?, workoutCount: Int, nav: Nav) {
    val dao = Graph.dao
    var sets by remember { mutableStateOf<List<WorkoutSet>>(emptyList()) }
    var exercises by remember { mutableStateOf<Map<Long, Exercise>>(emptyMap()) }
    var workoutDays by remember { mutableStateOf<Map<Long, Long>>(emptyMap()) }
    LaunchedEffect(workoutCount) {
        sets = dao.allSets()
        exercises = dao.allExercises().associateBy { it.id }
        workoutDays = dao.allWorkouts().filter { it.endedAt != null }.associate { it.id to it.startedAt.toLocalDate().toEpochDay() }
    }
    val since = today() - 84
    val sessions = sets.asSequence()
        .filter { it.completed && it.kind != "W" && (it.weightKg ?: 0.0) > 0 && (it.reps ?: 0) > 0 }
        .filter { exercises[it.exerciseId]?.type == ExerciseType.WEIGHT_REPS }
        .filter { (workoutDays[it.workoutId] ?: -1) >= since }
        .groupBy { it.exerciseId }
        .mapValues { (_, ss) ->
            ss.groupBy { it.workoutId }.map { (wid, ws) -> workoutDays.getValue(wid) to ws.maxOf { Rpe.e1rm(it.weightKg!!, it.reps!!, it.rpe) } }.sortedBy { it.first }
        }
    val picked = liftsSetting?.split(',')?.mapNotNull { it.trim().toLongOrNull() }?.filter { it in exercises }.orEmpty()
    val lifts = (picked.ifEmpty { sessions.entries.sortedByDescending { it.value.size }.take(2).map { it.key } }).take(3)
    val colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.tertiary)
    SectionCard("Key lifts", onClick = { nav.go(Screen.EditToday) }) {
        val shown = lifts.filter { (sessions[it]?.size ?: 0) > 0 }
        if (shown.isEmpty()) {
            Muted("Your main lifts' estimated 1RM over 12 weeks will show here once you've trained them.")
        } else {
            LineChart(shown.map { id -> sessions.getValue(id).map { it.first.toFloat() to it.second.toFloat() } }, colors.take(shown.size))
            Gap(4)
            shown.forEachIndexed { i, id ->
                val pts = sessions.getValue(id)
                val change = pts.last().second - pts.first().second
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(Modifier.size(10.dp)) { drawCircle(colors[i]) }
                    Text(
                        "  ${exercises[id]?.name}: ${(pts.last().second * 2).roundToInt() / 2.0} kg" +
                            if (pts.size > 1) " (${if (change >= 0) "+" else ""}${Calc.fmt(Calc.round2(change))} in 12 wks)" else "",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Muted("Estimated 1RM per session, using your RPE where you rated sets. Choose lifts in Edit Today.")
        }
    }
}

@Composable
private fun BentoTile(t: Tile, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.height(170.dp).bentoSurface(glowFor(t.title), onClick).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                t.title + if (t.done) " ✓" else "",
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                maxLines = 1,
            )
            if (t.live) { LiveDot(); HGap(6) }
            ArrowUpRight()
        }
        Gap(10)
        val big = MaterialTheme.typography.displaySmall
        if (t.value != null) {
            CountUp(t.value, { v -> if (t.decimals > 0) "%.${t.decimals}f".format(v) else "%,d".format(v.roundToInt()) }, big, from = t.from)
        } else {
            Text(t.text ?: "–", style = if ((t.text?.length ?: 0) > 4) MaterialTheme.typography.headlineMedium else big, maxLines = 1)
        }
        Text(t.unit, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Box(Modifier.weight(1f))
        t.bars?.let { MiniBars(it); Gap(8) }
        Row {
            Text(t.footLeft, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            t.footRight?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium) }
        }
    }
}

/** A small bronze dot that pulses slowly: this number is counting right now. */
@Composable
private fun LiveDot() {
    val primary = MaterialTheme.colorScheme.primary
    val a = if (Motion.animateLists) {
        val t = rememberInfiniteTransition(label = "live")
        t.animateFloat(1f, 0.25f, infiniteRepeatable(tween(1_200), RepeatMode.Reverse), label = "dot").value
    } else 1f
    Canvas(Modifier.size(7.dp)) { drawCircle(primary.copy(alpha = a)) }
}
