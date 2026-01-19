@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.gettogether.app.jami

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryOptionAllowBluetooth
import platform.AVFAudio.AVAudioSessionCategoryOptionDefaultToSpeaker
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.AVAudioSessionModeVoiceChat
import platform.AVFAudio.AVAudioSessionPortOverrideNone
import platform.AVFAudio.AVAudioSessionPortOverrideSpeaker
import platform.Foundation.NSDate
import platform.Foundation.NSLog
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUUID
import platform.Foundation.timeIntervalSince1970

/**
 * iOS implementation of JamiBridge.
 *
 * This implementation provides mock data for testing the UI while the native
 * daemon integration is pending. The JamiBridgeWrapper Objective-C files have
 * been created and can be integrated via Xcode's bridging header once the
 * cinterop issues are resolved.
 *
 * Current approach: Mock implementation that allows the app to build and run.
 */
class IOSJamiBridge : JamiBridge {

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

    // Mock state
    private var _isDaemonRunning = false
    private val mockAccounts = mutableListOf<String>()
    private val mockAccountDetails = mutableMapOf<String, MutableMap<String, String>>()
    private val mockContacts = mutableMapOf<String, MutableList<JamiContact>>()
    private val mockConversations = mutableMapOf<String, MutableList<String>>()

    // Audio/Video state - lazy initialization to avoid init-time crashes
    private val audioSession by lazy { AVAudioSession.sharedInstance() }
    private var currentVideoDeviceId: String = "front"
    private var isVideoActive = false
    private var isSpeakerEnabled = false
    private var currentAudioOutputIndex = 0
    private var currentAudioInputIndex = 0
    private var audioSessionConfigured = false

    companion object {
        private const val TAG = "JamiBridge-iOS"

        // Video device IDs
        private const val VIDEO_DEVICE_FRONT = "front"
        private const val VIDEO_DEVICE_BACK = "back"

        // UserDefaults keys for persistence
        private const val KEY_ACCOUNTS = "jami_mock_accounts"
        private const val KEY_ACCOUNT_DETAILS_PREFIX = "jami_mock_account_details_"
    }

    private val userDefaults = NSUserDefaults.standardUserDefaults

    init {
        NSLog("$TAG: IOSJamiBridge initialized")
        // Load persisted accounts
        loadPersistedAccounts()
        // Don't configure audio session in init - do it lazily when needed
    }

    @Suppress("UNCHECKED_CAST")
    private fun loadPersistedAccounts() {
        try {
            val accountIdsArray = userDefaults.arrayForKey(KEY_ACCOUNTS) as? List<*>
            if (accountIdsArray != null) {
                mockAccounts.clear()
                for (item in accountIdsArray) {
                    val accountId = item as? String
                    if (accountId != null) {
                        mockAccounts.add(accountId)

                        // Load account details
                        val detailsDict = userDefaults.dictionaryForKey("$KEY_ACCOUNT_DETAILS_PREFIX$accountId") as? Map<*, *>
                        if (detailsDict != null) {
                            val details = mutableMapOf<String, String>()
                            for ((key, value) in detailsDict) {
                                if (key is String && value is String) {
                                    details[key] = value
                                }
                            }
                            mockAccountDetails[accountId] = details
                        }

                        // Initialize empty contacts/conversations
                        mockContacts[accountId] = mutableListOf()
                        mockConversations[accountId] = mutableListOf()
                    }
                }
            }
            NSLog("$TAG: Loaded ${mockAccounts.size} persisted accounts")
        } catch (e: Exception) {
            NSLog("$TAG: Failed to load persisted accounts: ${e.message}")
        }
    }

    private fun persistAccounts() {
        try {
            userDefaults.setObject(mockAccounts.toList(), KEY_ACCOUNTS)
            for (accountId in mockAccounts) {
                mockAccountDetails[accountId]?.let { details ->
                    userDefaults.setObject(details.toMap(), "$KEY_ACCOUNT_DETAILS_PREFIX$accountId")
                }
            }
            userDefaults.synchronize()
            NSLog("$TAG: Persisted ${mockAccounts.size} accounts")
        } catch (e: Exception) {
            NSLog("$TAG: Failed to persist accounts: ${e.message}")
        }
    }

