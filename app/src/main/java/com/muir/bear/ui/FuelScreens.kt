@file:OptIn(ExperimentalMaterial3Api::class)

package com.muir.bear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.muir.bear.Graph
import com.muir.bear.data.FuelCheck
import com.muir.bear.data.FuelRule
import com.muir.bear.data.RuleKind
import com.muir.bear.domain.Calc
import com.muir.bear.domain.FuelScore
import com.muir.bear.data.Setting
import com.muir.bear.data.Settings
import com.muir.bear.domain.SnackPrompt
import com.muir.bear.fuel.FuelPrompt
import com.muir.bear.fuel.FuelStore
import kotlinx.coroutines.launch
import java.time.LocalDate

// ---------- Today ----------

/** Today's score for the Fuel chip on Today, or null if every rule is off. */
@Composable
fun rememberFuelToday(): FuelScore.Day? {
    val dao = Graph.dao
    val rules by dao.fuelRules().collectAsState(initial = emptyList())
    val checks by dao.fuelChecksOn(FuelStore.todayIso()).collectAsState(initial = emptyList())
    val workouts by dao.finishedWorkouts().collectAsState(initial = emptyList())
    val activities by dao.activities().collectAsState(initial = emptyList())
    val live = rules.filter { it.enabled }
    if (live.isEmpty()) return null
    return FuelStore.score(live, checks, LocalDate.now() in FuelStore.trainingDays(workouts, activities))
}

/** Top of the Fuel page: today's rules as tappable rows, fruit as a 0–2 stepper, today's snack. */
@Composable
private fun FuelTodayCard(nav: Nav) {
    val dao = Graph.dao
    val scope = rememberCoroutineScope()
    val date = FuelStore.todayIso()
    val rules by dao.fuelRules().collectAsState(initial = emptyList())
    val checks by dao.fuelChecksOn(date).collectAsState(initial = emptyList())
    val logs by dao.foodLogsOn(date).collectAsState(initial = emptyList())
    val workouts by dao.finishedWorkouts().collectAsState(initial = emptyList())
    val activities by dao.activities().collectAsState(initial = emptyList())
    val trained = LocalDate.now() in FuelStore.trainingDays(workouts, activities)
    val live = rules.filter { it.enabled }
    if (live.isEmpty()) return
    val byRule = checks.associateBy { it.ruleId }
    val day = FuelStore.score(live, checks, trained)

    SectionCard("Today") {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "${day.hit} of ${day.applicable}",
                style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                color = if (day.goalMet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            HGap(10)
            Muted("Goal: ${(day.applicable - 1).coerceAtLeast(0)} of ${day.applicable} most days", Modifier.padding(bottom = 4.dp))
        }
        val phase = rememberPhase()
        if (phase.cut) Muted(phase.line())
        Gap(8)
        live.forEach { r ->
            val check = byRule[r.id]
            val applies = FuelScore.applies(FuelStore.ruleIn(r), check?.let(FuelStore::checkIn), trained)
            FuelRuleRow(r, check, applies) { action ->
                scope.launch {
                    when (action) {
                        is RuleAction.Toggle -> FuelStore.toggle(r, date)
                        is RuleAction.Count -> FuelStore.setCount(r, date, action.value)
                    }
                }
            }
        }
        Gap(6)
        val snacks = logs.filter { it.slot == com.muir.bear.data.MealSlot.SNACK }
        snacks.forEach { Muted("Snack: ${it.nameSnapshot} · ${fmtTime(it.loggedAt)}") }
        TextButton(onClick = { nav.go(Screen.Snacks) }) { Text(if (snacks.isEmpty()) "Log a snack ›" else "Snack list ›") }
    }
}

sealed interface RuleAction {
    data object Toggle : RuleAction
    data class Count(val value: Int) : RuleAction
}

