package com.smsforwarder.gateway.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2

/** Spec 0036: one circular trailing action revealed by a left-swipe (e.g. archive/delete). */
data class SwipeAction(
    val icon: ImageVector,
    val contentDescription: String,
    val containerColor: Color,
    val testTag: String,
    val onClick: () -> Unit,
)

private val SWIPE_ACTION_BUTTON_SIZE = 48.dp
private val SWIPE_ACTION_SPACING = 8.dp
// Extra breathing room between the revealed buttons and the row's right edge,
// beyond SWIPE_ACTION_SPACING - product owner feedback (2026-09-06, live check on
// device) that the default spacing alone felt too tight in the open state.
private val SWIPE_ACTION_END_PADDING = 12.dp

private enum class RevealState { Closed, Revealed }

/**
 * Spec 0036: replaces Material3's `SwipeToDismissBox` with an iOS Mail-style reveal gesture -
 * a left swipe past [revealThreshold] pins [actions] open as circular buttons instead of firing
 * an action immediately; the action only runs when the user taps a button. A right swipe from
 * the open state needs [closeThreshold] to close again, otherwise it springs back open (not to
 * fully closed) - this asymmetry (open needs one threshold, closing needs another, independently
 * missable) is why this isn't just a mirrored pair of `AnimateFloatAsState`s.
 *
 * [maxAngleDegrees] rejects near-vertical drags (accidental diagonal touches) - the angle is
 * computed from the gesture's total displacement from its down point, not a per-frame delta, so
 * a drag that starts vertical then straightens out still counts as vertical overall (spec 0036,
 * assumption 3).
 */
@Composable
fun SwipeActionsRow(
    actions: List<SwipeAction>,
    modifier: Modifier = Modifier,
    revealThreshold: Float = 0.3f,
    closeThreshold: Float = 0.75f,
    maxAngleDegrees: Float = 30f,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val openWidthPx = with(density) {
        (SWIPE_ACTION_BUTTON_SIZE * actions.size + SWIPE_ACTION_SPACING * actions.size + SWIPE_ACTION_END_PADDING).toPx()
    }
    var revealState by remember { mutableStateOf(RevealState.Closed) }
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    // Actions can change identity across recompositions (e.g. isArchivedView flips the icon) -
    // re-clamping keeps an already-open row from getting stuck past a now-narrower action list.
    LaunchedEffect(actions.size) {
        val target = if (revealState == RevealState.Revealed) -openWidthPx else 0f
        offsetX.snapTo(target)
    }

    Box(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .align(Alignment.CenterEnd)
                .padding(end = SWIPE_ACTION_END_PADDING),
            horizontalArrangement = Arrangement.spacedBy(SWIPE_ACTION_SPACING),
        ) {
            // Reading offsetX.value directly here (not inside the graphicsLayer lambda
            // below, which is deferred and doesn't trigger recomposition) makes this
            // Row recompose as the drag progresses. The buttons are only emitted at
            // all while genuinely peeking out - not just hidden via
            // Modifier.semantics{invisibleToUser()} - because Compose UI test's
            // assertIsDisplayed()/performClick() go through the semantics tree using
            // pure layout geometry, not real on-screen hit-testing: a node that
            // exists with nonzero bounds reads as "displayed" and is clickable via
            // testTag even while another node is drawn on top of it at the same
            // position. Not composing the buttons at all when closed is the only way
            // their exists/doesn't-exist state matches the real, physically-covered
            // reveal state (verified live - this replaced an invisibleToUser-only
            // attempt that a real gesture test showed still reported "displayed").
            if (offsetX.value < -0.5f) {
                actions.forEach { action ->
                    IconButton(
                        onClick = action.onClick,
                        modifier = Modifier.size(SWIPE_ACTION_BUTTON_SIZE).testTag(action.testTag),
                        colors = IconButtonDefaults.iconButtonColors(containerColor = action.containerColor),
                    ) {
                        Icon(action.icon, contentDescription = action.contentDescription)
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .graphicsLayer { translationX = offsetX.value }
                .background(MaterialTheme.colorScheme.surface)
                .pointerInput(actions, openWidthPx) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var totalDx = 0f
                        var totalDy = 0f
                        val closedAtGestureStart = revealState == RevealState.Closed
                        drag(down.id) { change ->
                            totalDx += change.positionChange().x
                            totalDy += change.positionChange().y
                            val angle = Math.toDegrees(
                                atan2(abs(totalDy).toDouble(), abs(totalDx).toDouble()),
                            )
                            if (angle <= maxAngleDegrees) {
                                change.consume()
                                val base = if (closedAtGestureStart) 0f else -openWidthPx
                                val target = (base + totalDx).coerceIn(-openWidthPx, 0f)
                                // onDrag isn't suspend, so each move needs its own
                                // launch to call the suspend snapTo - this looks like
                                // it could race across many launches, but Animatable
                                // internally serializes concurrent snapTo/animateTo
                                // calls through a mutex in the order they're launched
                                // (same dispatcher, no suspension between the launch
                                // calls themselves), so these apply in the same order
                                // as the drag events that produced them.
                                scope.launch { offsetX.snapTo(target) }
                            }
                        }
                        val revealedNow = -offsetX.value >= openWidthPx * (
                            if (closedAtGestureStart) revealThreshold else (1f - closeThreshold)
                        )
                        revealState = if (revealedNow) RevealState.Revealed else RevealState.Closed
                        // Launched after the loop above, so it queues behind every
                        // in-flight snapTo from this gesture on Animatable's mutex.
                        scope.launch {
                            offsetX.animateTo(if (revealedNow) -openWidthPx else 0f)
                        }
                    }
                },
        ) {
            content()
        }
    }
}
