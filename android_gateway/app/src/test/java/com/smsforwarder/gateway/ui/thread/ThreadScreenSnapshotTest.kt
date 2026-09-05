package com.smsforwarder.gateway.ui.thread

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.smsforwarder.gateway.data.local.db.DeliveryStatus
import com.smsforwarder.gateway.data.local.db.MessageDirection
import com.smsforwarder.gateway.data.local.db.MessageEntity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

// Spec 0033, Stage A: Roborazzi baseline snapshots of ThreadContent, both themes.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ThreadScreenSnapshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val noopActions = object : ThreadActions {
        override fun onDraftChange(value: String) {}
        override fun onSend() {}
        override fun onRetry(messageId: Long) {}
        override fun onSelectSim(subscriptionId: Int) {}
        override fun onDeleteMessage(messageId: Long) {}
        override fun onDeleteConversation() {}
        override fun onToggleMessageSelection(messageId: Long) {}
        override fun onClearSelection() {}
        override fun onDeleteSelectedMessages() {}
    }

    private fun message(id: Long, direction: MessageDirection, text: String) = MessageEntity(
        id = id,
        sender = "+15551234",
        text = text,
        sentStamp = if (direction == MessageDirection.OUT) id else null,
        receivedStamp = id,
        simSlot = 0,
        deliveryStatus = DeliveryStatus.SENT,
        createdAt = id,
        direction = direction,
    )

    private val messages = listOf(
        message(1L, MessageDirection.IN, "Hi, how are you?"),
        message(2L, MessageDirection.OUT, "Doing well, thanks!"),
        message(3L, MessageDirection.IN, "Your code is 123456"),
    )

    private fun capture(dark: Boolean) {
        composeRule.setContent {
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    ThreadContent(
                        uiState = ThreadUiState(sender = "+15551234", messages = messages),
                        actions = noopActions,
                    )
                }
            }
        }
        // See ConversationsScreenSnapshotTest for why auto-naming (no explicit
        // filePath) is used - it's the one that honors roborazzi { outputDir }.
        composeRule.onRoot().captureRoboImage()
    }

    @Test
    fun threadLight() = capture(dark = false)

    @Test
    fun threadDark() = capture(dark = true)
}