    private fun configureAudioSessionIfNeeded() {
        if (audioSessionConfigured) return
        try {
            audioSession.setCategory(
                AVAudioSessionCategoryPlayAndRecord,
                mode = AVAudioSessionModeVoiceChat,
                options = AVAudioSessionCategoryOptionAllowBluetooth or
                         AVAudioSessionCategoryOptionDefaultToSpeaker,
                error = null
            )
            audioSessionConfigured = true
            NSLog("$TAG: Audio session configured for VoIP")
        } catch (e: Exception) {
            NSLog("$TAG: Failed to configure audio session: ${e.message}")
        }
    }

    // =========================================================================
    // Daemon Lifecycle
    // =========================================================================

    override suspend fun initDaemon(dataPath: String) {
        withContext(Dispatchers.Default) {
            NSLog("$TAG: initDaemon with path: $dataPath")
        }
    }

    override suspend fun startDaemon() {
        withContext(Dispatchers.Default) {
            NSLog("$TAG: startDaemon")
            _isDaemonRunning = true
        }
    }

    override suspend fun stopDaemon() {
        withContext(Dispatchers.Default) {
            NSLog("$TAG: stopDaemon")
            _isDaemonRunning = false
        }
    }

    override fun isDaemonRunning(): Boolean = _isDaemonRunning

    // =========================================================================
    // Account Management
    // =========================================================================

    override suspend fun createAccount(displayName: String, password: String): String = withContext(Dispatchers.Default) {
        NSLog("$TAG: createAccount: $displayName")
        val accountId = generateId()
        mockAccounts.add(accountId)
        mockAccountDetails[accountId] = mutableMapOf(
            "Account.displayName" to displayName,
            "Account.alias" to displayName,
            "Account.type" to "JAMI",
            "Account.enable" to "true",
            "Account.username" to "jami:$accountId"
        )
        mockContacts[accountId] = mutableListOf()
        mockConversations[accountId] = mutableListOf()

        // Persist to UserDefaults
        persistAccounts()

        // Emit registration events
        _accountEvents.tryEmit(JamiAccountEvent.RegistrationStateChanged(
            accountId, RegistrationState.TRYING, 0, ""
        ))
        _accountEvents.tryEmit(JamiAccountEvent.RegistrationStateChanged(
            accountId, RegistrationState.REGISTERED, 0, ""
        ))

        accountId
    }

    override suspend fun importAccount(archivePath: String, password: String): String = withContext(Dispatchers.Default) {
        NSLog("$TAG: importAccount from: $archivePath")
        createAccount("Imported Account", password)
    }

    override suspend fun exportAccount(accountId: String, destinationPath: String, password: String): Boolean =
        withContext(Dispatchers.Default) {
            NSLog("$TAG: exportAccount: $accountId to $destinationPath")
            true
        }

    override suspend fun deleteAccount(accountId: String) {
        withContext(Dispatchers.Default) {
            NSLog("$TAG: deleteAccount: $accountId")
            mockAccounts.remove(accountId)
            mockAccountDetails.remove(accountId)
            mockContacts.remove(accountId)
            mockConversations.remove(accountId)

            // Remove persisted details and update accounts list
            userDefaults.removeObjectForKey("$KEY_ACCOUNT_DETAILS_PREFIX$accountId")
            persistAccounts()
        }
    }

    override fun getAccountIds(): List<String> = mockAccounts.toList()

    override fun getAccountDetails(accountId: String): Map<String, String> =
        mockAccountDetails[accountId]?.toMap() ?: emptyMap()

