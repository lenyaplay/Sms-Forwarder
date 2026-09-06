package com.smsforwarder.gateway.ui.deliverylog

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.smsforwarder.gateway.data.local.db.DeliveryLogEntity
import com.smsforwarder.gateway.ui.tooling.exportGeometry
import com.smsforwarder.gateway.ui.tooling.writeGeometryJson
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File

// Spec 0033/0034 coverage extension (2026-09-06) - see DeliveryScreenSnapshotTest.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DeliveryLogScreenSnapshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val entries = listOf(
        DeliveryLogEntity(id = 1, sender = "+15551234", attemptNumber = 1, timestamp = 1_000L, success = true, errorMessage = null),
        DeliveryLogEntity(id = 2, sender = "+15559876", attemptNumber = 2, timestamp = 2_000L, success = false, errorMessage = "Connection timed out"),
        DeliveryLogEntity(id = 3, sender = "Bank", attemptNumber = 1, timestamp = 3_000L, success = true, errorMessage = null),
    )

    private fun capture(dark: Boolean, methodName: String) {
        composeRule.setContent {
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    DeliveryLogContent(uiState = DeliveryLogUiState(entries = entries), onBack = {})
                }
            }
        }
        val root = composeRule.onRoot()
        root.captureRoboImage()
        writeGeometryJson(
            outputDir = File("src/test/snapshots"),
            testClassFqcn = "com.smsforwarder.gateway.ui.deliverylog.DeliveryLogScreenSnapshotTest",
            testMethodName = methodName,
            geometry = exportGeometry(root.fetchSemanticsNode()),
        )
    }

    @Test
    fun deliveryLogLight() = capture(dark = false, methodName = "deliveryLogLight")

    @Test
    fun deliveryLogDark() = capture(dark = true, methodName = "deliveryLogDark")
}
