package com.smsforwarder.gateway.ui.conversations

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

// Spec 0033, Stage A: Roborazzi baseline snapshots of ConversationsContent, both
// themes. photoUri intentionally null on every fake conversation - ContactAvatar
// falls back to initial-letter/silhouette rendering without needing Coil test
// infrastructure (real photo loading is out of scope for this stage).
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ConversationsScreenSnapshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val conversations = listOf(
        ConversationUi(
            sender = "+15551234",
            displayName = "Alice Johnson",
            photoUri = null,
            text = "See you tomorrow!",
            createdAt = 1_000L,
        ),
        ConversationUi(
            sender = "+15559876",
            displayName = "+15559876",
            photoUri = null,
            text = "Your code is 123456",
            createdAt = 2_000L,
        ),
        ConversationUi(
            sender = "Bank",
            displayName = "Bank",
            photoUri = null,
            text = "Archived conversation preview text that is long enough to wrap onto a second line",
            createdAt = 3_000L,
        ),
    )

    private fun capture(dark: Boolean) {
        composeRule.setContent {
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    ConversationsContent(
                        conversations = conversations,
                        isImporting = false,
                        onOpenThread = { _, _ -> },
                    )
                }
            }
        }
        // No explicit filePath: an explicit path bypasses the roborazzi { outputDir }
        // Gradle config in this version (1.30.0) and lands at the module root instead -
        // confirmed by a throwaway spike. Auto-naming (test class + method) does honor
        // outputDir, so it's used here despite the less readable resulting filename.
        composeRule.onRoot().captureRoboImage()
    }

    @Test
    fun conversationsLight() = capture(dark = false)

    @Test
    fun conversationsDark() = capture(dark = true)
}