    override fun getVolatileAccountDetails(accountId: String): Map<String, String> {
        return if (mockAccounts.contains(accountId)) {
            mapOf(
                "Account.registrationStatus" to "REGISTERED",
                "Account.registrationStateCode" to "0"
            )
        } else {
            emptyMap()
        }
    }

    override suspend fun setAccountDetails(accountId: String, details: Map<String, String>) {
        withContext(Dispatchers.Default) {
            NSLog("$TAG: setAccountDetails: $accountId")
            mockAccountDetails[accountId]?.putAll(details)
            _accountEvents.tryEmit(JamiAccountEvent.AccountDetailsChanged(
                accountId, mockAccountDetails[accountId]?.toMap() ?: emptyMap()
            ))
        }
    }

    override suspend fun setAccountActive(accountId: String, active: Boolean) {
        withContext(Dispatchers.Default) {
            NSLog("$TAG: setAccountActive: $accountId = $active")
            val state = if (active) RegistrationState.REGISTERED else RegistrationState.UNREGISTERED
            _accountEvents.tryEmit(JamiAccountEvent.RegistrationStateChanged(accountId, state, 0, ""))
        }
    }

    override suspend fun connectivityChanged() {
        withContext(Dispatchers.Default) {
            NSLog("$TAG: connectivityChanged (stub)")
        }
    }

    override suspend fun updateProfile(accountId: String, displayName: String, avatarPath: String?) {
        withContext(Dispatchers.Default) {
            NSLog("$TAG: updateProfile: $accountId, name=$displayName")
            mockAccountDetails[accountId]?.set("Account.displayName", displayName)
            avatarPath?.let { mockAccountDetails[accountId]?.set("Account.avatar", it) }
        }
    }

    override suspend fun registerName(accountId: String, name: String, password: String): Boolean =
        withContext(Dispatchers.Default) {
            NSLog("$TAG: registerName: $name for $accountId")
            _accountEvents.tryEmit(JamiAccountEvent.NameRegistrationEnded(accountId, 0, name))
            true
        }

    override suspend fun lookupName(accountId: String, name: String): LookupResult? {
        NSLog("$TAG: lookupName: $name")
        _accountEvents.tryEmit(JamiAccountEvent.RegisteredNameFound(
            accountId, LookupState.NOT_FOUND, "", name
        ))
        return LookupResult("", name, LookupState.NOT_FOUND)
    }

    override suspend fun lookupAddress(accountId: String, address: String): LookupResult? {
        NSLog("$TAG: lookupAddress: $address")
        return LookupResult(address, "", LookupState.NOT_FOUND)
    }

    // =========================================================================
    // Contact Management
    // =========================================================================

    override fun getContacts(accountId: String): List<JamiContact> =
        mockContacts[accountId]?.toList() ?: emptyList()

    override suspend fun addContact(accountId: String, uri: String) {
        withContext(Dispatchers.Default) {
            NSLog("$TAG: ⚠️ [MOCK WARNING] addContact() - NO NETWORK REQUEST SENT")
            NSLog("$TAG: ⚠️ This is a MOCK implementation. No contact request is sent over the Jami network.")
            NSLog("$TAG: ⚠️ Real cross-platform contact requests require native iOS Jami daemon integration.")
            NSLog("$TAG: addContact: accountId=$accountId, uri=$uri")

            val contact = JamiContact(
                uri = uri,
                displayName = "Contact ${uri.take(8)}",
                avatarPath = null,
                isConfirmed = false,
                isBanned = false
            )
            mockContacts[accountId]?.add(contact)
            _contactEvents.tryEmit(JamiContactEvent.ContactAdded(accountId, uri, false))
            NSLog("$TAG: ✓ Mock contact added locally (not sent to network)")
        }
    }

    override suspend fun removeContact(accountId: String, uri: String, ban: Boolean) {
        withContext(Dispatchers.Default) {
            NSLog("$TAG: removeContact: $uri, ban=$ban")
            mockContacts[accountId]?.removeAll { it.uri == uri }
            _contactEvents.tryEmit(JamiContactEvent.ContactRemoved(accountId, uri, ban))
        }
    }

