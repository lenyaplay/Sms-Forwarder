package com.smsforwarder.gateway.data.perf

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.metrics.performance.JankStats
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runners.MethodSorters
import kotlin.math.ceil

/**
 * Spec 0025 (Milestone 23, Фаза 2): Фаза 1 ([ColdScrollWarmupTest]) locked the effect down
 * to the first decile of every cold run (100% of a run's jank, all 20 runs, zero spread).
 * This class goes one level deeper - per-frame (not per-decile) segmentation of just that
 * first decile, PLUS Compose recomposition-count/layout-timing collected in the SAME cold
 * run as the frame data (spec Допущение 3 - collecting them in separate runs would make the
 * frame-level finding and the recomposition/layout finding unrelated to each other).
 *
 * Same "10 separate @Test methods per scenario" cold-run mechanism as [ColdScrollWarmupTest]
 * (ComposeTestRule.setContent once per method, JUnit relaunches the Activity per method).
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@HiltAndroidTest
class ColdScrollJankRootCauseTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun scenarioA_coldRun01() = coldRun(scenario = "A", showCard = false, exaggeratedShadow = false, showSwipeToDismiss = false)
    @Test
    fun scenarioA_coldRun02() = coldRun(scenario = "A", showCard = false, exaggeratedShadow = false, showSwipeToDismiss = false)
    @Test
    fun scenarioA_coldRun03() = coldRun(scenario = "A", showCard = false, exaggeratedShadow = false, showSwipeToDismiss = false)
    @Test
    fun scenarioA_coldRun04() = coldRun(scenario = "A", showCard = false, exaggeratedShadow = false, showSwipeToDismiss = false)
    @Test
    fun scenarioA_coldRun05() = coldRun(scenario = "A", showCard = false, exaggeratedShadow = false, showSwipeToDismiss = false)
    @Test
    fun scenarioA_coldRun06() = coldRun(scenario = "A", showCard = false, exaggeratedShadow = false, showSwipeToDismiss = false)
    @Test
    fun scenarioA_coldRun07() = coldRun(scenario = "A", showCard = false, exaggeratedShadow = false, showSwipeToDismiss = false)
    @Test
    fun scenarioA_coldRun08() = coldRun(scenario = "A", showCard = false, exaggeratedShadow = false, showSwipeToDismiss = false)
    @Test
    fun scenarioA_coldRun09() = coldRun(scenario = "A", showCard = false, exaggeratedShadow = false, showSwipeToDismiss = false)
    @Test
    fun scenarioA_coldRun10() = coldRun(scenario = "A", showCard = false, exaggeratedShadow = false, showSwipeToDismiss = false, isLastRun = true)

    @Test
    fun scenarioB_coldRun01() = coldRun(scenario = "B", showCard = true, exaggeratedShadow = true, showSwipeToDismiss = true)
    @Test
    fun scenarioB_coldRun02() = coldRun(scenario = "B", showCard = true, exaggeratedShadow = true, showSwipeToDismiss = true)
    @Test
    fun scenarioB_coldRun03() = coldRun(scenario = "B", showCard = true, exaggeratedShadow = true, showSwipeToDismiss = true)
    @Test
    fun scenarioB_coldRun04() = coldRun(scenario = "B", showCard = true, exaggeratedShadow = true, showSwipeToDismiss = true)
    @Test
    fun scenarioB_coldRun05() = coldRun(scenario = "B", showCard = true, exaggeratedShadow = true, showSwipeToDismiss = true)
    @Test
    fun scenarioB_coldRun06() = coldRun(scenario = "B", showCard = true, exaggeratedShadow = true, showSwipeToDismiss = true)
    @Test
    fun scenarioB_coldRun07() = coldRun(scenario = "B", showCard = true, exaggeratedShadow = true, showSwipeToDismiss = true)
    @Test
    fun scenarioB_coldRun08() = coldRun(scenario = "B", showCard = true, exaggeratedShadow = true, showSwipeToDismiss = true)
    @Test
    fun scenarioB_coldRun09() = coldRun(scenario = "B", showCard = true, exaggeratedShadow = true, showSwipeToDismiss = true)
    @Test
    fun scenarioB_coldRun10() = coldRun(scenario = "B", showCard = true, exaggeratedShadow = true, showSwipeToDismiss = true, isLastRun = true)

    /**
     * Spec 0025 Допущение 3: recomposition-count and layout timing are collected in THIS
     * SAME call, alongside the frame data - not in a separate run - so they describe the
     * same cold start as the frame-level finding.
     */
    private fun coldRun(scenario: String, showCard: Boolean, exaggeratedShadow: Boolean, showSwipeToDismiss: Boolean, isLastRun: Boolean = false) {
        val listState = LazyListState()
        val rows = ScrollJankLayerTest.syntheticRows(ScrollJankLayerTest.ROW_COUNT)
        var recompositionCount = 0

        val setContentStartNanos = System.nanoTime()
        composeRule.setContent {
            SideEffect { recompositionCount++ }
            IsolatedScrollTestScreen(rows = rows, state = listState, showCard = showCard, exaggeratedShadow = exaggeratedShadow, showSwipeToDismiss = showSwipeToDismiss)
        }
        composeRule.waitForIdle()
        val setContentToFirstIdleNanos = System.nanoTime() - setContentStartNanos

        val counter = FrameCounter()
        lateinit var jankStats: JankStats
        composeRule.runOnUiThread {
            jankStats = JankStats.createAndTrack(composeRule.activity.window) { frameData -> counter.onFrame(frameData.isJank) }
        }
        repeat(SWIPES_PER_RUN) {
            composeRule.onNodeWithTag(SCREEN_TAG).performTouchInput { swipeUp() }
        }
        composeRule.waitForIdle()
        composeRule.runOnUiThread { jankStats.isTrackingEnabled = false }

        val observation = ColdRunObservation(
            frameFlags = counter.frameFlags,
            recompositionCount = recompositionCount,
            setContentToFirstIdleNanos = setContentToFirstIdleNanos,
        )
        android.util.Log.i(
            TAG,
            "Scenario $scenario run: frames=${observation.frameFlags.size} janky=${observation.frameFlags.count { it }} " +
                "recompositions=$recompositionCount setContentToFirstIdleMs=${"%.2f".format(setContentToFirstIdleNanos / 1_000_000.0)}",
        )

        val store = if (scenario == "A") RunResults.scenarioA else RunResults.scenarioB
        store.observations += observation

        if (isLastRun) reportScenario(scenario, store)
    }

    /**
     * Spec 0025 Допущение 1: per-frame (not per-decile) analysis of just the first decile's
     * worth of frames. A frame counts as "the source" only if it's janky in ALL 10 runs; if
     * no single frame reaches 10/10 (a "floating" janky frame across runs), the most frequent
     * one is reported only if it reaches >= 8/10 - otherwise "no dominant frame" is recorded
     * honestly rather than forcing a conclusion.
     */
    private fun reportScenario(scenario: String, store: ScenarioResults) {
        check(store.observations.size == RUN_COUNT) { "expected $RUN_COUNT runs, got ${store.observations.size}" }

        val frameCounts = store.observations.map { it.frameFlags.size }
        val minFrames = frameCounts.min()
        if (frameCounts.toSet().size > 1) {
            android.util.Log.w(TAG, "Scenario $scenario: frame count varies across runs $frameCounts - restricting per-frame analysis to the shortest run's length ($minFrames)")
        }
        val firstDecileSize = ceil(minFrames / 10.0).toInt().coerceAtLeast(1)

        val jankyFrequencyByIndex = (0 until firstDecileSize).map { frameIndex ->
            frameIndex to store.observations.count { it.frameFlags[frameIndex] }
        }
        val (bestIndex, bestCount) = jankyFrequencyByIndex.maxBy { it.second }
        val verdict = when {
            bestCount == RUN_COUNT -> "SOURCE FRAME #$bestIndex (janky in $bestCount/$RUN_COUNT runs)"
            bestCount >= DOMINANT_FRAME_THRESHOLD -> "DOMINANT FRAME #$bestIndex (janky in $bestCount/$RUN_COUNT runs, below 10/10 but >= $DOMINANT_FRAME_THRESHOLD/$RUN_COUNT threshold)"
            else -> "NO DOMINANT FRAME (best candidate #$bestIndex only janky in $bestCount/$RUN_COUNT runs, below the $DOMINANT_FRAME_THRESHOLD/$RUN_COUNT threshold - effect is unstable in position within the first decile)"
        }

        val recompositionCounts = store.observations.map { it.recompositionCount }
        val setContentToFirstIdleMs = store.observations.map { it.setContentToFirstIdleNanos / 1_000_000.0 }

        android.util.Log.i(
            TAG,
            "Scenario $scenario SUMMARY: firstDecileSize=$firstDecileSize perFrameJankyCounts=$jankyFrequencyByIndex verdict=$verdict; " +
                "recompositionCounts=$recompositionCounts (mean=${"%.1f".format(recompositionCounts.average())}); " +
                "setContentToFirstIdleMs=${setContentToFirstIdleMs.map { "%.2f".format(it) }} (mean=${"%.2f".format(setContentToFirstIdleMs.average())})",
        )
    }

    private data class ColdRunObservation(
        val frameFlags: List<Boolean>,
        val recompositionCount: Int,
        val setContentToFirstIdleNanos: Long,
    )

    private class ScenarioResults {
        val observations = mutableListOf<ColdRunObservation>()
    }

    private object RunResults {
        val scenarioA = ScenarioResults()
        val scenarioB = ScenarioResults()
    }

    companion object {
        private const val TAG = "ColdScrollJankRootCauseTest"
        private const val RUN_COUNT = 10
        private const val DOMINANT_FRAME_THRESHOLD = 8
        private const val SWIPES_PER_RUN = ScrollJankLayerTest.SWIPES_PER_RUN
        private const val SCREEN_TAG = ScrollJankLayerTest.SCREEN_TAG
    }
}
