package com.smsforwarder.viewer.ui.common

import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString

/**
 * material-icons-extended (where a copy-icon glyph lives) isn't a project
 * dependency - only material-icons-core, which has no copy icon - so this is
 * a text button rather than adding that dependency for one glyph.
 */
@Composable
fun CopyTextButton(
    label: String,
    textToCopy: String,
    modifier: Modifier = Modifier,
    onCopied: () -> Unit = {},
) {
    val clipboardManager = LocalClipboardManager.current
    TextButton(
        onClick = {
            clipboardManager.setText(AnnotatedString(textToCopy))
            onCopied()
        },
        modifier = modifier,
    ) {
        Text(label)
    }
}