@Composable
private fun FuelRuleRow(rule: FuelRule, check: FuelCheck?, applies: Boolean, onAction: (RuleAction) -> Unit) {
    val value = FuelScore.value(FuelStore.ruleIn(rule), check?.let(FuelStore::checkIn))
    val hit = value >= rule.target.coerceAtLeast(1)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .then(if (rule.kind == RuleKind.COUNTER) Modifier else Modifier.clickable { onAction(RuleAction.Toggle) }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                rule.label, style = MaterialTheme.typography.bodyLarge,
                color = if (applies) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!applies) Muted("Rest day: doesn't count")
            else if (check?.source == "AUTO") Muted("Ticked for you · tap to change")
        }
        if (rule.kind == RuleKind.COUNTER) {
            SmallSquareButton("−") { onAction(RuleAction.Count(value - 1)) }
            Text("$value/${rule.target}", Modifier.width(52.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium,
                color = if (hit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            SmallSquareButton("+") { onAction(RuleAction.Count(value + 1)) }
        } else {
            TickBox(hit, Modifier.padding(end = 8.dp))
        }
    }
}

@Composable
private fun TickBox(on: Boolean, modifier: Modifier = Modifier, size: Int = 26) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier.size(size.dp).clip(shape)
            .then(
                if (on) Modifier.background(MaterialTheme.colorScheme.primary)
                else Modifier.border(1.5.dp, MaterialTheme.colorScheme.outline, shape)
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (on && size >= 20) Text("✓", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge)
    }
}

// ---------- Week view ----------