    override fun getContactDetails(accountId: String, uri: String): Map<String, String> {
        val contact = mockContacts[accountId]?.find { it.uri == uri }
        return contact?.let {
            mapOf(
                "uri" to it.uri,
                "displayName" to it.displayName,
                "confirmed" to it.isConfirmed.toString(),
                "banned" to it.isBanned.toString()
            )
        } ?: emptyMap()
    }

    override suspend fun acceptTrustRequest(accountId: String, uri: String) {
        withContext(Dispatchers.Default) {
            NSLog("$TAG: ⚠️ [MOCK WARNING] acceptTrustRequest() - MOCK-ONLY OPERATION")
            NSLog("$TAG: ⚠️ This is a MOCK implementation. No acceptance is sent over the Jami network.")
            NSLog("$TAG: ⚠️ Real trust request acceptance requires native iOS Jami daemon integration.")
            NSLog("$TAG: acceptTrustRequest: accountId=$accountId, from=$uri")

            mockContacts[accountId]?.find { it.uri == uri }?.let {
                val updated = it.copy(isConfirmed = true)
                mockContacts[accountId]?.remove(it)
                mockContacts[accountId]?.add(updated)
            }
            _contactEvents.tryEmit(JamiContactEvent.ContactAdded(accountId, uri, true))
            NSLog("$TAG: ✓ Mock trust request accepted locally (not sent to network)")
        }
    }

    override suspend fun discardTrustRequest(accountId: String, uri: String) {
        withContext(Dispatchers.Default) {
            NSLog("$TAG: ⚠️ [MOCK WARNING] discardTrustRequest() - MOCK-ONLY OPERATION")
            NSLog("$TAG: ⚠️ This is a MOCK implementation. No discard is sent over the Jami network.")
            NSLog("$TAG: ⚠️ Real trust request discard requires native iOS Jami daemon integration.")
            NSLog("$TAG: discardTrustRequest: accountId=$accountId, from=$uri")
            NSLog("$TAG: ✓ Mock trust request discarded locally (not sent to network)")
        }
    }

    override fun getTrustRequests(accountId: String): List<TrustRequest> {
        NSLog("$TAG: ⚠️ [MOCK WARNING] getTrustRequests() - ALWAYS RETURNS EMPTY LIST")
        NSLog("$TAG: ⚠️ This is a MOCK implementation. Real trust requests are not fetched from the network.")
        NSLog("$TAG: ⚠️ Cross-platform contact requests will NOT appear here until native iOS integration is complete.")
        NSLog("$TAG: getTrustRequests: accountId=$accountId, returning empty list")
        return emptyList()
    }

    override suspend fun subscribeBuddy(accountId: String, uri: String, flag: Boolean) {
        withContext(Dispatchers.Default) {
            NSLog("$TAG: subscribeBuddy: $uri, flag=$flag")
            // Emit a presence event for UI testing
            if (flag) {
                _contactEvents.tryEmit(JamiContactEvent.PresenceChanged(accountId, uri, false))
            }
        }
    }

    override suspend fun publishPresence(accountId: String, isOnline: Boolean, note: String) {
        withContext(Dispatchers.Default) {
            NSLog("$TAG: publishPresence: accountId=${accountId.take(16)}..., isOnline=$isOnline, note=$note")
            NSLog("$TAG: ⚠️ Presence publishing not implemented for iOS (stub)")
        }
    }

    // =========================================================================
    // Conversation Management
    // =========================================================================

    override fun getConversations(accountId: String): List<String> =
        mockConversations[accountId]?.toList() ?: emptyList()

    override suspend fun startConversation(accountId: String): String = withContext(Dispatchers.Default) {
        NSLog("$TAG: startConversation")
        val conversationId = generateId()
        mockConversations[accountId]?.add(conversationId)
        _conversationEvents.tryEmit(JamiConversationEvent.ConversationReady(accountId, conversationId))
        conversationId
    }

