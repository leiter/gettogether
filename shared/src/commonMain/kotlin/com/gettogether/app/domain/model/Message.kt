package com.gettogether.app.domain.model

import kotlin.time.Instant

data class Message(
    val id: String,
    val conversationId: String,
    val authorId: String,
    val content: String,
    val timestamp: Instant,
    val status: MessageStatus = MessageStatus.SENDING,
    val type: MessageType = MessageType.TEXT,
    val fileId: String? = null  // For file/image messages, the daemon's file identifier
)

enum class MessageStatus {
    SENDING,
    SENT,
    DELIVERED,
    READ,
    FAILED
}

enum class MessageType {
    TEXT,
    FILE,
    IMAGE,
    VIDEO,
    AUDIO,
    CALL
}
