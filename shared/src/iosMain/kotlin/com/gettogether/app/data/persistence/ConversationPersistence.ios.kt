package com.gettogether.app.data.persistence

import com.gettogether.app.domain.model.Contact
import com.gettogether.app.domain.model.Conversation
import com.gettogether.app.domain.model.Message
import com.gettogether.app.domain.model.MessageStatus
import com.gettogether.app.domain.model.MessageType
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import platform.Foundation.NSUserDefaults

/**
 * iOS implementation of ConversationPersistence using UserDefaults.
 */
class IosConversationPersistence : ConversationPersistence {

    private val userDefaults = NSUserDefaults.standardUserDefaults

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun saveConversations(accountId: String, conversations: List<Conversation>) {
        val key = getConversationsKey(accountId)
        val data = conversations.map { it.toSerializable() }
        val jsonString = json.encodeToString(data)
        userDefaults.setObject(jsonString, forKey = key)
        userDefaults.synchronize()
    }

    override suspend fun loadConversations(accountId: String): List<Conversation> {
        val key = getConversationsKey(accountId)
        val jsonString = userDefaults.stringForKey(key) ?: return emptyList()
        return try {
            val data = json.decodeFromString<List<SerializableConversation>>(jsonString)
            data.map { it.toConversation() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun saveMessages(accountId: String, conversationId: String, messages: List<Message>) {
        val key = getMessagesKey(accountId, conversationId)
        val data = messages.map { it.toSerializable() }
        val jsonString = json.encodeToString(data)
        userDefaults.setObject(jsonString, forKey = key)
        userDefaults.synchronize()
    }

    override suspend fun loadMessages(accountId: String, conversationId: String): List<Message> {
        val key = getMessagesKey(accountId, conversationId)
        val jsonString = userDefaults.stringForKey(key) ?: return emptyList()
        return try {
            val data = json.decodeFromString<List<SerializableMessage>>(jsonString)
            data.map { it.toMessage() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun clearConversations(accountId: String) {
        val key = getConversationsKey(accountId)
        userDefaults.removeObjectForKey(key)
        userDefaults.synchronize()
    }

    override suspend fun clearMessages(accountId: String, conversationId: String) {
        val key = getMessagesKey(accountId, conversationId)
        userDefaults.removeObjectForKey(key)
        userDefaults.synchronize()
    }

    private fun getConversationsKey(accountId: String): String {
        return "${CONVERSATIONS_KEY_PREFIX}$accountId"
    }

    private fun getMessagesKey(accountId: String, conversationId: String): String {
        return "${MESSAGES_KEY_PREFIX}${accountId}_$conversationId"
    }

    companion object {
        private const val CONVERSATIONS_KEY_PREFIX = "conversations_"
        private const val MESSAGES_KEY_PREFIX = "messages_"
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
