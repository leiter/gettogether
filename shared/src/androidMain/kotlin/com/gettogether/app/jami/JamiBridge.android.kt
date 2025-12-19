package com.gettogether.app.jami

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext

/**
 * Android implementation of JamiBridge using JNI to interface with jami-daemon.
 *
 * This class serves as the bridge between Kotlin code and the native Jami daemon.
 * It loads the native library, initializes the daemon, and provides Kotlin-friendly
 * wrappers around the JNI functions.
 */
class AndroidJamiBridge(private val context: Context) : JamiBridge {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Event flows
    private val _events = MutableSharedFlow<JamiEvent>(replay = 0, extraBufferCapacity = 64)
    private val _accountEvents = MutableSharedFlow<JamiAccountEvent>(replay = 0, extraBufferCapacity = 64)
    private val _callEvents = MutableSharedFlow<JamiCallEvent>(replay = 0, extraBufferCapacity = 64)
    private val _conversationEvents = MutableSharedFlow<JamiConversationEvent>(replay = 0, extraBufferCapacity = 64)
    private val _contactEvents = MutableSharedFlow<JamiContactEvent>(replay = 0, extraBufferCapacity = 64)

    override val events: SharedFlow<JamiEvent> = _events.asSharedFlow()
    override val accountEvents: SharedFlow<JamiAccountEvent> = _accountEvents.asSharedFlow()
    override val callEvents: SharedFlow<JamiCallEvent> = _callEvents.asSharedFlow()
    override val conversationEvents: SharedFlow<JamiConversationEvent> = _conversationEvents.asSharedFlow()
    override val contactEvents: SharedFlow<JamiContactEvent> = _contactEvents.asSharedFlow()

    private var _isDaemonRunning = false

    companion object {
        private const val TAG = "JamiBridge"

        init {
            // Load native library when class is loaded
            try {
                System.loadLibrary("jami")
                System.loadLibrary("jami_jni")
            } catch (e: UnsatisfiedLinkError) {
                android.util.Log.e(TAG, "Failed to load native libraries: ${e.message}")
            }
        }
    }

    // =========================================================================
    // Native JNI methods - to be implemented in C++
    // =========================================================================

    private external fun nativeInit(dataPath: String)
    private external fun nativeStart()
    private external fun nativeStop()
    private external fun nativeIsRunning(): Boolean

    // Account
    private external fun nativeAddAccount(details: Map<String, String>): String
    private external fun nativeRemoveAccount(accountId: String)
    private external fun nativeGetAccountList(): Array<String>
    private external fun nativeGetAccountDetails(accountId: String): Map<String, String>
    private external fun nativeGetVolatileAccountDetails(accountId: String): Map<String, String>
    private external fun nativeSetAccountDetails(accountId: String, details: Map<String, String>)
    private external fun nativeSetAccountActive(accountId: String, active: Boolean)
    private external fun nativeUpdateProfile(accountId: String, displayName: String, avatar: String, fileType: String, flag: Int)
    private external fun nativeRegisterName(accountId: String, name: String, scheme: String, password: String): Boolean
    private external fun nativeLookupName(accountId: String, nameserver: String, name: String): Boolean
    private external fun nativeLookupAddress(accountId: String, nameserver: String, address: String): Boolean
    private external fun nativeExportToFile(accountId: String, destPath: String, scheme: String, password: String): Boolean

    // Contacts
    private external fun nativeGetContacts(accountId: String): Array<Map<String, String>>
    private external fun nativeAddContact(accountId: String, uri: String)
    private external fun nativeRemoveContact(accountId: String, uri: String, ban: Boolean)
    private external fun nativeGetContactDetails(accountId: String, uri: String): Map<String, String>
    private external fun nativeAcceptTrustRequest(accountId: String, from: String)
    private external fun nativeDiscardTrustRequest(accountId: String, from: String)
    private external fun nativeGetTrustRequests(accountId: String): Array<Map<String, String>>