    override suspend fun removeConversation(accountId: String, conversationId: String) {
        withContext(Dispatchers.Default) {
            NSLog("$TAG: removeConversation: $conversationId")
            mockConversations[accountId]?.remove(conversationId)
            _conversationEvents.tryEmit(JamiConversationEvent.ConversationRemoved(accountId, conversationId))
        }
    }

    override fun getConversationInfo(accountId: String, conversationId: String): Map<String, String> =
        mapOf("id" to conversationId, "title" to "Conversation", "mode" to "0")

    override suspend fun updateConversationInfo(accountId: String, conversationId: String, info: Map<String, String>) {
        withContext(Dispatchers.Default) {
            NSLog("$TAG: updateConversationInfo: $conversationId")
            _conversationEvents.tryEmit(JamiConversationEvent.ConversationProfileUpdated(
                accountId, conversationId, info
            ))
        }
    }

    override fun getConversationMembers(accountId: String, conversationId: String): List<ConversationMember> {
        val selfUri = mockAccountDetails[accountId]?.get("Account.username") ?: ""
        return listOf(ConversationMember(selfUri, MemberRole.ADMIN))
    }

    override suspend fun addConversationMember(accountId: String, conversationId: String, contactUri: String) {
        withContext(Dispatchers.Default) {
            NSLog("$TAG: addConversationMember: $contactUri to $conversationId")
            _conversationEvents.tryEmit(JamiConversationEvent.ConversationMemberEvent(
                accountId, conversationId, contactUri, MemberEventType.JOIN
            ))
        }
    }

    override suspend fun removeConversationMember(accountId: String, conversationId: String, contactUri: String) {
        withContext(Dispatchers.Default) {
            NSLog("$TAG: removeConversationMember: $contactUri from $conversationId")
            _conversationEvents.tryEmit(JamiConversationEvent.ConversationMemberEvent(
                accountId, conversationId, contactUri, MemberEventType.LEAVE
            ))
        }
    }

    override suspend fun acceptConversationRequest(accountId: String, conversationId: String) {
        withContext(Dispatchers.Default) {
            NSLog("$TAG: acceptConversationRequest: $conversationId")
            mockConversations[accountId]?.add(conversationId)
            _conversationEvents.tryEmit(JamiConversationEvent.ConversationReady(accountId, conversationId))
        }
    }

    override suspend fun declineConversationRequest(accountId: String, conversationId: String) {
        withContext(Dispatchers.Default) {
            NSLog("$TAG: declineConversationRequest: $conversationId")
        }
    }

    override fun getConversationRequests(accountId: String): List<ConversationRequest> = emptyList()

    // =========================================================================
    // Messaging
    // =========================================================================

    override suspend fun sendMessage(
        accountId: String,
        conversationId: String,
        message: String,
        replyTo: String?
    ): String = withContext(Dispatchers.Default) {
        NSLog("$TAG: sendMessage to $conversationId: $message")
        val messageId = generateId()
        val selfUri = mockAccountDetails[accountId]?.get("Account.username") ?: ""
        val swarmMessage = SwarmMessage(
            id = messageId,
            type = "text/plain",
            author = selfUri,
            body = mapOf("body" to message),
            reactions = emptyList(),
            timestamp = currentTimestamp(),
            replyTo = replyTo,
            status = emptyMap()
        )
        _conversationEvents.tryEmit(JamiConversationEvent.MessageReceived(accountId, conversationId, swarmMessage))
        messageId
    }

    override suspend fun loadConversationMessages(
        accountId: String,
        conversationId: String,
        fromMessage: String,
        count: Int
    ): Int = withContext(Dispatchers.Default) {
        NSLog("$TAG: loadConversationMessages: $conversationId, count=$count")
        val requestId = (0..Int.MAX_VALUE).random()
        _conversationEvents.tryEmit(JamiConversationEvent.MessagesLoaded(
            requestId, accountId, conversationId, emptyList()
        ))
        requestId
    }

