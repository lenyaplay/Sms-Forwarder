package com.smsforwarder.gateway

import android.app.Application
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.smsforwarder.gateway.data.local.SmsHistoryImporter
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class GatewayApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var smsHistoryImporter: SmsHistoryImporter

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Rows other apps write directly to content://sms (e.g. an OEM
        // dialer's "decline with message" quick-reply) never pass through
        // SMS_DELIVER/RESPOND_VIA_MESSAGE - this is the only way to notice
        // them without the user reinstalling the app to force a re-import.
        contentResolver.registerContentObserver(
            Telephony.Sms.CONTENT_URI,
            true,
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    scope.launch { smsHistoryImporter.syncNewMessages() }
                }
            },
        )

        // Spec 0026: background warm-up in Application.onCreate (both a Room DB
        // open AND, alone, just the RoleManager check) was tried and measured via
        // `adb shell am start -W` on a real cold process (TECNO LI9) - both made
        // TotalTime WORSE, not better: baseline ~339ms -> ~465ms with DB+role
        // warm-up -> ~401ms with role warm-up alone. A genuinely cold process has
        // very little scheduling slack; dispatching ANY background coroutine here,
        // even a single Binder call, measurably competes with MainActivity/
        // Compose's own class-loading and CPU time on this device. Not used -
        // DefaultSmsAppCache.isDefault() falls back to computing synchronously,
        // identical to the pre-spec-0026 behavior.
    }
}
