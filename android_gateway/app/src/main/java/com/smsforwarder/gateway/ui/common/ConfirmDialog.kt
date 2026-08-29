package com.smsforwarder.gateway.ui.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

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
