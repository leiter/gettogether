package com.gettogether.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gettogether.app.data.repository.AccountRepository
import com.gettogether.app.data.repository.ConversationRepositoryImpl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ConversationRequestUiItem(
    val conversationId: String,
    val from: String,
    val fromShort: String,
    val isProcessing: Boolean = false
)

data class ConversationRequestsState(
    val requests: List<ConversationRequestUiItem> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

class ConversationRequestsViewModel(
    private val conversationRepository: ConversationRepositoryImpl,
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ConversationRequestsState())
    val state: StateFlow<ConversationRequestsState> = _state.asStateFlow()

    init {
        // Observe conversation requests reactively
        viewModelScope.launch {
            accountRepository.currentAccountId.collect { accountId ->
                if (accountId != null) {
                    observeConversationRequests(accountId)
                } else {
                    _state.update {
                        it.copy(
                            requests = emptyList(),
                            isLoading = false
                        )
                    }
                }
            }
        }
    }

    private fun observeConversationRequests(accountId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            try {
                conversationRepository.getConversationRequests(accountId).collect { requests ->
                    val uiItems = requests.map { request ->
                        ConversationRequestUiItem(
                            conversationId = request.conversationId,
                            from = request.from,
                            fromShort = request.from.take(8)
                        )
                    }

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
                        error = e.message ?: "Failed to load conversation requests"
                    )
                }
            }
        }
    }

    fun acceptRequest(conversationId: String) {
        val accountId = accountRepository.currentAccountId.value ?: return

        viewModelScope.launch {
            // Mark as processing
            _state.update { state ->
                state.copy(
                    requests = state.requests.map { request ->
                        if (request.conversationId == conversationId) {
                            request.copy(isProcessing = true)
                        } else {
                            request
                        }
                    }
                )
            }

            try {
                conversationRepository.acceptConversationRequest(accountId, conversationId)

                // Remove from list after accepting
                _state.update { state ->
                    state.copy(
                        requests = state.requests.filter { it.conversationId != conversationId }
                    )
                }
            } catch (e: Exception) {
                // Unmark processing on error
                _state.update { state ->
                    state.copy(
                        requests = state.requests.map { request ->
                            if (request.conversationId == conversationId) {
                                request.copy(isProcessing = false)
                            } else {
                                request
                            }
                        },
                        error = e.message ?: "Failed to accept request"
                    )
                }
            }
        }
    }

    fun declineRequest(conversationId: String) {
        val accountId = accountRepository.currentAccountId.value ?: return

        viewModelScope.launch {
            // Mark as processing
            _state.update { state ->
                state.copy(
                    requests = state.requests.map { request ->
                        if (request.conversationId == conversationId) {
                            request.copy(isProcessing = true)
                        } else {
                            request
                        }
                    }
                )
            }

            try {
                conversationRepository.declineConversationRequest(accountId, conversationId)

                // Remove from list after declining
                _state.update { state ->
                    state.copy(
                        requests = state.requests.filter { it.conversationId != conversationId }
                    )
                }
            } catch (e: Exception) {
                // Unmark processing on error
                _state.update { state ->
                    state.copy(
                        requests = state.requests.map { request ->
                            if (request.conversationId == conversationId) {
                                request.copy(isProcessing = false)
                            } else {
                                request
                            }
                        },
                        error = e.message ?: "Failed to decline request"
                    )
                }
            }
        }
    }

    fun refresh() {
        val accountId = accountRepository.currentAccountId.value ?: return
        viewModelScope.launch {
            conversationRepository.refreshConversationRequests(accountId)
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
