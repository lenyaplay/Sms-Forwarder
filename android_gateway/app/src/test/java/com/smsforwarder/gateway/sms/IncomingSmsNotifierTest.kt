package com.smsforwarder.gateway.sms

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

// Spec 0038. First test for this class - it had none before.
@RunWith(RobolectricTestRunner::class)
class IncomingSmsNotifierTest {

    private val context: Application = ApplicationProvider.getApplicationContext()
    private val notifier = IncomingSmsNotifier(context)

    // POST_NOTIFICATIONS is a runtime (dangerous) permission on API 33+ - declaring
    // it in the manifest alone isn't enough for Robolectric's checkSelfPermission,
    // unlike normal permissions; notifyIncoming() silently no-ops without this.
    @Before
    fun grantNotificationPermission() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun lastNotificationActionTitles(): List<String> {
        val manager = context.getSystemService(NotificationManager::class.java)
        val notification = shadowOf(manager).allNotifications.last()
        return notification.actions?.map { it.title.toString() } ?: emptyList()
    }

    @Test
    fun noCodeInText_hasNoActions() {
        notifier.notifyIncoming("+15551234", "hello, no codes here")
        assertEquals(emptyList<String>(), lastNotificationActionTitles())
    }

    @Test
    fun oneCodeInText_hasSingleCopyAction() {
        notifier.notifyIncoming("+15551234", "Your code is 482913")
        assertEquals(listOf("Скопировать код"), lastNotificationActionTitles())
    }

    @Test
    fun twoCodesInText_hasTwoNumberedCopyActions() {
        notifier.notifyIncoming("+15551234", "Code 1234 or backup 5678")
        assertEquals(listOf("Скопировать код 1", "Скопировать код 2"), lastNotificationActionTitles())
    }

    @Test
    fun moreThanTwoCodesInText_onlyFirstTwoGetActions() {
        notifier.notifyIncoming("+15551234", "1111 then 2222 then 3333")
        assertEquals(listOf("Скопировать код 1", "Скопировать код 2"), lastNotificationActionTitles())
    }

    // Regression for a bug found in peer review: the copy-code PendingIntent's
    // requestCode was originally salted only by sender+index, so two OTP messages
    // from the SAME sender shared one requestCode and FLAG_UPDATE_CURRENT silently
    // rewrote the still-visible first notification's button to the second
    // message's code. requestCode now includes the per-call notification id.
    @Test
    fun twoNotificationsFromSameSender_eachButtonStillCopiesItsOwnCode() {
        notifier.notifyIncoming("+15551234", "First code: 1111")
        notifier.notifyIncoming("+15551234", "Second code: 2222")

        val manager = context.getSystemService(NotificationManager::class.java)
        val notifications = shadowOf(manager).allNotifications
        val firstCode = readActionCode(notifications[notifications.size - 2].actions[0].actionIntent)
        val secondCode = readActionCode(notifications.last().actions[0].actionIntent)

        assertEquals("1111", firstCode)
        assertEquals("2222", secondCode)
    }

    private fun readActionCode(pendingIntent: android.app.PendingIntent): String? =
        shadowOf(pendingIntent).savedIntent.getStringExtra(CopyOtpCodeReceiver.EXTRA_CODE)
}
