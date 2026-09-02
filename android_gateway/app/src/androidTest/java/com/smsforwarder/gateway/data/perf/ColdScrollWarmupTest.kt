package com.smsforwarder.gateway.data.perf

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.LazyListState
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

/**
 * Spec 0024: does scroll jank concentrate in the FIRST cold scrolls after a fresh
 * Activity/composition, the way the product owner observed on the real device (unlike
 * the stock SMS app, which is smooth from the first scroll)? Milestone 22's
 * [ScrollJankLayerTest] can't answer this - it reuses ONE composition across 5 runs,
 * resetting only scroll position between them, which is itself the "scroll back and
 * forth" pattern that (per the product owner) makes things improve over time.
 *
 * Each run here gets a genuinely fresh Activity/composition: `ComposeTestRule.setContent`
 * can only be called once per @Test method (a real constraint, hit and fixed during
 * Milestone 22), so "10 cold runs" is implemented as 10 SEPARATE @Test methods per
 * scenario - JUnit re-instantiates the whole @Rule chain (including the Activity
 * launched by createAndroidComposeRule) fresh for every method, which is what actually
 * makes each run "cold" rather than warmed-up.
 *
 * Two scenarios only (spec 0024 Допущение 2) - not all 5 Milestone 22 layers, to avoid a
 * 20-method-per-scenario x 5 explosion: A = Layer 0 (plain LazyColumn+Text baseline),
 * B = Layer 3 (full synthetic stack: Card + exaggerated shadow + SwipeToDismissBox,
 * where a warm-up effect should be most visible if it exists at all).
 *
 * Two independent significance checks (spec 0024 Допущение 6), both via the existing
 * [compareJank] - no new formula:
 * - BETWEEN runs: early = runs 1-3, late = runs 8-10 (runs 4-7 are a deliberately unused
 *   transition zone, not part of either set).
 * - WITHIN runs, by decile: early = decile 1 of every run (10 values), late = deciles
 *   6-10 of every run (50 values) - asymmetric on purpose, because the hypothesis is
 *   specifically about "the first ~10%", not "the first half".
 *
 * Method names are zero-padded (coldRun01..coldRun10) so MethodSorters.NAME_ASCENDING
 * sorts them numerically, not lexicographically ("10" would otherwise sort before "2").
 * The same RunResults/companion-object fragility documented on [ScrollJankLayerTest]
 * applies here: this class is an exploratory diagnostic tool run as a whole, not a
 * regression-gate suite where any single @Test must be independently runnable.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@HiltAndroidTest
class ColdScrollWarmupTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    // --- Scenario A: Layer 0 (baseline) ---

    @Test
    fun scenarioA_coldRun01() = coldRun(scenario = "A", runIndex = 1, showCard = false, exaggeratedShadow = false, showSwipeToDismiss = false)
    @Test
    fun scenarioA_coldRun02() = coldRun(scenario = "A", runIndex = 2, showCard = false, exaggeratedShadow = false, showSwipeToDismiss = false)
    @Test
    fun scenarioA_coldRun03() = coldRun(scenario = "A", runIndex = 3, showCard = false, exaggeratedShadow = false, showSwipeToDismiss = false)
    @Test
    fun scenarioA_coldRun04() = coldRun(scenario = "A", runIndex = 4, showCard = false, exaggeratedShadow = false, showSwipeToDismiss = false)
    @Test
    fun scenarioA_coldRun05() = coldRun(scenario = "A", runIndex = 5, showCard = false, exaggeratedShadow = false, showSwipeToDismiss = false)
    @Test
    fun scenarioA_coldRun06() = coldRun(scenario = "A", runIndex = 6, showCard = false, exaggeratedShadow = false, showSwipeToDismiss = false)
    @Test
    fun scenarioA_coldRun07() = coldRun(scenario = "A", runIndex = 7, showCard = false, exaggeratedShadow = false, showSwipeToDismiss = false)
    @Test
    fun scenarioA_coldRun08() = coldRun(scenario = "A", runIndex = 8, showCard = false, exaggeratedShadow = false, showSwipeToDismiss = false)
    @Test
    fun scenarioA_coldRun09() = coldRun(scenario = "A", runIndex = 9, showCard = false, exaggeratedShadow = false, showSwipeToDismiss = false)
    @Test
    fun scenarioA_coldRun10() = coldRun(scenario = "A", runIndex = 10, showCard = false, exaggeratedShadow = false, showSwipeToDismiss = false, isLastRun = true)

    // --- Scenario B: Layer 3 (full synthetic stack) ---

    @Test
    fun scenarioB_coldRun01() = coldRun(scenario = "B", runIndex = 1, showCard = true, exaggeratedShadow = true, showSwipeToDismiss = true)
    @Test
    fun scenarioB_coldRun02() = coldRun(scenario = "B", runIndex = 2, showCard = true, exaggeratedShadow = true, showSwipeToDismiss = true)
    @Test
    fun scenarioB_coldRun03() = coldRun(scenario = "B", runIndex = 3, showCard = true, exaggeratedShadow = true, showSwipeToDismiss = true)
    @Test
    fun scenarioB_coldRun04() = coldRun(scenario = "B", runIndex = 4, showCard = true, exaggeratedShadow = true, showSwipeToDismiss = true)
    @Test
    fun scenarioB_coldRun05() = coldRun(scenario = "B", runIndex = 5, showCard = true, exaggeratedShadow = true, showSwipeToDismiss = true)
    @Test
    fun scenarioB_coldRun06() = coldRun(scenario = "B", runIndex = 6, showCard = true, exaggeratedShadow = true, showSwipeToDismiss = true)
    @Test
    fun scenarioB_coldRun07() = coldRun(scenario = "B", runIndex = 7, showCard = true, exaggeratedShadow = true, showSwipeToDismiss = true)
    @Test
    fun scenarioB_coldRun08() = coldRun(scenario = "B", runIndex = 8, showCard = true, exaggeratedShadow = true, showSwipeToDismiss = true)
    @Test
    fun scenarioB_coldRun09() = coldRun(scenario = "B", runIndex = 9, showCard = true, exaggeratedShadow = true, showSwipeToDismiss = true)
    @Test
    fun scenarioB_coldRun10() = coldRun(scenario = "B", runIndex = 10, showCard = true, exaggeratedShadow = true, showSwipeToDismiss = true, isLastRun = true)

    /**
     * One fresh Activity/composition, one measured pass of [SWIPES_PER_RUN] swipes, then
     * torn down when the @Test method returns (the next method's @Rule chain relaunches
     * a brand-new Activity) - this IS the "cold run", unlike Milestone 22's runLayer()
     * which reset scroll position on one long-lived composition across 5 passes.
     */
    private fun coldRun(scenario: String, runIndex: Int, showCard: Boolean, exaggeratedShadow: Boolean, showSwipeToDismiss: Boolean, isLastRun: Boolean = false) {
        val listState = LazyListState()
        val rows = ScrollJankLayerTest.syntheticRows(ScrollJankLayerTest.ROW_COUNT)
        composeRule.setContent {
            IsolatedScrollTestScreen(rows = rows, state = listState, showCard = showCard, exaggeratedShadow = exaggeratedShadow, showSwipeToDismiss = showSwipeToDismiss)
        }
        composeRule.waitForIdle()

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

        val aggregatePercent = counter.jankyPercent
        val deciles = segmentJankyPercents(counter.frameFlags, segmentCount = DECILE_COUNT)
        android.util.Log.i(
            TAG,
            "Scenario $scenario run $runIndex/$RUN_COUNT: aggregate=${"%.2f".format(aggregatePercent)}%% deciles=${deciles.map { "%.2f".format(it) }}",
        )

        val store = if (scenario == "A") RunResults.scenarioA else RunResults.scenarioB
        store.aggregatePercents += aggregatePercent
        store.decilePercents += deciles

        if (isLastRun) reportScenario(scenario, store)
    }

    private fun reportScenario(scenario: String, store: ScenarioResults) {
        check(store.aggregatePercents.size == RUN_COUNT) { "expected $RUN_COUNT runs, got ${store.aggregatePercents.size}" }

        // Between-run: early = runs 1-3, late = runs 8-10 (index 0..2 / 7..9), runs 4-7 unused.
        val earlyRuns = JankRunSet(store.aggregatePercents.subList(0, 3))
        val lateRuns = JankRunSet(store.aggregatePercents.subList(7, 10))
        val betweenRunResult = compareJank(candidate = earlyRuns, compareTo = lateRuns)

        // Within-run by decile: early = decile 1 of every run, late = deciles 6-10 of every run.
        val earlyDecile1 = JankRunSet(store.decilePercents.map { it[0] })
        val lateDeciles6to10 = JankRunSet(store.decilePercents.flatMap { it.subList(5, 10) })
        val withinRunResult = compareJank(candidate = earlyDecile1, compareTo = lateDeciles6to10)

        android.util.Log.i(
            TAG,
            "Scenario $scenario SUMMARY: " +
                "betweenRuns early(mean=${"%.2f".format(earlyRuns.mean)}%%,spread=${"%.2f".format(earlyRuns.spread)}%%) " +
                "vs late(mean=${"%.2f".format(lateRuns.mean)}%%,spread=${"%.2f".format(lateRuns.spread)}%%) => $betweenRunResult; " +
                "withinRun decile1(mean=${"%.2f".format(earlyDecile1.mean)}%%,spread=${"%.2f".format(earlyDecile1.spread)}%%) " +
                "vs deciles6-10(mean=${"%.2f".format(lateDeciles6to10.mean)}%%,spread=${"%.2f".format(lateDeciles6to10.spread)}%%) => $withinRunResult",
        )
    }

    private class ScenarioResults {
        val aggregatePercents = mutableListOf<Double>()
        val decilePercents = mutableListOf<List<Double>>()
    }

    private object RunResults {
        val scenarioA = ScenarioResults()
        val scenarioB = ScenarioResults()
    }

    companion object {
        private const val TAG = "ColdScrollWarmupTest"
        private const val RUN_COUNT = 10
        private const val DECILE_COUNT = 10
        private const val SWIPES_PER_RUN = ScrollJankLayerTest.SWIPES_PER_RUN
        private const val SCREEN_TAG = ScrollJankLayerTest.SCREEN_TAG
    }
}
