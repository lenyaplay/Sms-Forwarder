package com.smsforwarder.gateway.macrobenchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TARGET_PACKAGE = "com.smsforwarder.gateway"

/**
 * Spec 0030, category 3 (`content://sms` + file I/O): the one-time full
 * history import (`SmsHistoryImporter.importIfNeeded()`) was never measured
 * separately from the rest of cold start in any of specs 0024/0025/0026 -
 * this is new coverage, not a rewrite of an existing test.
 *
 * `importIfNeeded()` is gated by a persisted flag (`configStore.isHistoryImported()`)
 * and runs only once per app lifetime (see spec 0026 "Контекст") - to get a
 * repeatable 5-iteration measurement rather than 4 no-op iterations, each
 * iteration clears app data (`pm clear`) before launch, which both resets that
 * flag AND revokes the SMS role/permissions app data reset always revokes -
 * both are re-granted in setupBlock every iteration (not just once), unlike
 * the other benchmarks in this module where the grant is a one-time no-op
 * after iteration 1.
 */
@OptIn(ExperimentalMetricApi::class)
@RunWith(AndroidJUnit4::class)
class SmsImportBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun fullHistoryImport() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(
            TraceSectionMetric("sms_history_query", TraceSectionMetric.Mode.Sum),
            TraceSectionMetric("sms_history_room_write", TraceSectionMetric.Mode.Sum),
        ),
        iterations = 5,
        startupMode = StartupMode.COLD,
        compilationMode = CompilationMode.Partial(),
        setupBlock = {
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            automation.executeShellCommand("pm clear $TARGET_PACKAGE").close()
            automation.executeShellCommand("cmd role add-role-holder android.app.role.SMS $TARGET_PACKAGE").close()
            automation.executeShellCommand("pm grant $TARGET_PACKAGE android.permission.READ_SMS").close()
            automation.executeShellCommand("pm grant $TARGET_PACKAGE android.permission.RECEIVE_SMS").close()
        },
    ) {
        startActivityAndWait()
        // importIfNeeded() runs in a LaunchedEffect on MainActivity's first
        // composition, off the UI-blocking path - give it time to complete
        // rather than asserting on a specific UI state (no dedicated
        // "import finished" indicator exists to wait on deterministically).
        Thread.sleep(3_000)
    }
}
