package com.smsforwarder.gateway.sms

import android.app.Application
import android.content.ClipboardManager
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CopyOtpCodeReceiverTest {

    private val context: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun onReceive_copiesCodeFromIntentExtraToClipboard() {
        val intent = Intent(context, CopyOtpCodeReceiver::class.java)
            .putExtra(CopyOtpCodeReceiver.EXTRA_CODE, "482913")

        CopyOtpCodeReceiver().onReceive(context, intent)

        val clipboard = context.getSystemService(ClipboardManager::class.java)
        assertEquals("482913", clipboard.primaryClip?.getItemAt(0)?.text.toString())
    }
}
