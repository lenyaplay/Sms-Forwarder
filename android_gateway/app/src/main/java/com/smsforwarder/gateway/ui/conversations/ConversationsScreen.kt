package com.smsforwarder.gateway.ui.conversations

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.smsforwarder.gateway.ui.common.AVATAR_SIZE
import com.smsforwarder.gateway.ui.common.ConfirmDialog
import com.smsforwarder.gateway.ui.common.ContactAvatar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ConversationsTestTags {
    const val LIST = "conversations_list"
    const val EMPTY_STATE = "conversations_empty_state"
    const val IMPORTING_INDICATOR = "conversations_importing_indicator"
    const val NEW_MESSAGE_FAB = "conversations_new_message_fab"
    const val SEARCH_FIELD = "conversations_search_field"
    const val ARCHIVE_TOGGLE = "conversations_archive_toggle"
    const val RESEND_ALL_FAILED = "conversations_resend_all_failed"
    const val SEARCH_RESULTS_LIST = "conversations_search_results_list"
    const val SEARCH_CLEAR_BUTTON = "conversations_search_clear_button"
    const val SETTINGS_BUTTON = "conversations_settings_button"
    const val ROW_MENU_ARCHIVE = "conversations_row_menu_archive"
    const val ROW_MENU_DELETE = "conversations_row_menu_delete"
    fun row(sender: String) = "conversations_row_$sender"
    fun searchResultRow(messageId: Long) = "conversations_search_result_row_$messageId"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationsScreen(
    viewModel: ConversationsViewModel = hiltViewModel(),
    onOpenThread: (String, Long?) -> Unit,
    onOpenSettings: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    var showNewMessageDialog by remember { mutableStateOf(false) }
    var pendingDeleteSender by remember { mutableStateOf<String?>(null) }
    var showResendAllConfirm by remember { mutableStateOf(false) }

    // Keeping the OutlinedTextField Compose-focused after the keyboard is dismissed
    // (whichever way - back press, tapping outside, the system) left its cursor
    // blinking (and the field still silently accepting typed input) with no visible
    // keyboard - confirmed live via adb: `focusManager.clearFocus()` alone, called
    // from BackHandler, did NOT actually detach the field's input connection.
    // Driven off real IME visibility instead, via the classic
    // ViewTreeObserver+WindowInsetsCompat approach rather than Compose's own
    // `WindowInsets.isImeVisible` - this app has no edge-to-edge/WindowCompat setup,
    // so Compose's own ime insets never update and that composable stays stuck at
    // its initial value (also confirmed live: no-op). The View-level query below
    // reads real window insets state directly and doesn't need edge-to-edge.
    val focusManager = LocalFocusManager.current
    val view = LocalView.current
    var imeVisible by remember { mutableStateOf(false) }
    DisposableEffect(view) {
        val listener = android.view.ViewTreeObserver.OnGlobalLayoutListener {
            imeVisible = ViewCompat.getRootWindowInsets(view)?.isVisible(WindowInsetsCompat.Type.ime()) ?: false
        }
        view.viewTreeObserver.addOnGlobalLayoutListener(listener)
        onDispose { view.viewTreeObserver.removeOnGlobalLayoutListener(listener) }
    }
    LaunchedEffect(imeVisible) {
        if (!imeVisible) focusManager.clearFocus(force = true)
    }

    // Without this, system back while searching closes the whole app (Conversations
    // is the start destination, no back stack to pop) - jarring mid-search.
    //
    // No explicit "dismiss focus first" step here, even though the product ask was
    // "first back removes focus, second clears the query": verified live (real
    // KEYCODE_BACK via adb, not just the instrumented test) that when the IME is
    // actually shown, Android itself consumes the very first back to hide the
    // keyboard - it never reaches this callback at all, and the field's Compose
    // focus survives that regardless of what this code does (clearFocus() doesn't
    // reliably flip it either, per instrumented-test observation). So this handler
    // clearing the query on its very first invocation already gives the intended
    // 2-press feel: press 1 hides the keyboard (OS-level, free), press 2 lands here
    // and clears the query. Adding our own extra "focus-only" step on top of that
    // made it a 3-press flow instead, confirmed with real back-key events.
    BackHandler(enabled = uiState.isSearching) {
        viewModel.onQueryChange("")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.isArchivedView) "Архив" else "SMS Forwarder Gateway") },
                actions = {
                    if (uiState.failedCount > 0) {
                        IconButton(
                            onClick = { showResendAllConfirm = true },
                            modifier = Modifier.testTag(ConversationsTestTags.RESEND_ALL_FAILED),
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Повторить неудавшиеся")
                        }
                    }
                    IconButton(
                        onClick = viewModel::onToggleArchivedView,
                        modifier = Modifier.testTag(ConversationsTestTags.ARCHIVE_TOGGLE),
                    ) {
                        Icon(
                            if (uiState.isArchivedView) Icons.Default.Inbox else Icons.Default.Archive,
                            contentDescription = if (uiState.isArchivedView) "Показать активные" else "Показать архив",
                        )
                    }
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.testTag(ConversationsTestTags.SETTINGS_BUTTON),
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Настройки")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showNewMessageDialog = true },
                shape = CircleShape,
                modifier = Modifier.testTag(ConversationsTestTags.NEW_MESSAGE_FAB),
            ) {
                Icon(Icons.Default.Add, contentDescription = "Новое сообщение")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = uiState.query,
                onValueChange = viewModel::onQueryChange,
                label = { Text("Поиск по переписке") },
                shape = MaterialTheme.shapes.small,
                trailingIcon = {
                    if (uiState.query.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.onQueryChange("") },
                            modifier = Modifier.testTag(ConversationsTestTags.SEARCH_CLEAR_BUTTON),
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Очистить поиск")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .testTag(ConversationsTestTags.SEARCH_FIELD),
            )
            if (uiState.isSearching) {
                SearchResultsList(results = uiState.searchResults, onOpenThread = onOpenThread)
            } else {
                ConversationsContent(
                    conversations = uiState.conversations,
                    isImporting = uiState.isImporting,
                    hasLoadedOnce = uiState.hasLoadedOnce,
                    isArchivedView = uiState.isArchivedView,
                    onOpenThread = onOpenThread,
                    onArchiveToggle = { sender -> viewModel.onArchiveToggle(sender, uiState.isArchivedView) },
                    onDeleteRequested = { sender -> pendingDeleteSender = sender },
                )
            }
        }
    }

    if (showNewMessageDialog) {
        NewMessageDialog(
            onDismiss = { showNewMessageDialog = false },
            onConfirm = { number ->
                showNewMessageDialog = false
                onOpenThread(number, null)
            },
        )
    }

    pendingDeleteSender?.let { sender ->
        ConfirmDialog(
            title = "Удалить диалог?",
            text = "Все сообщения с $sender будут удалены безвозвратно.",
            onConfirm = {
                viewModel.onDeleteConversation(sender)
                pendingDeleteSender = null
            },
            onDismiss = { pendingDeleteSender = null },
        )
    }

    if (showResendAllConfirm) {
        ConfirmDialog(
            title = "Повторить неудавшиеся?",
            text = "Будет предпринята повторная попытка отправки ${uiState.failedCount} сообщений.",
            confirmLabel = "Повторить",
            onConfirm = {
                viewModel.onResendAllFailed()
                showResendAllConfirm = false
            },
            onDismiss = { showResendAllConfirm = false },
        )
    }
}

