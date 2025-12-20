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
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Implementation of ConversationRepository using JamiBridge.
 */
class ConversationRepositoryImpl(
    private val jamiBridge: JamiBridge,
    private val accountRepository: AccountRepository,
    private val conversationPersistence: com.gettogether.app.data.persistence.ConversationPersistence
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
                    // Load persisted conversations first
                    loadPersistedConversations(accountId)
                    // Then refresh from Jami
                    refreshConversations(accountId)
                }
            }
        }

        // Auto-save conversations when cache changes
        scope.launch {
            _conversationsCache.collect { conversationsMap ->
                println("ConversationRepository: Auto-save conversations triggered (${conversationsMap.size} accounts)")
                conversationsMap.forEach { (accountId, conversations) ->
                    println("  → Saving ${conversations.size} conversations for account $accountId")
                    try {
                        conversationPersistence.saveConversations(accountId, conversations)
                        println("  ✓ Saved conversations for account $accountId")
                    } catch (e: Exception) {
                        println("  ✗ Failed to save conversations: ${e.message}")
                    }
                }
            }
        }

        // Auto-save messages when cache changes
        scope.launch {
            _messagesCache.collect { messagesMap ->
                println("ConversationRepository: Auto-save messages triggered (${messagesMap.size} conversations)")
                if (messagesMap.isEmpty()) {
                    println("ConversationRepository: No messages to save (cache is empty)")
                }
                messagesMap.forEach { (key, messages) ->
                    println("ConversationRepository: Processing key='$key', messages=${messages.size}")
                    val parts = key.split(":")
                    if (parts.size == 2) {
                        val accountId = parts[0]
                        val conversationId = parts[1]
                        if (messages.isNotEmpty()) {
                            println("  → Saving ${messages.size} messages for conversation $conversationId")
                            try {
                                conversationPersistence.saveMessages(accountId, conversationId, messages)
                                println("  ✓ Saved messages for conversation $conversationId")
                            } catch (e: Exception) {
                                println("  ✗ Failed to save messages: ${e.message}")
                                e.printStackTrace()
                            }
                        } else {
                            println("  → Conversation $conversationId has 0 messages, skipping save")
                        }
                    } else {
                        println("  ✗ Invalid key format: '$key' (expected 'accountId:conversationId')")
                    }
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
                // Load persisted messages first
                loadPersistedMessages(accountId, conversationId)
                // Then load from Jami
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
            println("ConversationRepository.sendMessage: accountId=$accountId, conversationId=$conversationId, content='$content'")
            jamiBridge.sendMessage(accountId, conversationId, content, null)
            println("ConversationRepository.sendMessage: jamiBridge.sendMessage completed")

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
            println("ConversationRepository.sendMessage: Message added to cache, total messages=${(currentMessages + message).size}")

            Result.success(message)
        } catch (e: Exception) {
            println("ConversationRepository.sendMessage: Exception - ${e.message}")
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

            // Also load persisted messages for all conversations
            println("ConversationRepository: Loading persisted messages for ${conversationIds.size} conversations")
            conversationIds.forEach { convId ->
                loadPersistedMessages(accountId, convId)
            }

            // Update conversations with lastMessage from loaded messages
            val updatedConversations = conversations.map { conversation ->
                val key = "$accountId:${conversation.id}"
                val messages = _messagesCache.value[key] ?: emptyList()
                if (messages.isNotEmpty()) {
                    val lastMsg = messages.last()
                    println("ConversationRepository: Updating conversation ${conversation.id.take(16)}... with lastMessage: '${lastMsg.content}'")
                    conversation.copy(lastMessage = lastMsg)
                } else {
                    conversation
                }
            }
            _conversationsCache.value = _conversationsCache.value + (accountId to updatedConversations)
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
        val accountId = accountRepository.currentAccountId.value
        if (accountId == null) {
            println("ConversationRepository.handleConversationEvent: No current account ID")
            return
        }

        when (event) {
            is JamiConversationEvent.MessageReceived -> {
                println("ConversationRepository.handleConversationEvent: MessageReceived - accountId=$accountId, conversationId=${event.conversationId}, messageId=${event.message.id}, author=${event.message.author}, content=${event.message.body["body"]}")
                val key = "$accountId:${event.conversationId}"
                // Jami timestamps are in seconds, convert to milliseconds
                val timestampMillis = if (event.message.timestamp < 100000000000) {
                    event.message.timestamp * 1000  // Convert seconds to milliseconds
                } else {
                    event.message.timestamp  // Already in milliseconds
                }
                val message = Message(
                    id = event.message.id,
                    conversationId = event.conversationId,
                    authorId = event.message.author,
                    content = event.message.body["body"] ?: "",
                    timestamp = Instant.fromEpochMilliseconds(timestampMillis),
                    status = MessageStatus.DELIVERED,
                    type = MessageType.TEXT
                )

                val currentMessages = _messagesCache.value[key] ?: emptyList()
                if (currentMessages.none { it.id == message.id }) {
                    _messagesCache.value = _messagesCache.value + (key to (currentMessages + message))
                    println("ConversationRepository.handleConversationEvent: Message added to cache, key=$key, total messages=${(currentMessages + message).size}")
                } else {
                    println("ConversationRepository.handleConversationEvent: Message already in cache, skipping")
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
     * Clear all messages from a conversation.
     */
    suspend fun clearMessages(accountId: String, conversationId: String) {
        println("ConversationRepository.clearMessages: accountId=$accountId, conversationId=$conversationId")
        val key = "$accountId:$conversationId"
        _messagesCache.value = _messagesCache.value - key
        println("ConversationRepository.clearMessages: Messages cleared from cache")
    }

    /**
     * Clear all conversations for an account.
     */
    suspend fun clearAllConversations(accountId: String) {
        println("ConversationRepository.clearAllConversations: Clearing all conversations for account $accountId")

        // Get user's own Jami ID for logging purposes
        val userJamiId = accountRepository.accountState.value.jamiId
        println("ConversationRepository.clearAllConversations: User Jami ID: $userJamiId")

        // Get current conversations to delete them from daemon
        val conversations = _conversationsCache.value[accountId] ?: emptyList()
        println("ConversationRepository.clearAllConversations: Found ${conversations.size} conversations to delete")

        // First, remove all contacts to prevent conversations from being recreated
        val contactsToRemove = mutableSetOf<String>()
        conversations.forEach { conversation ->
            conversation.participants.forEach { participant ->
                // Don't try to remove/ban the user themselves
                if (participant.uri != userJamiId) {
                    contactsToRemove.add(participant.uri)
                }
            }
        }

        println("ConversationRepository.clearAllConversations: Removing ${contactsToRemove.size} contacts")
        contactsToRemove.forEach { contactUri ->
            try {
                println("ConversationRepository.clearAllConversations: Removing contact $contactUri")
                jamiBridge.removeContact(accountId, contactUri, ban = false)
            } catch (e: Exception) {
                println("ConversationRepository.clearAllConversations: Failed to remove contact $contactUri: ${e.message}")
            }
        }

        // Then delete ALL conversations from Jami daemon (including self-conversations)
        conversations.forEach { conversation ->
            try {
                println("ConversationRepository.clearAllConversations: Deleting conversation ${conversation.id}")
                jamiBridge.removeConversation(accountId, conversation.id)
            } catch (e: Exception) {
                println("ConversationRepository.clearAllConversations: Failed to delete conversation ${conversation.id}: ${e.message}")
            }
        }

        // Clear ALL conversations from cache
        _conversationsCache.value = _conversationsCache.value - accountId

        // Clear ALL messages for this account's conversations
        _messagesCache.value = _messagesCache.value.filterKeys { !it.startsWith("$accountId:") }

        // Clear from persistence
        try {
            conversationPersistence.clearConversations(accountId)
            println("ConversationRepository.clearAllConversations: Cleared conversations from persistence")
        } catch (e: Exception) {
            println("ConversationRepository.clearAllConversations: Failed to clear persistence: ${e.message}")
        }

        println("ConversationRepository.clearAllConversations: All conversations, messages, and contacts cleared from cache, daemon, and persistence")
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

    /**
     * Load persisted conversations from storage.
     */
    private suspend fun loadPersistedConversations(accountId: String) {
        println("ConversationRepository: loadPersistedConversations() for account: $accountId")
        try {
            val persistedConversations = conversationPersistence.loadConversations(accountId)
            println("ConversationRepository: ✓ Loaded ${persistedConversations.size} persisted conversations")
            if (persistedConversations.isNotEmpty()) {
                persistedConversations.forEach { conversation ->
                    println("  - ${conversation.title} (${conversation.id.take(16)}...)")
                }
                _conversationsCache.value = _conversationsCache.value + (accountId to persistedConversations)
                println("ConversationRepository: ✓ Added persisted conversations to cache")
            } else {
                println("ConversationRepository: No persisted conversations found")
            }
        } catch (e: Exception) {
            println("ConversationRepository: ✗ Failed to load persisted conversations: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Load persisted messages from storage.
     */
    private suspend fun loadPersistedMessages(accountId: String, conversationId: String) {
        println("ConversationRepository: loadPersistedMessages() for conversation: $conversationId")
        try {
            val persistedMessages = conversationPersistence.loadMessages(accountId, conversationId)
            println("ConversationRepository: ✓ Loaded ${persistedMessages.size} persisted messages")
            if (persistedMessages.isNotEmpty()) {
                val key = "$accountId:$conversationId"
                _messagesCache.value = _messagesCache.value + (key to persistedMessages)
                println("ConversationRepository: ✓ Added persisted messages to cache")
            } else {
                println("ConversationRepository: No persisted messages found")
            }
        } catch (e: Exception) {
            println("ConversationRepository: ✗ Failed to load persisted messages: ${e.message}")
            e.printStackTrace()
        }
    }
}
