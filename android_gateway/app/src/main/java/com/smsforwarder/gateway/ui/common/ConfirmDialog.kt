package com.smsforwarder.gateway.ui.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import android.content.res.Configuration

object ConfirmDialogTestTags {
    const val CONFIRM_BUTTON = "confirm_dialog_confirm_button"
    const val DISMISS_BUTTON = "confirm_dialog_dismiss_button"
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmLabel: String = "Удалить",
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag(ConfirmDialogTestTags.CONFIRM_BUTTON)) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag(ConfirmDialogTestTags.DISMISS_BUTTON)) {
                Text("Отмена")
            }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun ConfirmDialogPreviewLight() {
    MaterialTheme(colorScheme = lightColorScheme()) {
        ConfirmDialog(title = "Удалить диалог?", text = "Все сообщения с +15551234 будут удалены безвозвратно.", onConfirm = {}, onDismiss = {})
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ConfirmDialogPreviewDark() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        ConfirmDialog(title = "Удалить диалог?", text = "Все сообщения с +15551234 будут удалены безвозвратно.", onConfirm = {}, onDismiss = {})
    }
}
