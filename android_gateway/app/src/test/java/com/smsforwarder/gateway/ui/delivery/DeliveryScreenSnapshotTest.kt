package com.smsforwarder.gateway.ui.delivery

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.work.BackoffPolicy
import com.github.takahirom.roborazzi.captureRoboImage
import com.smsforwarder.gateway.data.remote.TestConnectionResult
import com.smsforwarder.gateway.ui.tooling.exportGeometry
import com.smsforwarder.gateway.ui.tooling.writeGeometryJson
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

// Spec 0033/0034 coverage extension (2026-09-06): same Roborazzi + geometry
// export pattern as ConversationsScreenSnapshotTest, applied to the
// remaining screens so all 7 app screens get the same before/after
// UI-metrics comparison, not just the original 3.
//
// @Config(qualifiers): this screen's form (verticalScroll, many fields) is
// taller than Robolectric's default virtual device (~470dp) - captureRoboImage()
// only captures the visible viewport, not the full scrollable content, so
// without a taller virtual screen the bottom of the form (backoff toggle,
// switches, save/reset buttons) would be silently cut off the PNG. Height
// picked generously (not measured exactly) so future field additions don't
// immediately re-trigger clipping - only this class's baseline is affected,
// not the other screens'.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w320dp-h1200dp")
class DeliveryScreenSnapshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val noopActions = object : DeliveryActions {
        override fun onServerUrlChange(value: String) {}
        override fun onUploadTokenChange(value: String) {}
        override fun onMaxAttemptsChange(value: String) {}
        override fun onBaseIntervalSecondsChange(value: String) {}
        override fun onBackoffPolicyChange(value: BackoffPolicy) {}
        override fun onForwardingPausedChange(value: Boolean) {}
        override fun onDeleteAfterForwardChange(value: Boolean) {}
        override fun onHideContactNameInPayloadChange(value: Boolean) {}
        override fun onSave() {}
        override fun onTestConnection() {}
        override fun onResetDeliverySettings() {}
    }

    private val uiState = DeliveryUiState(
        serverUrl = "https://sms.example.com",
        uploadToken = "abc123def456",
        maxAttempts = "5",
        baseIntervalSeconds = "60",
        backoffPolicy = BackoffPolicy.EXPONENTIAL,
        testConnectionResult = TestConnectionResult.Success(httpCode = 200),
    )

    private fun capture(dark: Boolean, methodName: String) {
        composeRule.setContent {
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    DeliveryContent(uiState = uiState, actions = noopActions, onBack = {})
                }
            }
        }
        val root = composeRule.onRoot()
        root.captureRoboImage()
        writeGeometryJson(
            outputDir = File("src/test/snapshots"),
            testClassFqcn = "com.smsforwarder.gateway.ui.delivery.DeliveryScreenSnapshotTest",
            testMethodName = methodName,
            geometry = exportGeometry(root.fetchSemanticsNode()),
        )
    }

    @Test
    fun deliveryLight() = capture(dark = false, methodName = "deliveryLight")

    @Test
    fun deliveryDark() = capture(dark = true, methodName = "deliveryDark")
}
