package com.smsforwarder.gateway.ui.deliverylog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.smsforwarder.gateway.data.local.db.DeliveryLogEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.content.res.Configuration

object DeliveryLogTestTags {
    const val EMPTY_STATE = "delivery_log_empty_state"
    const val LIST = "delivery_log_list"
    fun entryRow(id: Long) = "delivery_log_entry_$id"
}

@Composable
fun DeliveryLogScreen(viewModel: DeliveryLogViewModel = hiltViewModel(), onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    DeliveryLogContent(uiState = uiState, onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeliveryLogContent(uiState: DeliveryLogUiState, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Лог доставки") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        if (uiState.entries.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text("Попыток доставки пока нет", modifier = Modifier.testTag(DeliveryLogTestTags.EMPTY_STATE))
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).testTag(DeliveryLogTestTags.LIST),
            ) {
                items(uiState.entries, key = { it.id }) { entry -> DeliveryLogRow(entry) }
            }
        }
    }
}

@Composable
private fun DeliveryLogRow(entry: DeliveryLogEntity) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag(DeliveryLogTestTags.entryRow(entry.id)),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = "${if (entry.success) "✓" else "✗"} ${entry.sender} — попытка ${entry.attemptNumber}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = formatTimestamp(entry.timestamp),
            style = MaterialTheme.typography.bodySmall,
        )
        if (!entry.success && entry.errorMessage != null) {
            Text(
                text = entry.errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

private fun formatTimestamp(timestampMillis: Long): String =
    SimpleDateFormat("dd.MM HH:mm:ss", Locale.getDefault()).format(Date(timestampMillis))

// Spec 0035: @Preview for manual design review in Android Studio, no ViewModel/Hilt.
private val previewDeliveryLogEntries = listOf(
    DeliveryLogEntity(id = 1, sender = "+15551234", attemptNumber = 1, timestamp = 1_700_000_000_000L, success = true, errorMessage = null),
    DeliveryLogEntity(id = 2, sender = "Bank", attemptNumber = 2, timestamp = 1_700_000_100_000L, success = false, errorMessage = "Connection timed out"),
    DeliveryLogEntity(id = 3, sender = "+15559876", attemptNumber = 1, timestamp = 1_700_000_200_000L, success = true, errorMessage = null),
)

@Preview(showBackground = true)
@Composable
private fun DeliveryLogContentPreviewLight() {
    MaterialTheme(colorScheme = lightColorScheme()) {
        Surface(color = MaterialTheme.colorScheme.background) {
            DeliveryLogContent(uiState = DeliveryLogUiState(entries = previewDeliveryLogEntries), onBack = {})
        }
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DeliveryLogContentPreviewDark() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(color = MaterialTheme.colorScheme.background) {
            DeliveryLogContent(uiState = DeliveryLogUiState(entries = previewDeliveryLogEntries), onBack = {})
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DeliveryLogRowPreviewLight() {
    MaterialTheme(colorScheme = lightColorScheme()) {
        Surface(color = MaterialTheme.colorScheme.background) { DeliveryLogRow(previewDeliveryLogEntries[1]) }
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DeliveryLogRowPreviewDark() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(color = MaterialTheme.colorScheme.background) { DeliveryLogRow(previewDeliveryLogEntries[1]) }
    }
}
