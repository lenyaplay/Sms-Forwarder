package com.smsforwarder.gateway

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.espresso.Espresso
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.smsforwarder.gateway.data.local.db.DeliveryStatus
import com.smsforwarder.gateway.data.local.db.MessageDao
import com.smsforwarder.gateway.data.local.db.MessageEntity
import com.smsforwarder.gateway.ui.conversations.ConversationsTestTags
import com.smsforwarder.gateway.ui.thread.ThreadTestTags
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import javax.inject.Inject

/**
 * Spec 0037. Second Activity-level instrumented test in this project (see
 * DeliveryResetActivityTest's doc comment) - real MainActivity, real Hilt DI graph,
 * real Room-backed MessageDao (no fake/mocked repository), real system back events
 * via Espresso.pressBack(). Needed because the fix (BackHandler in ThreadScreen)
 * wraps the real ViewModel via hiltViewModel() - the project's usual lightweight
 * ThreadContent+fake-ThreadActions test shape (see ThreadScreenTest.kt) never
 * constructs ThreadScreen itself, so it cannot exercise this BackHandler at all.
 */
@HiltAndroidTest
class ThreadBackClearsSelectionActivityTest {

    private val sender = "+15559876543"

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var messageDao: MessageDao

    private var seededMessageId: Long = 0L

    // Seeded before composeRule (order=1, launches MainActivity) so the conversation
    // row already exists by first composition - same reasoning as
    // DeliveryResetActivityTest's GatewayConfigStore seeding, though here it's not
    // strictly required (ConversationsViewModel collects a live Room Flow, so an
    // insert after launch would be picked up too - seeding first is just simpler).
    @get:Rule(order = 1)
    val seedRule = object : ExternalResource() {
        override fun before() {
            hiltRule.inject()
            // GatewayNavGraph (and so ConversationsScreen) only renders once the app
            // is the default SMS app (see MainActivity.MainContent's isDefault gate) -
            // same grant DeliveryResetActivityTest performs before composeRule (order=2)
            // launches MainActivity.
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            automation.executeShellCommand("cmd role add-role-holder android.app.role.SMS ${context.packageName}").close()
            runBlocking {
                seededMessageId = messageDao.insert(
                    MessageEntity(
                        sender = sender,
                        text = "hello from back-clears-selection test",
                        sentStamp = null,
                        receivedStamp = System.currentTimeMillis(),
                        simSlot = 0,
                        deliveryStatus = DeliveryStatus.SENT,
                        createdAt = System.currentTimeMillis(),
                    ),
                )
            }
        }

        override fun after() {
            runBlocking { messageDao.deleteBySender(sender) }
        }
    }

    @get:Rule(order = 2)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun systemBackWithSelectionClearsSelectionBeforeLeavingTheThread() {
        composeRule.onNodeWithTag(ConversationsTestTags.row(sender)).performClick()
        composeRule.onNodeWithTag(ThreadTestTags.bubble(seededMessageId), useUnmergedTree = true)
            .performTouchInput { longClick() }
        composeRule.onNodeWithTag(ThreadTestTags.SELECTION_CLOSE_BUTTON).assertIsDisplayed()

        // First back: must only clear selection, not pop the thread off the back
        // stack - the real regression this spec fixes.
        Espresso.pressBack()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(ThreadTestTags.SELECTION_CLOSE_BUTTON).fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag(ThreadTestTags.bubble(seededMessageId), useUnmergedTree = true).assertIsDisplayed()
        assertTrue("Activity must still be alive after the first back", !composeRule.activity.isFinishing)

        // Second back: normal navigation now applies - back out of the thread to
        // the conversations list.
        Espresso.pressBack()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(ConversationsTestTags.row(sender)).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