    // Conversations
    private external fun nativeGetConversations(accountId: String): Array<String>
    private external fun nativeStartConversation(accountId: String): String
    private external fun nativeRemoveConversation(accountId: String, conversationId: String): Boolean
    private external fun nativeConversationInfos(accountId: String, conversationId: String): Map<String, String>
    private external fun nativeUpdateConversationInfos(accountId: String, conversationId: String, infos: Map<String, String>)
    private external fun nativeGetConversationMembers(accountId: String, conversationId: String): Array<Map<String, String>>
    private external fun nativeAddConversationMember(accountId: String, conversationId: String, contactUri: String)
    private external fun nativeRemoveConversationMember(accountId: String, conversationId: String, contactUri: String)
    private external fun nativeAcceptConversationRequest(accountId: String, conversationId: String)
    private external fun nativeDeclineConversationRequest(accountId: String, conversationId: String)
    private external fun nativeGetConversationRequests(accountId: String): Array<Map<String, String>>

    // Messaging
    private external fun nativeSendMessage(accountId: String, conversationId: String, message: String, replyTo: String, flag: Int)
    private external fun nativeLoadConversation(accountId: String, conversationId: String, fromMessage: String, n: Int): Int
    private external fun nativeSetIsComposing(accountId: String, conversationUri: String, isWriting: Boolean)
    private external fun nativeSetMessageDisplayed(accountId: String, conversationUri: String, messageId: String, status: Int): Boolean

    // Calls
    private external fun nativePlaceCallWithMedia(accountId: String, to: String, mediaList: Array<Map<String, String>>): String
    private external fun nativeAccept(accountId: String, callId: String)
    private external fun nativeAcceptWithMedia(accountId: String, callId: String, mediaList: Array<Map<String, String>>)
    private external fun nativeRefuse(accountId: String, callId: String)
    private external fun nativeHangUp(accountId: String, callId: String)
    private external fun nativeHold(accountId: String, callId: String)
    private external fun nativeUnhold(accountId: String, callId: String)
    private external fun nativeMuteLocalMedia(accountId: String, callId: String, mediaType: String, mute: Boolean)
    private external fun nativeGetCallDetails(accountId: String, callId: String): Map<String, String>
    private external fun nativeGetCallList(accountId: String): Array<String>

    // Conference
    private external fun nativeCreateConfFromParticipantList(accountId: String, participants: Array<String>)
    private external fun nativeJoinParticipant(accountId: String, callId1: String, accountId2: String, callId2: String)
    private external fun nativeAddParticipant(accountId: String, callId: String, account2Id: String, confId: String)
    private external fun nativeHangUpConference(accountId: String, confId: String)
    private external fun nativeGetConferenceDetails(accountId: String, confId: String): Map<String, String>
    private external fun nativeGetParticipantList(accountId: String, confId: String): Array<String>
    private external fun nativeGetConferenceInfos(accountId: String, confId: String): Array<Map<String, String>>
    private external fun nativeSetConferenceLayout(accountId: String, confId: String, layout: Int)
    private external fun nativeMuteParticipant(accountId: String, confId: String, peerId: String, state: Boolean)
    private external fun nativeHangupParticipant(accountId: String, confId: String, accountUri: String, deviceId: String)

    // Video
    private external fun nativeGetVideoDeviceList(): Array<String>
    private external fun nativeGetCurrentVideoDevice(): String
    private external fun nativeSetVideoDevice(deviceId: String)
    private external fun nativeStartVideo()
    private external fun nativeStopVideo()
    private external fun nativeSwitchInput(accountId: String, callId: String, resource: String)

    // Audio
    private external fun nativeGetAudioOutputDeviceList(): Array<String>
    private external fun nativeGetAudioInputDeviceList(): Array<String>
    private external fun nativeSetAudioOutputDevice(index: Int)
    private external fun nativeSetAudioInputDevice(index: Int)

    // =========================================================================
    // Daemon Lifecycle
    // =========================================================================

