package com.gettogether.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gettogether.app.data.repository.AccountRepository
import com.gettogether.app.data.repository.ConversationRepositoryImpl
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
import kotlinx.datetime.Clock

class ChatViewModel(
    private val jamiBridge: JamiBridge,
    private val accountRepository: AccountRepository,
    private val conversationRepository: ConversationRepositoryImpl
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
        println("ChatViewModel.loadConversation: conversationId=$conversationId")
        viewModelScope.launch {
            _state.update { it.copy(conversationId = conversationId) }

            try {
                val accountId = accountRepository.currentAccountId.value
                val userJamiId = accountRepository.accountState.value.jamiId
                println("ChatViewModel.loadConversation: accountId=$accountId, userJamiId=$userJamiId")

                if (accountId != null) {
                    // Load conversation info
                    val convInfo = jamiBridge.getConversationInfo(accountId, conversationId)
                    val contactName = convInfo["title"] ?: "Conversation"
                    println("ChatViewModel.loadConversation: contactName=$contactName, convInfo=$convInfo")

                    // Subscribe to messages from ConversationRepository (includes persisted messages)
                    viewModelScope.launch {
                        conversationRepository.getMessages(accountId, conversationId).collect { messages ->
                            println("ChatViewModel: Received ${messages.size} messages from repository")
                            val chatMessages = messages.map { msg ->
                                ChatMessage(
                                    id = msg.id,
                                    content = msg.content,
                                    timestamp = formatTimestamp(msg.timestamp.toEpochMilliseconds()),
                                    isFromMe = msg.authorId == accountId || msg.authorId == userJamiId,
                                    status = MessageStatus.Sent
                                )
                            }
                            _state.update { it.copy(messages = chatMessages) }
                            println("ChatViewModel: Updated state with ${chatMessages.size} messages")
                        }
                    }

                    // Request messages to be loaded (results come via conversationEvents)
                    jamiBridge.loadConversationMessages(accountId, conversationId, "", 50)
                    println("ChatViewModel.loadConversation: loadConversationMessages called")

                    _state.update {
                        it.copy(
                            contactName = contactName,
                            userJamiId = userJamiId
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
        if (!currentState.canSend) {
            println("ChatViewModel.sendMessage: canSend=false, skipping")
            return
        }

        val messageContent = currentState.messageInput.trim()
        println("ChatViewModel.sendMessage: content='$messageContent', conversationId=${currentState.conversationId}")

        viewModelScope.launch {
            // Clear input and set sending state
            _state.update {
                it.copy(
                    messageInput = "",
                    isSending = true
                )
            }
            println("ChatViewModel.sendMessage: Cleared input, waiting for message callback")

            try {
                val accountId = accountRepository.currentAccountId.value
                println("ChatViewModel.sendMessage: accountId=$accountId")
                if (accountId != null) {
                    jamiBridge.sendMessage(
                        accountId,
                        currentState.conversationId,
                        messageContent,
                        null
                    )
                    println("ChatViewModel.sendMessage: jamiBridge.sendMessage called")
                }

                // Message will appear via swarmMessageReceived callback
                _state.update { state ->
                    state.copy(isSending = false)
                }
            } catch (e: Exception) {
                _state.update { state ->
                    state.copy(
                        isSending = false,
                        error = e.message ?: "Failed to send message"
                    )
                }
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun clearMessages() {
        println("ChatViewModel.clearMessages: Clearing all messages from UI")
        _state.update { it.copy(messages = emptyList()) }
    }

    fun deleteConversation() {
        viewModelScope.launch {
            try {
                val accountId = accountRepository.currentAccountId.value
                val conversationId = _state.value.conversationId
                if (accountId != null && conversationId.isNotEmpty()) {
                    println("ChatViewModel.deleteConversation: Deleting conversation - accountId=$accountId, conversationId=$conversationId")
                    jamiBridge.removeConversation(accountId, conversationId)
                    println("ChatViewModel.deleteConversation: Conversation deleted successfully")
                }
            } catch (e: Exception) {
                println("ChatViewModel.deleteConversation: Error - ${e.message}")
                _state.update { it.copy(error = "Failed to delete conversation: ${e.message}") }
            }
        }
    }

    private fun handleConversationEvent(event: JamiConversationEvent) {
        when (event) {
            is JamiConversationEvent.MessageReceived -> {
                println("ChatViewModel.handleConversationEvent: MessageReceived - conversationId=${event.conversationId}, currentConversationId=${_state.value.conversationId}, match=${event.conversationId == _state.value.conversationId}")
                if (event.conversationId == _state.value.conversationId) {
                    // Get userJamiId from accountRepository instead of state (might not be set yet)
                    val userJamiId = accountRepository.accountState.value.jamiId
                    val messageBody = event.message.body["body"] ?: ""

                    // Check if message already exists (to avoid duplicates from optimistic UI)
                    val messageExists = _state.value.messages.any { it.id == event.message.id }
                    if (messageExists) {
                        println("ChatViewModel.handleConversationEvent: Message already exists, skipping duplicate")
                        return
                    }

                    println("ChatViewModel.handleConversationEvent: Adding message to UI - content='$messageBody', author=${event.message.author}, userJamiId=$userJamiId, isFromMe=${event.message.author == userJamiId}")
                    val newMessage = ChatMessage(
                        id = event.message.id,
                        content = messageBody,
                        timestamp = formatTimestamp(event.message.timestamp),
                        isFromMe = event.message.author == userJamiId,
                        status = MessageStatus.Sent
                    )
                    _state.update { it.copy(messages = it.messages + newMessage) }
                    println("ChatViewModel.handleConversationEvent: Message added, total messages=${_state.value.messages.size}")
                } else {
                    println("ChatViewModel.handleConversationEvent: Message ignored - not for current conversation")
                }
            }
            is JamiConversationEvent.MessagesLoaded -> {
                println("ChatViewModel.handleConversationEvent: MessagesLoaded - conversationId=${event.conversationId}, currentConversationId=${_state.value.conversationId}, messageCount=${event.messages.size}")
                if (event.conversationId == _state.value.conversationId) {
                    // Get userJamiId from accountRepository instead of state
                    val userJamiId = accountRepository.accountState.value.jamiId
                    val chatMessages = event.messages.mapNotNull { msg ->
                        val body = msg.body["body"] ?: return@mapNotNull null
                        ChatMessage(
                            id = msg.id,
                            content = body,
                            timestamp = formatTimestamp(msg.timestamp),
                            isFromMe = msg.author == userJamiId,
                            status = MessageStatus.Sent
                        )
                    }
                    _state.update { it.copy(messages = chatMessages) }
                    println("ChatViewModel.handleConversationEvent: Messages loaded, total=${chatMessages.size}")
                } else {
                    println("ChatViewModel.handleConversationEvent: MessagesLoaded ignored - not for current conversation")
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
