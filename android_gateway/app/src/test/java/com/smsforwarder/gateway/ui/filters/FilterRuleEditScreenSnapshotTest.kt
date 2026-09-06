package com.smsforwarder.gateway.ui.filters

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.smsforwarder.gateway.data.local.SimOption
import com.smsforwarder.gateway.data.local.db.FilterStage
import com.smsforwarder.gateway.ui.tooling.exportGeometry
import com.smsforwarder.gateway.ui.tooling.writeGeometryJson
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

// Spec 0033/0034 coverage extension (2026-09-06) - see DeliveryScreenSnapshotTest.
// @Config(qualifiers): this form doesn't scroll but is still taller than the
// default virtual device (~470dp) - without a taller screen, the Save button
// is cut off the bottom of the PNG (confirmed - see DeliveryScreenSnapshotTest
// for why).
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w320dp-h800dp")
class FilterRuleEditScreenSnapshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val noopActions = object : FilterRuleEditActions {
        override fun onSenderPatternChange(value: String) {}
        override fun onSenderIsRegexChange(value: Boolean) {}
        override fun onSubscriptionIdChange(value: Int?) {}
        override fun onContentPatternChange(value: String) {}
        override fun onContentIsRegexChange(value: Boolean) {}
        override fun onEnabledChange(value: Boolean) {}
        override fun onSave() {}
    }

    private val uiState = FilterRuleEditUiState(
        stage = FilterStage.RECEPTION,
        senderPattern = "+15551234",
        subscriptionId = null,
        contentPattern = "",
        enabled = true,
        availableSims = listOf(
            SimOption(subscriptionId = 1, slotIndex = 0, displayName = "SIM 1"),
            SimOption(subscriptionId = 2, slotIndex = 1, displayName = "SIM 2"),
        ),
    )

    private fun capture(dark: Boolean, methodName: String) {
        composeRule.setContent {
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    FilterRuleEditContent(uiState = uiState, actions = noopActions, onBack = {})
                }
            }
        }
        val root = composeRule.onRoot()
        root.captureRoboImage()
        writeGeometryJson(
            outputDir = File("src/test/snapshots"),
            testClassFqcn = "com.smsforwarder.gateway.ui.filters.FilterRuleEditScreenSnapshotTest",
            testMethodName = methodName,
            geometry = exportGeometry(root.fetchSemanticsNode()),
        )
    }

    @Test
    fun filterRuleEditLight() = capture(dark = false, methodName = "filterRuleEditLight")

    @Test
    fun filterRuleEditDark() = capture(dark = true, methodName = "filterRuleEditDark")
}
