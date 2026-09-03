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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * Spec 0025 Допущение 6, гипотеза 1 (уточнённая): [ColdScrollJankRootCauseTest] нашёл, что
 * frame #0/#1 janky в 10/10 прогонах ОБОИХ сценариев - но JankStats-трекинг там стартует
 * ПОСЛЕ первого setContent+waitForIdle, то есть эти кадры - первые два кадра первого свайпа,
 * не самой первой композиции экрана. Проверяет: если строки прокручиваются в видимую область
 * ВПЕРВЫЕ (холодная композиция/layout строк 4-6 при первом свайпе) - предварительный,
 * неизмеряемый свайп-и-возврат-к-0 ДО начала трекинга должен устранить эффект на измеряемом
 * проходе (те же строки уже скомпонованы).
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@HiltAndroidTest
class ColdScrollJankWarmupHypothesisTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun warmedRun01() = warmedColdRun(isLastRun = false)
    @Test
    fun warmedRun02() = warmedColdRun(isLastRun = false)
    @Test
    fun warmedRun03() = warmedColdRun(isLastRun = false)
    @Test
    fun warmedRun04() = warmedColdRun(isLastRun = false)
    @Test
    fun warmedRun05() = warmedColdRun(isLastRun = false)
    @Test
    fun warmedRun06() = warmedColdRun(isLastRun = false)
    @Test
    fun warmedRun07() = warmedColdRun(isLastRun = false)
    @Test
    fun warmedRun08() = warmedColdRun(isLastRun = false)
    @Test
    fun warmedRun09() = warmedColdRun(isLastRun = false)
    @Test
    fun warmedRun10() = warmedColdRun(isLastRun = true)

    private fun warmedColdRun(isLastRun: Boolean) {
        val listState = LazyListState()
        val rows = ScrollJankLayerTest.syntheticRows(ScrollJankLayerTest.ROW_COUNT)

        composeRule.setContent {
            IsolatedScrollTestScreen(rows = rows, state = listState, showCard = false, exaggeratedShadow = false, showSwipeToDismiss = false)
        }
        composeRule.waitForIdle()

        // Unmeasured warm-up: scroll the same rows into view once, then back to the top -
        // if the effect is "first time these rows are composed", this pre-composes them.
        repeat(ScrollJankLayerTest.SWIPES_PER_RUN) {
            composeRule.onNodeWithTag(ScrollJankLayerTest.SCREEN_TAG).performTouchInput { swipeUp() }
        }
        composeRule.waitForIdle()
        runBlocking(Dispatchers.Main) { listState.scrollToItem(0) }
        composeRule.waitForIdle()

        val counter = FrameCounter()
        lateinit var jankStats: JankStats
        composeRule.runOnUiThread {
            jankStats = JankStats.createAndTrack(composeRule.activity.window) { frameData -> counter.onFrame(frameData.isJank) }
        }
        repeat(ScrollJankLayerTest.SWIPES_PER_RUN) {
            composeRule.onNodeWithTag(ScrollJankLayerTest.SCREEN_TAG).performTouchInput { swipeUp() }
        }
        composeRule.waitForIdle()
        composeRule.runOnUiThread { jankStats.isTrackingEnabled = false }

        val flags = counter.frameFlags
        android.util.Log.i(TAG, "Warmed run: frames=${flags.size} janky=${flags.count { it }} first4=${flags.take(4)}")

        RunResults.allFirst4 += flags.take(4)
        if (isLastRun) {
            check(RunResults.allFirst4.size == RUN_COUNT * 4) { "expected $RUN_COUNT runs x 4 frames" }
            val perIndexJankyCount = (0 until 4).map { idx -> idx to (0 until RUN_COUNT).count { run -> RunResults.allFirst4[run * 4 + idx] } }
            android.util.Log.i(TAG, "WARMED SUMMARY: perIndexJankyCount(of $RUN_COUNT runs)=$perIndexJankyCount")
        }
    }

    private object RunResults {
        val allFirst4 = mutableListOf<Boolean>()
    }

    companion object {
        private const val TAG = "ColdScrollJankWarmupHypothesisTest"
        private const val RUN_COUNT = 10
    }
}
