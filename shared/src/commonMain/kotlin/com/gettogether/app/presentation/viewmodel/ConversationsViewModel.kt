package com.gettogether.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gettogether.app.data.repository.AccountRepository
import com.gettogether.app.data.repository.ConversationRepositoryImpl
import com.gettogether.app.domain.model.Conversation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ConversationsState(
    val conversations: List<ConversationUiItem> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val hasAccount: Boolean = false
)

data class ConversationUiItem(
    val id: String,
    val name: String,
    val lastMessage: String,
    val time: String,
    val unreadCount: Int,
    val avatarInitial: String
)

class ConversationsViewModel(
    private val conversationRepository: ConversationRepositoryImpl,
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ConversationsState())
    val state: StateFlow<ConversationsState> = _state.asStateFlow()

    init {
        // Observe account state
        viewModelScope.launch {
            accountRepository.currentAccountId.collect { accountId ->
                if (accountId != null) {
                    _state.update { it.copy(hasAccount = true) }
                    loadConversations(accountId)
                } else {
                    _state.update {
                        it.copy(
                            hasAccount = false,
                            conversations = emptyList(),
                            isLoading = false
                        )
                    }
                }
            }
        }
    }

    private fun loadConversations(accountId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            try {
                conversationRepository.getConversations(accountId).collect { conversations ->
                    val uiItems = conversations.map { conv ->
                        conv.toUiItem()
                    }.sortedByDescending { it.time }

                    _state.update {
                        it.copy(
                            conversations = uiItems,
                            isLoading = false
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load conversations"
                    )
                }
            }
        }
    }

    fun refresh() {
        val accountId = accountRepository.currentAccountId.value ?: return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            conversationRepository.refreshConversations(accountId)
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    private fun Conversation.toUiItem(): ConversationUiItem {
        val lastMessageText = lastMessage?.content ?: "No messages yet"
        val timeText = lastMessage?.timestamp?.let { formatTimestamp(it.toEpochMilliseconds()) }
            ?: ""
        val initial = title.firstOrNull()?.uppercase() ?: "?"

        return ConversationUiItem(
            id = id,
            name = title,
            lastMessage = lastMessageText,
            time = timeText,
            unreadCount = unreadCount,
            avatarInitial = initial
        )
    }

    private fun formatTimestamp(timestamp: Long): String {
        if (timestamp == 0L) return ""

        val now = System.currentTimeMillis()
        val diff = now - timestamp

        return when {
            diff < 60_000 -> "Just now"
            diff < 3600_000 -> "${diff / 60_000}m ago"
            diff < 86400_000 -> "${diff / 3600_000}h ago"
            diff < 172800_000 -> "Yesterday"
            else -> {
                val days = diff / 86400_000
                "$days days ago"
            }
        }
    }
}
