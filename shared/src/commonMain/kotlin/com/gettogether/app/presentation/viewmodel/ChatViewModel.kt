package com.gettogether.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gettogether.app.data.repository.AccountRepository
import com.gettogether.app.jami.JamiBridge
import com.gettogether.app.jami.JamiConversationEvent
import com.gettogether.app.presentation.state.ChatMessage
import com.gettogether.app.presentation.state.ChatState
import com.gettogether.app.presentation.state.MessageStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock

class ChatViewModel(
    private val jamiBridge: JamiBridge,
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    init {
        // Listen to conversation events
        viewModelScope.launch {
            jamiBridge.conversationEvents.collect { event ->
                handleConversationEvent(event)
            }
        }
    }

    fun loadConversation(conversationId: String) {
        viewModelScope.launch {
            _state.update { it.copy(conversationId = conversationId) }

            try {
                val accountId = accountRepository.currentAccountId.value

                if (accountId != null) {
                    // Load conversation info
                    val convInfo = jamiBridge.getConversationInfo(accountId, conversationId)
                    val contactName = convInfo["title"] ?: "Conversation"

                    // Request messages to be loaded (results come via conversationEvents)
                    jamiBridge.loadConversationMessages(accountId, conversationId, "", 50)

                    _state.update {
                        it.copy(
                            contactName = contactName
                        )
                    }
                } else {
                    // Fallback to demo data if no account
                    val (contactName, messages) = getDemoConversation(conversationId)
                    _state.update {
                        it.copy(
                            contactName = contactName,
                            messages = messages
                        )
                    }
                }
            } catch (e: Exception) {
                // Fallback to demo data on error
                val (contactName, messages) = getDemoConversation(conversationId)
                _state.update {
                    it.copy(
                        contactName = contactName,
                        messages = messages
                    )
                }
            }
        }
    }

    fun onMessageInputChanged(input: String) {
        _state.update { it.copy(messageInput = input, error = null) }
    }

    fun sendMessage() {
        val currentState = _state.value
        if (!currentState.canSend) return

        val messageContent = currentState.messageInput.trim()
        val newMessage = ChatMessage(
            id = Clock.System.now().toEpochMilliseconds().toString(),
            content = messageContent,
            timestamp = "Just now",
            isFromMe = true,
            status = MessageStatus.Sending
        )

        viewModelScope.launch {
            _state.update {
                it.copy(
                    messages = it.messages + newMessage,
                    messageInput = "",
                    isSending = true
                )
            }

            try {
                val accountId = accountRepository.currentAccountId.value
                if (accountId != null) {
                    jamiBridge.sendMessage(
                        accountId,
                        currentState.conversationId,
                        messageContent,
                        null
                    )
                }

                // Update message status to sent
                _state.update { state ->
                    state.copy(
                        isSending = false,
                        messages = state.messages.map { msg ->
                            if (msg.id == newMessage.id) {
                                msg.copy(status = MessageStatus.Sent)
                            } else {
                                msg
                            }
                        }
                    )
                }
            } catch (e: Exception) {
                _state.update { state ->
                    state.copy(
                        isSending = false,
                        error = e.message ?: "Failed to send message",
                        messages = state.messages.map { msg ->
                            if (msg.id == newMessage.id) {
                                msg.copy(status = MessageStatus.Failed)
                            } else {
                                msg
                            }
                        }
                    )
                }
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    private fun handleConversationEvent(event: JamiConversationEvent) {
        when (event) {
            is JamiConversationEvent.MessageReceived -> {
                if (event.conversationId == _state.value.conversationId) {
                    val accountId = accountRepository.currentAccountId.value
                    val messageBody = event.message.body["body"] ?: ""
                    val newMessage = ChatMessage(
                        id = event.message.id,
                        content = messageBody,
                        timestamp = formatTimestamp(event.message.timestamp),
                        isFromMe = event.message.author == accountId,
                        status = MessageStatus.Sent
                    )
                    _state.update { it.copy(messages = it.messages + newMessage) }
                }
            }
            is JamiConversationEvent.MessagesLoaded -> {
                if (event.conversationId == _state.value.conversationId) {
                    val accountId = accountRepository.currentAccountId.value
                    val chatMessages = event.messages.mapNotNull { msg ->
                        val body = msg.body["body"] ?: return@mapNotNull null
                        ChatMessage(
                            id = msg.id,
                            content = body,
                            timestamp = formatTimestamp(msg.timestamp),
                            isFromMe = msg.author == accountId,
                            status = MessageStatus.Sent
                        )
                    }
                    _state.update { it.copy(messages = chatMessages) }
                }
            }
            else -> { /* Handle other events */ }
        }
    }

    private fun formatTimestamp(timestamp: Long): String {
        // Simple timestamp formatting - could be enhanced
        val now = Clock.System.now().toEpochMilliseconds()
        val diff = now - timestamp
        return when {
            diff < 60_000 -> "Just now"
            diff < 3600_000 -> "${diff / 60_000}m ago"
            diff < 86400_000 -> "${diff / 3600_000}h ago"
            else -> "Yesterday"
        }
    }

    private fun getDemoConversation(conversationId: String): Pair<String, List<ChatMessage>> {
        return when (conversationId) {
            "1" -> "Alice" to listOf(
                ChatMessage("1", "Hey! How are you?", "10:25 AM", isFromMe = false),
                ChatMessage("2", "I'm good, thanks! How about you?", "10:26 AM", isFromMe = true),
                ChatMessage("3", "Doing great! Want to grab coffee later?", "10:28 AM", isFromMe = false),
                ChatMessage("4", "Sure, sounds good!", "10:29 AM", isFromMe = true),
                ChatMessage("5", "Hey, how are you?", "10:30 AM", isFromMe = false)
            )
            "2" -> "Bob" to listOf(
                ChatMessage("1", "Did you finish the project?", "Yesterday", isFromMe = false),
                ChatMessage("2", "Almost done, just need to review", "Yesterday", isFromMe = true),
                ChatMessage("3", "Great! Let me know when ready", "Yesterday", isFromMe = false),
                ChatMessage("4", "See you tomorrow!", "Yesterday", isFromMe = true)
            )
            "3" -> "Team Chat" to listOf(
                ChatMessage("1", "Team meeting at 3pm", "Monday", isFromMe = false),
                ChatMessage("2", "I'll be there", "Monday", isFromMe = true),
                ChatMessage("3", "Me too!", "Monday", isFromMe = false),
                ChatMessage("4", "Don't forget to bring the reports", "Monday", isFromMe = false),
                ChatMessage("5", "Meeting at 3pm", "Monday", isFromMe = false)
            )
            else -> "Unknown" to emptyList()
        }
    }
}
