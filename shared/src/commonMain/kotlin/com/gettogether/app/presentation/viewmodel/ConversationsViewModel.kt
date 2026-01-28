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
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

data class ConversationsState(
    val conversations: List<ConversationUiItem> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val hasAccount: Boolean = false,
    val conversationToDelete: ConversationUiItem? = null,
    val showDeleteDialog: Boolean = false
)

data class ConversationUiItem(
    val id: String,
    val name: String,
    val lastMessage: String,
    val time: String,
    val unreadCount: Int,
    val avatarInitial: String,
    val avatarUri: String?,
    val isGroup: Boolean = false,
    val isOnline: Boolean = false
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
            try {
                conversationRepository.refreshConversations(accountId)
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

    fun clearAllConversations() {
        val accountId = accountRepository.currentAccountId.value ?: return
        viewModelScope.launch {
            println("ConversationsViewModel.clearAllConversations: Clearing all conversations for account $accountId")
            conversationRepository.clearAllConversations(accountId)
            _state.update { it.copy(conversations = emptyList()) }
        }
    }

    fun showDeleteDialog(conversation: ConversationUiItem) {
        _state.update {
            it.copy(
                conversationToDelete = conversation,
                showDeleteDialog = true
            )
        }
    }

    fun hideDeleteDialog() {
        _state.update {
            it.copy(
                conversationToDelete = null,
                showDeleteDialog = false
            )
        }
    }

    fun deleteConversation() {
        val accountId = accountRepository.currentAccountId.value ?: return
        val conversation = _state.value.conversationToDelete ?: return

        viewModelScope.launch {
            println("ConversationsViewModel.deleteConversation: Deleting conversation ${conversation.id}")
            conversationRepository.deleteConversation(accountId, conversation.id)
            hideDeleteDialog()
        }
    }

    private fun Conversation.toUiItem(): ConversationUiItem {
        val lastMessageText = lastMessage?.content ?: "No messages yet"
        val timeText = lastMessage?.timestamp?.let { formatTimestamp(it.toEpochMilliseconds()) }
            ?: ""
        val initial = title.firstOrNull()?.uppercase() ?: "?"

        // For one-to-one conversations, use the OTHER participant's avatar (not self)
        // For group conversations, avatarUri will be null (use initial fallback)
        val userJamiId = accountRepository.accountState.value.jamiId
        val otherParticipant = if (!isGroup && participants.isNotEmpty()) {
            // Find the other participant (not the current user)
            participants.firstOrNull { it.uri != userJamiId }
        } else {
            null
        }
        val conversationAvatar = otherParticipant?.avatarUri
        val isContactOnline = otherParticipant?.isOnline ?: false

        return ConversationUiItem(
            id = id,
            name = title,
            lastMessage = lastMessageText,
            time = timeText,
            unreadCount = unreadCount,
            avatarInitial = initial,
            avatarUri = conversationAvatar,
            isGroup = isGroup,
            isOnline = isContactOnline
        )
    }

    private fun formatTimestamp(timestamp: Long): String {
        if (timestamp == 0L) return ""

        val instant = kotlin.time.Instant.fromEpochMilliseconds(timestamp)
        val now = Clock.System.now()
        val tz = TimeZone.currentSystemDefault()
        val nowDateTime = now.toLocalDateTime(tz)
        val msgDateTime = instant.toLocalDateTime(tz)
        val today = nowDateTime.date
        val msgDate = msgDateTime.date

        // Less than 1 minute ago
        val diffMs = now.toEpochMilliseconds() - timestamp
        if (diffMs < 60_000) return "Just now"

        return when {
            msgDate == today -> {
                // Today: show time "10:30"
                "${msgDateTime.hour.toString().padStart(2, '0')}:${msgDateTime.minute.toString().padStart(2, '0')}"
            }
            msgDate.toEpochDays() == today.toEpochDays() - 1 -> "Yesterday"
            diffMs < 7 * 24 * 60 * 60 * 1000L -> {
                // This week: show short day name "Mon"
                msgDateTime.dayOfWeek.name.take(3).lowercase()
                    .replaceFirstChar { it.uppercase() }
            }
            msgDateTime.year == nowDateTime.year -> {
                // Same year: "Jan 15"
                val month = msgDateTime.month.name.take(3).lowercase()
                    .replaceFirstChar { it.uppercase() }
                "$month ${msgDate.day}"
            }
            else -> {
                // Different year: "Jan 15, 2024"
                val month = msgDateTime.month.name.take(3).lowercase()
                    .replaceFirstChar { it.uppercase() }
                "$month ${msgDate.day}, ${msgDateTime.year}"
            }
        }
    }
}
