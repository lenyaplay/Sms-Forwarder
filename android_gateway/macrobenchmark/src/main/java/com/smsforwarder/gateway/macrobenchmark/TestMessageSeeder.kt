package com.smsforwarder.gateway.macrobenchmark

import android.app.UiAutomation
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry

/** One synthetic inbox SMS: a distinct [sender] so it becomes its own conversation row (`MessageDao.observeConversations` groups by sender - see spec 0030). */
data class SeedMessage(val sender: String, val body: String, val minutesAgo: Long)

/**
 * Spec 0030: fixed (not randomly generated per run) list of synthetic messages, one per
 * distinct sender, seeded into `content://sms` via `adb shell content insert` (the shell
 * has provider write access that bypasses the "only the default SMS app may write"
 * restriction normal apps hit - the same reason role/permission grants elsewhere in this
 * module go through `executeShellCommand`, not direct API calls).
 *
 * Exists specifically to fix [ComposeFrameBenchmark]'s `FrameTimingMetric` failure
 * ("Observed no expect/actual slices in trace") on a clean emulator with no real SMS
 * history - `ConversationsScreen`'s list was empty, so the scroll gesture produced zero
 * frames to measure. 30 distinct senders is comfortably more than a typical device screen
 * shows at once, guaranteeing the list is actually scrollable.
 */
object TestMessageSeeder {

    // No spaces in body (confirmed live, twice): `UiAutomation.executeShellCommand`
    // does NOT go through `/system/bin/sh -c` the way a host `adb shell "..."` call
    // does - it tokenizes the command string itself, so shell-style quoting (single
    // OR double) does not protect embedded spaces here, even though the exact same
    // quoting DOES work when typed by hand through `adb shell` from a host terminal
    // (a different execution path - verified this is genuinely different, not a typo,
    // by re-testing both ways back-to-back). An underscore-joined body is the robust fix.
    val MESSAGES: List<SeedMessage> = (1..30).map { i ->
        SeedMessage(
            sender = "+1555010%04d".format(i),
            body = "spec_0030_seeded_test_message_%02d".format(i),
            minutesAgo = i.toLong(),
        )
    }

    /** Idempotent - no-ops if `content://sms` already has rows (avoids re-seeding on every iteration of a repeated benchmark). */
    fun seedIfEmpty() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val countOutput = automation.executeShell("content query --uri content://sms --projection _id")
        if (countOutput.contains("_id=")) return

        val now = System.currentTimeMillis()
        MESSAGES.forEach { message ->
            val date = now - message.minutesAgo * 60_000
            automation.executeShell(
                "content insert --uri content://sms --bind address:s:${message.sender} " +
                    "--bind body:s:${message.body} --bind date:l:$date --bind type:i:1 --bind read:i:1",
            )
        }
    }

    private fun UiAutomation.executeShell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(executeShellCommand(command)).use { it.readBytes().decodeToString() }
}
