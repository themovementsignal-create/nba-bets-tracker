package io.github.themovementsignal.training.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import io.github.themovementsignal.training.Graph
import io.github.themovementsignal.training.data.Gear
import io.github.themovementsignal.training.data.GearWithWear
import io.github.themovementsignal.training.data.SessionType
import io.github.themovementsignal.training.domain.Calc
import kotlinx.coroutines.launch

/** Replacement nudge text, or null if the item is fine. */
fun gearNudge(g: GearWithWear): String? {
    if (g.gear.retired) return null
    val age = today() - g.gear.startDay
    val bySessions = g.gear.lifespanSessions?.let { g.sessions.toDouble() / it } ?: 0.0
    val byDays = g.gear.lifespanDays?.let { age.toDouble() / it } ?: 0.0
    val worst = maxOf(bySessions, byDays)
    return when {
        worst >= 1.0 -> "Due for replacement"
        worst >= 0.9 -> "Replace soon"
        else -> null
    }
}

private val categories = listOf("Shoes", "Shirt", "Shorts", "Socks", "Sleeve / brace", "Other")

@Composable
fun GearScreen(nav: Nav) {
    val dao = Graph.dao
    val gear by dao.gearWithWear().collectAsState(initial = emptyList())
    var editing by remember { mutableStateOf<Gear?>(null) }
    var creating by remember { mutableStateOf(false) }

    LogScaffold("Gear", nav) {
        item { BigButton("+ Add gear", onClick = { creating = true }, secondary = true) }
        item { Muted("Each session you log counts against the gear set as default for that session type.") }
        gear.forEach { g ->
            item(key = g.gear.id) {
                val age = today() - g.gear.startDay
                val nudge = gearNudge(g)
                SectionCard(onClick = { editing = g.gear }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(g.gear.name + if (g.gear.retired) " (retired)" else "", style = MaterialTheme.typography.titleMedium)
                            Muted(
                                "${g.gear.category} · ${g.sessions}${g.gear.lifespanSessions?.let { "/$it" } ?: ""} sessions · " +
                                    "$age${g.gear.lifespanDays?.let { "/$it" } ?: ""} days"
                            )
                            val defaults = Calc.tags(g.gear.defaultFor)
                            if (defaults.isNotEmpty()) Muted("Default for: " + defaults.joinToString { SessionType.label(it) })
                        }
                    }
                    if (nudge != null) Text("⚠ $nudge", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (creating) GearDialog(null, onDismiss = { creating = false })
    editing?.let { GearDialog(it, onDismiss = { editing = null }) }
}

@Composable
private fun GearDialog(existing: Gear?, onDismiss: () -> Unit) {
    val dao = Graph.dao
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var category by remember { mutableStateOf(existing?.category ?: "Shoes") }
    var daysAgo by remember { mutableStateOf(existing?.let { (today() - it.startDay).toString() } ?: "0") }
    var lifeSessions by remember { mutableStateOf(existing?.lifespanSessions?.toString() ?: "") }
    var lifeDays by remember { mutableStateOf(existing?.lifespanDays?.toString() ?: "") }
    var defaults by remember { mutableStateOf(existing?.let { Calc.tags(it.defaultFor) } ?: setOf(SessionType.STRENGTH)) }
    var retired by remember { mutableStateOf(existing?.retired ?: false) }
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add gear" else "Edit gear") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                TextInput(name, { name = it }, "Name (e.g. Nike Metcon 9)")
                Gap(8)
                Text("Category")
                ChipGroup(categories, setOf(category), { it }) { category = it }
                Gap(8)
                NumberField(daysAgo, { daysAgo = it }, "Started using (days ago)", Modifier.fillMaxWidth(), decimal = false)
                NumberField(lifeSessions, { lifeSessions = it }, "Replace after N sessions (optional)", Modifier.fillMaxWidth(), decimal = false)
                NumberField(lifeDays, { lifeDays = it }, "Replace after N days (optional)", Modifier.fillMaxWidth(), decimal = false)
                Gap(8)
                Text("Default for")
                ChipGroup(SessionType.all, defaults, SessionType::label) { t ->
                    defaults = if (t in defaults) defaults - t else defaults + t
                }
                if (existing != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Retired", Modifier.weight(1f))
                        Switch(checked = retired, onCheckedChange = { retired = it })
                    }
                    DangerTextButton("Delete") { confirmDelete = true }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                val g = Gear(
                    id = existing?.id ?: 0,
                    name = name.trim(),
                    category = category,
                    startDay = today() - (daysAgo.toLongOrNull() ?: 0),
                    lifespanSessions = lifeSessions.toIntOrNull(),
                    lifespanDays = lifeDays.toIntOrNull(),
                    defaultFor = defaults.joinToString(","),
                    retired = retired,
                    notes = existing?.notes.orEmpty(),
                )
                scope.launch { if (existing == null) dao.insertGear(g) else dao.updateGear(g) }
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (confirmDelete && existing != null) {
        ConfirmDialog("Delete ${existing.name}?", "Its wear history is deleted too. Use Retired to keep history.", onConfirm = {
            scope.launch { dao.deleteGearUsageForGear(existing.id); dao.deleteGearRow(existing.id) }
            onDismiss()
        }, onDismiss = { confirmDelete = false })
    }
}