    override suspend fun initDaemon(dataPath: String) = withContext(Dispatchers.IO) {
        try {
            nativeInit(dataPath)
        } catch (e: UnsatisfiedLinkError) {
            android.util.Log.e(TAG, "Native library not loaded: ${e.message}")
            throw JamiBridgeException("Failed to initialize daemon: native library not loaded", e)
        }
    }

    override suspend fun startDaemon() = withContext(Dispatchers.IO) {
        try {
            nativeStart()
            _isDaemonRunning = true
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to start daemon: ${e.message}")
            throw JamiBridgeException("Failed to start daemon", e)
        }
    }

    override suspend fun stopDaemon(): Unit = withContext(Dispatchers.IO) {
        try {
            nativeStop()
            _isDaemonRunning = false
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to stop daemon: ${e.message}")
        }
    }

    override fun isDaemonRunning(): Boolean {
        return try {
            nativeIsRunning()
        } catch (e: UnsatisfiedLinkError) {
            _isDaemonRunning
        }
    }

    // =========================================================================
    // Account Management
    // =========================================================================

    override suspend fun createAccount(displayName: String, password: String): String = withContext(Dispatchers.IO) {
        val details = mutableMapOf(
            "Account.type" to "RING",
            "Account.displayName" to displayName,
            "Account.archivePassword" to password
        )
        nativeAddAccount(details)
    }

    override suspend fun importAccount(archivePath: String, password: String): String = withContext(Dispatchers.IO) {
        val details = mutableMapOf(
            "Account.type" to "RING",
            "Account.archivePath" to archivePath,
            "Account.archivePassword" to password
        )
        nativeAddAccount(details)
    }

    override suspend fun exportAccount(accountId: String, destinationPath: String, password: String): Boolean =
        withContext(Dispatchers.IO) {
            nativeExportToFile(accountId, destinationPath, "", password)
        }

    override suspend fun deleteAccount(accountId: String) = withContext(Dispatchers.IO) {
        nativeRemoveAccount(accountId)
    }

    override fun getAccountIds(): List<String> {
        return try {
            nativeGetAccountList().toList()
        } catch (e: UnsatisfiedLinkError) {
            emptyList()
        }
    }

    override fun getAccountDetails(accountId: String): Map<String, String> {
        return try {
            nativeGetAccountDetails(accountId)
        } catch (e: UnsatisfiedLinkError) {
            emptyMap()
        }
    }

    override fun getVolatileAccountDetails(accountId: String): Map<String, String> {
        return try {
            nativeGetVolatileAccountDetails(accountId)
        } catch (e: UnsatisfiedLinkError) {
            emptyMap()
        }
    }

    override suspend fun setAccountDetails(accountId: String, details: Map<String, String>) =
        withContext(Dispatchers.IO) {
            nativeSetAccountDetails(accountId, details)
        }

    override suspend fun setAccountActive(accountId: String, active: Boolean) = withContext(Dispatchers.IO) {
        nativeSetAccountActive(accountId, active)
    }

    override suspend fun updateProfile(accountId: String, displayName: String, avatarPath: String?) =
        withContext(Dispatchers.IO) {
            nativeUpdateProfile(accountId, displayName, avatarPath ?: "", "", 0)
        }

    override suspend fun registerName(accountId: String, name: String, password: String): Boolean =
        withContext(Dispatchers.IO) {
            nativeRegisterName(accountId, name, "", password)
        }

    override suspend fun lookupName(accountId: String, name: String): LookupResult? {
        // This is async - the result comes via callback
        withContext(Dispatchers.IO) {
            nativeLookupName(accountId, "", name)
        }
        return null // Result will come via accountEvents
    }

    override suspend fun lookupAddress(accountId: String, address: String): LookupResult? {
        withContext(Dispatchers.IO) {
            nativeLookupAddress(accountId, "", address)
        }
        return null // Result will come via accountEvents
    }

    // =========================================================================
    // Contact Management
    // =========================================================================

