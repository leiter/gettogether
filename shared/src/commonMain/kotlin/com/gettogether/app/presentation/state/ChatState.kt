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
    val error: String? = null
) {
    val canSend: Boolean get() = messageInput.isNotBlank() && !isSending
}

data class ChatMessage(
    val id: String,
    val content: String,
    val timestamp: String,
    val isFromMe: Boolean,
    val status: MessageStatus = MessageStatus.Sent
)

enum class MessageStatus {
    Sending,
    Sent,
    Delivered,
    Read,
    Failed
}
