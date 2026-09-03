package com.smsforwarder.gateway.data.perf

import android.content.Context
import android.os.SystemClock
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.smsforwarder.gateway.MainActivity
import com.smsforwarder.gateway.ui.conversations.ConversationsTestTags
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * Spec 0026: real-device measurement of the time from Activity creation to the
 * first frame where [ConversationsScreen]'s list actually contains real rows -
 * distinct from `adb shell am start -W`'s `TotalTime`, which only captures the
 * first drawn window (splash dismissal), confirmed via live device logcat to
 * fire well before the Flow-backed conversations list is populated.
 *
 * "Cold" here follows the same definition already established in spec 0024/0025
 * (`ColdScrollWarmupTest`/`ColdScrollWarmupRealScreenTest`): a fresh Activity/
 * ViewModel per run, not an actual killed OS process - the instrumentation APK
 * shares the app's process, so `am force-stop` would kill the test runner
 * itself. "Warm" reuses the SAME [ActivityScenario] across runs (STOPPED then
 * back to RESUMED), so the ViewModel/its in-memory contact-name cache survive.
 *
 * Uses [createEmptyComposeRule] + manually-launched [ActivityScenario] (rather
 * than [androidx.compose.ui.test.junit4.createAndroidComposeRule], which
 * auto-launches the Activity as part of applying its own Rule, before the test
 * body can record a start timestamp) - the documented pattern for measuring
 * Activity launch time with Compose's test APIs.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ColdStartConversationsListTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val permissionsRule = object : ExternalResource() {
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
    val composeRule = createEmptyComposeRule()

    @Test
    fun coldRun01() = coldRun(runIndex = 1)
    @Test
    fun coldRun02() = coldRun(runIndex = 2)
    @Test
    fun coldRun03() = coldRun(runIndex = 3)
    @Test
    fun coldRun04() = coldRun(runIndex = 4)
    @Test
    fun coldRun05() = coldRun(runIndex = 5, isLastColdRun = true)

    @Test
    fun warmRun06() = warmRun(runIndex = 1)
    @Test
    fun warmRun07() = warmRun(runIndex = 2)
    @Test
    fun warmRun08() = warmRun(runIndex = 3)
    @Test
    fun warmRun09() = warmRun(runIndex = 4)
    @Test
    fun warmRun10() = warmRun(runIndex = 5, isLastWarmRun = true)

    private fun coldRun(runIndex: Int, isLastColdRun: Boolean = false) {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        val elapsedToRowsMs = waitForRealRows()
        android.util.Log.i(
            TAG,
            "COLD run $runIndex/$RUN_COUNT: elapsedToFirstRealRow=${elapsedToRowsMs}ms (since launch() call)",
        )
        ColdResults.elapsedMs += elapsedToRowsMs
        scenario.close()

        if (isLastColdRun) reportScenario("COLD", ColdResults.elapsedMs)
    }

    private fun warmRun(runIndex: Int, isLastWarmRun: Boolean = false) {
        // Two other approaches were tried first and rejected, both with a real repro:
        // (1) moveToState(CREATED) then back to RESUMED on one long-lived scenario - on
        // the rootable_api35 emulator the system actually destroyed the backgrounded
        // Activity under memory pressure, so the next moveToState(RESUMED) threw
        // "Cannot move to state RESUMED since the Activity has been destroyed already"
        // (4/5 warm runs failed this way). (2) ActivityScenario.recreate() on one
        // long-lived scenario - a real androidx.test bug: its SECOND call throws
        // NullPointerException at ActivityScenario.java:711 (internal currentActivity
        // reference not refreshed after the first recreate), reproduced identically on
        // both the emulator and TECNO LI9 (4/5 warm runs failed the same way).
        // Closing and re-launching a fresh scenario each run avoids both: the app
        // process (and therefore the ViewModel's in-memory contact-name cache, Room
        // connection) stays alive throughout - only the Activity/task instance changes,
        // which is what "warm start" means for this spec's purposes.
        WarmScenarioHolder.scenario?.close()
        val elapsedToRowsMs = measureElapsedMs {
            WarmScenarioHolder.scenario = ActivityScenario.launch(MainActivity::class.java)
            waitForRealRows()
        }
        android.util.Log.i(
            TAG,
            "WARM run $runIndex/$RUN_COUNT: elapsedToFirstRealRow=${elapsedToRowsMs}ms (since launch())",
        )
        WarmResults.elapsedMs += elapsedToRowsMs

        if (isLastWarmRun) {
            WarmScenarioHolder.scenario?.close()
            reportScenario("WARM", WarmResults.elapsedMs)
        }
    }

    /** Polls for [ConversationsTestTags.LIST] to exist AND contain at least one real row, returning elapsed ms since this call started. */
    private fun waitForRealRows(): Long = measureElapsedMs {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag(ConversationsTestTags.LIST).fetchSemanticsNodes().isNotEmpty()
        }
        val deadlineNanos = System.nanoTime() + 30_000_000_000L
        var rowCount = 0
        while (System.nanoTime() < deadlineNanos) {
            rowCount = composeRule.onNodeWithTag(ConversationsTestTags.LIST).fetchSemanticsNode().children.size
            if (rowCount > 0) break
            composeRule.waitForIdle()
            Thread.sleep(50)
        }
        assertTrue(
            "Device has no real SMS history - cannot run spec 0026's cold-start measurement. Seed conversations before running this test.",
            rowCount > 0,
        )
    }

    private inline fun measureElapsedMs(block: () -> Unit): Long {
        val start = SystemClock.elapsedRealtime()
        block()
        return SystemClock.elapsedRealtime() - start
    }

    private fun reportScenario(label: String, samples: List<Long>) {
        check(samples.size == RUN_COUNT) { "expected $RUN_COUNT $label runs, got ${samples.size}" }
        val mean = samples.average()
        val max = samples.max()
        android.util.Log.i(
            TAG,
            "$label SUMMARY: samples=$samples mean=${"%.1f".format(mean)}ms max=${max}ms",
        )
    }

    private object ColdResults {
        val elapsedMs = mutableListOf<Long>()
    }

    private object WarmResults {
        val elapsedMs = mutableListOf<Long>()
    }

    private object WarmScenarioHolder {
        var scenario: ActivityScenario<MainActivity>? = null
    }

    companion object {
        private const val TAG = "ColdStartConversationsListTest"
        private const val RUN_COUNT = 5
    }
}
