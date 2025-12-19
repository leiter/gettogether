package com.gettogether.app.data.repository

import com.gettogether.app.domain.model.Contact
import com.gettogether.app.domain.model.Conversation
import com.gettogether.app.domain.model.Message
import com.gettogether.app.domain.model.MessageStatus
import com.gettogether.app.domain.model.MessageType
import com.gettogether.app.domain.repository.ConversationRepository
import com.gettogether.app.jami.JamiBridge
import com.gettogether.app.jami.JamiConversationEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Implementation of ConversationRepository using JamiBridge.
 */
class ConversationRepositoryImpl(
    private val jamiBridge: JamiBridge,
    private val accountRepository: AccountRepository
) : ConversationRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Cache for conversations by account
    private val _conversationsCache = MutableStateFlow<Map<String, List<Conversation>>>(emptyMap())

    // Cache for messages by conversation
    private val _messagesCache = MutableStateFlow<Map<String, List<Message>>>(emptyMap())

    init {
        // Listen for conversation events
        scope.launch {
            jamiBridge.conversationEvents.collect { event ->
                handleConversationEvent(event)
            }
        }

        // Load conversations when account changes
        scope.launch {
            accountRepository.currentAccountId.collect { accountId ->
                if (accountId != null) {
                    refreshConversations(accountId)
                }
            }
        }
    }

    override fun getConversations(accountId: String): Flow<List<Conversation>> {
        // Trigger refresh if not cached
        scope.launch {
            if (_conversationsCache.value[accountId].isNullOrEmpty()) {
                refreshConversations(accountId)
            }
        }
        return _conversationsCache.map { cache ->
            cache[accountId] ?: emptyList()
        }
    }

    override fun getConversationById(accountId: String, conversationId: String): Flow<Conversation?> {
        return getConversations(accountId).map { conversations ->
            conversations.find { it.id == conversationId }
        }
    }

    override fun getMessages(accountId: String, conversationId: String): Flow<List<Message>> {
        // Trigger load if not cached
        scope.launch {
            val key = "$accountId:$conversationId"
            if (_messagesCache.value[key].isNullOrEmpty()) {
                loadMessages(accountId, conversationId)
            }
        }
        return _messagesCache.map { cache ->
            cache["$accountId:$conversationId"] ?: emptyList()
        }
    }

    override suspend fun sendMessage(
        accountId: String,
        conversationId: String,
        content: String
    ): Result<Message> {
        return try {
            jamiBridge.sendMessage(accountId, conversationId, content, null)

            val message = Message(
                id = Clock.System.now().toEpochMilliseconds().toString(),
                conversationId = conversationId,
                authorId = accountId,
                content = content,
                timestamp = Clock.System.now(),
                status = MessageStatus.SENT,
                type = MessageType.TEXT
            )

            // Add to cache
            val key = "$accountId:$conversationId"
            val currentMessages = _messagesCache.value[key] ?: emptyList()
            _messagesCache.value = _messagesCache.value + (key to (currentMessages + message))

            Result.success(message)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun createGroupConversation(
        accountId: String,
        title: String,
        participantIds: List<String>
    ): Result<Conversation> {
        return try {
            val conversationId = jamiBridge.startConversation(accountId)

            // Update conversation info with title
            if (title.isNotBlank()) {
                jamiBridge.updateConversationInfo(accountId, conversationId, mapOf("title" to title))
            }

            // Add participants
            participantIds.forEach { participantId ->
                jamiBridge.addConversationMember(accountId, conversationId, participantId)
            }

            val conversation = Conversation(
                id = conversationId,
                accountId = accountId,
                title = title.ifBlank { "New Conversation" },
                participants = emptyList(),
                isGroup = participantIds.size > 1,
                createdAt = Clock.System.now()
            )

            // Add to cache
            val currentConversations = _conversationsCache.value[accountId] ?: emptyList()
            _conversationsCache.value = _conversationsCache.value +
                (accountId to (currentConversations + conversation))

            Result.success(conversation)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun addParticipant(
        accountId: String,
        conversationId: String,
        contactId: String
    ): Result<Unit> {
        return try {
            jamiBridge.addConversationMember(accountId, conversationId, contactId)
            refreshConversations(accountId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun removeParticipant(
        accountId: String,
        conversationId: String,
        contactId: String
    ): Result<Unit> {
        return try {
            jamiBridge.removeConversationMember(accountId, conversationId, contactId)
            refreshConversations(accountId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun leaveConversation(accountId: String, conversationId: String): Result<Unit> {
        return try {
            jamiBridge.removeConversation(accountId, conversationId)

            // Remove from cache
            val currentConversations = _conversationsCache.value[accountId] ?: emptyList()
            _conversationsCache.value = _conversationsCache.value +
                (accountId to currentConversations.filter { it.id != conversationId })

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Refresh conversations from JamiBridge.
     */
    suspend fun refreshConversations(accountId: String) {
        try {
            val conversationIds = jamiBridge.getConversations(accountId)
            val conversations = conversationIds.map { convId ->
                loadConversation(accountId, convId)
            }
            _conversationsCache.value = _conversationsCache.value + (accountId to conversations)
        } catch (e: Exception) {
            // Keep existing cache on error
        }
    }

    private fun loadConversation(accountId: String, conversationId: String): Conversation {
        val info = jamiBridge.getConversationInfo(accountId, conversationId)
        val members = jamiBridge.getConversationMembers(accountId, conversationId)

        val title = info["title"] ?: members.firstOrNull()?.uri ?: "Conversation"
        val isGroup = members.size > 2

        val participants = members.map { member ->
            Contact(
                id = member.uri,
                uri = member.uri,
                displayName = member.uri.take(8),
                isOnline = false
            )
        }

        return Conversation(
            id = conversationId,
            accountId = accountId,
            title = title,
            participants = participants,
            lastMessage = null,
            unreadCount = 0,
            isGroup = isGroup,
            createdAt = Instant.fromEpochMilliseconds(0)
        )
    }

    private suspend fun loadMessages(accountId: String, conversationId: String) {
        try {
            jamiBridge.loadConversationMessages(accountId, conversationId, "", 50)
            // Messages will arrive via conversationEvents
        } catch (e: Exception) {
            // Handle error
        }
    }

    private fun handleConversationEvent(event: JamiConversationEvent) {
        val accountId = accountRepository.currentAccountId.value ?: return

        when (event) {
            is JamiConversationEvent.MessageReceived -> {
                val key = "$accountId:${event.conversationId}"
                val message = Message(
                    id = event.message.id,
                    conversationId = event.conversationId,
                    authorId = event.message.author,
                    content = event.message.body["body"] ?: "",
                    timestamp = Instant.fromEpochMilliseconds(event.message.timestamp),
                    status = MessageStatus.DELIVERED,
                    type = MessageType.TEXT
                )

                val currentMessages = _messagesCache.value[key] ?: emptyList()
                if (currentMessages.none { it.id == message.id }) {
                    _messagesCache.value = _messagesCache.value + (key to (currentMessages + message))
                }

                // Update conversation's last message
                updateConversationLastMessage(accountId, event.conversationId, message)
            }

            is JamiConversationEvent.MessagesLoaded -> {
                val key = "$accountId:${event.conversationId}"
                val messages = event.messages.mapNotNull { msg ->
                    val body = msg.body["body"] ?: return@mapNotNull null
                    Message(
                        id = msg.id,
                        conversationId = event.conversationId,
                        authorId = msg.author,
                        content = body,
                        timestamp = Instant.fromEpochMilliseconds(msg.timestamp),
                        status = MessageStatus.DELIVERED,
                        type = MessageType.TEXT
                    )
                }
                _messagesCache.value = _messagesCache.value + (key to messages)
            }

            is JamiConversationEvent.ConversationReady -> {
                scope.launch { refreshConversations(accountId) }
            }

            is JamiConversationEvent.ConversationRemoved -> {
                val currentConversations = _conversationsCache.value[accountId] ?: emptyList()
                _conversationsCache.value = _conversationsCache.value +
                    (accountId to currentConversations.filter { it.id != event.conversationId })
            }

            is JamiConversationEvent.ConversationRequestReceived -> {
                // Could notify about new conversation request
            }

            else -> { /* Handle other events */ }
        }
    }

    private fun updateConversationLastMessage(accountId: String, conversationId: String, message: Message) {
        val currentConversations = _conversationsCache.value[accountId] ?: return
        val updatedConversations = currentConversations.map { conv ->
            if (conv.id == conversationId) {
                conv.copy(lastMessage = message)
            } else {
                conv
            }
        }
        _conversationsCache.value = _conversationsCache.value + (accountId to updatedConversations)
    }

    /**
     * Mark a conversation as read.
     */
    suspend fun markAsRead(accountId: String, conversationId: String) {
        try {
            // Get the latest message ID to mark as read
            val key = "$accountId:$conversationId"
            val messages = _messagesCache.value[key]
            val lastMessageId = messages?.lastOrNull()?.id

            if (lastMessageId != null) {
                jamiBridge.setMessageDisplayed(accountId, conversationId, lastMessageId)
            }

            // Update local unread count
            val currentConversations = _conversationsCache.value[accountId] ?: return
            val updatedConversations = currentConversations.map { conv ->
                if (conv.id == conversationId) {
                    conv.copy(unreadCount = 0)
                } else {
                    conv
                }
            }
            _conversationsCache.value = _conversationsCache.value + (accountId to updatedConversations)
        } catch (e: Exception) {
            // Handle error silently
        }
    }
}
