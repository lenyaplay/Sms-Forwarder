package com.smsforwarder.gateway.ui.filters

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.smsforwarder.gateway.data.local.db.FilterMode
import com.smsforwarder.gateway.data.local.db.FilterRuleEntity
import com.smsforwarder.gateway.data.local.db.FilterStage
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
class FilterRulesScreenSnapshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val noopActions = object : FilterRulesActions {
        override fun onTabSelected(stage: FilterStage) {}
        override fun onModeChange(mode: FilterMode) {}
        override fun onToggleEnabled(rule: FilterRuleEntity) {}
        override fun onDeleteRule(id: Long) {}
        override fun onMoveUp(rule: FilterRuleEntity) {}
        override fun onMoveDown(rule: FilterRuleEntity) {}
    }

    private fun rule(id: Long, senderPattern: String?, sortOrder: Int) = FilterRuleEntity(
        id = id,
        stage = FilterStage.RECEPTION,
        senderPattern = senderPattern,
        senderIsRegex = false,
        subscriptionId = null,
        contentPattern = null,
        contentIsRegex = false,
        enabled = true,
        sortOrder = sortOrder,
    )

    private val uiState = FilterRulesUiState(
        selectedStage = FilterStage.RECEPTION,
        rules = listOf(
            rule(1, "+15551234", 0),
            rule(2, "Bank", 1),
            rule(3, null, 2),
        ),
        mode = FilterMode.BLACKLIST,
    )

    private fun capture(dark: Boolean, methodName: String) {
        composeRule.setContent {
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    FilterRulesContent(
                        uiState = uiState,
                        actions = noopActions,
                        onBack = {},
                        onAddRule = {},
                        onEditRule = { _, _ -> },
                    )
                }
            }
        }
        val root = composeRule.onRoot()
        root.captureRoboImage()
        writeGeometryJson(
            outputDir = File("src/test/snapshots"),
            testClassFqcn = "com.smsforwarder.gateway.ui.filters.FilterRulesScreenSnapshotTest",
            testMethodName = methodName,
            geometry = exportGeometry(root.fetchSemanticsNode()),
        )
    }

    @Test
    fun filterRulesLight() = capture(dark = false, methodName = "filterRulesLight")

    @Test
    fun filterRulesDark() = capture(dark = true, methodName = "filterRulesDark")
}
