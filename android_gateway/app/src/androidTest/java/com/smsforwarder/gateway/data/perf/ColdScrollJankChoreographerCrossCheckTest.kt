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

/**
 * Spec 0025 Допущение 6: independent cross-check requested after [ColdScrollJankAttachArtifactTest]
 * and [ColdScrollJankWarmupHypothesisTest] pointed at `JankStats.createAndTrack` itself as the
 * likely source of the "first 2 frames always janky" signal (constant magnitude, present even
 * after warm-up, absent entirely with zero touch input). This measures the SAME cold first-swipe
 * window using raw `Choreographer.postFrameCallback` nanosecond timestamps - completely bypassing
 * JankStats' own isJank classification - to see whether actual inter-frame deltas exceed the
 * ~16.6ms vsync budget on frames #0/#1, independent of how JankStats labels them.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@HiltAndroidTest
class ColdScrollJankChoreographerCrossCheckTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun crossCheckRun01() = crossCheckRun()
    @Test
    fun crossCheckRun02() = crossCheckRun()
    @Test
    fun crossCheckRun03() = crossCheckRun()
    @Test
    fun crossCheckRun04() = crossCheckRun()
    @Test
    fun crossCheckRun05() = crossCheckRun()

    private fun crossCheckRun() {
        val listState = LazyListState()
        val rows = ScrollJankLayerTest.syntheticRows(ScrollJankLayerTest.ROW_COUNT)

        composeRule.setContent {
            IsolatedScrollTestScreen(rows = rows, state = listState, showCard = false, exaggeratedShadow = false, showSwipeToDismiss = false)
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

        // Recorded right before the FIRST touch event is dispatched - lets us separate the
        // idle-to-first-frame gap (nothing invalidated yet, not real render cost) from actual
        // inter-frame deltas once the swipe gesture is genuinely producing frames.
        val touchStartNanos = System.nanoTime()
        repeat(ScrollJankLayerTest.SWIPES_PER_RUN) {
            composeRule.onNodeWithTag(ScrollJankLayerTest.SCREEN_TAG).performTouchInput { swipeUp() }
        }
        composeRule.waitForIdle()
        composeRule.runOnUiThread { Choreographer.getInstance().removeFrameCallback(callback) }

        val timestamps = frameTimestampsNanos.toList()
        val framesAfterTouchStart = timestamps.filter { it >= touchStartNanos }
        val idleToFirstFrameMs = if (framesAfterTouchStart.isNotEmpty()) (framesAfterTouchStart.first() - touchStartNanos) / 1_000_000.0 else -1.0
        val deltasMs = framesAfterTouchStart.zipWithNext { a, b -> (b - a) / 1_000_000.0 }
        val firstDeltasMs = deltasMs.take(6)
        android.util.Log.i(
            TAG,
            "CrossCheck run: totalFrames=${timestamps.size} framesAfterTouchStart=${framesAfterTouchStart.size} " +
                "idleToFirstFrameMs=${"%.2f".format(idleToFirstFrameMs)} " +
                "firstInterFrameDeltasMsAfterTouchStart=${firstDeltasMs.map { "%.2f".format(it) }} " +
                "(>16.6ms budget flagged: ${firstDeltasMs.map { it > 16.6 }})",
        )
    }

    companion object {
        private const val TAG = "ColdScrollJankChoreographerCrossCheckTest"
    }
}
