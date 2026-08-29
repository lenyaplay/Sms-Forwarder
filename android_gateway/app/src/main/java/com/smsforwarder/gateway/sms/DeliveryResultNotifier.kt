package com.smsforwarder.gateway.sms

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Notifies about the outcome of webhook delivery, separate from
 * IncomingSmsNotifier (which fires on SMS receipt, not delivery result) - a
 * distinct channel lets the user mute delivery-status noise independently of
 * receipt notifications, per spec 0017.
 */
@Singleton
open class DeliveryResultNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val nextId = AtomicInteger(1)

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Статус доставки",
                NotificationManager.IMPORTANCE_DEFAULT,
            )
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    open fun notifyDeliveryFailed(sender: String, attempts: Int) {
        notify(
            title = "Не удалось переслать сообщение",
            text = "$sender: попытки исчерпаны ($attempts)",
        )
    }

    open fun notifyDeliverySucceededAfterRetry(sender: String, attempts: Int) {
        notify(
            title = "Сообщение доставлено",
            text = "$sender: доставлено после $attempts попыток",
        )
    }

    private fun notify(title: String, text: String) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_action_email)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(nextId.getAndIncrement(), notification)
    }

    private companion object {
        const val CHANNEL_ID = "delivery_result"
    }
}
