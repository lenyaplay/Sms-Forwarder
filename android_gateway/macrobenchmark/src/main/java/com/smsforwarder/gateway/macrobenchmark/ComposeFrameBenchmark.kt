package com.smsforwarder.gateway.macrobenchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TARGET_PACKAGE = "com.smsforwarder.gateway"

/**
 * Spec 0030, category 2 (Compose recomposition/layout/draw): per-frame cost
 * while scrolling the real [ConversationsScreen] on the real device, replacing
 * [RealScreenSteadyStateFrameCostTest]/[SteadyStateFrameCostTest]'s raw
 * `Choreographer` millisecond harness with Macrobenchmark's `FrameTimingMetric`.
 *
 * The per-phase breakdown (recompose/layout/draw, not just total frame cost) is
 * attempted via `TraceSectionMetric` on Compose runtime's OWN trace sections
 * (`Compose:recompose`, `Compose:Layout`, `Compose:Draw`) - not a hand-rolled
 * counter. Spec 0025's manual `recompositionCount` approach failed (constant
 * `1` across every run, uninformative) precisely because it counted
 * recomposition itself instead of reading the trace Compose's runtime already
 * emits; this reuses that existing instrumentation instead of reinventing it.
 * If a live run shows one of these sections isn't actually present in the
 * captured Perfetto trace on TECNO LI9, that must be recorded honestly in the
 * spec's "Результаты" (see spec 0030's Допущение про категорию 2), not
 * silently dropped.
 */
@OptIn(ExperimentalMetricApi::class)
@RunWith(AndroidJUnit4::class)
class ComposeFrameBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun scrollConversationsListSteadyState() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(
            FrameTimingMetric(),
            TraceSectionMetric("Compose:recompose", TraceSectionMetric.Mode.Sum),
            TraceSectionMetric("Compose:Layout", TraceSectionMetric.Mode.Sum),
            TraceSectionMetric("Compose:Draw", TraceSectionMetric.Mode.Sum),
        ),
        iterations = 5,
        startupMode = StartupMode.WARM,
        compilationMode = CompilationMode.Partial(),
        setupBlock = {
            // Spec 0030: a clean device/emulator has no real SMS history, so the
            // conversations list is empty and the scroll below produces zero frames
            // (the failure this benchmark originally hit - FrameTimingMetric's
            // "Observed no expect/actual slices in trace"). TestMessageSeeder inserts
            // a fixed set of synthetic messages via shell (idempotent - only seeds once).
            TestMessageSeeder.seedIfEmpty()
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .executeShellCommand("cmd role add-role-holder android.app.role.SMS $TARGET_PACKAGE")
                .close()
            startActivityAndWait()
        },
    ) {
        val list = device.wait(Until.findObject(By.res("conversations_list")), 5_000) ?: return@measureRepeated
        repeat(5) {
            list.fling(Direction.DOWN)
            device.waitForIdle(2_000)
        }
    }
}
