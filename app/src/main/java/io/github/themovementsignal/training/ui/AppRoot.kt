package io.github.themovementsignal.training.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/** Everything the app can navigate to (besides the four tabs). */
sealed interface Screen {
    data class Workout(val id: Long) : Screen
    data class WorkoutDetail(val id: Long) : Screen
    data object Exercises : Screen
    data class ExerciseDetail(val id: Long) : Screen
    data class ExerciseEdit(val id: Long?) : Screen
    data class TemplateEdit(val id: Long) : Screen
    data object Sauna : Screen
    data object Neat : Screen
    data object Activity : Screen
    data object Gear : Screen
    data object Sleep : Screen
    data object CheckIn : Screen
    data object Niggle : Screen
    data object Bodyweight : Screen
    data object Protein : Screen
    data object Supplements : Screen
    data object PlateCalc : Screen
    data object Settings : Screen
    data object Venues : Screen
    data object Data : Screen
    data object Errors : Screen
    data object JumpTest : Screen
}

enum class Tab(val label: String) { HOME("Today"), TRAIN("Train"), HISTORY("History"), MORE("More") }

/** Small hand-drawn tab icons (no icon library needed). They follow the current content colour. */
@Composable
private fun TabIcon(tab: Tab) {
    val c = LocalContentColor.current
    Canvas(Modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = w * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (tab) {
            Tab.HOME -> {
                // Target / today ring
                drawCircle(c, radius = w * 0.40f, style = stroke)
                drawCircle(c, radius = w * 0.14f)
            }
            Tab.TRAIN -> {
                // Dumbbell
                drawLine(c, Offset(w * 0.22f, h / 2), Offset(w * 0.78f, h / 2), strokeWidth = w * 0.09f, cap = StrokeCap.Round)
                drawRoundRect(c, Offset(w * 0.14f, h * 0.26f), Size(w * 0.12f, h * 0.48f), CornerRadius(w * 0.03f))
                drawRoundRect(c, Offset(w * 0.74f, h * 0.26f), Size(w * 0.12f, h * 0.48f), CornerRadius(w * 0.03f))
                drawRoundRect(c, Offset(w * 0.04f, h * 0.36f), Size(w * 0.09f, h * 0.28f), CornerRadius(w * 0.03f))
                drawRoundRect(c, Offset(w * 0.87f, h * 0.36f), Size(w * 0.09f, h * 0.28f), CornerRadius(w * 0.03f))
            }
            Tab.HISTORY -> {
                // Clock
                drawCircle(c, radius = w * 0.40f, style = stroke)
                drawLine(c, Offset(w / 2, h / 2), Offset(w / 2, h * 0.26f), strokeWidth = w * 0.09f, cap = StrokeCap.Round)
                drawLine(c, Offset(w / 2, h / 2), Offset(w * 0.68f, h * 0.60f), strokeWidth = w * 0.09f, cap = StrokeCap.Round)
            }
            Tab.MORE -> {
                listOf(0.2f, 0.5f, 0.8f).forEach { x -> drawCircle(c, radius = w * 0.08f, center = Offset(w * x, h / 2)) }
            }
        }
    }
}

/** Minimal navigation: a back stack on top of four tabs. */
class Nav {
    val stack = mutableStateListOf<Screen>()

    fun go(s: Screen) { stack.add(s) }
    fun back() { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }
    /** Replace the current screen (e.g. workout → its summary). */
    fun replace(s: Screen) { back(); go(s) }
}

@Composable
fun AppRoot() {
    val nav = remember { Nav() }
    var tabIndex by rememberSaveable { mutableStateOf(0) }

    val top = nav.stack.lastOrNull()
    if (top != null) {
        BackHandler { nav.back() }
        ScreenContent(top, nav)
        return
    }
    BackHandler(enabled = tabIndex != 0) { tabIndex = 0 }
    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = i == tabIndex,
                        onClick = { tabIndex = i },
                        icon = { TabIcon(t) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (Tab.entries[tabIndex]) {
                Tab.HOME -> HomeScreen(nav)
                Tab.TRAIN -> TrainScreen(nav)
                Tab.HISTORY -> HistoryScreen(nav)
                Tab.MORE -> MoreScreen(nav)
            }
        }
    }
}

@Composable
private fun ScreenContent(s: Screen, nav: Nav) {
    when (s) {
        is Screen.Workout -> WorkoutScreen(s.id, nav)
        is Screen.WorkoutDetail -> WorkoutDetailScreen(s.id, nav)
        Screen.Exercises -> ExercisesScreen(nav)
        is Screen.ExerciseDetail -> ExerciseDetailScreen(s.id, nav)
        is Screen.ExerciseEdit -> ExerciseEditScreen(s.id, nav)
        is Screen.TemplateEdit -> TemplateEditScreen(s.id, nav)
        Screen.Sauna -> SaunaScreen(nav)
        Screen.Neat -> NeatScreen(nav)
        Screen.Activity -> ActivityScreen(nav)
        Screen.Gear -> GearScreen(nav)
        Screen.Sleep -> SleepScreen(nav)
        Screen.CheckIn -> CheckInScreen(nav)
        Screen.Niggle -> NiggleScreen(nav)
        Screen.Bodyweight -> BodyweightScreen(nav)
        Screen.Protein -> ProteinScreen(nav)
        Screen.Supplements -> SupplementsScreen(nav)
        Screen.PlateCalc -> PlateCalcScreen(nav)
        Screen.Settings -> SettingsScreen(nav)
        Screen.Venues -> VenuesScreen(nav)
        Screen.Data -> DataScreen(nav)
        Screen.Errors -> ErrorLogScreen(nav)
        Screen.JumpTest -> JumpTestScreen(nav)
    }
}
