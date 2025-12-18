package com.gettogether.app.domain.model

import kotlinx.datetime.Instant

data class Conversation(
    val id: String,
    val accountId: String,
    val title: String,
    val participants: List<Contact>,
    val lastMessage: Message? = null,
    val unreadCount: Int = 0,
    val isGroup: Boolean = false,
    val createdAt: Instant
)
