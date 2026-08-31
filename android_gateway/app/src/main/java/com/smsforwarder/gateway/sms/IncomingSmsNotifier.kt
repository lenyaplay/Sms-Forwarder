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

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
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
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(nextId.getAndIncrement(), notification)
    }

    private companion object {
        const val CHANNEL_ID = "incoming_sms"
    }
}
