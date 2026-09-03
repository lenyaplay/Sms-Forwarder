package com.smsforwarder.gateway.data.perf

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.metrics.performance.JankStats
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * Spec 0025 Допущение 6: [ColdScrollJankWarmupHypothesisTest] refuted "cold row composition"
 * (warm-up scroll didn't help - frame #0/#1 still janky 10/10). This tests a different
 * hypothesis entirely, found by re-reading the protocol: `janky=2` was an EXACT constant
 * across every run of every scenario/variant so far (never 1, never 3) and always the first
 * two frames counted after `JankStats.createAndTrack(...)`, regardless of what's on screen -
 * consistent with `createAndTrack`'s own listener-attach overhead being misreported as jank,
 * not real UI work. This attaches JankStats and waits idle WITH NO TOUCH INPUT AT ALL - if
 * frame #0/#1 are still janky with zero swipes, the effect has nothing to do with scrolling.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@HiltAndroidTest
class ColdScrollJankAttachArtifactTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun noTouchRun01() = noTouchRun()
    @Test
    fun noTouchRun02() = noTouchRun()
    @Test
    fun noTouchRun03() = noTouchRun()
    @Test
    fun noTouchRun04() = noTouchRun()
    @Test
    fun noTouchRun05() = noTouchRun()

    private fun noTouchRun() {
        val listState = LazyListState()
        val rows = ScrollJankLayerTest.syntheticRows(ScrollJankLayerTest.ROW_COUNT)

        composeRule.setContent {
            IsolatedScrollTestScreen(rows = rows, state = listState, showCard = false, exaggeratedShadow = false, showSwipeToDismiss = false)
        }
        composeRule.waitForIdle()

        val counter = FrameCounter()
        lateinit var jankStats: JankStats
        composeRule.runOnUiThread {
            jankStats = JankStats.createAndTrack(composeRule.activity.window) { frameData -> counter.onFrame(frameData.isJank) }
        }
        // No touch input at all - just let idle frames pass with static content on screen.
        repeat(5) { composeRule.waitForIdle() }
        composeRule.runOnUiThread { jankStats.isTrackingEnabled = false }

        val flags = counter.frameFlags
        android.util.Log.i(TAG, "No-touch run: frames=${flags.size} janky=${flags.count { it }} first4=${flags.take(4)}")
    }

    companion object {
        private const val TAG = "ColdScrollJankAttachArtifactTest"
    }
}