    override suspend fun setIsComposing(accountId: String, conversationId: String, isComposing: Boolean) {
        withContext(Dispatchers.Default) { }
    }

    override suspend fun setMessageDisplayed(accountId: String, conversationId: String, messageId: String) {
        withContext(Dispatchers.Default) { }
    }

    // =========================================================================
    // Calls
    // =========================================================================

    override suspend fun placeCall(accountId: String, uri: String, withVideo: Boolean): String =
        withContext(Dispatchers.Default) {
            NSLog("$TAG: placeCall to $uri, video=$withVideo")
            val callId = generateId()
            _callEvents.tryEmit(JamiCallEvent.CallStateChanged(accountId, callId, CallState.CONNECTING, 0))
            _callEvents.tryEmit(JamiCallEvent.CallStateChanged(accountId, callId, CallState.RINGING, 0))
            callId
        }

    override suspend fun acceptCall(accountId: String, callId: String, withVideo: Boolean) {
        withContext(Dispatchers.Default) {
            NSLog("$TAG: acceptCall: $callId")
            _callEvents.tryEmit(JamiCallEvent.CallStateChanged(accountId, callId, CallState.CURRENT, 0))
        }
    }

    override suspend fun refuseCall(accountId: String, callId: String) {
        withContext(Dispatchers.Default) {
            NSLog("$TAG: refuseCall: $callId")
            _callEvents.tryEmit(JamiCallEvent.CallStateChanged(accountId, callId, CallState.OVER, 0))
        }
    }

    override suspend fun hangUp(accountId: String, callId: String) {
        withContext(Dispatchers.Default) {
            NSLog("$TAG: hangUp: $callId")
            _callEvents.tryEmit(JamiCallEvent.CallStateChanged(accountId, callId, CallState.OVER, 0))
        }
    }

    override suspend fun holdCall(accountId: String, callId: String) {
        withContext(Dispatchers.Default) {
            NSLog("$TAG: holdCall: $callId")
            _callEvents.tryEmit(JamiCallEvent.CallStateChanged(accountId, callId, CallState.HOLD, 0))
        }
    }

    override suspend fun unholdCall(accountId: String, callId: String) {
        withContext(Dispatchers.Default) {
            NSLog("$TAG: unholdCall: $callId")
            _callEvents.tryEmit(JamiCallEvent.CallStateChanged(accountId, callId, CallState.CURRENT, 0))
        }
    }

    override suspend fun muteAudio(accountId: String, callId: String, muted: Boolean) {
        withContext(Dispatchers.Default) {
            NSLog("$TAG: muteAudio: $callId = $muted")
            _callEvents.tryEmit(JamiCallEvent.AudioMuted(callId, muted))
        }
    }

    override suspend fun muteVideo(accountId: String, callId: String, muted: Boolean) {
        withContext(Dispatchers.Default) {
            NSLog("$TAG: muteVideo: $callId = $muted")
            _callEvents.tryEmit(JamiCallEvent.VideoMuted(callId, muted))
        }
    }

    override fun getCallDetails(accountId: String, callId: String): Map<String, String> =
        mapOf("CALL_STATE" to "CURRENT", "VIDEO_SOURCE" to "true")

    override fun getActiveCalls(accountId: String): List<String> = emptyList()

    override suspend fun switchCamera() {
        withContext(Dispatchers.Default) {
            // Toggle between front and back camera
            currentVideoDeviceId = if (currentVideoDeviceId == VIDEO_DEVICE_FRONT) {
                VIDEO_DEVICE_BACK
            } else {
                VIDEO_DEVICE_FRONT
            }
            NSLog("$TAG: switchCamera - now using: $currentVideoDeviceId")
        }
    }

