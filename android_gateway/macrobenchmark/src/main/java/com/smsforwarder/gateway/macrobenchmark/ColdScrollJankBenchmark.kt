package com.smsforwarder.gateway.macrobenchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
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
 * Spec 0030: reproduces, in a repeatable Macrobenchmark form, the real
 * measured delay [ColdScrollJankChoreographerCrossCheckTest] found on the
 * first 1-2 frames of the first cold swipe (spec 0025 Раздел A/дополнительный
 * метод: raw `Choreographer` deltas ~200-500ms, confirmed by
 * `Skipped frames`/`Davey!`/JIT-compiler logcat lines - not a `JankStats`
 * measurement artifact). `FrameTimingMetric` on a genuinely cold process +
 * first swipe gives this an ongoing regression signal instead of a one-off
 * diagnostic result.
 */
@RunWith(AndroidJUnit4::class)
class ColdScrollJankBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun firstColdSwipeFrameCost() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.COLD,
        compilationMode = CompilationMode.Partial(),
        setupBlock = {
            // Grant once, outside the measured block - `StartupMode.COLD` force-stops
            // the process before each iteration but doesn't revoke already-granted
            // roles/permissions, so this only has real work to do on iteration 1.
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .executeShellCommand("cmd role add-role-holder android.app.role.SMS $TARGET_PACKAGE")
                .close()
        },
    ) {
        startActivityAndWait()
        val list = device.wait(Until.findObject(By.res("conversations_list")), 10_000) ?: return@measureRepeated
        list.fling(Direction.DOWN)
        device.waitForIdle(1_000)
    }
}