    override fun getContacts(accountId: String): List<JamiContact> {
        return try {
            nativeGetContacts(accountId).map { contactMap ->
                JamiContact(
                    uri = contactMap["uri"] ?: "",
                    displayName = contactMap["displayName"] ?: "",
                    avatarPath = contactMap["avatar"],
                    isConfirmed = contactMap["confirmed"]?.toBoolean() ?: false,
                    isBanned = contactMap["banned"]?.toBoolean() ?: false
                )
            }
        } catch (e: UnsatisfiedLinkError) {
            emptyList()
        }
    }

    override suspend fun addContact(accountId: String, uri: String) = withContext(Dispatchers.IO) {
        nativeAddContact(accountId, uri)
    }

    override suspend fun removeContact(accountId: String, uri: String, ban: Boolean) = withContext(Dispatchers.IO) {
        nativeRemoveContact(accountId, uri, ban)
    }

    override fun getContactDetails(accountId: String, uri: String): Map<String, String> {
        return try {
            nativeGetContactDetails(accountId, uri)
        } catch (e: UnsatisfiedLinkError) {
            emptyMap()
        }
    }

    override suspend fun acceptTrustRequest(accountId: String, uri: String) = withContext(Dispatchers.IO) {
        nativeAcceptTrustRequest(accountId, uri)
    }

    override suspend fun discardTrustRequest(accountId: String, uri: String) = withContext(Dispatchers.IO) {
        nativeDiscardTrustRequest(accountId, uri)
    }

    override fun getTrustRequests(accountId: String): List<TrustRequest> {
        return try {
            nativeGetTrustRequests(accountId).map { reqMap ->
                TrustRequest(
                    from = reqMap["from"] ?: "",
                    conversationId = reqMap["conversationId"] ?: "",
                    payload = ByteArray(0), // TODO: handle payload
                    received = reqMap["received"]?.toLongOrNull() ?: 0L
                )
            }
        } catch (e: UnsatisfiedLinkError) {
            emptyList()
        }
    }

    // =========================================================================
    // Conversation Management
    // =========================================================================

    override fun getConversations(accountId: String): List<String> {
        return try {
            nativeGetConversations(accountId).toList()
        } catch (e: UnsatisfiedLinkError) {
            emptyList()
        }
    }

    override suspend fun startConversation(accountId: String): String = withContext(Dispatchers.IO) {
        nativeStartConversation(accountId)
    }

    override suspend fun removeConversation(accountId: String, conversationId: String): Unit = withContext(Dispatchers.IO) {
        nativeRemoveConversation(accountId, conversationId)
        Unit
    }

    override fun getConversationInfo(accountId: String, conversationId: String): Map<String, String> {
        return try {
            nativeConversationInfos(accountId, conversationId)
        } catch (e: UnsatisfiedLinkError) {
            emptyMap()
        }
    }

    override suspend fun updateConversationInfo(accountId: String, conversationId: String, info: Map<String, String>) =
        withContext(Dispatchers.IO) {
            nativeUpdateConversationInfos(accountId, conversationId, info)
        }

    override fun getConversationMembers(accountId: String, conversationId: String): List<ConversationMember> {
        return try {
            nativeGetConversationMembers(accountId, conversationId).map { memberMap ->
                ConversationMember(
                    uri = memberMap["uri"] ?: "",
                    role = when (memberMap["role"]) {
                        "admin" -> MemberRole.ADMIN
                        "member" -> MemberRole.MEMBER
                        "invited" -> MemberRole.INVITED
                        "banned" -> MemberRole.BANNED
                        else -> MemberRole.MEMBER
                    }
                )
            }
        } catch (e: UnsatisfiedLinkError) {
            emptyList()
        }
    }

    override suspend fun addConversationMember(accountId: String, conversationId: String, contactUri: String) =
        withContext(Dispatchers.IO) {
            nativeAddConversationMember(accountId, conversationId, contactUri)
        }

    override suspend fun removeConversationMember(accountId: String, conversationId: String, contactUri: String) =
        withContext(Dispatchers.IO) {
            nativeRemoveConversationMember(accountId, conversationId, contactUri)
        }

