package com.smsforwarder.gateway.ui.thread

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.smsforwarder.gateway.data.local.db.DeliveryStatus
import com.smsforwarder.gateway.data.local.db.MessageDirection
import com.smsforwarder.gateway.data.local.db.MessageEntity
import com.smsforwarder.gateway.ui.common.ConfirmDialog
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object ThreadTestTags {
    const val LIST = "thread_list"
    const val DRAFT_FIELD = "thread_draft_field"
    const val SEND_BUTTON = "thread_send_button"
    const val SIM_SELECTOR = "thread_sim_selector"
    const val DELETE_CONVERSATION_BUTTON = "thread_delete_conversation_button"
    const val SELECTION_CLOSE_BUTTON = "thread_selection_close_button"
    const val SELECTION_DELETE_BUTTON = "thread_selection_delete_button"
    const val SEGMENT_ACTION_OPEN = "thread_segment_action_open"
    const val SEGMENT_ACTION_COPY = "thread_segment_action_copy"
    const val SEGMENT_ACTION_CALL = "thread_segment_action_call"
    const val SEGMENT_ACTION_MESSAGE = "thread_segment_action_message"
    fun retryButton(id: Long) = "thread_retry_button_$id"
    fun simMenuItem(subscriptionId: Int) = "thread_sim_menu_item_$subscriptionId"
    fun bubble(id: Long) = "thread_bubble_$id"
    fun simIndicator(messageId: Long) = "thread_sim_indicator_$messageId"
    fun dateSeparator(label: String) = "thread_date_separator_$label"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadScreen(viewModel: ThreadViewModel = hiltViewModel(), onBack: () -> Unit = {}) {
    val uiState by viewModel.uiState.collectAsState()
    var showDeleteConversationConfirm by remember { mutableStateOf(false) }
    var showDeleteSelectedConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            if (uiState.isSelectionMode) {
                TopAppBar(
                    title = { Text("${uiState.selectedMessageIds.size}") },
                    navigationIcon = {
                        IconButton(
                            onClick = viewModel::onClearSelection,
                            modifier = Modifier.testTag(ThreadTestTags.SELECTION_CLOSE_BUTTON),
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Отменить выделение")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { showDeleteSelectedConfirm = true },
                            modifier = Modifier.testTag(ThreadTestTags.SELECTION_DELETE_BUTTON),
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Удалить выделенные сообщения")
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text(uiState.title) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { showDeleteConversationConfirm = true },
                            modifier = Modifier.testTag(ThreadTestTags.DELETE_CONVERSATION_BUTTON),
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Удалить диалог")
                        }
                    },
                )
            }
        },
    ) { padding ->
        ThreadContent(uiState = uiState, actions = viewModel, modifier = Modifier.padding(padding))
    }

    if (showDeleteConversationConfirm) {
        ConfirmDialog(
            title = "Удалить диалог?",
            text = "Все сообщения с ${uiState.title} будут удалены безвозвратно.",
            onConfirm = {
                viewModel.onDeleteConversation()
                showDeleteConversationConfirm = false
                onBack()
            },
            onDismiss = { showDeleteConversationConfirm = false },
        )
    }

    if (showDeleteSelectedConfirm) {
        ConfirmDialog(
            title = "Удалить ${uiState.selectedMessageIds.size} сообщений?",
            text = "Выбранные сообщения будут удалены безвозвратно.",
            onConfirm = {
                viewModel.onDeleteSelectedMessages()
                showDeleteSelectedConfirm = false
            },
            onDismiss = { showDeleteSelectedConfirm = false },
        )
    }
}

private sealed class ThreadListItem {
    data class MessageItem(val message: MessageEntity, val isFirstInGroup: Boolean) : ThreadListItem()
    data class DateHeader(val label: String) : ThreadListItem()
}

// Spec 0032: a date header is inserted before the first message of each new calendar
// day - independent of isFirstInGroup's 5-minute grouping, which stays unchanged.
private fun buildThreadListItems(messages: List<MessageEntity>): List<ThreadListItem> {
    val items = mutableListOf<ThreadListItem>()
    messages.forEachIndexed { index, message ->
        if (messages.isFirstOnNewDay(index)) {
            items += ThreadListItem.DateHeader(formatDateHeader(message.createdAt))
        }
        items += ThreadListItem.MessageItem(message, messages.isFirstInGroup(index))
    }
    return items
}

private val dateHeaderFormatWithoutYear = SimpleDateFormat("d MMMM", Locale.getDefault())
private val dateHeaderFormatWithYear = SimpleDateFormat("d MMMM yyyy", Locale.getDefault())

