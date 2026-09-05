package com.smsforwarder.gateway.macrobenchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TARGET_PACKAGE = "com.smsforwarder.gateway"

/**
 * Spec 0030, category 4 (JIT vs AOT): direct A/B of [CompilationMode.None]
 * (pure JIT, no baseline profile / AOT at all) against [CompilationMode.Full]
 * (force full AOT ahead of measurement).
 *
 * KNOWN RISK, not hidden: both specs 0025 and 0026 independently found that
 * directly forcing AOT via `adb shell cmd package compile -m speed` on this
 * physical device (TECNO LI9) stably reports back `actualCompilerFilter=verify`
 * regardless of root/flags - a platform-level dexopt-trigger limitation, not a
 * project bug. `CompilationMode.Full()` uses a DIFFERENT internal mechanism
 * (Macrobenchmark's own test-runner-driven compile invocation, not a bare adb
 * command) - it MAY behave differently, but this is not guaranteed. If this
 * test's `speedProfileIterations`/warmup still resolves to `verify` on TECNO
 * LI9 the same way, that is the expected, already-twice-documented outcome,
 * not a new failure - record it as such in spec 0030's "Результаты", not as
 * an unexplained test problem.
 */
@RunWith(AndroidJUnit4::class)
class CompilationModeBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartJitOnly() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.COLD,
        compilationMode = CompilationMode.None(),
        setupBlock = { grantSmsRole() },
    ) {
        startActivityAndWait()
    }

    @Test
    fun coldStartFullAot() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.COLD,
        compilationMode = CompilationMode.Full(),
        setupBlock = { grantSmsRole() },
    ) {
        startActivityAndWait()
    }

    private fun grantSmsRole() {
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("cmd role add-role-holder android.app.role.SMS $TARGET_PACKAGE")
            .close()
    }
}
