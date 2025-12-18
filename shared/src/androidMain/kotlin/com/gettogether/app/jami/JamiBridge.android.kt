package com.gettogether.app.jami

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Android implementation of JamiBridge using JNI to interface with jami-daemon.
 * TODO: Implement JNI bindings to native jami-daemon library.
 */
class AndroidJamiBridge : JamiBridge {
    private val accountEventsFlow = MutableSharedFlow<JamiEvent>()
    private val callEventsFlow = MutableSharedFlow<JamiEvent>()
    private val messageEventsFlow = MutableSharedFlow<JamiEvent>()

    private var isDaemonRunning = false

    override suspend fun initDaemon() {
        // TODO: Initialize native jami-daemon via JNI
        // System.loadLibrary("jami")
    }

    override suspend fun startDaemon() {
        // TODO: Start jami-daemon via JNI
        isDaemonRunning = true
    }

    override suspend fun stopDaemon() {
        // TODO: Stop jami-daemon via JNI
        isDaemonRunning = false
    }

    override fun isDaemonRunning(): Boolean = isDaemonRunning

    override suspend fun createAccount(displayName: String): String {
        // TODO: Create account via JNI
        return "placeholder-account-id"
    }

    override suspend fun importAccount(archivePath: String, password: String): String {
        // TODO: Import account via JNI
        return "placeholder-account-id"
    }

    override suspend fun exportAccount(accountId: String, password: String): String {
        // TODO: Export account via JNI
        return "/path/to/exported/archive.gz"
    }

    override suspend fun deleteAccount(accountId: String) {
        // TODO: Delete account via JNI
    }

    override fun getAccountIds(): List<String> {
        // TODO: Get account IDs via JNI
        return emptyList()
    }

    override fun getAccountDetails(accountId: String): Map<String, String> {
        // TODO: Get account details via JNI
        return emptyMap()
    }

    override suspend fun setAccountDetails(accountId: String, details: Map<String, String>) {
        // TODO: Set account details via JNI
    }

    override fun getContacts(accountId: String): List<Map<String, String>> {
        // TODO: Get contacts via JNI
        return emptyList()
    }

    override suspend fun addContact(accountId: String, uri: String) {
        // TODO: Add contact via JNI
    }

    override suspend fun removeContact(accountId: String, uri: String) {
        // TODO: Remove contact via JNI
    }

    override fun getConversations(accountId: String): List<String> {
        // TODO: Get conversations via JNI
        return emptyList()
    }

    override suspend fun startConversation(accountId: String): String {
        // TODO: Start conversation via JNI
        return "placeholder-conversation-id"
    }

    override suspend fun sendMessage(accountId: String, conversationId: String, message: String): String {
        // TODO: Send message via JNI
        return "placeholder-message-id"
    }

    override fun getConversationMessages(accountId: String, conversationId: String): List<Map<String, String>> {
        // TODO: Get messages via JNI
        return emptyList()
    }

    override suspend fun placeCall(accountId: String, uri: String, withVideo: Boolean): String {
        // TODO: Place call via JNI
        return "placeholder-call-id"
    }

    override suspend fun accept(callId: String) {
        // TODO: Accept call via JNI
    }

    override suspend fun refuse(callId: String) {
        // TODO: Refuse call via JNI
    }

    override suspend fun hangUp(callId: String) {
        // TODO: Hang up call via JNI
    }

    override suspend fun setMuted(callId: String, muted: Boolean) {
        // TODO: Set mute via JNI
    }

    override suspend fun setVideoMuted(callId: String, muted: Boolean) {
        // TODO: Set video mute via JNI
    }

    override fun accountEvents(): Flow<JamiEvent> = accountEventsFlow
    override fun callEvents(): Flow<JamiEvent> = callEventsFlow
    override fun messageEvents(): Flow<JamiEvent> = messageEventsFlow
}

actual fun createJamiBridge(): JamiBridge = AndroidJamiBridge()
