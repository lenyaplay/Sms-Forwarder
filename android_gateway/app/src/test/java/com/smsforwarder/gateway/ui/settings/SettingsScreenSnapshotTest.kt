package com.smsforwarder.gateway.ui.settings

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

// Spec 0033, Stage A: Roborazzi baseline snapshots of SettingsContent, both themes.
// Uses all-default parameters - production code already provides a no-op
// SettingsActions default (SettingsScreen.kt), no fake construction needed.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsScreenSnapshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun capture(dark: Boolean) {
        composeRule.setContent {
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    SettingsContent()
                }
            }
        }
        // See ConversationsScreenSnapshotTest for why auto-naming (no explicit
        // filePath) is used - it's the one that honors roborazzi { outputDir }.
        composeRule.onRoot().captureRoboImage()
    }

    @Test
    fun settingsLight() = capture(dark = false)

    @Test
    fun settingsDark() = capture(dark = true)
}