// Spec 0027: search results render with the same ConversationRowContent as the
// conversations list (same avatar/fonts/spacing/time) - only the interaction differs:
// a plain click to the exact message, no swipe-to-archive/delete, no long-press menu
// (a search hit is one specific message, not a whole conversation to act on).
@Composable
private fun SearchResultsList(results: List<SearchResultUi>, onOpenThread: (String, Long?) -> Unit) {
    if (results.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Ничего не найдено", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxWidth().testTag(ConversationsTestTags.SEARCH_RESULTS_LIST),
        contentPadding = PaddingValues(8.dp),
    ) {
        items(results, key = { it.messageId }) { result ->
            ConversationRowContent(
                displayName = result.displayName,
                photoUri = result.photoUri,
                sender = result.sender,
                text = result.text,
                timestampText = formatConversationTime(result.createdAt),
                modifier = Modifier
                    .clickable { onOpenThread(result.sender, result.messageId) }
                    .testTag(ConversationsTestTags.searchResultRow(result.messageId)),
            )
        }
    }
}

@Composable
fun ConversationsContent(
    conversations: List<ConversationUi>,
    isImporting: Boolean,
    hasLoadedOnce: Boolean = true,
    isArchivedView: Boolean = false,
    onOpenThread: (String, Long?) -> Unit,
    onArchiveToggle: (String) -> Unit = {},
    onDeleteRequested: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        if (isImporting) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().testTag(ConversationsTestTags.IMPORTING_INDICATOR))
        }
        if (conversations.isEmpty() && !isImporting && hasLoadedOnce) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (isArchivedView) "Архив пуст" else "Нет сообщений",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.testTag(ConversationsTestTags.EMPTY_STATE),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(ConversationsTestTags.LIST),
                contentPadding = PaddingValues(8.dp),
            ) {
                items(conversations, key = { it.sender }) { conversation ->
                    // Pass the callbacks through unwrapped (not `{ onOpenThread(conversation.sender, null) }`
                    // recreated per item per recomposition) - a fresh lambda identity here would defeat
                    // ConversationRow's ability to skip recomposition when scrolling/unrelated state changes,
                    // since Compose compares these lambda params by identity, not by captured value.
                    ConversationRow(
                        conversation = conversation,
                        isArchivedView = isArchivedView,
                        onOpenThread = onOpenThread,
                        onArchiveToggle = onArchiveToggle,
                        onDeleteRequested = onDeleteRequested,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ConversationRow(
    conversation: ConversationUi,
    isArchivedView: Boolean,
    onOpenThread: (String, Long?) -> Unit,
    onArchiveToggle: (String) -> Unit,
    onDeleteRequested: (String) -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    val dismissState = rememberSwipeToDismissBoxState(
        // Default (0.5) reacts to a fairly short drag - easy to trigger by accident
        // while scrolling diagonally. Raised (0.75 -> 0.85, per product owner
        // feedback that 0.75 still triggered too easily) so only a near-full-row
        // swipe commits the action; a long-press menu below is the non-gesture
        // equivalent for TalkBack users.
        positionalThreshold = { totalDistance -> totalDistance * 0.85f },
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    onArchiveToggle(conversation.sender)
                    false
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    onDeleteRequested(conversation.sender)
                    false
                }
                SwipeToDismissBoxValue.Settled -> true
            }
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val (icon, color) = when (dismissState.dismissDirection) {
                SwipeToDismissBoxValue.StartToEnd -> (if (isArchivedView) Icons.Default.Inbox else Icons.Default.Archive) to MaterialTheme.colorScheme.primaryContainer
                SwipeToDismissBoxValue.EndToStart -> Icons.Default.Delete to MaterialTheme.colorScheme.errorContainer
                SwipeToDismissBoxValue.Settled -> null to MaterialTheme.colorScheme.surface
            }
            Box(
                modifier = Modifier.fillMaxSize().background(color).padding(horizontal = 20.dp),
                contentAlignment = if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                icon?.let { Icon(it, contentDescription = null) }
            }
        },
    ) {
        Box(
            modifier = Modifier
                .combinedClickable(
                    onClick = { onOpenThread(conversation.sender, null) },
                    // Non-gesture equivalent of the swipe actions above - TalkBack
                    // users (and anyone who doesn't want to swipe) can long-press
                    // instead, since swipe-to-dismiss has no built-in a11y action.
                    onLongClick = { showMenu = true },
                )
                .semantics(mergeDescendants = true) {}
                .testTag(ConversationsTestTags.row(conversation.sender)),
        ) {
            ConversationRowContent(
                displayName = conversation.displayName,
                photoUri = conversation.photoUri,
                sender = conversation.sender,
                text = conversation.text,
                timestampText = formatConversationTime(conversation.createdAt),
            )
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text(if (isArchivedView) "Показать во входящих" else "Архивировать") },
                    onClick = {
                        showMenu = false
                        onArchiveToggle(conversation.sender)
                    },
                    modifier = Modifier.testTag(ConversationsTestTags.ROW_MENU_ARCHIVE),
                )
                DropdownMenuItem(
                    text = { Text("Удалить") },
                    onClick = {
                        showMenu = false
                        onDeleteRequested(conversation.sender)
                    },
                    modifier = Modifier.testTag(ConversationsTestTags.ROW_MENU_DELETE),
                )
            }
        }
    }
}

/** Content-only row shared by [ConversationRow] (list) and [SearchResultsList] (spec 0027) - avatar/name/text/time/divider, no swipe/click/menu behavior of its own. */
@Composable
private fun ConversationRowContent(
    displayName: String,
    photoUri: String?,
    sender: String,
    text: String,
    timestampText: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ContactAvatar(displayName = displayName, photoUri = photoUri, sender = sender)
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(text = displayName, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(text = timestampText, style = MaterialTheme.typography.labelSmall)
        }
        // Indent matches the row's own layout above: 12dp row padding + AVATAR_SIZE +
        // 12dp gap to the text column, so the divider starts under the text, not the
        // avatar - reusing ContactAvatar's own size constant rather than a second,
        // independently-drifting 40.dp+12dp+12dp magic number here.
        HorizontalDivider(
            modifier = Modifier.padding(start = 12.dp + AVATAR_SIZE + 12.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )
    }
}

// Single shared formatter, not a fresh SimpleDateFormat per row per recomposition -
// safe because all calls happen on the main/Compose UI thread.
private val conversationTimeFormat = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())

private fun formatConversationTime(timestampMillis: Long): String =
    conversationTimeFormat.format(Date(timestampMillis))
