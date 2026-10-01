package io.github.themovementsignal.training

import android.Manifest
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import java.io.File

/**
 * Walks through every screen like a user would, doing a real workout and a few logs, and saves a
 * screenshot of each step. CI runs this on an emulator so problems show up before the phone install.
 */
@RunWith(AndroidJUnit4::class)
class UiTourTest {

    private val permissions: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: TestRule = RuleChain.outerRule(permissions).around(compose)

    private var shotIndex = 0

    private fun shot(name: String) {
        compose.waitForIdle()
        Thread.sleep(400)
        val bmp: Bitmap? = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "screens").apply { mkdirs() }
        val file = File(dir, "%02d-%s.png".format(shotIndex++, name))
        if (bmp != null) file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
        Log.i("UiTour", "screenshot ${file.name}")
    }

    private fun waitFor(text: String, timeout: Long = 15_000) {
        compose.waitUntil(timeout) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun tap(text: String) {
        waitFor(text)
        compose.onAllNodesWithText(text).onFirst().performClick()
        compose.waitForIdle()
    }

    private fun tapInDialog(text: String) {
        compose.onNode(hasText(text) and hasAnyAncestor(isDialog())).performClick()
        compose.waitForIdle()
    }

    private fun tab(label: String) {
        compose.onNode(hasText(label) and hasClickAction() and isTabLike()).performClick()
        compose.waitForIdle()
    }

    /** Bottom navigation items have the Tab role. */
    private fun isTabLike() = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    private fun scrollTo(text: String) {
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text))
        compose.waitForIdle()
    }

    private fun back() {
        Espresso.closeSoftKeyboard()
        Espresso.pressBack()
        compose.waitForIdle()
    }

    private fun openFromMore(label: String, screenName: String, then: () -> Unit = {}) {
        tab("More")
        scrollTo(label)
        tap(label)
        shot(screenName)
        then()
        back()
    }

    @Test
    fun tour() {
        Log.i("UiTour", "start")
        // Home first (seed data loads in the background).
        compose.waitForIdle()
        shot("home-empty")

        // ---- Train: start Session 1, log sets, finish ----
        tab("Train")
        waitFor("Session 1 · Athletic Lower")
        shot("train")
        tap("Start")
        waitFor("Trap Bar Deadlift")
        shot("workout-start")

        // Type a weight into set 1, tick it, tick set 2 (uses the set above as placeholder).
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("100")
        compose.waitForIdle()
        compose.onAllNodesWithText("✓")[0].performClick()
        compose.waitForIdle()
        compose.onAllNodesWithText("✓")[1].performClick()
        compose.waitForIdle()
        Espresso.closeSoftKeyboard()
        compose.waitForIdle()
        shot("workout-inline-rest-timer")
        // Big rest timer from the header pill: ring, −15/+15, Skip.
        compose.onNodeWithTag("restPill").performClick()
        waitFor("Skip")
        shot("rest-timer-dialog")
        tap("+15s")
        tap("Skip")

        // Exercise menu → plate calculator.
        compose.onAllNodesWithText("⋮")[0].performClick()
        compose.waitForIdle()
        shot("exercise-menu")
        tap("Plate calculator")
        shot("plate-calculator-dialog")
        tapInDialog("Close")

        tap("Finish")
        waitFor("Session RPE (how hard was it overall?)")
        tapInDialog("7")
        shot("finish-dialog")
        tapInDialog("Finish")
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Delete").fetchSemanticsNodes().isNotEmpty() }
        shot("workout-summary")
        back()

        // ---- History ----
        tab("History")
        waitFor("Workouts")
        shot("history")
        tap("Exercises")
        scrollTo("Trap Bar Deadlift")
        tap("Trap Bar Deadlift")
        waitFor("Personal records")
        shot("exercise-detail")
        back()

        // ---- Second Session 1: Previous shows last time's numbers; beating them is a PR ----
        tab("Train")
        tap("Start")
        waitFor("100 × 5")
        shot("workout-previous-numbers")
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("110")
        compose.waitForIdle()
        compose.onAllNodesWithText("✓")[0].performClick()
        waitFor("🏆 PR")
        Espresso.closeSoftKeyboard()
        compose.waitForIdle()
        shot("workout-live-pr")
        tap("Finish")
        waitFor("Session RPE (how hard was it overall?)")
        tapInDialog("8")
        tapInDialog("Finish")
        compose.waitUntil(15_000) { compose.onAllNodesWithText("PRs").fetchSemanticsNodes().isNotEmpty() }
        shot("workout-summary-pr")
        back()
        tab("History")
        waitFor("Best set")
        shot("history-two-workouts")

        // ---- Logs ----
        openFromMore("Morning check-in", "checkin") {
            compose.onAllNodesWithText("4")[0].performClick()
            compose.onAllNodesWithText("2")[1].performClick()
            compose.onAllNodesWithText("4")[2].performClick()
            compose.waitForIdle()
            shot("checkin-filled")
            tap("Save") // returns to More; the back() after this goes to the Today tab, which is fine.
        }
        openFromMore("Sleep", "sleep") {
            tap("🌙 Going to bed")
            waitFor("☀ I'm awake")
            tap("☀ I'm awake")
            waitFor("How did you sleep?")
            shot("sleep-rate")
        }
        openFromMore("Bodyweight", "bodyweight") {
            compose.onAllNodes(hasSetTextAction())[0].performTextInput("99.2")
            tap("Save")
            waitFor("7-day average")
            shot("bodyweight-saved")
        }
        openFromMore("Protein", "protein") {
            tap("Protein shake · 30g")
            waitFor("30 g")
            shot("protein-added")
        }
        openFromMore("Supplements", "supplements") {
            tap("Creatine")
            waitFor("Today: 1 of 4 taken")
            shot("supplements-ticked")
        }
        openFromMore("Sauna", "sauna") {
            tap("Save 45 min session")
            waitFor("Saved ✓")
            shot("sauna-saved")
        }
        openFromMore("NEAT · treadmill", "neat") {
            tap("Start timer")
            waitFor("Stop & save")
            shot("neat-running")
            tap("Stop & save")
            waitFor("Start timer")
        }
        openFromMore("Basketball & conditioning", "activity") {
            compose.onAllNodesWithText("7")[0].performClick()
            tap("Save")
            waitFor("Saved ✓")
            shot("activity-saved")
        }
        openFromMore("Niggles", "niggles")
        openFromMore("Gear", "gear")
        openFromMore("Plate calculator & bars", "plates")
        openFromMore("Gyms & travel mode", "venues")
        openFromMore("Jump height test", "jump")
        openFromMore("Targets & settings", "settings")
        openFromMore("Backup, export & import", "backup")
        openFromMore("Error log", "errors")
        openFromMore("Exercise library", "library")

        // ---- Dashboard with data ----
        tab("Today")
        compose.waitForIdle()
        shot("home-filled-top")
        scrollTo("Sessions this month")
        scrollTo("Weekly load (RPE × minutes)")
        shot("home-filled-load")
        scrollTo("Sleep vs performance")
        shot("home-filled-bottom")

        // ---- Travel mode: hotel gym swaps exercises automatically ----
        tab("Train")
        tap("Hotel gym ✈")
        tap("Start")
        waitFor("Session 1 · Athletic Lower · Hotel gym")
        shot("travel-workout")
        // Drag the first exercise down below the second (long-press its header, then slide).
        compose.onAllNodesWithTag("exerciseHeader")[0].performTouchInput {
            down(center)
            advanceEventTime(1_000)
            repeat(30) { moveBy(Offset(0f, 40f)); advanceEventTime(16) }
            up()
        }
        compose.waitForIdle()
        compose.waitUntil(10_000) {
            val first = compose.onAllNodesWithText("DB Romanian Deadlift").fetchSemanticsNodes().firstOrNull()?.boundsInRoot?.top
            val second = compose.onAllNodesWithText("Bulgarian Split Squat").fetchSemanticsNodes().firstOrNull()?.boundsInRoot?.top
            first != null && second != null && first > second
        }
        shot("travel-workout-reordered")
        scrollTo("Discard workout")
        tap("Discard workout")
        tapInDialog("Discard")
        tab("Train")
        tap("Main gym")
        shot("train-end")
        Log.i("UiTour", "done")
    }
}
