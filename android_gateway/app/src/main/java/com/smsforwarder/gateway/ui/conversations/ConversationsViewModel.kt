package com.smsforwarder.gateway.ui.conversations

import android.os.SystemClock
import android.os.Trace
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smsforwarder.gateway.data.local.ContactInfo
import com.smsforwarder.gateway.data.local.ContactNameResolver
import com.smsforwarder.gateway.data.local.SmsHistoryImporter
import com.smsforwarder.gateway.data.repository.MessageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ConversationUi(
    val sender: String,
    val displayName: String,
    val photoUri: String?,
    val text: String,
    val createdAt: Long,
)

/** Spec 0027: search results render with the same row component as the conversations list - needs the same avatar-relevant fields as [ConversationUi], resolved from the same [ContactNameResolver] cache. */
data class SearchResultUi(
    val messageId: Long,
    val sender: String,
    val displayName: String,
    val photoUri: String?,
    val text: String,
    val createdAt: Long,
)

data class ConversationsUiState(
    val query: String = "",
    val isArchivedView: Boolean = false,
    val conversations: List<ConversationUi> = emptyList(),
    val searchResults: List<SearchResultUi> = emptyList(),
    val isImporting: Boolean = false,
    val failedCount: Int = 0,
    /** False until observeConversations's Flow has emitted at least once - distinguishes "not loaded yet" from "loaded, genuinely empty" so cold start doesn't flash an empty-state message on data that's already in Room (spec 0022). Transient UI flag, not persisted. */
    val hasLoadedOnce: Boolean = false,
) {
    val isSearching: Boolean get() = query.isNotBlank()
}

interface ConversationsActions {
    fun onQueryChange(value: String)
    fun onToggleArchivedView()
    fun onArchiveToggle(sender: String, currentlyArchived: Boolean)
    fun onDeleteConversation(sender: String)
    fun onResendAllFailed()
}

@HiltViewModel
class ConversationsViewModel @Inject constructor(
    private val repository: MessageRepository,
    private val contactNameResolver: ContactNameResolver,
    historyImporter: SmsHistoryImporter,
) : ViewModel(), ConversationsActions {

    private val _uiState = MutableStateFlow(ConversationsUiState())
    val uiState: StateFlow<ConversationsUiState> = _uiState.asStateFlow()

    private var conversationsJob: Job? = null
    private var searchJob: Job? = null

    // onToggleArchivedView cancels conversationsJob and launches a new one; cancellation
    // is cooperative, so the old collector's in-flight withContext(Dispatchers.IO) map
    // pass can still be running when the new one starts on another IO thread - guard
    // with synchronized rather than assuming a single writer.
    private val contactNameCache = mutableMapOf<String, ContactInfo>()

    // Spec 0026 diagnostics: localizes where cold-start time to the first real
    // conversations-list frame goes (Room query/Flow dispatch vs contact resolution) -
    // always-on plain Log.i (unlike PerfMonitor's timers, not gated behind the
    // diagnostics toggle, since it's cheap - two nanoTime() reads and one log line
    // per VM lifetime, not per frame) so it's available without extra setup during
    // spec 0026's investigation. Not surfaced in UiState.
    private val createdAtNanos = System.nanoTime()
    private var firstEmissionLogged = false

    init {
        viewModelScope.launch {
            historyImporter.isImporting.collect { isImporting ->
                _uiState.update { it.copy(isImporting = isImporting) }
            }
        }
        observeConversations(archived = false)
        viewModelScope.launch {
            repository.observeFailedCount().collect { count ->
                _uiState.update { it.copy(failedCount = count) }
            }
        }
    }

    override fun onQueryChange(value: String) {
        _uiState.update { it.copy(query = value) }
        searchJob?.cancel()
        if (value.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList()) }
            return
        }
        searchJob = viewModelScope.launch {
            repository.searchMessages(value).collect { results ->
                val resultsUi = withContext(Dispatchers.IO) {
                    results.map { message ->
                        val contactInfo = synchronized(contactNameCache) {
                            contactNameCache.getOrPut(message.sender) { contactNameResolver.contactInfoFor(message.sender) }
                        }
                        SearchResultUi(
                            messageId = message.id,
                            sender = message.sender,
                            displayName = contactInfo.displayName ?: message.sender,
                            photoUri = contactInfo.photoUri,
                            text = message.text,
                            createdAt = message.createdAt,
                        )
                    }
                }
                _uiState.update { it.copy(searchResults = resultsUi) }
            }
        }
    }

    override fun onToggleArchivedView() {
        val newArchivedView = !_uiState.value.isArchivedView
        _uiState.update { it.copy(isArchivedView = newArchivedView) }
        observeConversations(newArchivedView)
    }

    override fun onArchiveToggle(sender: String, currentlyArchived: Boolean) {
        viewModelScope.launch {
            if (currentlyArchived) repository.unarchiveConversation(sender) else repository.archiveConversation(sender)
        }
    }

    override fun onDeleteConversation(sender: String) {
        viewModelScope.launch { repository.deleteConversation(sender) }
    }

    override fun onResendAllFailed() {
        viewModelScope.launch { repository.retryAllFailed() }
    }

    private fun observeConversations(archived: Boolean) {
        conversationsJob?.cancel()
        conversationsJob = viewModelScope.launch {
            repository.observeConversations(archived).distinctUntilChanged().collect { conversations ->
                val isFirstEmission = !firstEmissionLogged
                val emissionReceivedAtNanos = System.nanoTime()
                val resolveStartNanos = System.nanoTime()
                // Spec 0030: marks the resolve-to-first-real-row window for
                // Macrobenchmark's TraceSectionMetric("first_real_row") - only on
                // the first emission, to isolate cold-start cost from every
                // subsequent (already-warm) recomposition of this same collector.
                if (isFirstEmission) Trace.beginSection("first_real_row")
                val conversationsUi = try {
                    withContext(Dispatchers.IO) {
                        conversations.map { entity ->
                            val contactInfo = synchronized(contactNameCache) {
                                contactNameCache.getOrPut(entity.sender) { contactNameResolver.contactInfoFor(entity.sender) }
                            }
                            ConversationUi(
                                sender = entity.sender,
                                displayName = contactInfo.displayName ?: entity.sender,
                                photoUri = contactInfo.photoUri,
                                text = entity.text,
                                createdAt = entity.createdAt,
                            )
                        }
                    }
                } finally {
                    if (isFirstEmission) Trace.endSection()
                }
                if (isFirstEmission) {
                    val sinceCreatedMs = (emissionReceivedAtNanos - createdAtNanos) / 1_000_000
                    val resolveMs = (System.nanoTime() - resolveStartNanos) / 1_000_000
                    Log.i(
                        TAG,
                        "spec0026 first_emission_since_vm_created_ms=$sinceCreatedMs contact_resolve_ms=$resolveMs rows=${conversations.size}",
                    )
                }
                firstEmissionLogged = true
                _uiState.update { it.copy(conversations = conversationsUi, hasLoadedOnce = true) }
            }
        }
    }

    private companion object {
        const val TAG = "ConversationsViewModel"
    }
}
