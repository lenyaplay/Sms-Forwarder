package com.smsforwarder.gateway.data.perf

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.metrics.performance.JankStats
import androidx.test.core.app.ApplicationProvider
import com.smsforwarder.gateway.data.repository.MessageRepository
import com.smsforwarder.gateway.ui.conversations.ConversationUi
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * Spec 0023: isolated layered scroll-jank experiment. Lives only in androidTest - never
 * shipped in a debug or release APK (spec Допущение 1). Reuses JankStats directly (not
 * through PerfMonitor's logcat/file wrapper, which never exposes raw frame counts) to
 * accumulate per-run total/janky frame counts, and JankComparison (spec Допущение 3) to
 * judge whether a layer's mean differs from a reference set by more than the reference
 * set's own run-to-run spread.
 *
 * Layers are cumulative (0: plain LazyColumn+Text -> 1: Card -> 2: exaggerated shadow ->
 * 3: SwipeToDismissBox -> 4: real ConversationsViewModel data) and each layer is compared
 * BOTH against Layer 0 (accumulated effect) and against the immediately preceding layer
 * (the effect this specific layer adds) - a single "vs baseline" comparison can't tell
 * which layer actually introduced an effect once several layers are stacked (spec
 * `query-validation` finding, see spec section C).
 *
 * Method names are prefixed layerN_ and run in that fixed order (MethodSorters.NAME_ASCENDING)
 * because later layers compare against the RunResults recorded by earlier ones within the
 * same JVM/instrumentation process (companion object state) - not because of any shared
 * mutable app state. This is a real constraint, accepted deliberately: this class is an
 * exploratory diagnostic tool run as a whole, not a regression-gate suite where each @Test
 * must be independently runnable - filtering to a single layerN_ method in isolation (IDE
 * "run this test" / gradle --tests) will NPE on a null RunResults.layerN-1.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@HiltAndroidTest
class ScrollJankLayerTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface RepositoryEntryPoint {
        fun messageRepository(): MessageRepository
    }

    @Test
    fun layer0_baselinePlainTextColumn() {
        val result = runLayer(rows = syntheticRows(ROW_COUNT), showCard = false, exaggeratedShadow = false, showSwipeToDismiss = false)
        RunResults.layer0 = result
        report("Layer 0 (baseline)", result, vsBaseline = null, vsPrevious = null)
    }

    @Test
    fun layer1_card() {
        val result = runLayer(rows = syntheticRows(ROW_COUNT), showCard = true, exaggeratedShadow = false, showSwipeToDismiss = false)
        RunResults.layer1 = result
        report("Layer 1 (Card)", result, vsBaseline = compareJank(result, RunResults.layer0!!), vsPrevious = compareJank(result, RunResults.layer0!!))
    }

    @Test
    fun layer2_exaggeratedShadow() {
        val result = runLayer(rows = syntheticRows(ROW_COUNT), showCard = true, exaggeratedShadow = true, showSwipeToDismiss = false)
        RunResults.layer2 = result
        report("Layer 2 (exaggerated shadow)", result, vsBaseline = compareJank(result, RunResults.layer0!!), vsPrevious = compareJank(result, RunResults.layer1!!))
    }

    @Test
    fun layer3_swipeToDismissBox() {
        val result = runLayer(rows = syntheticRows(ROW_COUNT), showCard = true, exaggeratedShadow = true, showSwipeToDismiss = true)
        RunResults.layer3 = result
        report("Layer 3 (SwipeToDismissBox)", result, vsBaseline = compareJank(result, RunResults.layer0!!), vsPrevious = compareJank(result, RunResults.layer2!!))
    }

    @Test
    fun layer4_realConversationData() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val repository = EntryPointAccessors.fromApplication(context, RepositoryEntryPoint::class.java).messageRepository()
        val realConversations = runBlocking {
            repository.observeConversations(archived = false).first().map { entity ->
                // Real Room data, real row count/content - contact-name resolution is skipped
                // (it runs on Dispatchers.IO in the real ViewModel and isn't part of the UI-thread
                // composition/scroll cost this experiment measures), sender used as display name.
                ConversationUi(sender = entity.sender, displayName = entity.sender, text = entity.text, createdAt = entity.createdAt)
            }
        }
        // Logged explicitly, not just row count, because ROW_COUNT (40) could coincidentally
        // match a real device's conversation count - "40 rows" alone wouldn't tell a reader of
        // the log/results table whether the real-data or synthetic-fallback path actually ran.
        val (rowsForLayer4, dataSource) = if (realConversations.isNotEmpty()) realConversations to "REAL" else syntheticRows(ROW_COUNT) to "SYNTHETIC-FALLBACK"
        val result = runLayer(rows = rowsForLayer4, showCard = true, exaggeratedShadow = true, showSwipeToDismiss = true)
        RunResults.layer4 = result
        report(
            "Layer 4 ($dataSource data, ${rowsForLayer4.size} rows)",
            result,
            vsBaseline = compareJank(result, RunResults.layer0!!),
            vsPrevious = compareJank(result, RunResults.layer3!!),
        )
    }

    /**
     * Composes [rows] ONCE (ComposeTestRule.setContent can only be called once per test
     * method) with a hoisted LazyListState, then runs RUN_COUNT scroll-and-measure passes
     * over that single composition - resetting scroll position to 0 between passes instead
     * of recomposing from scratch, so each pass starts from the same state.
     */
    private fun runLayer(rows: List<ConversationUi>, showCard: Boolean, exaggeratedShadow: Boolean, showSwipeToDismiss: Boolean): JankRunSet {
        val listState = LazyListState()
        composeRule.setContent {
            IsolatedScrollTestScreen(rows = rows, state = listState, showCard = showCard, exaggeratedShadow = exaggeratedShadow, showSwipeToDismiss = showSwipeToDismiss)
        }
        composeRule.waitForIdle()

        val percents = (1..RUN_COUNT).map {
            runBlocking(Dispatchers.Main) { listState.scrollToItem(0) }
            composeRule.waitForIdle()

            val counter = FrameCounter()
            // JankStats.createAndTrack (like the rest of the Choreographer/Looper machinery it
            // wraps) must be called on the window's main/UI thread - the instrumentation thread
            // running this test method is not that thread.
            lateinit var jankStats: JankStats
            composeRule.runOnUiThread {
                jankStats = JankStats.createAndTrack(composeRule.activity.window) { frameData -> counter.onFrame(frameData.isJank) }
            }
            repeat(SWIPES_PER_RUN) {
                composeRule.onNodeWithTag(SCREEN_TAG).performTouchInput { swipeUp() }
            }
            composeRule.waitForIdle()
            composeRule.runOnUiThread { jankStats.isTrackingEnabled = false }
            counter.jankyPercent
        }
        return JankRunSet(percents)
    }

    private fun report(label: String, result: JankRunSet, vsBaseline: JankComparisonResult?, vsPrevious: JankComparisonResult?) {
        android.util.Log.i(
            "ScrollJankLayerTest",
            "$label: mean=${"%.2f".format(result.mean)}%% min=${"%.2f".format(result.min)}%% max=${"%.2f".format(result.max)}%% " +
                "runs=${result.jankyPercents} vsBaseline=$vsBaseline vsPrevious=$vsPrevious",
        )
    }

    private object RunResults {
        var layer0: JankRunSet? = null
        var layer1: JankRunSet? = null
        var layer2: JankRunSet? = null
        var layer3: JankRunSet? = null
        var layer4: JankRunSet? = null
    }

    companion object {
        internal const val ROW_COUNT = 40
        private const val RUN_COUNT = 5
        internal const val SWIPES_PER_RUN = 3
        internal const val SCREEN_TAG = "isolated_scroll_test_list"

        internal fun syntheticRows(count: Int): List<ConversationUi> = (1..count).map { i ->
            ConversationUi(sender = "+1555000$i", displayName = "Synthetic $i", text = "Synthetic message body for row $i, long enough to be realistic.", createdAt = i.toLong())
        }
    }
}

