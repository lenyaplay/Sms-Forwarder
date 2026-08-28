package com.smsforwarder.gateway.ui.conversations

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.testTag

object NewMessageDialogTestTags {
    const val NUMBER_FIELD = "new_message_dialog_number_field"
    const val CONFIRM_BUTTON = "new_message_dialog_confirm_button"
}

@Composable
fun NewMessageDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var number by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новое сообщение") },
        text = {
            OutlinedTextField(
                value = number,
                onValueChange = { number = it },
                label = { Text("Номер телефона") },
                modifier = androidx.compose.ui.Modifier.testTag(NewMessageDialogTestTags.NUMBER_FIELD),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(number) },
                enabled = number.isNotBlank(),
                modifier = androidx.compose.ui.Modifier.testTag(NewMessageDialogTestTags.CONFIRM_BUTTON),
            ) {
                Text("Начать")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}
