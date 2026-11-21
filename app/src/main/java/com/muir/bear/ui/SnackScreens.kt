package com.muir.bear.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.muir.bear.Graph
import com.muir.bear.data.FoodCategory
import com.muir.bear.data.FoodItem
import com.muir.bear.data.LogSource
import com.muir.bear.data.MealSlot
import com.muir.bear.domain.Calc
import com.muir.bear.fuel.FuelPrompt
import com.muir.bear.fuel.FuelStore
import kotlinx.coroutines.launch

/** The snack list: log one, add, edit, reorder, favourite, archive (never delete). */
@Composable
fun SnackListScreen(nav: Nav) {
    val dao = Graph.dao
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val items by dao.allFoodItemsFlow().collectAsState(initial = emptyList())
    val logs by dao.foodLogsOn(FuelStore.todayIso()).collectAsState(initial = emptyList())
    var editing by remember { mutableStateOf<FoodItem?>(null) }
    var adding by remember { mutableStateOf(false) }
    var showArchived by remember { mutableStateOf(false) }
    var justLogged by remember { mutableStateOf<Long?>(null) }
    val snacks = items.filter { it.category in FoodCategory.snacks }
    val live = snacks.filter { !it.archived }
    val archived = snacks.filter { it.archived }

    /** Swap positions with the neighbour in the same category. */
    fun move(item: FoodItem, delta: Int) {
        val group = live.filter { it.category == item.category }
        val i = group.indexOfFirst { it.id == item.id }
        val cur = group.getOrNull(i) ?: return
        val other = group.getOrNull(i + delta) ?: return
        // Equal positions (e.g. both 0) would swap to no effect, so fall back to the list index.
        val (a, b) = if (cur.position != other.position) cur.position to other.position else i to i + delta
        scope.launch { dao.updateFoodItems(listOf(cur.copy(position = b), other.copy(position = a))) }
    }

    LogScaffold("Snacks", nav) {
        val today = logs.filter { it.slot == MealSlot.SNACK }
        if (today.isNotEmpty()) item {
            SectionCard("Today") {
                today.forEach { l ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${l.nameSnapshot} · ${fmtTime(l.loggedAt)}", Modifier.weight(1f))
                        TextButton(onClick = { scope.launch { FuelStore.removeLog(l) } }) { Text("Undo") }
                    }
                }
            }
        }
        FoodCategory.snacks.forEach { cat ->
            val group = live.filter { it.category == cat }
            if (group.isNotEmpty()) item {
                SectionCard(FoodCategory.label(cat)) {
                    group.forEachIndexed { i, f ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { editing = f },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text((if (f.favourite) "★ " else "") + f.name, style = MaterialTheme.typography.bodyLarge)
                                Muted(listOfNotNull(f.portion.ifBlank { null }, f.kcal?.let { "$it kcal" }, f.proteinG?.let { "${Calc.fmt(it)} g protein" }).joinToString(" · "))
                            }
                            OutlinedButton(onClick = {
                                scope.launch { FuelStore.logSnack(f, LogSource.LIST); justLogged = f.id }
                                FuelPrompt.dismiss(context)
                            }) { Text(if (justLogged == f.id) "Logged ✓" else "Log") }
                        }
                        if (i < group.lastIndex) Gap(2)
                    }
                }
            }
        }
        item {
            BigButton("+ Add snack", onClick = { adding = true }, secondary = true)
        }
        if (archived.isNotEmpty()) item {
            SectionCard {
                TextButton(onClick = { showArchived = !showArchived }) {
                    Text("Archived (${archived.size}) ${if (showArchived) "▴" else "▾"}")
                }
                if (showArchived) archived.forEach { f ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${f.name} · ${f.portion}", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { scope.launch { dao.updateFoodItem(f.copy(archived = false)) } }) { Text("Restore") }
                    }
                }
            }
        }
        item { Muted("Archived snacks stay in past logs but are never suggested.", Modifier.padding(horizontal = 4.dp)) }
    }

    editing?.let { f ->
        SnackDialog(
            initial = f,
            onDismiss = { editing = null },
            onSave = { scope.launch { dao.updateFoodItem(it) }; editing = null },
            onArchive = { scope.launch { dao.updateFoodItem(f.copy(archived = true)) }; editing = null },
            onMove = { d -> move(f, d) },
        )
    }
    if (adding) SnackDialog(
        initial = null,
        onDismiss = { adding = false },
        onSave = { f ->
            scope.launch { dao.insertFoodItem(f.copy(position = (items.maxOfOrNull { it.position } ?: 0) + 1)) }
            adding = false
        },
    )
}

@Composable
private fun SnackDialog(
    initial: FoodItem?,
    onDismiss: () -> Unit,
    onSave: (FoodItem) -> Unit,
    onArchive: (() -> Unit)? = null,
    onMove: ((Int) -> Unit)? = null,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var portion by remember { mutableStateOf(initial?.portion.orEmpty()) }
    var category by remember { mutableStateOf(initial?.category ?: FoodCategory.SAVOURY) }
    var kcal by remember { mutableStateOf(initial?.kcal?.toString().orEmpty()) }
    var protein by remember { mutableStateOf(initial?.proteinG?.let { Calc.fmt(it) }.orEmpty()) }
    var favourite by remember { mutableStateOf(initial?.favourite ?: false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add snack" else "Edit snack") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput(name, { name = it }, "Name")
                TextInput(portion, { portion = it }, "Portion (e.g. 50 g, 1 bar)")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FoodCategory.snacks.forEach { c ->
                        FilterChip(selected = category == c, onClick = { category = c }, label = { Text(FoodCategory.label(c)) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(kcal, { kcal = it }, "kcal", Modifier.weight(1f), decimal = false)
                    NumberField(protein, { protein = it }, "Protein g", Modifier.weight(1f))
                }
                Muted("kcal and protein are optional.")
                FilterChip(selected = favourite, onClick = { favourite = !favourite }, label = { Text("★ Favourite (suggested twice as often)") })
                if (onMove != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onMove(-1) }) { Text("Move up") }
                    OutlinedButton(onClick = { onMove(1) }) { Text("Move down") }
                }
                if (onArchive != null) TextButton(onClick = onArchive) { Text("Archive") }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                val base = initial ?: FoodItem(name = "", portion = "", category = category)
                onSave(
                    base.copy(
                        name = name.trim(), portion = portion.trim(), category = category,
                        kcal = kcal.toIntOrNull(), proteinG = Calc.parseNumber(protein), favourite = favourite,
                    )
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