private fun formatDateHeader(timestampMillis: Long): String {
    val messageYear = Calendar.getInstance().apply { timeInMillis = timestampMillis }.get(Calendar.YEAR)
    val currentYear = Calendar.getInstance().get(Calendar.YEAR)
    val formatter = if (messageYear == currentYear) dateHeaderFormatWithoutYear else dateHeaderFormatWithYear
    return formatter.format(Date(timestampMillis))
}

@Composable
fun ThreadContent(uiState: ThreadUiState, actions: ThreadActions, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    var initialScrollDone by remember { mutableStateOf(false) }
    val threadListItems = remember(uiState.messages) { buildThreadListItems(uiState.messages) }
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isEmpty()) return@LaunchedEffect
        if (!initialScrollDone) {
            // First appearance of a (possibly large) history - jump straight to
            // the target with no visible scroll-through, not an animation.
            val targetIndex = uiState.scrollToMessageId
                ?.let { id -> threadListItems.indexOfFirst { it is ThreadListItem.MessageItem && it.message.id == id } }
                ?.takeIf { it >= 0 }
                ?: threadListItems.lastIndex
            listState.scrollToItem(targetIndex)
            initialScrollDone = true
        } else {
            listState.animateScrollToItem(threadListItems.lastIndex)
        }
    }

    // Scrolling the history upward (toward older messages) hides the keyboard, like
    // most messengers - a separate LaunchedEffect from the auto-scroll-to-newest one
    // above, since that one only ever increases firstVisibleItemIndex/offset and so
    // never reads as "scrolled up" here.
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    LaunchedEffect(listState) {
        var previousIndex = listState.firstVisibleItemIndex
        var previousOffset = listState.firstVisibleItemScrollOffset
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                val scrolledUp = index < previousIndex || (index == previousIndex && offset < previousOffset)
                if (scrolledUp) {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                }
                previousIndex = index
                previousOffset = offset
            }
    }

    // Memoized once per `actions` identity (stable - the ViewModel instance doesn't
    // change), not recreated per item per recomposition - passing a fresh closure to
    // MessageBubble on every keystroke/state update would defeat LazyColumn's ability
    // to skip recomposing bubbles whose own message data hasn't changed.
    val onRetry: (Long) -> Unit = remember(actions) { { id -> actions.onRetry(id) } }
    val onToggleSelection: (Long) -> Unit = remember(actions) { { id -> actions.onToggleMessageSelection(id) } }

    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag(ThreadTestTags.LIST),
            // Horizontal inset moved off the LazyColumn itself and onto each item's own
            // content (the bubble's Row) so the selection highlight (spec 0031 follow-up)
            // can span the true full screen width - contentPadding here would otherwise
            // inset every item, highlight included, from the screen edges.
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
        ) {
            items(
                threadListItems,
                key = { item ->
                    when (item) {
                        is ThreadListItem.MessageItem -> item.message.id
                        is ThreadListItem.DateHeader -> "date_${item.label}"
                    }
                },
            ) { item ->
                when (item) {
                    is ThreadListItem.DateHeader -> DateSeparatorRow(label = item.label)
                    is ThreadListItem.MessageItem -> MessageBubble(
                        message = item.message,
                        onRetry = onRetry,
                        isSelected = item.message.id in uiState.selectedMessageIds,
                        isSelectionMode = uiState.isSelectionMode,
                        onToggleSelection = onToggleSelection,
                        showSimIndicator = uiState.showSimSelector,
                        isFirstInGroup = item.isFirstInGroup,
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = uiState.draft,
                onValueChange = actions::onDraftChange,
                enabled = !uiState.isSending,
                shape = MaterialTheme.shapes.small,
                // Replaces the old FilterChip row that used to sit above the draft
                // field (spec 0028 fixed its selected-chip fill color, but the product
                // owner then flagged, live on-device, that the row itself still read as
                // a solid dark bar over the last messages) - moving SIM selection into
                // the field's own trailing slot keeps it compact and inside the field's
                // bounds instead of a separate full-width element.
                trailingIcon = {
                    if (uiState.showSimSelector) {
                        var showSimMenu by remember { mutableStateOf(false) }
                        val selectedSim = uiState.availableSims.firstOrNull { it.subscriptionId == uiState.selectedSubscriptionId }
                        Box {
                            Column(
                                modifier = Modifier
                                    .clickable { showSimMenu = true }
                                    .padding(4.dp)
                                    .testTag(ThreadTestTags.SIM_SELECTOR),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Icon(Icons.Default.SimCard, contentDescription = "Выбор SIM")
                                selectedSim?.let {
                                    Text(
                                        text = "SIM ${it.slotIndex + 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                            DropdownMenu(expanded = showSimMenu, onDismissRequest = { showSimMenu = false }) {
                                uiState.availableSims.forEach { sim ->
                                    DropdownMenuItem(
                                        text = { Text("SIM ${sim.slotIndex + 1} · ${sim.displayName}") },
                                        onClick = {
                                            actions.onSelectSim(sim.subscriptionId)
                                            showSimMenu = false
                                        },
                                        leadingIcon = {
                                            RadioButton(
                                                selected = sim.subscriptionId == uiState.selectedSubscriptionId,
                                                onClick = null,
                                            )
                                        },
                                        modifier = Modifier.testTag(ThreadTestTags.simMenuItem(sim.subscriptionId)),
                                    )
                                }
                            }
                        }
                    }
                },
                modifier = Modifier.weight(1f).testTag(ThreadTestTags.DRAFT_FIELD),
            )
            if (uiState.isSending) {
                CircularProgressIndicator(modifier = Modifier.padding(start = 12.dp).heightIn(24.dp))
            } else {
                FilledIconButton(
                    onClick = actions::onSend,
                    enabled = uiState.canSend,
                    shape = CircleShape,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(48.dp)
                        .testTag(ThreadTestTags.SEND_BUTTON),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Отправить")
                }
            }
        }
    }
}

@Composable
private fun DateSeparatorRow(label: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .testTag(ThreadTestTags.dateSeparator(label)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: MessageEntity,
    onRetry: (Long) -> Unit,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onToggleSelection: (Long) -> Unit,
    showSimIndicator: Boolean,
    isFirstInGroup: Boolean,
) {
    val isOutgoing = message.direction == MessageDirection.OUT
    var actionMenuSegment by remember { mutableStateOf<TextSegment?>(null) }
    val bubbleContentColor = if (isOutgoing) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val density = LocalDensity.current
    val view = LocalView.current

    // Spec 0031 B.1: the segment action menu opens near the tap point, not at a fixed
    // corner - the tap position is captured on the Initial pointer pass (observing
    // only, not consuming) so the link/phone/otp click handling downstream in Text's
    // own pointerInput still fires normally. DropdownMenu's offset is relative to its
    // own anchor (the bubble Column it's declared in), not the window - so both the
    // tap position and the bubble's own position are captured in window coordinates
    // and subtracted, which is correct regardless of any padding sitting between them
    // (e.g. the bubble's own 12dp content padding).
    var textBoxPositionInWindow by remember { mutableStateOf(Offset.Zero) }
    var bubbleAnchorPositionInWindow by remember { mutableStateOf(Offset.Zero) }
    var lastTapOffset by remember { mutableStateOf(Offset.Zero) }

    fun copyToClipboard(text: String) {
        clipboardManager.setText(AnnotatedString(text))
    }

    // Selection highlight (spec 0031 follow-up): the fill is applied to the whole row
    // *before* the existing group-spacing top padding, so it covers that padding's
    // space too - since LazyColumn items are contiguous (no gap between them), two
    // consecutive selected messages' fills touch automatically with no neighbor-aware
    // logic needed. A date header between two messages is a separate list item with
    // no fill of its own, so the highlight naturally breaks there too. Selection
    // tap/long-press also targets this same full-width area, not just the bubble -
    // matches most messengers (Telegram included), where tapping the empty space
    // next to a short message still selects it.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent)
            .combinedClickable(
                onClick = { if (isSelectionMode) onToggleSelection(message.id) },
                // Spec 0031 p.7 (2026-09-05): long-press only *enters* selection - once a
                // message is already selected, a further long-press is handled by the
                // SelectionContainer wrapping its Text below (real word text selection),
                // not by toggling selection off here.
                onLongClick = { if (!isSelected) onToggleSelection(message.id) },
            )
            .padding(top = if (isFirstInGroup) 2.dp else 0.dp)
            .padding(vertical = 2.dp),
    ) {
        Row(
            // Horizontal inset lives here now, not on the LazyColumn's contentPadding -
            // the highlight Box above spans full width, the bubble itself keeps the
            // same 8dp inset from the screen edges it always had.
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = if (isOutgoing) Arrangement.End else Arrangement.Start,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .background(
                        color = if (isOutgoing) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.medium,
                    )
                    .onGloballyPositioned { coordinates -> bubbleAnchorPositionInWindow = coordinates.positionInWindow() }
                    .padding(12.dp)
                    .testTag(ThreadTestTags.bubble(message.id)),
            ) {
                val segments = remember(message.text) { segmentMessageText(message.text) }
                val linkSpanStyle = SpanStyle(textDecoration = TextDecoration.Underline)
                val annotatedText = buildAnnotatedString {
                    segments.forEach { segment ->
                        when (segment) {
                            is TextSegment.Plain -> append(segment.text)
                            is TextSegment.Link -> withLink(
                                LinkAnnotation.Clickable("link") { actionMenuSegment = segment },
                            ) { withStyle(linkSpanStyle) { append(segment.text) } }
                            is TextSegment.Phone -> withLink(
                                LinkAnnotation.Clickable("phone") { actionMenuSegment = segment },
                            ) { withStyle(linkSpanStyle) { append(segment.text) } }
                            is TextSegment.Otp -> withLink(
                                LinkAnnotation.Clickable("otp") {
                                    copyToClipboard(segment.code)
                                    Toast.makeText(context, "Код скопирован", Toast.LENGTH_SHORT).show()
                                },
                            ) { withStyle(linkSpanStyle) { append(segment.text) } }
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .onGloballyPositioned { coordinates -> textBoxPositionInWindow = coordinates.positionInWindow() }
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(pass = PointerEventPass.Initial)
                                lastTapOffset = down.position
                            }
                        },
                ) {
                    val messageText = @Composable {
                        Text(
                            text = annotatedText,
                            color = bubbleContentColor,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    // Spec 0031 p.7 (2026-09-05): SelectionContainer only wraps the text
                    // once the message is already selected in multi-select - so the first
                    // long-press (message not yet selected) reaches the outer
                    // combinedClickable normally, and only a *second* long-press (now
                    // wrapped) is intercepted here for real word text selection, without
                    // clearing the message's own selection state.
                    if (isSelected) {
                        SelectionContainer { messageText() }
                    } else {
                        messageText()
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = formatTime(message.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = bubbleContentColor,
                    )
                    if (showSimIndicator && message.simSlot != null) {
                        Text(
                            text = "SIM ${message.simSlot + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = bubbleContentColor,
                            modifier = Modifier.testTag(ThreadTestTags.simIndicator(message.id)),
                        )
                    }
                }
                if (message.deliveryStatus == DeliveryStatus.FAILED) {
                    TextButton(
                        onClick = { onRetry(message.id) },
                        modifier = Modifier.testTag(ThreadTestTags.retryButton(message.id)),
                    ) {
                        Text("Повторить", color = MaterialTheme.colorScheme.error)
                    }
                }
                val menuSegment = actionMenuSegment
                val menuOffset = with(density) {
                    val estimatedMenuHeightPx = 160.dp.toPx()
                    val tapPositionInWindow = textBoxPositionInWindow + lastTapOffset
                    val relativeOffset = tapPositionInWindow - bubbleAnchorPositionInWindow
                    val opensUpward = menuOpensUpward(
                        tapY = tapPositionInWindow.y,
                        screenHeight = view.height.toFloat(),
                        estimatedMenuHeight = estimatedMenuHeightPx,
                    )
                    val yPx = if (opensUpward) relativeOffset.y - estimatedMenuHeightPx else relativeOffset.y
                    DpOffset(x = relativeOffset.x.toDp(), y = yPx.toDp())
                }
                DropdownMenu(
                    expanded = menuSegment != null,
                    onDismissRequest = { actionMenuSegment = null },
                    offset = menuOffset,
                ) {
                    when (menuSegment) {
                        is TextSegment.Link -> {
                            DropdownMenuItem(
                                text = { Text("Открыть в браузере") },
                                onClick = {
                                    actionMenuSegment = null
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(menuSegment.url)))
                                },
                                modifier = Modifier.testTag(ThreadTestTags.SEGMENT_ACTION_OPEN),
                            )
                            DropdownMenuItem(
                                text = { Text("Скопировать") },
                                onClick = {
                                    actionMenuSegment = null
                                    copyToClipboard(menuSegment.url)
                                },
                                modifier = Modifier.testTag(ThreadTestTags.SEGMENT_ACTION_COPY),
                            )
                        }
                        is TextSegment.Phone -> {
                            DropdownMenuItem(
                                text = { Text("Позвонить") },
                                onClick = {
                                    actionMenuSegment = null
                                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${menuSegment.number}")))
                                },
                                modifier = Modifier.testTag(ThreadTestTags.SEGMENT_ACTION_CALL),
                            )
                            DropdownMenuItem(
                                text = { Text("Написать сообщение") },
                                onClick = {
                                    actionMenuSegment = null
                                    context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${menuSegment.number}")))
                                },
                                modifier = Modifier.testTag(ThreadTestTags.SEGMENT_ACTION_MESSAGE),
                            )
                            DropdownMenuItem(
                                text = { Text("Скопировать") },
                                onClick = {
                                    actionMenuSegment = null
                                    copyToClipboard(menuSegment.number)
                                },
                                modifier = Modifier.testTag(ThreadTestTags.SEGMENT_ACTION_COPY),
                            )
                        }
                        else -> {}
                    }
                }
            }
        }
    }
}

// Single shared formatter, not a fresh SimpleDateFormat per bubble per recomposition -
// safe because all calls happen on the main/Compose UI thread.
private val messageTimeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

private fun formatTime(timestampMillis: Long): String =
    messageTimeFormat.format(Date(timestampMillis))