    override suspend fun switchAudioOutput(useSpeaker: Boolean) {
        withContext(Dispatchers.Default) {
            isSpeakerEnabled = useSpeaker
            try {
                val portOverride = if (useSpeaker) {
                    AVAudioSessionPortOverrideSpeaker
                } else {
                    AVAudioSessionPortOverrideNone
                }
                audioSession.overrideOutputAudioPort(portOverride, null)
                NSLog("$TAG: switchAudioOutput: speaker=$useSpeaker - success")
            } catch (e: Exception) {
                NSLog("$TAG: Failed to switch audio output: ${e.message}")
            }
        }
    }

    // =========================================================================
    // Conference Calls
    // =========================================================================

    override suspend fun createConference(accountId: String, participantUris: List<String>): String =
        withContext(Dispatchers.Default) {
            NSLog("$TAG: createConference with ${participantUris.size} participants")
            val conferenceId = generateId()
            _callEvents.tryEmit(JamiCallEvent.ConferenceCreated(accountId, "", conferenceId))
            conferenceId
        }

    override suspend fun joinParticipant(
        accountId: String,
        callId1: String,
        accountId2: String,
        callId2: String
    ) {
        withContext(Dispatchers.Default) {
            NSLog("$TAG: joinParticipant")
        }
    }

    override suspend fun addParticipantToConference(
        accountId: String,
        callId: String,
        conferenceAccountId: String,
        conferenceId: String
    ) {
        withContext(Dispatchers.Default) {
            NSLog("$TAG: addParticipantToConference")
        }
    }

    override suspend fun hangUpConference(accountId: String, conferenceId: String) {
        withContext(Dispatchers.Default) {
            NSLog("$TAG: hangUpConference: $conferenceId")
            _callEvents.tryEmit(JamiCallEvent.ConferenceRemoved(accountId, conferenceId))
        }
    }

    override fun getConferenceDetails(accountId: String, conferenceId: String): Map<String, String> =
        mapOf("CONF_STATE" to "ACTIVE_ATTACHED", "CONF_ID" to conferenceId)

    override fun getConferenceParticipants(accountId: String, conferenceId: String): List<String> = emptyList()

    override fun getConferenceInfos(accountId: String, conferenceId: String): List<Map<String, String>> = emptyList()

    override suspend fun setConferenceLayout(accountId: String, conferenceId: String, layout: ConferenceLayout) {
        withContext(Dispatchers.Default) {
            NSLog("$TAG: setConferenceLayout: $layout")
        }
    }

    override suspend fun muteConferenceParticipant(
        accountId: String,
        conferenceId: String,
        participantUri: String,
        muted: Boolean
    ) {
        withContext(Dispatchers.Default) {
            NSLog("$TAG: muteConferenceParticipant: $participantUri = $muted")
        }
    }

    override suspend fun hangUpConferenceParticipant(
        accountId: String,
        conferenceId: String,
        participantUri: String,
        deviceId: String
    ) {
        withContext(Dispatchers.Default) {
            NSLog("$TAG: hangUpConferenceParticipant: $participantUri")
        }
    }

    // =========================================================================
    // File Transfer
    // =========================================================================

    override suspend fun sendFile(
        accountId: String,
        conversationId: String,
        filePath: String,
        displayName: String
    ): String = withContext(Dispatchers.Default) {
        NSLog("$TAG: sendFile: $displayName")
        generateId()
    }

    override suspend fun acceptFileTransfer(
        accountId: String,
        conversationId: String,
        fileId: String,
        destinationPath: String
    ) {
        withContext(Dispatchers.Default) {
            NSLog("$TAG: acceptFileTransfer: $fileId")
        }
    }

    override suspend fun cancelFileTransfer(
        accountId: String,
        conversationId: String,
        fileId: String
    ) {
        withContext(Dispatchers.Default) {
            NSLog("$TAG: cancelFileTransfer: $fileId")
        }
    }

    override fun getFileTransferInfo(
        accountId: String,
        conversationId: String,
        fileId: String
    ): FileTransferInfo? = null

    // =========================================================================
    // Video
    // =========================================================================

