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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.muir.bear.Graph
import com.muir.bear.data.FuelCheck
import com.muir.bear.data.FuelRule
import com.muir.bear.data.RuleKind
import com.muir.bear.domain.Calc
import com.muir.bear.domain.FuelScore
import com.muir.bear.fuel.FuelStore
import kotlinx.coroutines.launch
import java.time.LocalDate

// ---------- Today card ----------

/** "Fuel" on Today: the rules as tappable rows, fruit as a 0–2 stepper, today's snack. */
@Composable
fun FuelCard(nav: Nav) {
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

    SectionCard("Fuel", onClick = { nav.go(Screen.Fuel) }) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "${day.hit} of ${day.applicable}",
                style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                color = if (day.goalMet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            HGap(10)
            Muted("Goal: ${(day.applicable - 1).coerceAtLeast(0)} of ${day.applicable} most days", Modifier.padding(bottom = 4.dp))
        }
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
        if (snacks.isEmpty()) Muted("No snack logged yet.")
        else snacks.forEach { Muted("Snack: ${it.nameSnapshot} · ${fmtTime(it.loggedAt)}") }
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
    val shown = days.filter { !it.isAfter(today) }
    val scores = shown.associateWith { d -> FuelStore.score(live, checks.filter { it.date == FuelStore.iso(d) }, d in trained) }

    LogScaffold("Fuel", nav) {
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
                                if (!d.isAfter(today)) {
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
        val snacks = logs.filter { it.slot == com.muir.bear.data.MealSlot.SNACK }
        if (snacks.isNotEmpty()) item {
            SectionCard("Snacks") {
                snacks.sortedByDescending { it.loggedAt }.forEach { Muted("${fmtDay(LocalDate.parse(it.date))} · ${it.nameSnapshot}") }
            }
        }
    }
}

private val CellW = 26.dp

private fun fmtDay(d: LocalDate): String =
    "${d.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }} ${d.dayOfMonth} ${d.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }}"
