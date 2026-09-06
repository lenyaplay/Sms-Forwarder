package com.smsforwarder.gateway.sms

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.smsforwarder.gateway.MainActivity
import com.smsforwarder.gateway.ui.thread.TextSegment
import com.smsforwarder.gateway.ui.thread.segmentMessageText
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IncomingSmsNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val nextId = AtomicInteger(1)

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Incoming SMS",
                NotificationManager.IMPORTANCE_DEFAULT,
            )
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    fun notifyIncoming(sender: String, text: String) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val notificationId = nextId.getAndIncrement()

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_action_email)
            .setContentTitle(sender)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    // requestCode keyed on sender, not a shared 0 - otherwise PendingIntent's
                    // (context, requestCode, intent) cache key collides across different senders
                    // and FLAG_UPDATE_CURRENT can return a stale intent with the wrong extra.
                    sender.hashCode(),
                    Intent(context, MainActivity::class.java)
                        .putExtra(MainActivity.EXTRA_OPEN_SENDER, sender)
                        // Without CLEAR_TOP, tapping while MainActivity is already resumed on
                        // another screen just reorders the existing task to front
                        // (ActivityTaskManager START_TASK_TO_FRONT) without ever calling
                        // onNewIntent - confirmed live on an emulator (logcat showed no
                        // onNewIntent invocation, screen stayed on the previous thread).
                        // Matches SendToActivity's existing sms:/mms: deep-link, which needs
                        // the same flags for the same reason.
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            )

        // Spec 0038: at most the first 2 OTP codes found (by order of appearance),
        // each as its own "copy" action button - reuses the same segmentation the
        // thread screen already highlights OTPs with, not a separate regex.
        val otpCodes = segmentMessageText(text).filterIsInstance<TextSegment.Otp>().take(2)
        otpCodes.forEachIndexed { index, segment ->
            val label = if (otpCodes.size == 1) "Скопировать код" else "Скопировать код ${index + 1}"
            builder.addAction(
                0,
                label,
                PendingIntent.getBroadcast(
                    context,
                    // requestCode salted by this notification's own id (not just
                    // sender+index) - PendingIntent matching ignores extras, so two
                    // OTP messages from the SAME sender arriving back to back would
                    // otherwise share one requestCode (sender.hashCode()*31+index is
                    // identical for both) and FLAG_UPDATE_CURRENT would silently
                    // rewrite the still-visible FIRST notification's button to copy
                    // the SECOND message's code instead - a real bug caught in
                    // peer review, not by the tests (which only ever call
                    // notifyIncoming once per case). notificationId is unique per
                    // call (AtomicInteger), so this can't happen anymore.
                    notificationId * 2 + index,
                    Intent(context, CopyOtpCodeReceiver::class.java)
                        .putExtra(CopyOtpCodeReceiver.EXTRA_CODE, segment.code),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
        }

        context.getSystemService(NotificationManager::class.java)
            .notify(notificationId, builder.build())
    }

    private companion object {
        const val CHANNEL_ID = "incoming_sms"
    }
}
