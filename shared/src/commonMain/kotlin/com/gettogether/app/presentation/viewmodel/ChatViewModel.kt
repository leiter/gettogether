package com.gettogether.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gettogether.app.data.repository.AccountRepository
import com.gettogether.app.data.repository.ConversationRepositoryImpl
import com.gettogether.app.domain.repository.ContactRepository
import com.gettogether.app.jami.JamiBridge
import com.gettogether.app.jami.JamiConversationEvent
import com.gettogether.app.platform.FileHelper
import com.gettogether.app.presentation.state.ChatMessage
import com.gettogether.app.presentation.state.ChatMessageType
import com.gettogether.app.presentation.state.ChatState
import com.gettogether.app.presentation.state.FileMessageInfo
import com.gettogether.app.presentation.state.FileTransferState
import com.gettogether.app.presentation.state.MessageStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock

class ChatViewModel(
    private val jamiBridge: JamiBridge,
    private val accountRepository: AccountRepository,
    private val conversationRepository: ConversationRepositoryImpl,
    private val contactRepository: ContactRepository,
    private val fileHelper: FileHelper
) : ViewModel() {

    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    // Map to store pending sent file paths: key is fileName, value is local path
    private val pendingSentFiles = mutableMapOf<String, String>()

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
                    println("ChatViewModel.loadConversation: convInfo=$convInfo")

                    // Get contact name and avatar from conversation members
                    var contactName = convInfo["title"] ?: ""
                    try {
                        val members = jamiBridge.getConversationMembers(accountId, conversationId)
                        println("ChatViewModel.loadConversation: Found ${members.size} members")
                        // Find the member that is not the current user
                        val otherMember = members.find { it.uri != userJamiId && it.uri != accountId }
                        if (otherMember != null) {
                            // Subscribe to contact updates for real-time presence tracking
                            viewModelScope.launch {
                                contactRepository.getContactById(accountId, otherMember.uri).collect { contact ->
                                    if (contact != null) {
                                        _state.update { currentState ->
                                            val effectiveName = contact.getEffectiveName()
                                            currentState.copy(
                                                contactName = effectiveName.takeIf { it.isNotBlank() }
                                                    ?: currentState.contactName.takeIf { it.isNotBlank() }
                                                    ?: contactName,
                                                contactAvatarUri = contact.avatarUri,
                                                contactIsOnline = contact.isOnline
                                            )
                                        }
                                        println("ChatViewModel.loadConversation: Contact updated - name: ${contact.displayName}, customName: ${contact.customName}, avatar: ${contact.avatarUri}, isOnline: ${contact.isOnline}")
                                    }
                                }
                            }
                        } else {
                            // Fallback: Use title if no other member found
                            if (contactName.isBlank()) {
                                contactName = "Conversation"
                            }
                        }
                    } catch (e: Exception) {
                        println("ChatViewModel.loadConversation: Failed to get contact info: ${e.message}")
                    }
                    // Final fallback
                    if (contactName.isBlank()) {
                        contactName = "Conversation"
                    }

                    // Subscribe to messages from ConversationRepository (includes persisted messages)
                    viewModelScope.launch {
                        conversationRepository.getMessages(accountId, conversationId).collect { messages ->
                            println("ChatViewModel: Received ${messages.size} messages from repository")
                            val chatMessages = messages.map { msg ->
                                val isFromMe = msg.authorId == accountId || msg.authorId == userJamiId
                                when (msg.type) {
                                    com.gettogether.app.domain.model.MessageType.IMAGE,
                                    com.gettogether.app.domain.model.MessageType.FILE -> {
                                        // File/image message
                                        val mimeType = fileHelper.getMimeType(msg.content)
                                        val chatMsgType = if (msg.type == com.gettogether.app.domain.model.MessageType.IMAGE) {
                                            ChatMessageType.Image
                                        } else {
                                            ChatMessageType.File
                                        }

                                        // Check if file exists at daemon path using fileId from message metadata
                                        val fileId = msg.fileId ?: msg.id
                                        val daemonPath = fileHelper.getConversationFilePath(accountId, conversationId, fileId)
                                        val localPath = daemonPath ?: pendingSentFiles[msg.content]
                                        val transferState = if (localPath != null) FileTransferState.Completed else FileTransferState.Pending

                                        ChatMessage(
                                            id = msg.id,
                                            content = msg.content,
                                            timestamp = formatTimestamp(msg.timestamp.toEpochMilliseconds()),
                                            isFromMe = isFromMe,
                                            status = MessageStatus.Sent,
                                            type = chatMsgType,
                                            fileInfo = FileMessageInfo(
                                                fileId = fileId,
                                                fileName = msg.content,
                                                fileSize = 0L,
                                                mimeType = mimeType,
                                                localPath = localPath,
                                                transferState = transferState,
                                                progress = if (localPath != null) 1f else 0f
                                            )
                                        )
                                    }
                                    else -> {
                                        // Text message
                                        ChatMessage(
                                            id = msg.id,
                                            content = msg.content,
                                            timestamp = formatTimestamp(msg.timestamp.toEpochMilliseconds()),
                                            isFromMe = isFromMe,
                                            status = MessageStatus.Sent,
                                            type = ChatMessageType.Text
                                        )
                                    }
                                }
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
                            contactName = if (it.contactName.isBlank()) contactName else it.contactName,
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

    fun setError(message: String) {
        _state.update { it.copy(error = message) }
    }

    fun sendImage(uri: String) {
        val conversationId = _state.value.conversationId
        println("[FILE-SEND] ┌─── sendImage START ───")
        println("[FILE-SEND] │ uri: $uri")
        println("[FILE-SEND] │ conversationId: $conversationId")

        viewModelScope.launch {
            try {
                val accountId = accountRepository.currentAccountId.value
                println("[FILE-SEND] │ accountId: $accountId")

                if (accountId == null) {
                    println("[FILE-SEND] │ ERROR: No account ID!")
                    println("[FILE-SEND] └─── sendImage FAILED ───")
                    _state.update { it.copy(error = "No active account") }
                    return@launch
                }

                // Copy URI to sendable file path
                println("[FILE-SEND] │ Copying URI to sendable file...")
                val filePath = fileHelper.copyUriToSendableFile(uri, conversationId)
                val fileName = filePath.substringAfterLast("/")
                println("[FILE-SEND] │ filePath: $filePath")
                println("[FILE-SEND] │ fileName: $fileName")

                // Store the local path so we can look it up when the message comes back
                pendingSentFiles[fileName] = filePath
                println("[FILE-SEND] │ pendingSentFiles[$fileName] = $filePath")
                println("[FILE-SEND] │ pendingSentFiles size: ${pendingSentFiles.size}")
                println("[FILE-SEND] │ pendingSentFiles keys: ${pendingSentFiles.keys}")

                // Send via Jami - the message will appear via swarmMessageReceived callback
                println("[FILE-SEND] │ Calling jamiBridge.sendFile()...")
                jamiBridge.sendFile(accountId, conversationId, filePath, fileName)
                println("[FILE-SEND] │ jamiBridge.sendFile() completed")
                println("[FILE-SEND] └─── sendImage SUCCESS ───")

            } catch (e: Exception) {
                println("[FILE-SEND] │ EXCEPTION: ${e.message}")
                e.printStackTrace()
                println("[FILE-SEND] └─── sendImage FAILED ───")
                _state.update { it.copy(error = "Failed to send image: ${e.message}") }
            }
        }
    }

    fun downloadFile(messageId: String) {
        println("[FILE-DOWNLOAD] ┌─── downloadFile START ───")
        println("[FILE-DOWNLOAD] │ messageId: $messageId")

        val message = _state.value.messages.find { it.id == messageId }
        val fileInfo = message?.fileInfo

        println("[FILE-DOWNLOAD] │ message found: ${message != null}")
        println("[FILE-DOWNLOAD] │ fileInfo found: ${fileInfo != null}")

        if (message == null || fileInfo == null) {
            println("[FILE-DOWNLOAD] │ ERROR: Message or fileInfo not found!")
            println("[FILE-DOWNLOAD] └─── downloadFile FAILED ───")
            return
        }

        println("[FILE-DOWNLOAD] │ fileId: ${fileInfo.fileId}")
        println("[FILE-DOWNLOAD] │ fileName: ${fileInfo.fileName}")
        println("[FILE-DOWNLOAD] │ fileSize: ${fileInfo.fileSize}")
        println("[FILE-DOWNLOAD] │ mimeType: ${fileInfo.mimeType}")
        println("[FILE-DOWNLOAD] │ transferState: ${fileInfo.transferState}")

        viewModelScope.launch {
            try {
                val accountId = accountRepository.currentAccountId.value
                val conversationId = _state.value.conversationId

                println("[FILE-DOWNLOAD] │ accountId: $accountId")
                println("[FILE-DOWNLOAD] │ conversationId: $conversationId")

                if (accountId == null) {
                    println("[FILE-DOWNLOAD] │ ERROR: No account ID!")
                    println("[FILE-DOWNLOAD] └─── downloadFile FAILED ───")
                    _state.update { it.copy(error = "No active account") }
                    return@launch
                }

                // Update state to downloading
                println("[FILE-DOWNLOAD] │ Setting state to Downloading...")
                updateMessageTransferState(messageId, FileTransferState.Downloading)

                // Get download path
                val destPath = fileHelper.getDownloadPath(conversationId, fileInfo.fileName)
                println("[FILE-DOWNLOAD] │ destPath: $destPath")

                // Start download
                println("[FILE-DOWNLOAD] │ Calling jamiBridge.acceptFileTransfer()...")
                jamiBridge.acceptFileTransfer(accountId, conversationId, fileInfo.fileId, destPath)
                println("[FILE-DOWNLOAD] │ acceptFileTransfer() completed")

                // Monitor download progress
                println("[FILE-DOWNLOAD] │ Starting monitorTransferProgress...")
                println("[FILE-DOWNLOAD] └─── downloadFile monitoring ───")
                monitorTransferProgress(messageId, destPath, isUpload = false)

            } catch (e: Exception) {
                println("[FILE-DOWNLOAD] │ EXCEPTION: ${e.message}")
                e.printStackTrace()
                println("[FILE-DOWNLOAD] └─── downloadFile FAILED ───")
                updateMessageTransferState(messageId, FileTransferState.Failed)
                _state.update { it.copy(error = "Failed to download: ${e.message}") }
            }
        }
    }

    private suspend fun monitorTransferProgress(messageId: String, filePath: String, isUpload: Boolean) {
        val accountId = accountRepository.currentAccountId.value ?: return
        val conversationId = _state.value.conversationId

        println("ChatViewModel.monitorTransferProgress: messageId=$messageId, isUpload=$isUpload")

        var attempts = 0
        val maxAttempts = 300 // 5 minutes at 1 second intervals

        while (attempts < maxAttempts) {
            try {
                val info = jamiBridge.getFileTransferInfo(accountId, conversationId, messageId)

                if (info != null && info.totalSize > 0) {
                    val progress = info.progress.toFloat() / info.totalSize.toFloat()
                    println("ChatViewModel.monitorTransferProgress: progress=${(progress * 100).toInt()}%, ${info.progress}/${info.totalSize}")
                    updateMessageProgress(messageId, progress)

                    if (info.progress >= info.totalSize) {
                        // Transfer complete
                        println("ChatViewModel.monitorTransferProgress: Transfer complete")
                        updateMessageCompleted(messageId, filePath)
                        return
                    }
                }

                delay(1000) // Poll every second
                attempts++
            } catch (e: Exception) {
                println("ChatViewModel.monitorTransferProgress: Error - ${e.message}")
                // Check if file exists (might be complete)
                if (fileHelper.fileExists(filePath)) {
                    println("ChatViewModel.monitorTransferProgress: File exists, marking as complete")
                    updateMessageCompleted(messageId, filePath)
                    return
                }
                delay(1000)
                attempts++
            }
        }

        // Timeout - check if file exists
        if (fileHelper.fileExists(filePath)) {
            updateMessageCompleted(messageId, filePath)
        } else {
            updateMessageTransferState(messageId, FileTransferState.Failed)
        }
    }

    private fun updateMessageTransferState(messageId: String, state: FileTransferState) {
        _state.update { currentState ->
            val updatedMessages = currentState.messages.map { msg ->
                if (msg.id == messageId && msg.fileInfo != null) {
                    msg.copy(fileInfo = msg.fileInfo.copy(transferState = state))
                } else {
                    msg
                }
            }
            currentState.copy(messages = updatedMessages)
        }
    }

    private fun updateMessageProgress(messageId: String, progress: Float) {
        _state.update { currentState ->
            val updatedMessages = currentState.messages.map { msg ->
                if (msg.id == messageId && msg.fileInfo != null) {
                    msg.copy(fileInfo = msg.fileInfo.copy(progress = progress))
                } else {
                    msg
                }
            }
            currentState.copy(messages = updatedMessages)
        }
    }

    private fun updateMessageCompleted(messageId: String, localPath: String) {
        _state.update { currentState ->
            val updatedMessages = currentState.messages.map { msg ->
                if (msg.id == messageId && msg.fileInfo != null) {
                    msg.copy(
                        status = MessageStatus.Sent,
                        fileInfo = msg.fileInfo.copy(
                            transferState = FileTransferState.Completed,
                            localPath = localPath,
                            progress = 1f
                        )
                    )
                } else {
                    msg
                }
            }
            currentState.copy(messages = updatedMessages)
        }
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
                    val messageBody = event.message.body

                    // Check if message already exists (to avoid duplicates from optimistic UI)
                    val messageExists = _state.value.messages.any { it.id == event.message.id }
                    if (messageExists) {
                        println("ChatViewModel.handleConversationEvent: Message already exists, skipping duplicate")
                        return
                    }

                    val isFromMe = event.message.author == userJamiId
                    val msgType = event.message.type

                    // Check if this is a file/image message
                    // Note: the type can be in msg.type OR in messageBody["type"]
                    val bodyType = messageBody["type"] ?: ""
                    val isFileMessage = msgType == "application/data-transfer+json" ||
                            bodyType == "application/data-transfer+json" ||
                            messageBody.containsKey("fileId") ||
                            messageBody.containsKey("tid")

                    val newMessage = if (isFileMessage) {
                        // File message
                        println("[FILE-MSG] ┌─── File Message Received ───")
                        println("[FILE-MSG] │ msgId: ${event.message.id}")
                        println("[FILE-MSG] │ msgType: $msgType")
                        println("[FILE-MSG] │ bodyType: $bodyType")
                        println("[FILE-MSG] │ isFromMe: $isFromMe")
                        println("[FILE-MSG] │ messageBody keys: ${messageBody.keys}")
                        println("[FILE-MSG] │ messageBody values: $messageBody")

                        val fileId = messageBody["fileId"] ?: messageBody["tid"] ?: event.message.id
                        val fileName = messageBody["displayName"] ?: messageBody["name"] ?: "file"
                        val totalSize = messageBody["totalSize"]?.toLongOrNull() ?: 0L
                        val mimeType = messageBody["mimetype"] ?: fileHelper.getMimeType(fileName)
                        val accountId = accountRepository.currentAccountId.value

                        println("[FILE-MSG] │ fileId: $fileId")
                        println("[FILE-MSG] │ fileName: $fileName")
                        println("[FILE-MSG] │ totalSize: $totalSize")
                        println("[FILE-MSG] │ mimeType: $mimeType")
                        println("[FILE-MSG] │ accountId: $accountId")

                        // Determine if it's an image based on mime type
                        val isImage = mimeType.startsWith("image/")
                        val messageType = if (isImage) ChatMessageType.Image else ChatMessageType.File
                        println("[FILE-MSG] │ isImage: $isImage")
                        println("[FILE-MSG] │ messageType: $messageType")

                        // First check if file exists at daemon's conversation_data path
                        val daemonFilePath = accountId?.let {
                            fileHelper.getConversationFilePath(it, event.conversationId, fileId)
                        }
                        println("[FILE-MSG] │ daemonFilePath: $daemonFilePath")

                        // For our own sent messages, also check pending files map
                        println("[FILE-MSG] │ pendingSentFiles keys before lookup: ${pendingSentFiles.keys}")
                        val pendingPath = if (isFromMe) {
                            pendingSentFiles.remove(fileName).also {
                                println("[FILE-MSG] │ Looked up pendingPath for '$fileName': $it")
                            }
                        } else null

                        // Use daemon path if available, otherwise pending path
                        val localPath = daemonFilePath ?: pendingPath
                        println("[FILE-MSG] │ Final localPath: $localPath")

                        val transferState = if (localPath != null) FileTransferState.Completed else FileTransferState.Pending
                        println("[FILE-MSG] │ transferState: $transferState")
                        println("[FILE-MSG] └─── File Message Processed ───")

                        ChatMessage(
                            id = event.message.id,
                            content = fileName,
                            timestamp = formatTimestamp(event.message.timestamp),
                            isFromMe = isFromMe,
                            status = MessageStatus.Sent,
                            type = messageType,
                            fileInfo = FileMessageInfo(
                                fileId = fileId,
                                fileName = fileName,
                                fileSize = totalSize,
                                mimeType = mimeType,
                                localPath = localPath,
                                transferState = transferState,
                                progress = if (localPath != null) 1f else 0f
                            )
                        )
                    } else {
                        // Text message
                        val textContent = messageBody["body"] ?: ""
                        println("ChatViewModel.handleConversationEvent: Text message - content='$textContent', author=${event.message.author}, userJamiId=$userJamiId, isFromMe=$isFromMe")
                        ChatMessage(
                            id = event.message.id,
                            content = textContent,
                            timestamp = formatTimestamp(event.message.timestamp),
                            isFromMe = isFromMe,
                            status = MessageStatus.Sent,
                            type = ChatMessageType.Text
                        )
                    }

                    _state.update { it.copy(messages = it.messages + newMessage) }
                    println("ChatViewModel.handleConversationEvent: Message added, total messages=${_state.value.messages.size}")
                } else {
                    println("ChatViewModel.handleConversationEvent: Message ignored - not for current conversation")
                }
            }
            is JamiConversationEvent.MessagesLoaded -> {
                // MessagesLoaded is handled by the repository subscription in loadConversation()
                // which properly checks daemon file paths. Do not duplicate handling here.
                println("ChatViewModel.handleConversationEvent: MessagesLoaded (handled by repository subscription)")
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
