package com.smsforwarder.gateway.data.local

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class SimOption(val subscriptionId: Int, val slotIndex: Int, val displayName: String)

/** Lists the device's active SIMs via the official SubscriptionManager API - never throws, returns empty when unavailable/unauthorized. */
@Singleton
open class SimOptionsProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    open fun activeSims(): List<SimOption> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return emptyList()
        }
        val subscriptionManager = context.getSystemService(SubscriptionManager::class.java) ?: return emptyList()
        val subscriptions = try {
            subscriptionManager.activeSubscriptionInfoList
        } catch (e: SecurityException) {
            null
        }
        return subscriptions?.map { info ->
            SimOption(
                subscriptionId = info.subscriptionId,
                slotIndex = info.simSlotIndex,
                displayName = info.displayName?.toString() ?: "SIM ${info.simSlotIndex + 1}",
            )
        }.orEmpty()
    }

    /** Maps a reception-time slot index (SimSlotResolver) to the subscriptionId filter rules key on. Null if the slot isn't among the currently active SIMs. */
    open fun subscriptionIdForSlot(slotIndex: Int?): Int? =
        activeSims().find { it.slotIndex == slotIndex }?.subscriptionId
}
