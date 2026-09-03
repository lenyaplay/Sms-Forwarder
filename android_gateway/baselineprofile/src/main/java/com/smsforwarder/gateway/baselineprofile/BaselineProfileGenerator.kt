package com.smsforwarder.gateway.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test

private const val TARGET_PACKAGE = "com.smsforwarder.gateway"

/**
 * Generates `app/src/main/baseline-prof.txt`, run against the RELEASE build only (a
 * debuggable build never runs ahead-of-time-compiled, so profiling one produces a profile
 * ART never actually consults on device - see Milestone 23 Фаза 2's finding that repeated
 * on-device JIT compilation on the main thread, not Card/SwipeToDismissBox, is the real
 * source of "still feels laggy" on this non-AOT-compiled app).
 *
 * Exercises exactly the user journey the whole investigation centered on: cold start ->
 * grant the SMS role (first-run-only, mirrors what a real user does once) -> scroll the
 * conversations list - so the recorded profile covers composition/layout/rendering of
 * MainActivity's Compose tree and ConversationsScreen specifically, not just process startup.
 */
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() = baselineProfileRule.collect(packageName = TARGET_PACKAGE) {
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("cmd role add-role-holder android.app.role.SMS $TARGET_PACKAGE")
            .close()

        pressHome()
        startActivityAndWait()

        // Defensive: if the search field somehow already has text (stray state from a
        // previous instrumentation run reusing the same process/device), the screen shows
        // search results instead of the conversations list - clear it before proceeding.
        device.findObject(By.res("conversations_search_field"))?.let { searchField ->
            if (searchField.text?.isNotEmpty() == true) {
                searchField.setText("")
                device.waitForIdle(5_000)
            }
        }

        val list = device.wait(Until.findObject(By.res("conversations_list")), 20_000)
        if (list != null) {
            repeat(5) {
                list.fling(androidx.test.uiautomator.Direction.DOWN)
                device.waitForIdle(5_000)
            }
        }
    }
}
