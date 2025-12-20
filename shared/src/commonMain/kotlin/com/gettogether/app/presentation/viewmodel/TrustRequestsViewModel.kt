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
            _state.update { it.copy(isLoading = true, error = null) }

            try {
                contactRepository.getTrustRequests(accountId).collect { trustRequests ->
                    val uiItems = trustRequests.map { it.toUiItem() }
                        .sortedByDescending { it.received }

                    _state.update {
                        it.copy(
                            requests = uiItems,
                            isLoading = false
                        )
                    }
                }
            } catch (e: Exception) {
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
                val result = contactRepository.acceptTrustRequest(accountId, from)
                if (result.isFailure) {
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
                }
                // On success, the request will be removed from the list via the flow update
            } catch (e: Exception) {
                _state.update {
                    it.copy(error = e.message ?: "Failed to accept request")
                }
            }
        }
    }

    fun rejectRequest(from: String, block: Boolean = false) {
        val accountId = accountRepository.currentAccountId.value ?: return

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
                val result = contactRepository.rejectTrustRequest(accountId, from, block)
                if (result.isFailure) {
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
                }
                // On success, the request will be removed from the list via the flow update
            } catch (e: Exception) {
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
            contactRepository.refreshTrustRequests(accountId)
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
