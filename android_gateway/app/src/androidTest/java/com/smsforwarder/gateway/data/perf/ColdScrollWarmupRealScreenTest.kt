package com.smsforwarder.gateway.data.perf

import android.content.Context
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.metrics.performance.JankStats
import androidx.test.core.app.ApplicationProvider
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
import org.junit.runners.MethodSorters

/**
 * Spec 0025 Раздел C (Сценарий D): базовый протокол Фазы 1 ([ColdScrollWarmupTest]) на
 * реальном `ConversationsScreen`/`MainActivity`, для сравнимости с уже полученными числами
 * Сценариев A/B. Роль SMS-приложения и permissions выставляются в [ExternalResource]
 * (order=1), ДО того как `createAndroidComposeRule<MainActivity>()` (order=2) запускает
 * Activity - тот же паттерн, что [com.smsforwarder.gateway.DeliveryResetActivityTest],
 * потому что `createAndroidComposeRule` запускает Activity как часть применения своего
 * собственного Rule, раньше любого `@Before`.
 *
 * Явное требование спеки: пустая реальная SMS-история - явный provал теста
 * (`assertTrue`), не тихий synthetic-фолбэк и не залогированное предупреждение - именно
 * молчаливое продолжение на пустых данных было причиной ложного вывода в Milestone 22,
 * Слое 4.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@HiltAndroidTest
class ColdScrollWarmupRealScreenTest {

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
    fun coldRun01() = coldRun(runIndex = 1)
    @Test
    fun coldRun02() = coldRun(runIndex = 2)
    @Test
    fun coldRun03() = coldRun(runIndex = 3)
    @Test
    fun coldRun04() = coldRun(runIndex = 4)
    @Test
    fun coldRun05() = coldRun(runIndex = 5)
    @Test
    fun coldRun06() = coldRun(runIndex = 6)
    @Test
    fun coldRun07() = coldRun(runIndex = 7)
    @Test
    fun coldRun08() = coldRun(runIndex = 8)
    @Test
    fun coldRun09() = coldRun(runIndex = 9)
    @Test
    fun coldRun10() = coldRun(runIndex = 10, isLastRun = true)

    private fun coldRun(runIndex: Int, isLastRun: Boolean = false) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag(ConversationsTestTags.LIST).fetchSemanticsNodes().isNotEmpty()
        }
        // History import (real SMS content provider -> Room) runs in the background after
        // the SMS role is granted and can take several seconds for a real inbox - poll for
        // rows to actually populate rather than failing on the (possibly still-empty) first
        // frame of the LIST node.
        val importDeadlineNanos = System.nanoTime() + 30_000_000_000L
        var rowCount = 0
        while (System.nanoTime() < importDeadlineNanos) {
            rowCount = composeRule.onNodeWithTag(ConversationsTestTags.LIST).fetchSemanticsNode().children.size
            if (rowCount > 0) break
            composeRule.waitForIdle()
            Thread.sleep(500)
        }
        assertTrue(
            "Device has no real SMS history - cannot run Scenario D. Seed conversations before running this test.",
            rowCount > 0,
        )

        val counter = FrameCounter()
        lateinit var jankStats: JankStats
        composeRule.runOnUiThread {
            jankStats = JankStats.createAndTrack(composeRule.activity.window) { frameData -> counter.onFrame(frameData.isJank) }
        }
        repeat(SWIPES_PER_RUN) {
            composeRule.onNodeWithTag(ConversationsTestTags.LIST).performTouchInput { swipeUp() }
        }
        composeRule.waitForIdle()
        composeRule.runOnUiThread { jankStats.isTrackingEnabled = false }

        val aggregatePercent = counter.jankyPercent
        val deciles = segmentJankyPercents(counter.frameFlags, segmentCount = DECILE_COUNT)
        android.util.Log.i(
            TAG,
            "Scenario D run $runIndex/$RUN_COUNT: rows=$rowCount aggregate=${"%.2f".format(aggregatePercent)}%% deciles=${deciles.map { "%.2f".format(it) }}",
        )

        RunResults.aggregatePercents += aggregatePercent
        RunResults.decilePercents += deciles

        if (isLastRun) reportScenario()
    }

    private fun reportScenario() {
        check(RunResults.aggregatePercents.size == RUN_COUNT) { "expected $RUN_COUNT runs, got ${RunResults.aggregatePercents.size}" }

        val earlyRuns = JankRunSet(RunResults.aggregatePercents.subList(0, 3))
        val lateRuns = JankRunSet(RunResults.aggregatePercents.subList(7, 10))
        val betweenRunResult = compareJank(candidate = earlyRuns, compareTo = lateRuns)

        val earlyDecile1 = JankRunSet(RunResults.decilePercents.map { it[0] })
        val lateDeciles6to10 = JankRunSet(RunResults.decilePercents.flatMap { it.subList(5, 10) })
        val withinRunResult = compareJank(candidate = earlyDecile1, compareTo = lateDeciles6to10)

        android.util.Log.i(
            TAG,
            "Scenario D SUMMARY: " +
                "betweenRuns early(mean=${"%.2f".format(earlyRuns.mean)}%%,spread=${"%.2f".format(earlyRuns.spread)}%%) " +
                "vs late(mean=${"%.2f".format(lateRuns.mean)}%%,spread=${"%.2f".format(lateRuns.spread)}%%) => $betweenRunResult; " +
                "withinRun decile1(mean=${"%.2f".format(earlyDecile1.mean)}%%,spread=${"%.2f".format(earlyDecile1.spread)}%%) " +
                "vs deciles6-10(mean=${"%.2f".format(lateDeciles6to10.mean)}%%,spread=${"%.2f".format(lateDeciles6to10.spread)}%%) => $withinRunResult",
        )
    }

    private object RunResults {
        val aggregatePercents = mutableListOf<Double>()
        val decilePercents = mutableListOf<List<Double>>()
    }

    companion object {
        private const val TAG = "ColdScrollWarmupRealScreenTest"
        private const val RUN_COUNT = 10
        private const val DECILE_COUNT = 10
        private const val SWIPES_PER_RUN = 3
    }
}