@Composable
fun FuelWeekScreen(nav: Nav) {
    val dao = Graph.dao
    var weekStart by remember { mutableStateOf(Calc.mondayOf(LocalDate.now()).toEpochDay()) }
    val days = (0L..6L).map { LocalDate.ofEpochDay(weekStart + it) }
    val rules by dao.fuelRules().collectAsState(initial = emptyList())
    val checks by dao.fuelChecksBetween(FuelStore.iso(days.first()), FuelStore.iso(days.last())).collectAsState(initial = emptyList())
    val logs by dao.foodLogsBetween(FuelStore.iso(days.first()), FuelStore.iso(days.last())).collectAsState(initial = emptyList())
    val workouts by dao.finishedWorkouts().collectAsState(initial = emptyList())
    val activities by dao.activities().collectAsState(initial = emptyList())
    val trained = FuelStore.trainingDays(workouts, activities)
    val live = rules.filter { it.enabled }
    val today = LocalDate.now()
    // Days before Fuel was set up (and future days) stay blank.
    val since = dao.settingFlow(Settings.FUEL_SINCE).collectAsState(initial = null).value
        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: today
    fun counts(d: LocalDate) = !d.isAfter(today) && !d.isBefore(since)
    val shown = days.filter(::counts)
    val scores = shown.associateWith { d -> FuelStore.score(live, checks.filter { it.date == FuelStore.iso(d) }, d in trained) }

    LogScaffold("Fuel", nav) {
        if (days.last() >= today) item { FuelTodayCard(nav) }
        item {
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { weekStart -= 7 }) { Text("‹") }
                    Text(
                        "Week of ${fmtDay(days.first())}", Modifier.weight(1f),
                        textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium,
                    )
                    TextButton(onClick = { weekStart += 7 }, enabled = days.last().isBefore(today)) { Text("›") }
                }
                Gap(8)
                // Header: day initials.
                Row {
                    Text("", Modifier.weight(1f))
                    days.forEach { d ->
                        Text(
                            d.dayOfWeek.name.take(1), Modifier.width(CellW), textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (d == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Gap(4)
                live.forEach { r ->
                    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(r.label, Modifier.weight(1f).padding(end = 6.dp), style = MaterialTheme.typography.bodySmall, maxLines = 2)
                        days.forEach { d ->
                            Box(Modifier.width(CellW), contentAlignment = Alignment.Center) {
                                if (counts(d)) {
                                    val c = checks.firstOrNull { it.date == FuelStore.iso(d) && it.ruleId == r.id }?.let(FuelStore::checkIn)
                                    val rin = FuelStore.ruleIn(r)
                                    if (FuelScore.applies(rin, c, d in trained)) TickBox(FuelScore.isHit(rin, c), size = 14)
                                    else Text("·", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                Gap(4)
                Row {
                    Muted("Score", Modifier.weight(1f))
                    days.forEach { d ->
                        val s = scores[d]
                        Text(
                            s?.let { "${it.hit}" } ?: "", Modifier.width(CellW), textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (s?.goalMet == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Gap(10)
                val good = FuelScore.goodDays(scores.values.toList())
                Text(
                    "5+ of 6 on $good ${if (good == 1) "day" else "days"} ${if (days.last().isBefore(today)) "that week" else "this week"}.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Muted("On rest days the shake rule drops out, so 4 of 5 counts.")
            }
        }
        item { BigButton("Snack list", onClick = { nav.go(Screen.Snacks) }, secondary = true) }
        item { BigButton("Fuel settings", onClick = { nav.go(Screen.FuelSettings) }, secondary = true) }
        val snacks = logs.filter { it.slot == com.muir.bear.data.MealSlot.SNACK }
        if (snacks.isNotEmpty()) item {
            SectionCard("Snacks") {
                snacks.sortedByDescending { it.loggedAt }.forEach { Muted("${fmtDay(LocalDate.parse(it.date))} · ${it.nameSnapshot}") }
            }
        }
    }
}

private val CellW = 26.dp

private fun fmtDay(d: LocalDate): String = fmtDay(d.toEpochDay())

// ---------- Phase ----------

/** Maintenance or Cut. In v1 it only changes the words on the card. */
data class FuelPhase(val cut: Boolean, val start: LocalDate?, val end: LocalDate?) {
    /** "Cut · week 2 of 8", "Cut ended Sat 16 Jan", or "Maintenance". */
    fun line(today: LocalDate = LocalDate.now()): String = when {
        !cut -> "Maintenance"
        start != null && today.isBefore(start) -> "Cut starts ${fmtDay(start)}"
        end != null && today.isAfter(end) -> "Cut ended ${fmtDay(end)}"
        start != null && end != null -> {
            val week = java.time.temporal.ChronoUnit.WEEKS.between(start, today) + 1
            val total = (java.time.temporal.ChronoUnit.DAYS.between(start, end) + 6) / 7
            "Cut · week $week of $total"
        }
        else -> "Cut"
    }

    companion object {
        fun from(phase: String?, start: String?, end: String?) = FuelPhase(
            phase == "CUT",
            start?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            end?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        )
    }
}

@Composable
private fun rememberPhase(): FuelPhase {
    val dao = Graph.dao
    val p = dao.settingFlow(Settings.FUEL_PHASE).collectAsState(initial = null).value
    val s = dao.settingFlow(Settings.FUEL_PHASE_START).collectAsState(initial = null).value
    val e = dao.settingFlow(Settings.FUEL_PHASE_END).collectAsState(initial = null).value
    return FuelPhase.from(p, s, e)
}

// ---------- Settings → Fuel ----------

@Composable
fun FuelSettingsScreen(nav: Nav) {
    val dao = Graph.dao
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val rules by dao.fuelRules().collectAsState(initial = emptyList())
    val promptOn = dao.settingFlow(Settings.FUEL_PROMPT_ON).collectAsState(initial = null).value != "0"
    val weekday = SnackPrompt.parse(dao.settingFlow(Settings.FUEL_PROMPT_WEEKDAY).collectAsState(initial = null).value)
    val weekend = SnackPrompt.parse(dao.settingFlow(Settings.FUEL_PROMPT_WEEKEND).collectAsState(initial = null).value)
    val phase = rememberPhase()
    var editRule by remember { mutableStateOf<FuelRule?>(null) }
    var pickTime by remember { mutableStateOf<String?>(null) }

    fun put(key: String, value: String, reschedule: Boolean = false) {
        Graph.scope.launch {
            dao.putSetting(Setting(key, value))
            if (reschedule) FuelPrompt.reschedule(context)
        }
    }

    LogScaffold("Fuel settings", nav) {
        item {
            SectionCard("Rules") {
                rules.forEach { r ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(r.label, Modifier.weight(1f).clickable { editRule = r }, style = MaterialTheme.typography.bodyLarge)
                        Switch(checked = r.enabled, onCheckedChange = { on -> scope.launch { dao.updateFuelRule(r.copy(enabled = on)) } })
                    }
                }
                Muted("Tap a rule to change its wording. Rules that are off don't count.")
            }
        }
        item {
            SectionCard("Afternoon snack prompt") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Prompt me", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = promptOn, onCheckedChange = { put(Settings.FUEL_PROMPT_ON, if (it) "1" else "0", reschedule = true) })
                }
                if (promptOn) {
                    TextButton(onClick = { pickTime = Settings.FUEL_PROMPT_WEEKDAY }) { Text("Weekdays at ${SnackPrompt.fmt(weekday)}") }
                    TextButton(onClick = { pickTime = Settings.FUEL_PROMPT_WEEKEND }) { Text("Weekends at ${SnackPrompt.fmt(weekend)}") }
                    Muted("One suggestion from your snack list, with Log it, Something else and Skip today. No prompt once a snack is logged.")
                }
            }
        }
        item {
            SectionCard("Phase") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !phase.cut, onClick = { put(Settings.FUEL_PHASE, "MAINTENANCE") }, label = { Text("Maintenance") })
                    FilterChip(selected = phase.cut, onClick = {
                        if (!phase.cut) Graph.scope.launch {
                            val start = LocalDate.now()
                            dao.putSetting(Setting(Settings.FUEL_PHASE, "CUT"))
                            dao.putSetting(Setting(Settings.FUEL_PHASE_START, start.toString()))
                            dao.putSetting(Setting(Settings.FUEL_PHASE_END, start.plusWeeks(8).minusDays(1).toString()))
                        }
                    }, label = { Text("Cut") })
                }
                if (phase.cut) {
                    Gap(6)
                    Text(phase.line())
                    phase.start?.let { st ->
                        Muted("From ${fmtDay(st)}" + (phase.end?.let { " to ${fmtDay(it)}" } ?: ""))
                        Gap(4)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(4, 8, 12, 16).forEach { w ->
                                val end = st.plusWeeks(w.toLong()).minusDays(1)
                                FilterChip(selected = phase.end == end, onClick = { put(Settings.FUEL_PHASE_END, end.toString()) }, label = { Text("$w wk") })
                            }
                        }
                        TextButton(onClick = { put(Settings.FUEL_PHASE_START, LocalDate.now().toString()) }) { Text("Restart from today") }
                    }
                }
                Muted("For now the phase only changes the words on the Fuel card.")
            }
        }
    }

    editRule?.let { r ->
        var label by remember(r.id) { mutableStateOf(r.label) }
        AlertDialog(
            onDismissRequest = { editRule = null },
            title = { Text("Rule wording") },
            text = { TextInput(label, { label = it }, "Rule") },
            confirmButton = {
                TextButton(enabled = label.isNotBlank(), onClick = {
                    scope.launch { dao.updateFuelRule(r.copy(label = label.trim())) }
                    editRule = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editRule = null }) { Text("Cancel") } },
        )
    }
    pickTime?.let { key ->
        val start = if (key == Settings.FUEL_PROMPT_WEEKEND) weekend else weekday
        val state = rememberTimePickerState(initialHour = start / 60, initialMinute = start % 60, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickTime = null },
            title = { Text(if (key == Settings.FUEL_PROMPT_WEEKEND) "Weekend prompt" else "Weekday prompt") },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    put(key, (state.hour * 60 + state.minute).toString(), reschedule = true)
                    pickTime = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { pickTime = null }) { Text("Cancel") } },
        )
    }
}
