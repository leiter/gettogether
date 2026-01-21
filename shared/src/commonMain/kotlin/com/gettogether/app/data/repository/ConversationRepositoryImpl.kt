package com.gettogether.app.data.repository

import com.gettogether.app.domain.model.Contact
import com.gettogether.app.domain.model.Conversation
import com.gettogether.app.domain.model.Message
import com.gettogether.app.domain.model.MessageStatus
import com.gettogether.app.domain.model.MessageType
import com.gettogether.app.domain.repository.ConversationRepository
import com.gettogether.app.jami.JamiBridge
import com.gettogether.app.jami.JamiConversationEvent
import com.gettogether.app.jami.MemberEventType
import com.gettogether.app.platform.ImageProcessingResult
import com.gettogether.app.platform.ImageProcessor
import com.gettogether.app.platform.NotificationConstants
import com.gettogether.app.platform.NotificationHelper
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
    private val accountRepository: AccountRepository,
    private val conversationPersistence: com.gettogether.app.data.persistence.ConversationPersistence,
    private val contactRepository: ContactRepositoryImpl,
    private val notificationHelper: NotificationHelper? = null,
    private val settingsRepository: SettingsRepository? = null,
    private val imageProcessor: ImageProcessor? = null
) : ConversationRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Cache for conversations by account
    private val _conversationsCache = MutableStateFlow<Map<String, List<Conversation>>>(emptyMap())

    // Cache for messages by conversation
    private val _messagesCache = MutableStateFlow<Map<String, List<Message>>>(emptyMap())

    // Cache for conversation requests by account
    private val _conversationRequestsCache = MutableStateFlow<Map<String, List<com.gettogether.app.jami.ConversationRequest>>>(emptyMap())

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
                    // TODO: SharedPrefs persistence disabled - might be deleted
                    // Load persisted conversations first
                    // loadPersistedConversations(accountId)
                    // Then refresh from Jami
                    refreshConversations(accountId)
                    // Also refresh conversation requests
                    refreshConversationRequests(accountId)
                }
            }
        }

        // Re-refresh conversations when contacts are updated (to get proper display names/avatars)
        scope.launch {
            contactRepository._contactsCache.collect { contactsMap ->
                val accountId = accountRepository.currentAccountId.value
                if (accountId != null && contactsMap[accountId]?.isNotEmpty() == true) {
                    // Only refresh if we already have conversations cached (avoid initial double-load)
                    if (_conversationsCache.value[accountId]?.isNotEmpty() == true) {
                        println("ConversationRepository: Contacts updated, refreshing conversations for display names/avatars")
                        refreshConversations(accountId)
                    }
                }
            }
        }

        // TODO: SharedPrefs persistence disabled - might be deleted
        // Auto-save conversations when cache changes
        // scope.launch {
        //     _conversationsCache.collect { conversationsMap ->
        //         println("ConversationRepository: Auto-save conversations triggered (${conversationsMap.size} accounts)")
        //         conversationsMap.forEach { (accountId, conversations) ->
        //             println("  → Saving ${conversations.size} conversations for account $accountId")
        //             try {
        //                 conversationPersistence.saveConversations(accountId, conversations)
        //                 println("  ✓ Saved conversations for account $accountId")
        //             } catch (e: Exception) {
        //                 println("  ✗ Failed to save conversations: ${e.message}")
        //             }
        //         }
        //     }
        // }

        // TODO: SharedPrefs persistence disabled - might be deleted
        // Auto-save messages when cache changes
        // scope.launch {
        //     _messagesCache.collect { messagesMap ->
        //         println("ConversationRepository: Auto-save messages triggered (${messagesMap.size} conversations)")
        //         if (messagesMap.isEmpty()) {
        //             println("ConversationRepository: No messages to save (cache is empty)")
        //         }
        //         messagesMap.forEach { (key, messages) ->
        //             println("ConversationRepository: Processing key='$key', messages=${messages.size}")
        //             val parts = key.split(":")
        //             if (parts.size == 2) {
        //                 val accountId = parts[0]
        //                 val conversationId = parts[1]
        //                 if (messages.isNotEmpty()) {
        //                     println("  → Saving ${messages.size} messages for conversation $conversationId")
        //                     try {
        //                         conversationPersistence.saveMessages(accountId, conversationId, messages)
        //                         println("  ✓ Saved messages for conversation $conversationId")
        //                     } catch (e: Exception) {
        //                         println("  ✗ Failed to save messages: ${e.message}")
        //                         e.printStackTrace()
        //                     }
        //                 } else {
        //                     println("  → Conversation $conversationId has 0 messages, skipping save")
        //                 }
        //             } else {
        //                 println("  ✗ Invalid key format: '$key' (expected 'accountId:conversationId')")
        //             }
        //         }
        //     }
        // }
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
                // TODO: SharedPrefs persistence disabled - might be deleted
                // Load persisted messages first
                // loadPersistedMessages(accountId, conversationId)
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
        return sendMessageWithRetry(accountId, conversationId, content)
    }

    /**
     * Send a message with retry logic for ban-related errors.
     *
     * Ban errors are often temporary (e.g., due to stale ban state) and can be
     * resolved by refreshing the contact's ban status and retrying.
     *
     * @param maxRetries Maximum number of retry attempts (default: 3)
     * @param initialDelayMs Initial delay before first retry in milliseconds (default: 1000)
     */
    private suspend fun sendMessageWithRetry(
        accountId: String,
        conversationId: String,
        content: String,
        maxRetries: Int = 3,
        initialDelayMs: Long = 1000
    ): Result<Message> {
        var lastException: Exception? = null
        var currentDelay = initialDelayMs

        for (attempt in 0..maxRetries) {
            try {
                if (attempt > 0) {
                    println("ConversationRepository.sendMessage: Retry attempt $attempt/$maxRetries after ${currentDelay}ms delay")
                }
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

                // Add to cache (sorted by timestamp)
                val key = "$accountId:$conversationId"
                val currentMessages = _messagesCache.value[key] ?: emptyList()
                val updatedMessages = (currentMessages + message).sortedBy { it.timestamp }
                _messagesCache.value = _messagesCache.value + (key to updatedMessages)
                println("ConversationRepository.sendMessage: Message added to cache, total messages=${updatedMessages.size}")

                return Result.success(message)
            } catch (e: Exception) {
                lastException = e
                println("ConversationRepository.sendMessage: Exception - ${e.message}")

                // Only retry for ban-related errors
                if (!BanErrorDetector.isBanRelatedError(e)) {
                    println("ConversationRepository.sendMessage: Non-ban error, not retrying")
                    return Result.failure(e)
                }

                // Don't retry if we've exhausted retries
                if (attempt >= maxRetries) {
                    println("ConversationRepository.sendMessage: Max retries ($maxRetries) reached, giving up")
                    break
                }

                // Attempt to refresh contact ban status before retrying
                println("ConversationRepository.sendMessage: Ban-related error detected, refreshing contact status...")
                refreshContactBanStatus(accountId, conversationId)

                // Exponential backoff delay before retry
                kotlinx.coroutines.delay(currentDelay)
                currentDelay *= 2  // Double delay for next retry
            }
        }

        return Result.failure(lastException ?: Exception("Send message failed after retries"))
    }

    /**
     * Refresh the ban status of contacts in a conversation.
     *
     * This queries the daemon for the current ban status and updates the
     * contact repository cache to ensure we have fresh state.
     */
    private suspend fun refreshContactBanStatus(accountId: String, conversationId: String) {
        try {
            // Get conversation members
            val members = jamiBridge.getConversationMembers(accountId, conversationId)
            println("ConversationRepository: Refreshing ban status for ${members.size} members")

            // Refresh contacts from daemon (this will update ban status)
            contactRepository.refreshContacts(accountId)

            println("ConversationRepository: Contact ban status refreshed")
        } catch (e: Exception) {
            println("ConversationRepository: Failed to refresh contact ban status: ${e.message}")
            // Continue anyway, the retry might still work
        }
    }

    override suspend fun createGroupConversation(
        accountId: String,
        title: String,
        participantIds: List<String>,
        avatarUri: String?
    ): Result<Conversation> {
        return try {
            val conversationId = jamiBridge.startConversation(accountId)

            // Process avatar if provided (compress to avoid PJ_ETOOLONG error - see issue #1071)
            val processedAvatarPath = avatarUri?.let { uri ->
                processAvatarForGroup(uri)
            }

            // Update conversation info with title and optional avatar
            val conversationInfo = mutableMapOf<String, String>()
            if (title.isNotBlank()) {
                conversationInfo["title"] = title
            }
            if (processedAvatarPath != null) {
                conversationInfo["avatar"] = processedAvatarPath
            }
            if (conversationInfo.isNotEmpty()) {
                jamiBridge.updateConversationInfo(accountId, conversationId, conversationInfo)
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
            _conversationsCache.value += (accountId to (currentConversations + conversation))

            Result.success(conversation)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Process an avatar image for group creation.
     *
     * Compresses the avatar to avoid "PJ_ETOOLONG" errors when setting
     * conversation info with large images (issue #1071).
     *
     * @param avatarUri The source avatar URI
     * @return The processed avatar file path, or null if processing fails
     */
    private suspend fun processAvatarForGroup(avatarUri: String): String? {
        if (imageProcessor == null) {
            println("ConversationRepository: ImageProcessor not available, skipping avatar compression")
            return null
        }

        return try {
            println("ConversationRepository: Processing avatar for group: $avatarUri")

            // Compress to 128x128 and target 50KB to ensure it fits in conversation info
            // Group avatars are smaller than profile avatars to reduce sync overhead
            val result = imageProcessor.processImage(
                sourceUri = avatarUri,
                maxSize = 128,      // Smaller than profile avatars
                targetSizeKB = 50   // More aggressive compression
            )

            when (result) {
                is ImageProcessingResult.Success -> {
                    println("ConversationRepository: ✓ Avatar processed: ${result.filePath} (${result.sizeBytes} bytes)")
                    result.filePath
                }
                is ImageProcessingResult.Error -> {
                    println("ConversationRepository: ✗ Avatar processing failed: ${result.message}")
                    null
                }
            }
        } catch (e: Exception) {
            println("ConversationRepository: ✗ Avatar processing exception: ${e.message}")
            null
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
     * Find an existing 1:1 conversation with a specific contact.
     *
     * @param accountId The account ID
     * @param contactUri The contact's Jami URI
     * @return The conversation ID if found, null otherwise
     */
    fun findConversationWithContact(accountId: String, contactUri: String): String? {
        val userJamiId = accountRepository.accountState.value.jamiId

        return _conversationsCache.value[accountId]?.find { conversation ->
            // Check for 1-on-1 conversations (not groups)
            !conversation.isGroup &&
            // Must have exactly 2 participants (user + contact)
            conversation.participants.size == 2 &&
            // Must include the specific contact
            conversation.participants.any { it.uri == contactUri }
        }?.id
    }

    /**
     * Refresh conversations from JamiBridge.
     */
    suspend fun refreshConversations(accountId: String) {
        try {
            // Get user's own Jami ID to filter out self-conversations
            val userJamiId = accountRepository.accountState.value.jamiId

            val conversationIds = jamiBridge.getConversations(accountId)
            val conversations = conversationIds.map { convId ->
                loadConversation(accountId, convId)
            }

            // Filter out self-conversations (where all participants are the user themselves)
            val filteredConversations = conversations.filter { conversation ->
                val hasOtherParticipants = conversation.participants.any { participant ->
                    participant.uri != userJamiId
                }
                hasOtherParticipants
            }

            // TODO: SharedPrefs persistence disabled - might be deleted
            // Load persisted messages for filtered conversations only
            // println("ConversationRepository: Loading persisted messages for ${filteredConversations.size} conversations")
            // filteredConversations.forEach { conversation ->
            //     loadPersistedMessages(accountId, conversation.id)
            // }

            // Update conversations with lastMessage from loaded messages
            // Keep conversations even if they have no messages yet (for new accepted conversations)
            val updatedConversations = filteredConversations.map { conversation ->
                val key = "$accountId:${conversation.id}"
                val messages = _messagesCache.value[key] ?: emptyList()
                if (messages.isNotEmpty()) {
                    val lastMsg = messages.last()
                    conversation.copy(lastMessage = lastMsg)
                } else {
                    // Keep conversation with null lastMessage - UI will show "No messages yet"
                    conversation.copy(lastMessage = null)
                }
            }

            // Deduplicate conversations with the same participants
            // Group by participant URIs and keep only the most recent conversation for each group
            val deduplicatedConversations = updatedConversations
                .groupBy { conversation ->
                    // Create a key from sorted participant URIs (excluding the user)
                    conversation.participants
                        .map { it.uri }
                        .filter { it != userJamiId }
                        .sorted()
                        .joinToString(",")
                }
                .mapNotNull { (_, conversationsGroup) ->
                    // Keep the conversation with the most recent message
                    conversationsGroup.maxByOrNull { conversation ->
                        conversation.lastMessage?.timestamp?.toEpochMilliseconds() ?: 0
                    }
                }

            val duplicatesRemoved = updatedConversations.size - deduplicatedConversations.size
            println("ConversationRepository.refreshConversations: Loaded ${deduplicatedConversations.size} conversations (filtered out ${conversations.size - updatedConversations.size} self/empty, removed $duplicatesRemoved duplicates)")
            _conversationsCache.value = _conversationsCache.value + (accountId to deduplicatedConversations)
        } catch (e: Exception) {
            // Keep existing cache on error
        }
    }

    private fun loadConversation(accountId: String, conversationId: String): Conversation {
        val info = jamiBridge.getConversationInfo(accountId, conversationId)
        val members = jamiBridge.getConversationMembers(accountId, conversationId)

        // Get user's own Jami ID to exclude from title
        val userJamiId = accountRepository.accountState.value.jamiId

        // Get cached contacts for proper display names and custom names
        val cachedContacts = contactRepository._contactsCache.value[accountId] ?: emptyList()
        val contactsMap = cachedContacts.associateBy { it.uri }

        // Get contact display names for participants
        val participants = members.map { member ->
            // First try to get contact from cache (has custom names and proper display names)
            val cachedContact = contactsMap[member.uri]

            // Check if cached display name is a real name or just a truncated URI fallback
            val cachedDisplayName = cachedContact?.displayName
            val hasRealDisplayName = cachedDisplayName != null &&
                cachedDisplayName.isNotBlank() &&
                cachedDisplayName != member.uri.take(8) &&  // Not a truncated URI
                !cachedDisplayName.matches(Regex("^[0-9a-f]{8}$"))  // Not an 8-char hex string

            val displayName = if (hasRealDisplayName) {
                // Use cached contact's real display name
                cachedDisplayName!!
            } else {
                // Fall back to getting from bridge (contact details may have profile info)
                try {
                    val details = jamiBridge.getContactDetails(accountId, member.uri)
                    val detailsName = details["displayName"]?.takeIf { it.isNotBlank() }
                    if (detailsName != null) {
                        // Update the cached contact with the real name
                        println("ConversationRepository: Got real displayName from contactDetails: $detailsName for ${member.uri.take(8)}...")
                        detailsName
                    } else {
                        cachedDisplayName ?: member.uri.take(8)
                    }
                } catch (e: Exception) {
                    cachedDisplayName ?: member.uri.take(8)
                }
            }

            Contact(
                id = member.uri,
                uri = member.uri,
                displayName = displayName,
                customName = cachedContact?.customName,
                avatarUri = cachedContact?.avatarUri,
                isOnline = cachedContact?.isOnline ?: false
            )
        }

        // Determine conversation title
        val title = info["title"] ?: run {
            // Find the OTHER participant (not the user) for 1-on-1 conversations
            val otherParticipant = participants.firstOrNull { it.uri != userJamiId }
            otherParticipant?.getEffectiveName() ?: "Conversation"
        }

        val isGroup = members.size > 2

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

                val msgBody = event.message.body
                val msgType = event.message.type

                // Check if this is a file message
                // Note: the type can be in msg.type OR in msgBody["type"]
                val bodyType = msgBody["type"] ?: ""
                val isFileMessage = msgType == "application/data-transfer+json" ||
                        bodyType == "application/data-transfer+json" ||
                        msgBody.containsKey("fileId") ||
                        msgBody.containsKey("tid")

                // Skip system messages that don't have content
                val messageBody = msgBody["body"]
                if (!isFileMessage && messageBody.isNullOrBlank()) {
                    println("ConversationRepository: Skipping system message without body")
                    return
                }

                val key = "$accountId:${event.conversationId}"
                // Jami timestamps are in seconds, convert to milliseconds
                val timestampMillis = if (event.message.timestamp < 100000000000) {
                    event.message.timestamp * 1000  // Convert seconds to milliseconds
                } else {
                    event.message.timestamp  // Already in milliseconds
                }

                val message = if (isFileMessage) {
                    // File message - extract file info
                    val fileName = msgBody["displayName"] ?: msgBody["name"] ?: "file"
                    // Try to get mimetype from body, fallback to determining from file extension
                    val mimeType = msgBody["mimetype"]?.takeIf { it.isNotEmpty() } ?: getMimeTypeFromFileName(fileName)
                    val isImage = mimeType.startsWith("image/")

                    println("ConversationRepository: File message detected - fileName=$fileName, mimeType=$mimeType, isImage=$isImage")

                    Message(
                        id = event.message.id,
                        conversationId = event.conversationId,
                        authorId = event.message.author,
                        content = fileName, // Store filename as content for file messages
                        timestamp = Instant.fromEpochMilliseconds(timestampMillis),
                        status = MessageStatus.DELIVERED,
                        type = if (isImage) MessageType.IMAGE else MessageType.FILE
                    )
                } else {
                    // Text message
                    Message(
                        id = event.message.id,
                        conversationId = event.conversationId,
                        authorId = event.message.author,
                        content = messageBody ?: "",
                        timestamp = Instant.fromEpochMilliseconds(timestampMillis),
                        status = MessageStatus.DELIVERED,
                        type = MessageType.TEXT
                    )
                }

                val currentMessages = _messagesCache.value[key] ?: emptyList()
                if (currentMessages.none { it.id == message.id }) {
                    val updatedMessages = (currentMessages + message).sortedBy { it.timestamp }
                    _messagesCache.value = _messagesCache.value + (key to updatedMessages)
                    println("ConversationRepository.handleConversationEvent: Message added to cache, key=$key, total messages=${updatedMessages.size}")

                    // Show notification for the new message
                    showMessageNotificationIfNeeded(
                        accountId = accountId,
                        conversationId = event.conversationId,
                        authorId = event.message.author,
                        messageText = message.content,
                        timestamp = timestampMillis
                    )
                } else {
                    println("ConversationRepository.handleConversationEvent: Message already in cache, skipping")
                }

                // Update conversation's last message
                updateConversationLastMessage(accountId, event.conversationId, message)
            }

            is JamiConversationEvent.MessagesLoaded -> {
                val key = "$accountId:${event.conversationId}"
                println("ConversationRepository.MessagesLoaded: Processing ${event.messages.size} messages")

                // Skip empty message loads - the daemon sometimes sends empty callbacks
                if (event.messages.isEmpty()) {
                    println("ConversationRepository.MessagesLoaded: Skipping empty message load")
                    return
                }

                val messages = event.messages.mapNotNull { msg ->
                    val msgBody = msg.body
                    val msgType = msg.type

                    // Check if this is a file message
                    // Note: the type can be in msg.type OR in msgBody["type"]
                    val bodyType = msgBody["type"] ?: ""
                    val isFileMessage = msgType == "application/data-transfer+json" ||
                            bodyType == "application/data-transfer+json" ||
                            msgBody.containsKey("fileId") ||
                            msgBody.containsKey("tid")

                    val textBody = msgBody["body"]

                    // Skip system messages that don't have content
                    if (!isFileMessage && textBody.isNullOrBlank()) {
                        return@mapNotNull null
                    }

                    if (isFileMessage) {
                        val fileName = msgBody["displayName"] ?: msgBody["name"] ?: "file"
                        val mimeType = msgBody["mimetype"]?.takeIf { it.isNotEmpty() } ?: getMimeTypeFromFileName(fileName)
                        val isImage = mimeType.startsWith("image/")

                        Message(
                            id = msg.id,
                            conversationId = event.conversationId,
                            authorId = msg.author,
                            content = fileName,
                            timestamp = Instant.fromEpochMilliseconds(msg.timestamp),
                            status = MessageStatus.DELIVERED,
                            type = if (isImage) MessageType.IMAGE else MessageType.FILE
                        )
                    } else {
                        Message(
                            id = msg.id,
                            conversationId = event.conversationId,
                            authorId = msg.author,
                            content = textBody ?: "",
                            timestamp = Instant.fromEpochMilliseconds(msg.timestamp),
                            status = MessageStatus.DELIVERED,
                            type = MessageType.TEXT
                        )
                    }
                }
                val sortedMessages = messages.sortedBy { it.timestamp }
                println("ConversationRepository.MessagesLoaded: Caching ${sortedMessages.size} messages for conversation ${event.conversationId}")
                _messagesCache.value = _messagesCache.value + (key to sortedMessages)
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
                println("ConversationRepository: New conversation request received for conversation ${event.conversationId}")
                // Refresh conversation requests from daemon to get the complete request info
                scope.launch {
                    refreshConversationRequests(accountId)
                }
            }

            is JamiConversationEvent.ConversationMemberEvent -> {
                println("ConversationRepository: Member event - ${event.event} for ${event.memberUri} in ${event.conversationId}")
                when (event.event) {
                    MemberEventType.JOIN -> {
                        // Refresh the conversation to get updated member list
                        scope.launch {
                            refreshConversations(accountId)
                        }
                    }
                    MemberEventType.LEAVE, MemberEventType.BAN -> {
                        // Refresh conversations when member leaves or is banned
                        scope.launch {
                            refreshConversations(accountId)
                        }
                    }
                    MemberEventType.UNBAN -> {
                        // No action needed for unban, member still not in conversation
                    }
                }
            }

            else -> { /* Handle other events */ }
        }
    }

    private fun updateConversationLastMessage(accountId: String, conversationId: String, message: Message) {
        val currentConversations = _conversationsCache.value[accountId] ?: emptyList()

        // Check if conversation exists in cache
        val conversationExists = currentConversations.any { it.id == conversationId }

        if (!conversationExists) {
            // Conversation not in cache - load it and add it
            println("ConversationRepository.updateConversationLastMessage: Conversation $conversationId not in cache, loading it")
            try {
                val conversation = loadConversation(accountId, conversationId)
                val conversationWithMessage = conversation.copy(lastMessage = message)
                _conversationsCache.value = _conversationsCache.value +
                    (accountId to (currentConversations + conversationWithMessage))
                println("ConversationRepository.updateConversationLastMessage: Added new conversation to cache with message")
            } catch (e: Exception) {
                println("ConversationRepository.updateConversationLastMessage: Failed to load conversation: ${e.message}")
            }
        } else {
            // Conversation exists - update it
            val updatedConversations = currentConversations.map { conv ->
                if (conv.id == conversationId) {
                    conv.copy(lastMessage = message)
                } else {
                    conv
                }
            }
            _conversationsCache.value = _conversationsCache.value + (accountId to updatedConversations)
        }
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

    suspend fun deleteConversation(accountId: String, conversationId: String) {
        println("ConversationRepository.deleteConversation: Deleting conversation $conversationId")

        try {
            // Remove conversation from Jami daemon
            jamiBridge.removeConversation(accountId, conversationId)
            println("ConversationRepository.deleteConversation: ✓ Removed from daemon")

            // Remove messages for this conversation from cache
            val messageKey = "$accountId:$conversationId"
            _messagesCache.value = _messagesCache.value - messageKey
            println("ConversationRepository.deleteConversation: ✓ Removed messages from cache")

            // Clear from persistence
            try {
                conversationPersistence.clearMessages(accountId, conversationId)
                println("ConversationRepository.deleteConversation: ✓ Cleared messages from persistence")
            } catch (e: Exception) {
                println("ConversationRepository.deleteConversation: Failed to clear messages from persistence: ${e.message}")
            }

            // Refresh conversations from daemon to get updated list
            // This ensures the UI updates properly
            refreshConversations(accountId)
            println("ConversationRepository.deleteConversation: ✓ Refreshed conversations")

            println("ConversationRepository.deleteConversation: ✓ Conversation deleted successfully")
        } catch (e: Exception) {
            println("ConversationRepository.deleteConversation: ✗ Failed to delete conversation: ${e.message}")
            throw e
        }
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

    // TODO: SharedPrefs persistence disabled - might be deleted
    // /**
    //  * Load persisted conversations from storage.
    //  */
    // private suspend fun loadPersistedConversations(accountId: String) {
    //     println("ConversationRepository: loadPersistedConversations() for account: $accountId")
    //     try {
    //         val persistedConversations = conversationPersistence.loadConversations(accountId)
    //         println("ConversationRepository: ✓ Loaded ${persistedConversations.size} persisted conversations")
    //         if (persistedConversations.isNotEmpty()) {
    //             persistedConversations.forEach { conversation ->
    //                 println("  - ${conversation.title} (${conversation.id.take(16)}...)")
    //             }
    //             _conversationsCache.value = _conversationsCache.value + (accountId to persistedConversations)
    //             println("ConversationRepository: ✓ Added persisted conversations to cache")
    //         } else {
    //             println("ConversationRepository: No persisted conversations found")
    //         }
    //     } catch (e: Exception) {
    //         println("ConversationRepository: ✗ Failed to load persisted conversations: ${e.message}")
    //         e.printStackTrace()
    //     }
    // }

    // TODO: SharedPrefs persistence disabled - might be deleted
    // /**
    //  * Load persisted messages from storage.
    //  */
    // private suspend fun loadPersistedMessages(accountId: String, conversationId: String) {
    //     println("ConversationRepository: loadPersistedMessages() for conversation: $conversationId")
    //     try {
    //         val persistedMessages = conversationPersistence.loadMessages(accountId, conversationId)
    //         println("ConversationRepository: ✓ Loaded ${persistedMessages.size} persisted messages")
    //         if (persistedMessages.isNotEmpty()) {
    //             val key = "$accountId:$conversationId"
    //             val sortedMessages = persistedMessages.sortedBy { it.timestamp }
    //             _messagesCache.value = _messagesCache.value + (key to sortedMessages)
    //             println("ConversationRepository: ✓ Added persisted messages to cache (sorted)")
    //         } else {
    //             println("ConversationRepository: No persisted messages found")
    //         }
    //     } catch (e: Exception) {
    //         println("ConversationRepository: ✗ Failed to load persisted messages: ${e.message}")
    //         e.printStackTrace()
    //     }
    // }

    /**
     * Get pending conversation requests as a reactive Flow.
     */
    fun getConversationRequests(accountId: String): Flow<List<com.gettogether.app.jami.ConversationRequest>> {
        // Trigger refresh if not cached
        scope.launch {
            if (_conversationRequestsCache.value[accountId].isNullOrEmpty()) {
                refreshConversationRequests(accountId)
            }
        }
        return _conversationRequestsCache.map { cache ->
            cache[accountId] ?: emptyList()
        }
    }

    /**
     * Refresh conversation requests from JamiBridge.
     */
    suspend fun refreshConversationRequests(accountId: String) {
        try {
            val requests = jamiBridge.getConversationRequests(accountId)
            _conversationRequestsCache.value = _conversationRequestsCache.value + (accountId to requests)
            println("ConversationRepository: Loaded ${requests.size} conversation requests")
        } catch (e: Exception) {
            println("ConversationRepository: Failed to refresh conversation requests: ${e.message}")
            // Keep existing cache on error
        }
    }

    /**
     * Accept a conversation request.
     */
    suspend fun acceptConversationRequest(accountId: String, conversationId: String) {
        try {
            println("ConversationRepository: Accepting conversation request $conversationId")
            jamiBridge.acceptConversationRequest(accountId, conversationId)

            // Remove from conversation requests cache
            val currentRequests = _conversationRequestsCache.value[accountId] ?: emptyList()
            _conversationRequestsCache.value = _conversationRequestsCache.value +
                (accountId to currentRequests.filter { it.conversationId != conversationId })

            // Refresh conversations after accepting
            refreshConversations(accountId)
        } catch (e: Exception) {
            println("ConversationRepository: Failed to accept conversation request: ${e.message}")
            throw e
        }
    }

    /**
     * Decline a conversation request.
     */
    suspend fun declineConversationRequest(accountId: String, conversationId: String) {
        try {
            println("ConversationRepository: Declining conversation request $conversationId")
            jamiBridge.declineConversationRequest(accountId, conversationId)

            // Remove from conversation requests cache
            val currentRequests = _conversationRequestsCache.value[accountId] ?: emptyList()
            _conversationRequestsCache.value = _conversationRequestsCache.value +
                (accountId to currentRequests.filter { it.conversationId != conversationId })
        } catch (e: Exception) {
            println("ConversationRepository: Failed to decline conversation request: ${e.message}")
            throw e
        }
    }

    /**
     * Shows a notification for a new message if conditions are met.
     * Only shows notifications for messages from others (not self).
     */
    private fun showMessageNotificationIfNeeded(
        accountId: String,
        conversationId: String,
        authorId: String,
        messageText: String,
        timestamp: Long
    ) {
        // Don't notify if NotificationHelper not available (iOS or test environment)
        if (notificationHelper == null) {
            println("ConversationRepository: NotificationHelper not available, skipping notification")
            return
        }

        // Check if message notifications are enabled in settings
        val notificationsEnabled = settingsRepository?.notificationSettings?.value?.messageNotifications ?: true
        if (!notificationsEnabled) {
            println("ConversationRepository: Message notifications disabled in settings, skipping notification")
            return
        }

        // Don't notify for own messages
        // Check if the message author is the current user (by comparing with the user's Jami ID)
        val currentUserJamiId = accountRepository.accountState.value.jamiId
        if (authorId == currentUserJamiId || authorId == accountId) {
            println("ConversationRepository: Skipping notification for own message (author=$authorId, currentUser=$currentUserJamiId)")
            return
        }

        try {
            // Get contact details for the author to get their display name
            val contactDetails = jamiBridge.getContactDetails(accountId, authorId)
            val contactName = contactDetails["displayName"]
                ?: contactDetails["username"]
                ?: authorId.take(8)

            // Generate notification ID based on conversation (so multiple messages stack)
            val notificationId = NotificationConstants.MESSAGE_NOTIFICATION_BASE_ID +
                conversationId.hashCode().and(0xFFFF)

            println("ConversationRepository: Showing message notification from $contactName in conversation $conversationId")

            notificationHelper.showMessageNotification(
                notificationId = notificationId,
                contactId = authorId,
                contactName = contactName,
                message = messageText,
                conversationId = conversationId,
                timestamp = timestamp
            )
        } catch (e: Exception) {
            println("ConversationRepository: Failed to show message notification: ${e.message}")
            // Don't throw - notification failure shouldn't break message handling
        }
    }

    /**
     * Determine MIME type from file name extension.
     */
    private fun getMimeTypeFromFileName(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "heic", "heif" -> "image/heic"
            "bmp" -> "image/bmp"
            "svg" -> "image/svg+xml"
            "mp4" -> "video/mp4"
            "mov" -> "video/quicktime"
            "avi" -> "video/x-msvideo"
            "pdf" -> "application/pdf"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            else -> "application/octet-stream"
        }
    }
}