    override suspend fun acceptConversationRequest(accountId: String, conversationId: String) =
        withContext(Dispatchers.IO) {
            nativeAcceptConversationRequest(accountId, conversationId)
        }

    override suspend fun declineConversationRequest(accountId: String, conversationId: String) =
        withContext(Dispatchers.IO) {
            nativeDeclineConversationRequest(accountId, conversationId)
        }

    override fun getConversationRequests(accountId: String): List<ConversationRequest> {
        return try {
            nativeGetConversationRequests(accountId).map { reqMap ->
                ConversationRequest(
                    conversationId = reqMap["id"] ?: "",
                    from = reqMap["from"] ?: "",
                    metadata = reqMap,
                    received = reqMap["received"]?.toLongOrNull() ?: 0L
                )
            }
        } catch (e: UnsatisfiedLinkError) {
            emptyList()
        }
    }

    // =========================================================================
    // Messaging
    // =========================================================================

    override suspend fun sendMessage(
        accountId: String,
        conversationId: String,
        message: String,
        replyTo: String?
    ): String = withContext(Dispatchers.IO) {
        nativeSendMessage(accountId, conversationId, message, replyTo ?: "", 0)
        "" // Message ID comes via callback
    }

    override suspend fun loadConversationMessages(
        accountId: String,
        conversationId: String,
        fromMessage: String,
        count: Int
    ): Int = withContext(Dispatchers.IO) {
        nativeLoadConversation(accountId, conversationId, fromMessage, count)
    }

    override suspend fun setIsComposing(accountId: String, conversationId: String, isComposing: Boolean) =
        withContext(Dispatchers.IO) {
            nativeSetIsComposing(accountId, conversationId, isComposing)
        }

    override suspend fun setMessageDisplayed(accountId: String, conversationId: String, messageId: String): Unit =
        withContext(Dispatchers.IO) {
            nativeSetMessageDisplayed(accountId, conversationId, messageId, 3) // 3 = displayed
            Unit
        }

    // =========================================================================
    // Calls
    // =========================================================================

    override suspend fun placeCall(accountId: String, uri: String, withVideo: Boolean): String =
        withContext(Dispatchers.IO) {
            val mediaList = buildMediaList(withVideo)
            nativePlaceCallWithMedia(accountId, uri, mediaList.toTypedArray())
        }

    override suspend fun acceptCall(accountId: String, callId: String, withVideo: Boolean) =
        withContext(Dispatchers.IO) {
            if (withVideo) {
                val mediaList = buildMediaList(true)
                nativeAcceptWithMedia(accountId, callId, mediaList.toTypedArray())
            } else {
                nativeAccept(accountId, callId)
            }
        }

    override suspend fun refuseCall(accountId: String, callId: String) = withContext(Dispatchers.IO) {
        nativeRefuse(accountId, callId)
    }

    override suspend fun hangUp(accountId: String, callId: String) = withContext(Dispatchers.IO) {
        nativeHangUp(accountId, callId)
    }

    override suspend fun holdCall(accountId: String, callId: String) = withContext(Dispatchers.IO) {
        nativeHold(accountId, callId)
    }

    override suspend fun unholdCall(accountId: String, callId: String) = withContext(Dispatchers.IO) {
        nativeUnhold(accountId, callId)
    }

    override suspend fun muteAudio(accountId: String, callId: String, muted: Boolean) = withContext(Dispatchers.IO) {
        nativeMuteLocalMedia(accountId, callId, "MEDIA_TYPE_AUDIO", muted)
    }

    override suspend fun muteVideo(accountId: String, callId: String, muted: Boolean) = withContext(Dispatchers.IO) {
        nativeMuteLocalMedia(accountId, callId, "MEDIA_TYPE_VIDEO", muted)
    }

    override fun getCallDetails(accountId: String, callId: String): Map<String, String> {
        return try {
            nativeGetCallDetails(accountId, callId)
        } catch (e: UnsatisfiedLinkError) {
            emptyMap()
        }
    }

