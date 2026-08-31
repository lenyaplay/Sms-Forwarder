package com.smsforwarder.gateway

import android.content.Context
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.smsforwarder.gateway.data.local.GatewayConfigStore
import com.smsforwarder.gateway.data.local.db.FilterMode
import com.smsforwarder.gateway.data.local.db.FilterStage
import com.smsforwarder.gateway.ui.common.ConfirmDialogTestTags
import com.smsforwarder.gateway.ui.delivery.DeliveryTestTags
import com.smsforwarder.gateway.ui.settings.SettingsTestTags
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource

/**
 * First Activity-level instrumented test in this project (real MainActivity + real
 * Hilt DI graph, not an isolated *Content composable on fake/mocked data - see every
 * other androidTest class). Closes the test-infrastructure gap Milestone 17
 * documented: a real navigation/Activity-lifecycle bug (the missing
 * FLAG_ACTIVITY_CLEAR_TOP in IncomingSmsNotifier) was only caught by manual live
 * verification, exactly the class of bug this shape of test would catch.
 */
@HiltAndroidTest
class DeliveryResetActivityTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    // Grants the default-SMS role/permissions and seeds GatewayConfigStore BEFORE
    // composeRule launches MainActivity below (order=2) - createAndroidComposeRule
    // launches the Activity as part of applying its own Rule, which runs before any
    // @Before method, so doing this setup in @Before landed too late: MainContent's
    // isDefaultSmsApp() check (a plain remember{}, re-checked only on a lifecycle
    // ON_RESUME that never naturally fires in-test) had already observed "not default".
    @get:Rule(order = 1)
    val stateSetupRule = object : ExternalResource() {
        override fun before() {
            hiltRule.inject()
            val context: Context = ApplicationProvider.getApplicationContext()
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            automation.executeShellCommand("cmd role add-role-holder android.app.role.SMS ${context.packageName}").close()
            automation.executeShellCommand("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS").close()
            automation.executeShellCommand("pm grant ${context.packageName} android.permission.READ_CONTACTS").close()
            automation.executeShellCommand("pm grant ${context.packageName} android.permission.READ_PHONE_STATE").close()

            context.getSharedPreferences("sms_forwarder_gateway_config", Context.MODE_PRIVATE).edit().clear().commit()
            val configStore = GatewayConfigStore(context)
            // Delivery settings to be wiped, plus filter/import bookkeeping that must survive untouched.
            configStore.save("https://reset-test.example.com", "reset-test-token")
            configStore.setRetryMaxAttempts(7)
            configStore.setRetryBaseIntervalSeconds(90L)
            configStore.setFilterMode(FilterStage.RECEPTION, FilterMode.WHITELIST)
            configStore.markHistoryImported()
        }

        override fun after() {
            val context: Context = ApplicationProvider.getApplicationContext()
            context.getSharedPreferences("sms_forwarder_gateway_config", Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    @get:Rule(order = 2)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun resettingDeliverySettingsThroughRealNavigationClearsDeliveryKeysOnly() {
        composeRule.onNodeWithText("Настройки").performClick()
        composeRule.onNodeWithTag(SettingsTestTags.OPEN_DELIVERY_BUTTON).performClick()
        composeRule.onNodeWithTag(DeliveryTestTags.RESET_BUTTON).performScrollTo().performClick()
        composeRule.onNodeWithTag(ConfirmDialogTestTags.CONFIRM_BUTTON).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("reset-test-token").fetchSemanticsNodes().isEmpty()
        }

        // Fresh GatewayConfigStore instance reading the same real SharedPreferences file -
        // not the app's in-memory ViewModel state - proves the reset was actually persisted.
        val verifyStore = GatewayConfigStore(ApplicationProvider.getApplicationContext())
        assertNull(verifyStore.getServerUrl())
        assertNull(verifyStore.getUploadToken())
        assertEquals(10, verifyStore.retryMaxAttempts())
        assertEquals(30L, verifyStore.retryBaseIntervalSeconds())
        assertEquals(false, verifyStore.isForwardingPaused())

        assertEquals(FilterMode.WHITELIST, verifyStore.filterMode(FilterStage.RECEPTION))
        assertTrue(verifyStore.isHistoryImported())
    }
}
