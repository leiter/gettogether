package com.gettogether.app.jami

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * iOS implementation of JamiBridge using Swift/ObjC interop to interface with jami-daemon.
 * TODO: Implement Swift bridge to native jami-daemon framework.
 */
class IOSJamiBridge : JamiBridge {
    private val accountEventsFlow = MutableSharedFlow<JamiEvent>()
    private val callEventsFlow = MutableSharedFlow<JamiEvent>()
    private val messageEventsFlow = MutableSharedFlow<JamiEvent>()

    private var isDaemonRunning = false

    override suspend fun initDaemon() {
        // TODO: Initialize jami-daemon via Swift bridge
    }

    override suspend fun startDaemon() {
        // TODO: Start jami-daemon via Swift bridge
        isDaemonRunning = true
    }

    override suspend fun stopDaemon() {
        // TODO: Stop jami-daemon via Swift bridge
        isDaemonRunning = false
    }

    override fun isDaemonRunning(): Boolean = isDaemonRunning

    override suspend fun createAccount(displayName: String): String {
        // TODO: Create account via Swift bridge
        return "placeholder-account-id"
    }

    override suspend fun importAccount(archivePath: String, password: String): String {
        // TODO: Import account via Swift bridge
        return "placeholder-account-id"
    }

    override suspend fun exportAccount(accountId: String, password: String): String {
        // TODO: Export account via Swift bridge
        return "/path/to/exported/archive.gz"
    }

    override suspend fun deleteAccount(accountId: String) {
        // TODO: Delete account via Swift bridge
    }

    override fun getAccountIds(): List<String> {
        // TODO: Get account IDs via Swift bridge
        return emptyList()
    }

    override fun getAccountDetails(accountId: String): Map<String, String> {
        // TODO: Get account details via Swift bridge
        return emptyMap()
    }

    override suspend fun setAccountDetails(accountId: String, details: Map<String, String>) {
        // TODO: Set account details via Swift bridge
    }

    override fun getContacts(accountId: String): List<Map<String, String>> {
        // TODO: Get contacts via Swift bridge
        return emptyList()
    }

    override suspend fun addContact(accountId: String, uri: String) {
        // TODO: Add contact via Swift bridge
    }

    override suspend fun removeContact(accountId: String, uri: String) {
        // TODO: Remove contact via Swift bridge
    }

    override fun getConversations(accountId: String): List<String> {
        // TODO: Get conversations via Swift bridge
        return emptyList()
    }

    override suspend fun startConversation(accountId: String): String {
        // TODO: Start conversation via Swift bridge
        return "placeholder-conversation-id"
    }

    override suspend fun sendMessage(accountId: String, conversationId: String, message: String): String {
        // TODO: Send message via Swift bridge
        return "placeholder-message-id"
    }

    override fun getConversationMessages(accountId: String, conversationId: String): List<Map<String, String>> {
        // TODO: Get messages via Swift bridge
        return emptyList()
    }

    override suspend fun placeCall(accountId: String, uri: String, withVideo: Boolean): String {
        // TODO: Place call via Swift bridge
        return "placeholder-call-id"
    }

    override suspend fun accept(callId: String) {
        // TODO: Accept call via Swift bridge
    }

    override suspend fun refuse(callId: String) {
        // TODO: Refuse call via Swift bridge
    }

    override suspend fun hangUp(callId: String) {
        // TODO: Hang up call via Swift bridge
    }

    override suspend fun setMuted(callId: String, muted: Boolean) {
        // TODO: Set mute via Swift bridge
    }

    override suspend fun setVideoMuted(callId: String, muted: Boolean) {
        // TODO: Set video mute via Swift bridge
    }

    override fun accountEvents(): Flow<JamiEvent> = accountEventsFlow
    override fun callEvents(): Flow<JamiEvent> = callEventsFlow
    override fun messageEvents(): Flow<JamiEvent> = messageEventsFlow
}

actual fun createJamiBridge(): JamiBridge = IOSJamiBridge()
