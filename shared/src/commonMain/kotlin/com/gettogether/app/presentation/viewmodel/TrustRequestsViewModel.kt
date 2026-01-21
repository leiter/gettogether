package com.gettogether.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gettogether.app.data.repository.AccountRepository
import com.gettogether.app.data.repository.ContactRepositoryImpl
import com.gettogether.app.jami.TrustRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TrustRequestsState(
    val requests: List<TrustRequestUiItem> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val hasAccount: Boolean = false
)

data class TrustRequestUiItem(
    val from: String,
    val conversationId: String,
    val displayName: String,
    val received: Long,
    val isProcessing: Boolean = false
)

class TrustRequestsViewModel(
    private val contactRepository: ContactRepositoryImpl,
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val _state = MutableStateFlow(TrustRequestsState())
    val state: StateFlow<TrustRequestsState> = _state.asStateFlow()

    init {
        // Observe account state
        viewModelScope.launch {
            accountRepository.currentAccountId.collect { accountId ->
                if (accountId != null) {
                    _state.update { it.copy(hasAccount = true) }
                    loadTrustRequests(accountId)
                } else {
                    _state.update {
                        it.copy(
                            hasAccount = false,
                            requests = emptyList(),
                            isLoading = false
                        )
                    }
                }
            }
        }
    }

    private fun loadTrustRequests(accountId: String) {
        viewModelScope.launch {
            println("[TRUST-REQUEST] === loadTrustRequests() called ===")
            println("[TRUST-REQUEST] Account ID: $accountId")
            _state.update { it.copy(isLoading = true, error = null) }

            try {
                contactRepository.getTrustRequests(accountId).collect { trustRequests ->
                    println("[TRUST-REQUEST] Received ${trustRequests.size} trust requests")
                    trustRequests.forEachIndexed { index, req ->
                        println("[TRUST-REQUEST]   [$index] From: ${req.from}")
                        println("[TRUST-REQUEST]   [$index] ConversationId: ${req.conversationId}")
                        println("[TRUST-REQUEST]   [$index] Received: ${req.received}")
                        println("[TRUST-REQUEST]   [$index] Payload size: ${req.payload.size} bytes")
                    }

                    val uiItems = trustRequests.map { it.toUiItem() }
                        .sortedByDescending { it.received }

                    _state.update {
                        it.copy(
                            requests = uiItems,
                            isLoading = false
                        )
                    }
                    println("[TRUST-REQUEST] ✓ UI state updated with ${uiItems.size} requests")
                }
            } catch (e: Exception) {
                println("[TRUST-REQUEST] ✗ Failed to load trust requests: ${e.message}")
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load trust requests"
                    )
                }
            }
        }
    }

    fun acceptRequest(from: String) {
        val accountId = accountRepository.currentAccountId.value ?: return
        val timestamp = kotlin.time.Clock.System.now().toEpochMilliseconds()

        println("[TRUST-ACCEPT] === acceptRequest() called ===")
        println("[TRUST-ACCEPT] Timestamp: $timestamp")
        println("[TRUST-ACCEPT] My Account ID: $accountId")
        println("[TRUST-ACCEPT] Accepting request from: $from")

        viewModelScope.launch {
            // Mark as processing
            _state.update { state ->
                state.copy(
                    requests = state.requests.map { request ->
                        if (request.from == from) {
                            request.copy(isProcessing = true)
                        } else {
                            request
                        }
                    }
                )
            }

            try {
                println("[TRUST-ACCEPT] → Calling contactRepository.acceptTrustRequest()...")
                val result = contactRepository.acceptTrustRequest(accountId, from)
                if (result.isFailure) {
                    println("[TRUST-ACCEPT] ✗ Accept failed: ${result.exceptionOrNull()?.message}")
                    _state.update {
                        it.copy(error = result.exceptionOrNull()?.message ?: "Failed to accept request")
                    }
                    // Unmark as processing on failure
                    _state.update { state ->
                        state.copy(
                            requests = state.requests.map { request ->
                                if (request.from == from) {
                                    request.copy(isProcessing = false)
                                } else {
                                    request
                                }
                            }
                        )
                    }
                } else {
                    println("[TRUST-ACCEPT] ✓ Request accepted successfully")
                    println("[TRUST-ACCEPT]   Contact added: $from")
                }
                // On success, the request will be removed from the list via the flow update
            } catch (e: Exception) {
                println("[TRUST-ACCEPT] ✗ Exception during accept: ${e.message}")
                _state.update {
                    it.copy(error = e.message ?: "Failed to accept request")
                }
            }
        }
    }

    fun rejectRequest(from: String, block: Boolean = false) {
        val accountId = accountRepository.currentAccountId.value ?: return
        val timestamp = kotlin.time.Clock.System.now().toEpochMilliseconds()

        println("[TRUST-REJECT] === rejectRequest() called ===")
        println("[TRUST-REJECT] Timestamp: $timestamp")
        println("[TRUST-REJECT] My Account ID: $accountId")
        println("[TRUST-REJECT] Rejecting request from: $from")
        println("[TRUST-REJECT] Block user: $block")

        viewModelScope.launch {
            // Mark as processing
            _state.update { state ->
                state.copy(
                    requests = state.requests.map { request ->
                        if (request.from == from) {
                            request.copy(isProcessing = true)
                        } else {
                            request
                        }
                    }
                )
            }

            try {
                println("[TRUST-REJECT] → Calling contactRepository.rejectTrustRequest(block=$block)...")
                val result = contactRepository.rejectTrustRequest(accountId, from)
                if (result.isFailure) {
                    println("[TRUST-REJECT] ✗ Reject failed: ${result.exceptionOrNull()?.message}")
                    _state.update {
                        it.copy(error = result.exceptionOrNull()?.message ?: "Failed to reject request")
                    }
                    // Unmark as processing on failure
                    _state.update { state ->
                        state.copy(
                            requests = state.requests.map { request ->
                                if (request.from == from) {
                                    request.copy(isProcessing = false)
                                } else {
                                    request
                                }
                            }
                        )
                    }
                } else {
                    println("[TRUST-REJECT] ✓ Request rejected successfully")
                    if (block) {
                        println("[TRUST-REJECT]   User blocked: $from")
                    }
                }
                // On success, the request will be removed from the list via the flow update
            } catch (e: Exception) {
                println("[TRUST-REJECT] ✗ Exception during reject: ${e.message}")
                _state.update {
                    it.copy(error = e.message ?: "Failed to reject request")
                }
            }
        }
    }

    fun refresh() {
        val accountId = accountRepository.currentAccountId.value ?: return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                contactRepository.refreshTrustRequests(accountId)
                // Force wait for flow emission to complete
                kotlinx.coroutines.delay(100)
            } finally {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    private fun TrustRequest.toUiItem(): TrustRequestUiItem {
        // Try to extract display name from payload (VCard data)
        val displayName = try {
            // VCard payload is in bytes, try to decode and extract display name
            // For now, just use first 8 chars of the from URI
            from.take(8)
        } catch (e: Exception) {
            from.take(8)
        }

        return TrustRequestUiItem(
            from = from,
            conversationId = conversationId,
            displayName = displayName,
            received = received,
            isProcessing = false
        )
    }
}
