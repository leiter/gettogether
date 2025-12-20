package com.gettogether.app.data.persistence

import android.content.Context
import android.content.SharedPreferences
import com.gettogether.app.domain.model.Contact
import com.gettogether.app.domain.model.Conversation
import com.gettogether.app.domain.model.Message
import com.gettogether.app.domain.model.MessageStatus
import com.gettogether.app.domain.model.MessageType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Android implementation of ConversationPersistence using SharedPreferences.
 */
class AndroidConversationPersistence(context: Context) : ConversationPersistence {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "jami_conversations",
        Context.MODE_PRIVATE
    )

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun saveConversations(accountId: String, conversations: List<Conversation>) = withContext(Dispatchers.IO) {
        val key = "conversations_$accountId"
        val data = conversations.map { it.toSerializable() }
        val jsonString = json.encodeToString(data)
        prefs.edit().putString(key, jsonString).apply()
    }

    override suspend fun loadConversations(accountId: String): List<Conversation> = withContext(Dispatchers.IO) {
        val key = "conversations_$accountId"
        val jsonString = prefs.getString(key, null) ?: return@withContext emptyList()
        try {
            val data = json.decodeFromString<List<SerializableConversation>>(jsonString)
            data.map { it.toConversation() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun saveMessages(accountId: String, conversationId: String, messages: List<Message>) = withContext(Dispatchers.IO) {
        val key = "messages_${accountId}_$conversationId"
        val data = messages.map { it.toSerializable() }
        val jsonString = json.encodeToString(data)
        prefs.edit().putString(key, jsonString).apply()
    }

    override suspend fun loadMessages(accountId: String, conversationId: String): List<Message> = withContext(Dispatchers.IO) {
        val key = "messages_${accountId}_$conversationId"
        val jsonString = prefs.getString(key, null) ?: return@withContext emptyList()
        try {
            val data = json.decodeFromString<List<SerializableMessage>>(jsonString)
            data.map { it.toMessage() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun clearConversations(accountId: String) = withContext(Dispatchers.IO) {
        val key = "conversations_$accountId"
        prefs.edit().remove(key).apply()
    }

    override suspend fun clearMessages(accountId: String, conversationId: String) = withContext(Dispatchers.IO) {
        val key = "messages_${accountId}_$conversationId"
        prefs.edit().remove(key).apply()
    }
}

// Serializable versions of domain models
@Serializable
private data class SerializableConversation(
    val id: String,
    val accountId: String,
    val title: String,
    val participants: List<SerializableContact>,
    val lastMessage: SerializableMessage?,
    val unreadCount: Int,
    val isGroup: Boolean,
    val createdAtMillis: Long
)

@Serializable
private data class SerializableContact(
    val id: String,
    val uri: String,
    val displayName: String,
    val avatarUri: String?,
    val isOnline: Boolean,
    val isBanned: Boolean
)

@Serializable
private data class SerializableMessage(
    val id: String,
    val conversationId: String,
    val authorId: String,
    val content: String,
    val timestampMillis: Long,
    val status: String,
    val type: String
)

// Extension functions for conversion
private fun Conversation.toSerializable() = SerializableConversation(
    id = id,
    accountId = accountId,
    title = title,
    participants = participants.map { it.toSerializable() },
    lastMessage = lastMessage?.toSerializable(),
    unreadCount = unreadCount,
    isGroup = isGroup,
    createdAtMillis = createdAt.toEpochMilliseconds()
)

private fun SerializableConversation.toConversation() = Conversation(
    id = id,
    accountId = accountId,
    title = title,
    participants = participants.map { it.toContact() },
    lastMessage = lastMessage?.toMessage(),
    unreadCount = unreadCount,
    isGroup = isGroup,
    createdAt = Instant.fromEpochMilliseconds(createdAtMillis)
)

private fun Contact.toSerializable() = SerializableContact(
    id = id,
    uri = uri,
    displayName = displayName,
    avatarUri = avatarUri,
    isOnline = isOnline,
    isBanned = isBanned
)

private fun SerializableContact.toContact() = Contact(
    id = id,
    uri = uri,
    displayName = displayName,
    avatarUri = avatarUri,
    isOnline = isOnline,
    isBanned = isBanned
)

private fun Message.toSerializable() = SerializableMessage(
    id = id,
    conversationId = conversationId,
    authorId = authorId,
    content = content,
    timestampMillis = timestamp.toEpochMilliseconds(),
    status = status.name,
    type = type.name
)

private fun SerializableMessage.toMessage() = Message(
    id = id,
    conversationId = conversationId,
    authorId = authorId,
    content = content,
    timestamp = Instant.fromEpochMilliseconds(timestampMillis),
    status = MessageStatus.valueOf(status),
    type = MessageType.valueOf(type)
)
