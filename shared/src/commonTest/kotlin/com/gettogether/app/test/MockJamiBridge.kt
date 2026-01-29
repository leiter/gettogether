package com.gettogether.app.test

import com.gettogether.app.jami.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Mock implementation of JamiBridge for unit testing.
 *
 * This mock allows tests to:
 * - Set predefined contacts with ban status
 * - Simulate send message failures (including ban-related errors)
 * - Track method calls for verification
 */
class MockJamiBridge : JamiBridge {

    // Test configuration
    var contactsToReturn: List<JamiContact> = emptyList()
    var sendMessageError: Exception? = null
    var sendMessageCallCount = 0
    var lastSentMessage: String? = null
    var conversationsToReturn: List<String> = emptyList()

    // Event flows
    private val _events = MutableSharedFlow<JamiEvent>()
    private val _accountEvents = MutableSharedFlow<JamiAccountEvent>()
    private val _callEvents = MutableSharedFlow<JamiCallEvent>()
    private val _conversationEvents = MutableSharedFlow<JamiConversationEvent>()
    private val _contactEvents = MutableSharedFlow<JamiContactEvent>()

    override val events: SharedFlow<JamiEvent> = _events
    override val accountEvents: SharedFlow<JamiAccountEvent> = _accountEvents
    override val callEvents: SharedFlow<JamiCallEvent> = _callEvents
    override val conversationEvents: SharedFlow<JamiConversationEvent> = _conversationEvents
    override val contactEvents: SharedFlow<JamiContactEvent> = _contactEvents

    // Methods used in tests
    override fun getContacts(accountId: String): List<JamiContact> {
        return contactsToReturn
    }

    override suspend fun sendMessage(
        accountId: String,
        conversationId: String,
        message: String,
        replyTo: String?
    ): String {
        sendMessageCallCount++
        lastSentMessage = message
        sendMessageError?.let { throw it }
        return "msg-${kotlin.time.Clock.System.now().toEpochMilliseconds()}"
    }

    override fun getConversations(accountId: String): List<String> {
        return conversationsToReturn
    }

    // Minimal implementations for other required methods
    override suspend fun initDaemon(dataPath: String) {}
    override suspend fun startDaemon() {}
    override suspend fun stopDaemon() {}
    override fun isDaemonRunning(): Boolean = true

    override suspend fun createAccount(displayName: String, password: String): String = "test-account-id"
    override suspend fun importAccount(archivePath: String, password: String): String = "test-account-id"
    override suspend fun exportAccount(accountId: String, destinationPath: String, password: String): Boolean = true
    override suspend fun deleteAccount(accountId: String) {}
    override fun getAccountIds(): List<String> = listOf("test-account-id")
    override fun getAccountDetails(accountId: String): Map<String, String> = emptyMap()
    override fun getVolatileAccountDetails(accountId: String): Map<String, String> = emptyMap()
    override suspend fun setAccountDetails(accountId: String, details: Map<String, String>) {}
    override suspend fun setAccountActive(accountId: String, active: Boolean) {}
    override suspend fun connectivityChanged() {}
    override suspend fun updateProfile(accountId: String, displayName: String, avatarPath: String?) {}
    override suspend fun registerName(accountId: String, name: String, password: String): Boolean = true
    override suspend fun lookupName(accountId: String, name: String): LookupResult? = null
    override suspend fun lookupAddress(accountId: String, address: String): LookupResult? = null

    override suspend fun addContact(accountId: String, uri: String) {}
    override suspend fun removeContact(accountId: String, uri: String, ban: Boolean) {}
    override fun getContactDetails(accountId: String, uri: String): Map<String, String> = emptyMap()
    override suspend fun acceptTrustRequest(accountId: String, uri: String) {}
    override suspend fun discardTrustRequest(accountId: String, uri: String) {}
    override fun getTrustRequests(accountId: String): List<TrustRequest> = emptyList()
    override suspend fun subscribeBuddy(accountId: String, uri: String, flag: Boolean) {}
    override suspend fun publishPresence(accountId: String, isOnline: Boolean, note: String) {}

