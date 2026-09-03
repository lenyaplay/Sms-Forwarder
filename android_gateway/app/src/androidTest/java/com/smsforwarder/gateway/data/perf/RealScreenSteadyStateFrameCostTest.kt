package com.smsforwarder.gateway.data.perf

import android.content.Context
import android.view.Choreographer
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.smsforwarder.gateway.MainActivity
import com.smsforwarder.gateway.ui.conversations.ConversationsTestTags
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import kotlin.math.roundToInt

/**
 * Measures raw per-frame duration (ms, not JankStats' binary isJank) on the REAL
 * ConversationsScreen, on the real device, after replacing per-row `Card` (elevation/shadow
 * compositing) with a flat row + `HorizontalDivider` (stock-app-style separator). Same
 * technique as [SteadyStateFrameCostTest] (synthetic harness) - directly comparable to that
 * test's Scenario A (~15.3ms mean, occasional 33/44ms drops) and Scenario B (~16.7ms mean,
 * Card+SwipeToDismissBox, zero drops) as reference points for whether removing the shadow
 * moved the real screen toward the lighter or heavier profile.
 */
@HiltAndroidTest
class RealScreenSteadyStateFrameCostTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val stateSetupRule = object : ExternalResource() {
        override fun before() {
            hiltRule.inject()
            val context: Context = ApplicationProvider.getApplicationContext()
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            automation.executeShellCommand("cmd role add-role-holder android.app.role.SMS ${context.packageName}").close()
            automation.executeShellCommand("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS").close()
            automation.executeShellCommand("pm grant ${context.packageName} android.permission.READ_CONTACTS").close()
            automation.executeShellCommand("pm grant ${context.packageName} android.permission.READ_SMS").close()
            automation.executeShellCommand("pm grant ${context.packageName} android.permission.RECEIVE_SMS").close()
            automation.executeShellCommand("pm grant ${context.packageName} android.permission.READ_PHONE_STATE").close()
        }
    }

    @get:Rule(order = 2)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun realScreenSteadyState() {
        val importDeadlineNanos = System.nanoTime() + 30_000_000_000L
        var rowCount = 0
        while (System.nanoTime() < importDeadlineNanos) {
            rowCount = composeRule.onNodeWithTag(ConversationsTestTags.LIST).fetchSemanticsNode().children.size
            if (rowCount > 0) break
            composeRule.waitForIdle()
            Thread.sleep(500)
        }
        assertTrue("Device has no real SMS history - cannot run this measurement.", rowCount > 0)

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
            composeRule.onNodeWithTag(ConversationsTestTags.LIST).performTouchInput { swipeUp() }
        }
        composeRule.waitForIdle()
        composeRule.runOnUiThread { Choreographer.getInstance().removeFrameCallback(callback) }

        val timestamps = frameTimestampsNanos.toList().filter { it >= touchStartNanos }
        val deltasMs = timestamps.zipWithNext { a, b -> (b - a) / 1_000_000.0 }
        val steadyState = deltasMs.drop(2)

        if (steadyState.isEmpty()) {
            android.util.Log.w(TAG, "No steady-state frames captured (rows=$rowCount, only ${deltasMs.size} total deltas - list may be too short to scroll far)")
            return
        }

        val sorted = steadyState.sorted()
        val mean = steadyState.average()
        val median = percentile(sorted, 50.0)
        val p90 = percentile(sorted, 90.0)
        val p99 = percentile(sorted, 99.0)
        val max = sorted.last()
        val overBudgetCount = steadyState.count { it > 16.6 }
        val dropCount = steadyState.count { it > 20.0 }

        android.util.Log.i(
            TAG,
            "Real screen (rows=$rowCount) STEADY-STATE (n=${steadyState.size} frames, cold-start window excluded): " +
                "mean=${"%.2f".format(mean)}ms median=${"%.2f".format(median)}ms p90=${"%.2f".format(p90)}ms " +
                "p99=${"%.2f".format(p99)}ms max=${"%.2f".format(max)}ms overBudget(>16.6ms)=$overBudgetCount/${steadyState.size} " +
                "realDrops(>20ms)=$dropCount raw=${steadyState.map { "%.2f".format(it) }}",
        )
    }

    private fun percentile(sorted: List<Double>, p: Double): Double {
        if (sorted.isEmpty()) return 0.0
        val index = ((p / 100.0) * (sorted.size - 1)).roundToInt().coerceIn(0, sorted.size - 1)
        return sorted[index]
    }

    companion object {
        private const val TAG = "RealScreenSteadyStateFrameCostTest"
        private const val SWIPES_PER_RUN = 10
    }
}
