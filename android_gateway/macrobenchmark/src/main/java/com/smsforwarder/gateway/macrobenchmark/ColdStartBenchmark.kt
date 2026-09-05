package com.smsforwarder.gateway.macrobenchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TARGET_PACKAGE = "com.smsforwarder.gateway"

/**
 * Spec 0030, category 1 (Activity/Compose cold start): time from a genuinely
 * killed process (`StartupMode.COLD`, via Macrobenchmark's own `am force-stop` -
 * a real cold start, unlike [ColdStartConversationsListTest]'s `ActivityScenario`
 * relaunch-in-the-same-process, which spec 0026 documented as a methodological
 * gap) to the first frame where the conversations list contains real rows -
 * marked in app code by `Trace.beginSection("first_real_row")` around the
 * contact-resolve pass in `ConversationsViewModel.observeConversations`.
 */
@OptIn(ExperimentalMetricApi::class)
@RunWith(AndroidJUnit4::class)
class ColdStartBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartToFirstRealRow() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric(), TraceSectionMetric("first_real_row")),
        iterations = 5,
        startupMode = StartupMode.COLD,
        compilationMode = CompilationMode.Partial(),
        setupBlock = {
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .executeShellCommand("cmd role add-role-holder android.app.role.SMS $TARGET_PACKAGE")
                .close()
        },
    ) {
        startActivityAndWait()
    }
}