    override suspend fun startConversation(accountId: String): String = "test-conversation-id"
    override suspend fun removeConversation(accountId: String, conversationId: String) {}
    override suspend fun clearConversationCache(accountId: String, conversationId: String) {}
    override fun getConversationInfo(accountId: String, conversationId: String): Map<String, String> = emptyMap()
    override suspend fun updateConversationInfo(accountId: String, conversationId: String, info: Map<String, String>) {}
    override fun getConversationMembers(accountId: String, conversationId: String): List<ConversationMember> = emptyList()
    override suspend fun addConversationMember(accountId: String, conversationId: String, contactUri: String) {}
    override suspend fun removeConversationMember(accountId: String, conversationId: String, contactUri: String) {}
    override suspend fun acceptConversationRequest(accountId: String, conversationId: String) {}
    override suspend fun declineConversationRequest(accountId: String, conversationId: String) {}
    override fun getConversationRequests(accountId: String): List<ConversationRequest> = emptyList()

    override suspend fun loadConversationMessages(accountId: String, conversationId: String, fromMessage: String, count: Int): Int = 0
    override suspend fun setIsComposing(accountId: String, conversationId: String, isComposing: Boolean) {}
    override suspend fun setMessageDisplayed(accountId: String, conversationId: String, messageId: String) {}

    override suspend fun placeCall(accountId: String, uri: String, withVideo: Boolean): String = "test-call-id"
    override suspend fun acceptCall(accountId: String, callId: String, withVideo: Boolean) {}
    override suspend fun refuseCall(accountId: String, callId: String) {}
    override suspend fun hangUp(accountId: String, callId: String) {}
    override suspend fun holdCall(accountId: String, callId: String) {}
    override suspend fun unholdCall(accountId: String, callId: String) {}
    override suspend fun muteAudio(accountId: String, callId: String, muted: Boolean) {}
    override suspend fun muteVideo(accountId: String, callId: String, muted: Boolean) {}
    override fun getCallDetails(accountId: String, callId: String): Map<String, String> = emptyMap()
    override fun getActiveCalls(accountId: String): List<String> = emptyList()
    override suspend fun switchCamera() {}
    override suspend fun switchAudioOutput(useSpeaker: Boolean) {}
    override suspend fun answerMediaChangeRequest(accountId: String, callId: String, mediaList: List<Map<String, String>>) {}

    override suspend fun createConference(accountId: String, participantUris: List<String>): String = "test-conference-id"
    override suspend fun joinParticipant(accountId: String, callId1: String, accountId2: String, callId2: String) {}
    override suspend fun addParticipantToConference(accountId: String, callId: String, conferenceAccountId: String, conferenceId: String) {}
    override suspend fun hangUpConference(accountId: String, conferenceId: String) {}
    override fun getConferenceDetails(accountId: String, conferenceId: String): Map<String, String> = emptyMap()
    override fun getConferenceParticipants(accountId: String, conferenceId: String): List<String> = emptyList()
    override fun getConferenceInfos(accountId: String, conferenceId: String): List<Map<String, String>> = emptyList()
    override suspend fun setConferenceLayout(accountId: String, conferenceId: String, layout: ConferenceLayout) {}
    override suspend fun muteConferenceParticipant(accountId: String, conferenceId: String, participantUri: String, muted: Boolean) {}
    override suspend fun hangUpConferenceParticipant(accountId: String, conferenceId: String, participantUri: String, deviceId: String) {}

    override suspend fun sendFile(accountId: String, conversationId: String, filePath: String, displayName: String): String = "test-file-id"
    override suspend fun acceptFileTransfer(accountId: String, conversationId: String, interactionId: String, fileId: String, destinationPath: String) {}
    override suspend fun cancelFileTransfer(accountId: String, conversationId: String, fileId: String) {}
    override fun getFileTransferInfo(accountId: String, conversationId: String, fileId: String): FileTransferInfo? = null

    override fun getVideoDevices(): List<String> = emptyList()
    override fun getCurrentVideoDevice(): String = ""
    override suspend fun setVideoDevice(deviceId: String) {}
    override suspend fun startVideo() {}
    override suspend fun stopVideo() {}

    override fun getAudioOutputDevices(): List<String> = emptyList()
    @Suppress("DEPRECATION_ERROR")
    override fun getAudioInputDevices(): List<String> = emptyList()
    override suspend fun setAudioOutputDevice(index: Int) {}
    override suspend fun setAudioInputDevice(index: Int) {}

    // Helper methods for tests
    fun reset() {
        contactsToReturn = emptyList()
        sendMessageError = null
        sendMessageCallCount = 0
        lastSentMessage = null
        conversationsToReturn = emptyList()
    }
}
