package com.gettogether.app.presentation.state

data class ChatState(
    val conversationId: String = "",
    val contactName: String = "",
    val contactAvatarUri: String? = null,
    val contactIsOnline: Boolean = false,
    val userJamiId: String = "",
    val messages: List<ChatMessage> = emptyList(),
    val messageInput: String = "",
    val isSending: Boolean = false,
    val error: String? = null,
    val isPickingImage: Boolean = false,
    val pendingImageUri: String? = null,
    val selectedMessageForMenu: ChatMessage? = null,
    val saveResult: SaveResult? = null,
    val pendingSaveMessage: ChatMessage? = null
) {
    val canSend: Boolean get() = messageInput.isNotBlank() && !isSending
}

sealed class SaveResult {
    data class Success(val message: String) : SaveResult()
    data class Failure(val message: String) : SaveResult()
}

data class ChatMessage(
    val id: String,
    val content: String,
    val timestamp: String,
    val isFromMe: Boolean,
    val status: MessageStatus = MessageStatus.Sent,
    val type: ChatMessageType = ChatMessageType.Text,
    val fileInfo: FileMessageInfo? = null
)

enum class ChatMessageType {
    Text,
    Image,
    File
}

data class FileMessageInfo(
    val fileId: String,
    val fileName: String,
    val fileSize: Long,
    val mimeType: String,
    val localPath: String? = null,
    val transferState: FileTransferState = FileTransferState.Pending,
    val progress: Float = 0f
)

enum class FileTransferState {
    Pending,
    Uploading,
    Downloading,
    Completed,
    Failed,
    Cancelled
}

enum class MessageStatus {
    Sending,
    Sent,
    Delivered,
    Read,
    Failed
}
