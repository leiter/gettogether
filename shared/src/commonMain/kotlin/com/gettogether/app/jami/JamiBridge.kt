package com.gettogether.app.jami

import kotlinx.coroutines.flow.Flow

/**
 * JamiBridge provides the platform-specific implementation to interface with
 * the Jami daemon. On Android this uses JNI, on iOS it uses Swift/ObjC interop.
 */
interface JamiBridge {
    // Daemon lifecycle
    suspend fun initDaemon()
    suspend fun startDaemon()
    suspend fun stopDaemon()
    fun isDaemonRunning(): Boolean

    // Account management
    suspend fun createAccount(displayName: String): String
    suspend fun importAccount(archivePath: String, password: String): String
    suspend fun exportAccount(accountId: String, password: String): String
    suspend fun deleteAccount(accountId: String)
    fun getAccountIds(): List<String>
    fun getAccountDetails(accountId: String): Map<String, String>
    suspend fun setAccountDetails(accountId: String, details: Map<String, String>)

    // Contacts
    fun getContacts(accountId: String): List<Map<String, String>>
    suspend fun addContact(accountId: String, uri: String)
    suspend fun removeContact(accountId: String, uri: String)

    // Conversations
    fun getConversations(accountId: String): List<String>
    suspend fun startConversation(accountId: String): String
    suspend fun sendMessage(accountId: String, conversationId: String, message: String): String
    fun getConversationMessages(accountId: String, conversationId: String): List<Map<String, String>>

    // Calls
    suspend fun placeCall(accountId: String, uri: String, withVideo: Boolean): String
    suspend fun accept(callId: String)
    suspend fun refuse(callId: String)
    suspend fun hangUp(callId: String)
    suspend fun setMuted(callId: String, muted: Boolean)
    suspend fun setVideoMuted(callId: String, muted: Boolean)

    // Events
    fun accountEvents(): Flow<JamiEvent>
    fun callEvents(): Flow<JamiEvent>
    fun messageEvents(): Flow<JamiEvent>
}

sealed class JamiEvent {
    data class AccountRegistrationStateChanged(val accountId: String, val state: String) : JamiEvent()
    data class IncomingCall(val accountId: String, val callId: String, val peerId: String) : JamiEvent()
    data class CallStateChanged(val callId: String, val state: String) : JamiEvent()
    data class MessageReceived(val accountId: String, val conversationId: String, val messageId: String) : JamiEvent()
    data class ConversationReady(val accountId: String, val conversationId: String) : JamiEvent()
}

expect fun createJamiBridge(): JamiBridge