/**
 * Shared by [ScrollJankLayerTest] and [ColdScrollWarmupTest] (spec 0024). Records each frame's
 * jank flag IN ORDER, not just aggregate counts, so a caller can later segment the sequence
 * (e.g. into deciles by position) without re-instrumenting - spec 0024 Допущение 4.
 */
internal class FrameCounter {
    private val frames = java.util.Collections.synchronizedList(mutableListOf<Boolean>())

    fun onFrame(isJank: Boolean) {
        frames.add(isJank)
    }

    val jankyPercent: Double get() = synchronized(frames) {
        if (frames.isEmpty()) 0.0 else frames.count { it } * 100.0 / frames.size
    }

    /** Ordered snapshot of every recorded frame's jank flag; position in the list is the frame's ordinal. */
    val frameFlags: List<Boolean> get() = synchronized(frames) { frames.toList() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun IsolatedScrollTestScreen(
    rows: List<ConversationUi>,
    state: LazyListState,
    showCard: Boolean,
    exaggeratedShadow: Boolean,
    showSwipeToDismiss: Boolean,
) {
    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize().testTag("isolated_scroll_test_list"),
        contentPadding = PaddingValues(8.dp),
    ) {
        items(rows, key = { it.sender }) { row ->
            when {
                !showCard -> Text(text = row.text, modifier = Modifier.fillMaxWidth().padding(12.dp))
                showSwipeToDismiss -> {
                    val dismissState = rememberSwipeToDismissBoxState()
                    SwipeToDismissBox(
                        state = dismissState,
                        backgroundContent = { Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) },
                    ) {
                        RowCard(row, exaggeratedShadow)
                    }
                }
                else -> RowCard(row, exaggeratedShadow)
            }
        }
    }
}

@Composable
private fun RowCard(row: ConversationUi, exaggeratedShadow: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        elevation = if (exaggeratedShadow) CardDefaults.cardElevation(defaultElevation = 24.dp) else CardDefaults.cardElevation(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = row.displayName, style = MaterialTheme.typography.titleMedium)
            Text(text = row.text, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        }
    }
}
