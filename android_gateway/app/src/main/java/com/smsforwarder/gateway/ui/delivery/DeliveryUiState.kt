package com.smsforwarder.gateway.ui.delivery

import androidx.work.BackoffPolicy
import com.smsforwarder.gateway.data.remote.TestConnectionResult

data class DeliveryUiState(
    val serverUrl: String = "",
    val uploadToken: String = "",
    val maxAttempts: String = "",
    val baseIntervalSeconds: String = "",
    val backoffPolicy: BackoffPolicy = BackoffPolicy.EXPONENTIAL,
    val forwardingPaused: Boolean = false,
    val deleteAfterForward: Boolean = false,
    val hideContactNameInPayload: Boolean = true,
    val isSaved: Boolean = false,
    val isTestingConnection: Boolean = false,
    val testConnectionResult: TestConnectionResult? = null,
) {
    val maxAttemptsError: String?
        get() {
            val value = maxAttempts.toIntOrNull() ?: return "Введите число"
            return if (value in 1..50) null else "От 1 до 50"
        }

    val baseIntervalSecondsError: String?
        get() {
            val value = baseIntervalSeconds.toLongOrNull() ?: return "Введите число"
            return if (value in 10..3600) null else "От 10 до 3600"
        }

    val canSave: Boolean
        get() = serverUrl.isNotBlank() &&
            uploadToken.isNotBlank() &&
            maxAttemptsError == null &&
            baseIntervalSecondsError == null
}