    override fun getVideoDevices(): List<String> {
        // On iOS, we generally have front and back cameras
        // Use AVCaptureDevice.devices() to enumerate, but for simplicity
        // return the standard device list for iOS devices
        val devices = listOf(VIDEO_DEVICE_FRONT, VIDEO_DEVICE_BACK)
        NSLog("$TAG: getVideoDevices: ${devices.size} cameras: $devices")
        return devices
    }

    override fun getCurrentVideoDevice(): String {
        NSLog("$TAG: getCurrentVideoDevice: $currentVideoDeviceId")
        return currentVideoDeviceId
    }

    override suspend fun setVideoDevice(deviceId: String) {
        withContext(Dispatchers.Default) {
            if (deviceId == VIDEO_DEVICE_FRONT || deviceId == VIDEO_DEVICE_BACK) {
                currentVideoDeviceId = deviceId
                NSLog("$TAG: setVideoDevice: $deviceId - success")
            } else {
                NSLog("$TAG: setVideoDevice: unknown device $deviceId")
            }
        }
    }

    override suspend fun startVideo() {
        withContext(Dispatchers.Default) {
            isVideoActive = true
            NSLog("$TAG: startVideo - video active with device: $currentVideoDeviceId")
        }
    }

    override suspend fun stopVideo() {
        withContext(Dispatchers.Default) {
            isVideoActive = false
            NSLog("$TAG: stopVideo - video stopped")
        }
    }

    // =========================================================================
    // Audio Settings
    // =========================================================================

    override fun getAudioOutputDevices(): List<String> {
        // Standard iOS audio outputs
        // The actual available devices depend on what's connected (Bluetooth, headphones, etc.)
        // For now, return the common built-in options
        val devices = listOf("Speaker", "Receiver")
        NSLog("$TAG: getAudioOutputDevices: $devices")
        return devices
    }

    override fun getAudioInputDevices(): List<String> {
        // ⚠️ CRASH PREVENTION: Android implementation crashes with SIGSEGV
        // iOS should also throw for API consistency
        // See: doc/audio-input-crash-analysis-pixel7a.md
        throw UnsupportedOperationException(
            "getAudioInputDevices() crashes with SIGSEGV on Android. " +
            "Use useDefaultAudioInputDevice() instead. " +
            "See doc/audio-input-crash-analysis-pixel7a.md for details."
        )
        // Original mock implementation (DO NOT UNCOMMENT):
        // val devices = listOf("Built-in Microphone")
        // NSLog("$TAG: getAudioInputDevices: $devices")
        // return devices
    }

    override suspend fun setAudioOutputDevice(index: Int) {
        withContext(Dispatchers.Default) {
            currentAudioOutputIndex = index
            try {
                // Index 0 = Speaker, Index 1 = Receiver (no override)
                when (index) {
                    0 -> {
                        audioSession.overrideOutputAudioPort(AVAudioSessionPortOverrideSpeaker, null)
                        isSpeakerEnabled = true
                        NSLog("$TAG: setAudioOutputDevice: Speaker")
                    }
                    else -> {
                        audioSession.overrideOutputAudioPort(AVAudioSessionPortOverrideNone, null)
                        isSpeakerEnabled = false
                        NSLog("$TAG: setAudioOutputDevice: Receiver/Default")
                    }
                }
            } catch (e: Exception) {
                NSLog("$TAG: Failed to set audio output device: ${e.message}")
            }
        }
    }

    override suspend fun setAudioInputDevice(index: Int) {
        withContext(Dispatchers.Default) {
            currentAudioInputIndex = index
            // On iOS, the system automatically manages input device selection
            // based on what's connected (built-in mic, headset mic, Bluetooth mic)
            NSLog("$TAG: setAudioInputDevice: index=$index (system managed)")
        }
    }

    // =========================================================================
    // Helper functions
    // =========================================================================

    private fun generateId(): String = NSUUID().UUIDString

    private fun currentTimestamp(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()
}

actual fun createJamiBridge(): JamiBridge = IOSJamiBridge()