    override fun getActiveCalls(accountId: String): List<String> {
        return try {
            nativeGetCallList(accountId).toList()
        } catch (e: UnsatisfiedLinkError) {
            emptyList()
        }
    }

    override suspend fun switchCamera() = withContext(Dispatchers.IO) {
        val devices = nativeGetVideoDeviceList()
        val current = nativeGetCurrentVideoDevice()
        val nextIndex = (devices.indexOf(current) + 1) % devices.size
        if (devices.isNotEmpty()) {
            nativeSetVideoDevice(devices[nextIndex])
        }
    }

    override suspend fun switchAudioOutput(useSpeaker: Boolean) = withContext(Dispatchers.IO) {
        // This is handled at the Android AudioManager level
        // The native daemon doesn't directly control Android audio routing
    }

    // =========================================================================
    // Conference Calls
    // =========================================================================

    override suspend fun createConference(accountId: String, participantUris: List<String>): String =
        withContext(Dispatchers.IO) {
            nativeCreateConfFromParticipantList(accountId, participantUris.toTypedArray())
            "" // Conference ID comes via callback
        }

    override suspend fun joinParticipant(
        accountId: String,
        callId1: String,
        accountId2: String,
        callId2: String
    ) = withContext(Dispatchers.IO) {
        nativeJoinParticipant(accountId, callId1, accountId2, callId2)
    }

    override suspend fun addParticipantToConference(
        accountId: String,
        callId: String,
        conferenceAccountId: String,
        conferenceId: String
    ) = withContext(Dispatchers.IO) {
        nativeAddParticipant(accountId, callId, conferenceAccountId, conferenceId)
    }

    override suspend fun hangUpConference(accountId: String, conferenceId: String) = withContext(Dispatchers.IO) {
        nativeHangUpConference(accountId, conferenceId)
    }

    override fun getConferenceDetails(accountId: String, conferenceId: String): Map<String, String> {
        return try {
            nativeGetConferenceDetails(accountId, conferenceId)
        } catch (e: UnsatisfiedLinkError) {
            emptyMap()
        }
    }

    override fun getConferenceParticipants(accountId: String, conferenceId: String): List<String> {
        return try {
            nativeGetParticipantList(accountId, conferenceId).toList()
        } catch (e: UnsatisfiedLinkError) {
            emptyList()
        }
    }

    override fun getConferenceInfos(accountId: String, conferenceId: String): List<Map<String, String>> {
        return try {
            nativeGetConferenceInfos(accountId, conferenceId).toList()
        } catch (e: UnsatisfiedLinkError) {
            emptyList()
        }
    }

    override suspend fun setConferenceLayout(accountId: String, conferenceId: String, layout: ConferenceLayout) =
        withContext(Dispatchers.IO) {
            val layoutInt = when (layout) {
                ConferenceLayout.GRID -> 0
                ConferenceLayout.ONE_BIG -> 1
                ConferenceLayout.ONE_BIG_SMALL -> 2
            }
            nativeSetConferenceLayout(accountId, conferenceId, layoutInt)
        }

    override suspend fun muteConferenceParticipant(
        accountId: String,
        conferenceId: String,
        participantUri: String,
        muted: Boolean
    ) = withContext(Dispatchers.IO) {
        nativeMuteParticipant(accountId, conferenceId, participantUri, muted)
    }

    override suspend fun hangUpConferenceParticipant(
        accountId: String,
        conferenceId: String,
        participantUri: String,
        deviceId: String
    ) = withContext(Dispatchers.IO) {
        nativeHangupParticipant(accountId, conferenceId, participantUri, deviceId)
    }

    // =========================================================================
    // File Transfer
    // =========================================================================

    override suspend fun sendFile(
        accountId: String,
        conversationId: String,
        filePath: String,
        displayName: String
    ): String = withContext(Dispatchers.IO) {
        // File transfer is handled via sendMessage with file attachment
        nativeSendMessage(accountId, conversationId, "", "", 1) // flag 1 = file
        ""
    }

