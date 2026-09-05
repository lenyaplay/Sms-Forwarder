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
 * A per-phase breakdown via `TraceSectionMetric` on Compose runtime's OWN
 * trace sections (`Compose:recompose`/`Compose:Layout`/`Compose:Draw`) does
 * not work: `Compose:Layout`/`Compose:Draw` are not real section names this
 * Compose runtime version emits at all (confirmed by direct Perfetto trace
 * inspection, 2026-09-05), and `Compose:recompose` - though genuinely present
 * in the trace - is only aggregated by `TraceSectionMetric` when composition
 * tracing (`androidx.compose.runtime:runtime-tracing` +
 * `androidx.benchmark.fullTracing.enable`) is active, which this project has
 * chosen not to add (per-composable granularity isn't worth the APK-size
 * cost).
 *
 * Instead: two manual `Trace.beginSection`/`endSection` wraps in
 * `ConversationsScreen.kt` (`ConversationRow`, the whole list row including
 * swipe/click/menu chrome, and `ConversationRowContent`, just the leaf
 * avatar/name/text/time content), gated on
 * `BuildConfig.ENABLE_COMPOSABLE_TRACING` (a dedicated build-time flag, not
 * `BuildConfig.DEBUG` - this benchmark measures `:app`'s `release` variant
 * directly, where DEBUG is always false). Comparing the two gives a rough
 * split between "row chrome" cost and "actual content rendering" cost. This
 * only measures anything when `:app` is built with
 * `-Penable_composable_tracing=true` - see spec 0030 for how to invoke that.
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
            TraceSectionMetric("ConversationRow", TraceSectionMetric.Mode.Sum),
            TraceSectionMetric("ConversationRowContent", TraceSectionMetric.Mode.Sum),
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
