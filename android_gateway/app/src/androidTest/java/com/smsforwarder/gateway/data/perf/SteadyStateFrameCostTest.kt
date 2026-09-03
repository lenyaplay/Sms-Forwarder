package com.smsforwarder.gateway.data.perf

import android.view.Choreographer
import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runners.MethodSorters
import kotlin.math.roundToInt

/**
 * Follow-up to [ColdScrollJankChoreographerCrossCheckTest]: `JankStats.isJank` is a BINARY
 * flag (did this frame cross the ~16.6ms vsync budget), so it is blind to a scenario that is
 * generally HEAVIER per frame but still stays under that threshold - which would explain
 * "still feels laggy even when JankStats says it's not jank." This measures raw
 * inter-frame duration (ms) via Choreographer directly, for STEADY-STATE frames only (the
 * first 2 frames of each run are excluded - already isolated as the separate cold-start
 * effect by [ColdScrollJankRootCauseTest]), comparing Scenario A (plain LazyColumn+Text)
 * against Scenario B (Card + SwipeToDismissBox per row, same as the real ConversationRow
 * stack) to see whether Card/SwipeToDismissBox raise the steady-state frame-time floor even
 * when neither crosses the binary jank threshold.
 *
 * More swipes per run than the cold-run protocols (10, not 3) purely to get a larger
 * steady-state sample for percentile stats - this is not measuring cold-start behavior.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@HiltAndroidTest
class SteadyStateFrameCostTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun scenarioA_plainList() = measureSteadyState(scenario = "A (plain LazyColumn+Text)", showCard = false, exaggeratedShadow = false, showSwipeToDismiss = false)

    @Test
    fun scenarioB_cardPlusSwipeToDismiss() = measureSteadyState(scenario = "B (Card+SwipeToDismissBox, real-screen stack)", showCard = true, exaggeratedShadow = false, showSwipeToDismiss = true)

    private fun measureSteadyState(scenario: String, showCard: Boolean, exaggeratedShadow: Boolean, showSwipeToDismiss: Boolean) {
        val listState = LazyListState()
        val rows = ScrollJankLayerTest.syntheticRows(ScrollJankLayerTest.ROW_COUNT)

        composeRule.setContent {
            IsolatedScrollTestScreen(rows = rows, state = listState, showCard = showCard, exaggeratedShadow = exaggeratedShadow, showSwipeToDismiss = showSwipeToDismiss)
        }
        composeRule.waitForIdle()

        val frameTimestampsNanos = java.util.Collections.synchronizedList(mutableListOf<Long>())
        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                frameTimestampsNanos.add(frameTimeNanos)
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
        composeRule.runOnUiThread { Choreographer.getInstance().postFrameCallback(callback) }

        val touchStartNanos = System.nanoTime()
        repeat(SWIPES_PER_RUN) {
            composeRule.onNodeWithTag(SCREEN_TAG).performTouchInput { swipeUp() }
        }
        composeRule.waitForIdle()
        composeRule.runOnUiThread { Choreographer.getInstance().removeFrameCallback(callback) }

        val timestamps = frameTimestampsNanos.toList().filter { it >= touchStartNanos }
        val deltasMs = timestamps.zipWithNext { a, b -> (b - a) / 1_000_000.0 }
        // Cold-start window (first 2 frames after touch begins) already isolated separately -
        // excluded here so this measures ongoing per-frame cost, not the one-time startup spike.
        val steadyState = deltasMs.drop(2)

        if (steadyState.isEmpty()) {
            android.util.Log.w(TAG, "Scenario $scenario: no steady-state frames captured (only ${deltasMs.size} total deltas)")
            return
        }

        val sorted = steadyState.sorted()
        val mean = steadyState.average()
        val median = percentile(sorted, 50.0)
        val p90 = percentile(sorted, 90.0)
        val p99 = percentile(sorted, 99.0)
        val max = sorted.last()
        val overBudgetCount = steadyState.count { it > 16.6 }

        android.util.Log.i(
            TAG,
            "Scenario $scenario STEADY-STATE (n=${steadyState.size} frames, cold-start window excluded): " +
                "mean=${"%.2f".format(mean)}ms median=${"%.2f".format(median)}ms p90=${"%.2f".format(p90)}ms " +
                "p99=${"%.2f".format(p99)}ms max=${"%.2f".format(max)}ms overBudget(>16.6ms)=$overBudgetCount/${steadyState.size} " +
                "raw=${steadyState.map { "%.2f".format(it) }}",
        )
    }

    private fun percentile(sorted: List<Double>, p: Double): Double {
        if (sorted.isEmpty()) return 0.0
        val index = ((p / 100.0) * (sorted.size - 1)).roundToInt().coerceIn(0, sorted.size - 1)
        return sorted[index]
    }

    companion object {
        private const val TAG = "SteadyStateFrameCostTest"
        private const val SWIPES_PER_RUN = 10
        private const val SCREEN_TAG = ScrollJankLayerTest.SCREEN_TAG
    }
}