    override suspend fun acceptFileTransfer(
        accountId: String,
        conversationId: String,
        fileId: String,
        destinationPath: String
    ) = withContext(Dispatchers.IO) {
        // TODO: Implement file transfer acceptance
    }

    override suspend fun cancelFileTransfer(
        accountId: String,
        conversationId: String,
        fileId: String
    ) = withContext(Dispatchers.IO) {
        // TODO: Implement file transfer cancellation
    }

    override fun getFileTransferInfo(
        accountId: String,
        conversationId: String,
        fileId: String
    ): FileTransferInfo? {
        // TODO: Implement file transfer info retrieval
        return null
    }

    // =========================================================================
    // Video
    // =========================================================================

    override fun getVideoDevices(): List<String> {
        return try {
            nativeGetVideoDeviceList().toList()
        } catch (e: UnsatisfiedLinkError) {
            emptyList()
        }
    }

    override fun getCurrentVideoDevice(): String {
        return try {
            nativeGetCurrentVideoDevice()
        } catch (e: UnsatisfiedLinkError) {
            ""
        }
    }

    override suspend fun setVideoDevice(deviceId: String) = withContext(Dispatchers.IO) {
        nativeSetVideoDevice(deviceId)
    }

    override suspend fun startVideo() = withContext(Dispatchers.IO) {
        nativeStartVideo()
    }

    override suspend fun stopVideo() = withContext(Dispatchers.IO) {
        nativeStopVideo()
    }

    // =========================================================================
    // Audio Settings
    // =========================================================================

    override fun getAudioOutputDevices(): List<String> {
        return try {
            nativeGetAudioOutputDeviceList().toList()
        } catch (e: UnsatisfiedLinkError) {
            emptyList()
        }
    }

    override fun getAudioInputDevices(): List<String> {
        return try {
            nativeGetAudioInputDeviceList().toList()
        } catch (e: UnsatisfiedLinkError) {
            emptyList()
        }
    }

    override suspend fun setAudioOutputDevice(index: Int) = withContext(Dispatchers.IO) {
        nativeSetAudioOutputDevice(index)
    }

