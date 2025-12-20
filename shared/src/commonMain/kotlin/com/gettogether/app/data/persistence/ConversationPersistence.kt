package com.gettogether.app.data.persistence

import com.gettogether.app.domain.model.Conversation
import com.gettogether.app.domain.model.Message

/**
 * Interface for persisting conversations and messages locally.
 */
interface ConversationPersistence {
    /**
     * Save conversations for an account.
     */
    suspend fun saveConversations(accountId: String, conversations: List<Conversation>)

    /**
     * Load conversations for an account.
     */
    suspend fun loadConversations(accountId: String): List<Conversation>

    /**
     * Save messages for a conversation.
     */
    suspend fun saveMessages(accountId: String, conversationId: String, messages: List<Message>)

    /**
     * Load messages for a conversation.
     */
    suspend fun loadMessages(accountId: String, conversationId: String): List<Message>

    /**
     * Clear all conversations for an account.
     */
    suspend fun clearConversations(accountId: String)

    /**
     * Clear all messages for a conversation.
     */
    suspend fun clearMessages(accountId: String, conversationId: String)
}
