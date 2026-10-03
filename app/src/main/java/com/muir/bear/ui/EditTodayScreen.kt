package com.muir.bear.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.muir.bear.Graph
import com.muir.bear.data.ExerciseType
import com.muir.bear.data.Setting
import com.muir.bear.data.Settings
import com.muir.bear.domain.Dashboard
import kotlinx.coroutines.launch

/** Choose what Today shows and in what order. Hidden things stay in More; nothing is deleted. */
@Composable
fun EditTodayScreen(nav: Nav) {
    val dao = Graph.dao
    val layout = Dashboard.parse(dao.settingFlow(Settings.DASH_LAYOUT).collectAsState(initial = null).value)
    val ticks = Dashboard.parseTicks(dao.settingFlow(Settings.DASH_TICKS).collectAsState(initial = null).value)
    val liftsSetting = dao.settingFlow(Settings.DASH_LIFTS).collectAsState(initial = null).value
    val exercises by dao.exercises().collectAsState(initial = emptyList())
    val picked = liftsSetting?.split(',')?.mapNotNull { it.trim().toLongOrNull() }.orEmpty()

    fun save(key: String, value: String) {
        Graph.scope.launch { dao.putSetting(Setting(key, value)) }
    }

    LogScaffold("Edit Today", nav) {
        item { Muted("Switch things on or off and move them up or down. Hidden items are still in More, and nothing you've logged is lost.") }
        layout.forEachIndexed { i, e ->
            item(key = e.item.id) {
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(e.item.label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                            color = if (e.shown) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(enabled = i > 0, onClick = { save(Settings.DASH_LAYOUT, Dashboard.serialize(Dashboard.move(layout, i, -1))) }) { Text("↑") }
                        TextButton(enabled = i < layout.lastIndex, onClick = { save(Settings.DASH_LAYOUT, Dashboard.serialize(Dashboard.move(layout, i, 1))) }) { Text("↓") }
                        Switch(
                            checked = e.shown,
                            onCheckedChange = { on -> save(Settings.DASH_LAYOUT, Dashboard.serialize(layout.map { if (it.item == e.item) it.copy(shown = on) else it })) },
                            modifier = Modifier.testTag("show-${e.item.id}"),
                        )
                    }
                    if (e.item == Dashboard.Item.TICKS && e.shown) {
                        Muted("Chips to show:")
                        Dashboard.Tick.entries.forEach { t ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = t in ticks, onCheckedChange = { on ->
                                    save(Settings.DASH_TICKS, Dashboard.serializeTicks(if (on) ticks + t else ticks - t))
                                }, modifier = Modifier.testTag("tick-${t.id}"))
                                Text(t.label)
                            }
                        }
                    }
                    if (e.item == Dashboard.Item.LIFTS && e.shown) {
                        Muted(if (picked.isEmpty()) "Showing your two most-trained lifts. Pick up to three:" else "Pick up to three:")
                        exercises.filter { it.type == ExerciseType.WEIGHT_REPS && !it.archived }.sortedBy { it.name }.forEach { ex ->
                            val on = ex.id in picked
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = on, enabled = on || picked.size < 3, onCheckedChange = { c ->
                                    save(Settings.DASH_LIFTS, (if (c) picked + ex.id else picked - ex.id).joinToString(","))
                                })
                                Text(ex.name)
                            }
                        }
                        if (picked.isNotEmpty()) TextButton(onClick = { save(Settings.DASH_LIFTS, "") }) { Text("Back to automatic") }
                    }
                }
            }
        }
        item {
            TextButton(onClick = {
                save(Settings.DASH_LAYOUT, Dashboard.serialize(Dashboard.default))
                save(Settings.DASH_TICKS, Dashboard.serializeTicks(Dashboard.defaultTicks))
                save(Settings.DASH_LIFTS, "")
            }, modifier = Modifier.fillMaxWidth()) { Text("Reset to the default layout") }
        }
    }
}