    override suspend fun setAudioInputDevice(index: Int) = withContext(Dispatchers.IO) {
        nativeSetAudioInputDevice(index)
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private fun buildMediaList(withVideo: Boolean): List<Map<String, String>> {
        val mediaList = mutableListOf<Map<String, String>>()

        // Audio media
        mediaList.add(
            mapOf(
                "MEDIA_TYPE" to "MEDIA_TYPE_AUDIO",
                "ENABLED" to "true",
                "MUTED" to "false",
                "SOURCE" to ""
            )
        )

        // Video media
        if (withVideo) {
            mediaList.add(
                mapOf(
                    "MEDIA_TYPE" to "MEDIA_TYPE_VIDEO",
                    "ENABLED" to "true",
                    "MUTED" to "false",
                    "SOURCE" to "camera://0"
                )
            )
        }

        return mediaList
    }

    // =========================================================================
    // JNI Callbacks (called from native code)
    // =========================================================================

    /**
     * Called from JNI when account registration state changes.
     */
    @Suppress("unused")
    private fun onRegistrationStateChanged(accountId: String, state: String, code: Int, detail: String) {
        val regState = parseRegistrationState(state)
        val event = JamiAccountEvent.RegistrationStateChanged(accountId, regState, code, detail)
        _accountEvents.tryEmit(event)
        _events.tryEmit(event)
    }

    /**
     * Called from JNI when an incoming call is received.
     */
    @Suppress("unused")
    private fun onIncomingCall(accountId: String, callId: String, from: String, mediaList: Array<Map<String, String>>) {
        val hasVideo = mediaList.any { it["MEDIA_TYPE"] == "MEDIA_TYPE_VIDEO" }
        val event = JamiCallEvent.IncomingCall(accountId, callId, from, "", hasVideo)
        _callEvents.tryEmit(event)
        _events.tryEmit(event)
    }

    /**
     * Called from JNI when call state changes.
     */
    @Suppress("unused")
    private fun onCallStateChanged(accountId: String, callId: String, state: String, code: Int) {
        val callState = parseCallState(state)
        val event = JamiCallEvent.CallStateChanged(accountId, callId, callState, code)
        _callEvents.tryEmit(event)
        _events.tryEmit(event)
    }

    /**
     * Called from JNI when a message is received.
     */
    @Suppress("unused")
    private fun onSwarmMessageReceived(accountId: String, conversationId: String, message: Map<String, Any>) {
        @Suppress("UNCHECKED_CAST")
        val swarmMessage = SwarmMessage(
            id = message["id"] as? String ?: "",
            type = message["type"] as? String ?: "",
            author = message["author"] as? String ?: "",
            body = message["body"] as? Map<String, String> ?: emptyMap(),
            reactions = (message["reactions"] as? List<Map<String, String>>) ?: emptyList(),
            timestamp = (message["timestamp"] as? Long) ?: 0L,
            replyTo = message["replyTo"] as? String,
            status = (message["status"] as? Map<String, Int>) ?: emptyMap()
        )
        val event = JamiConversationEvent.MessageReceived(accountId, conversationId, swarmMessage)
        _conversationEvents.tryEmit(event)
        _events.tryEmit(event)
    }

    /**
     * Called from JNI when a conversation is ready.
     */
    @Suppress("unused")
    private fun onConversationReady(accountId: String, conversationId: String) {
        val event = JamiConversationEvent.ConversationReady(accountId, conversationId)
        _conversationEvents.tryEmit(event)
        _events.tryEmit(event)
    }

    /**
     * Called from JNI when a contact is added.
     */
    @Suppress("unused")
    private fun onContactAdded(accountId: String, uri: String, confirmed: Boolean) {
        val event = JamiContactEvent.ContactAdded(accountId, uri, confirmed)
        _contactEvents.tryEmit(event)
        _events.tryEmit(event)
    }

    private fun parseRegistrationState(state: String): RegistrationState {
        return when (state.uppercase()) {
            "UNREGISTERED" -> RegistrationState.UNREGISTERED
            "TRYING" -> RegistrationState.TRYING
            "REGISTERED" -> RegistrationState.REGISTERED
            "ERROR_GENERIC" -> RegistrationState.ERROR_GENERIC
            "ERROR_AUTH" -> RegistrationState.ERROR_AUTH
            "ERROR_NETWORK" -> RegistrationState.ERROR_NETWORK
            "ERROR_HOST" -> RegistrationState.ERROR_HOST
            "ERROR_SERVICE_UNAVAILABLE" -> RegistrationState.ERROR_SERVICE_UNAVAILABLE
            "ERROR_NEED_MIGRATION" -> RegistrationState.ERROR_NEED_MIGRATION
            "INITIALIZING" -> RegistrationState.INITIALIZING
            else -> RegistrationState.UNREGISTERED
        }
    }

    private fun parseCallState(state: String): CallState {
        return when (state.uppercase()) {
            "INACTIVE" -> CallState.INACTIVE
            "INCOMING" -> CallState.INCOMING
            "CONNECTING" -> CallState.CONNECTING
            "RINGING" -> CallState.RINGING
            "CURRENT" -> CallState.CURRENT
            "HUNGUP" -> CallState.HUNGUP
            "BUSY" -> CallState.BUSY
            "FAILURE" -> CallState.FAILURE
            "HOLD" -> CallState.HOLD
            "UNHOLD" -> CallState.UNHOLD
            "OVER" -> CallState.OVER
            else -> CallState.INACTIVE
        }
    }
}

class JamiBridgeException(message: String, cause: Throwable? = null) : Exception(message, cause)

actual fun createJamiBridge(): JamiBridge {
    // Context will be provided via DI
    throw IllegalStateException("Use Koin to inject AndroidJamiBridge with context")
}
