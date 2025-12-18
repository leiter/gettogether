package com.gettogether.app.domain.repository

import com.gettogether.app.domain.model.Conversation
import com.gettogether.app.domain.model.Message
import kotlinx.coroutines.flow.Flow

interface ConversationRepository {
    fun getConversations(accountId: String): Flow<List<Conversation>>
    fun getConversationById(accountId: String, conversationId: String): Flow<Conversation?>
    fun getMessages(accountId: String, conversationId: String): Flow<List<Message>>
    suspend fun sendMessage(accountId: String, conversationId: String, content: String): Result<Message>
    suspend fun createGroupConversation(accountId: String, title: String, participantIds: List<String>): Result<Conversation>
    suspend fun addParticipant(accountId: String, conversationId: String, contactId: String): Result<Unit>
    suspend fun removeParticipant(accountId: String, conversationId: String, contactId: String): Result<Unit>
    suspend fun leaveConversation(accountId: String, conversationId: String): Result<Unit>
}
