@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.gettogether.app.jami

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import com.gettogether.app.data.util.VCardParser
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.get
import kotlinx.cinterop.reinterpret
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryOptionAllowBluetooth
import platform.AVFAudio.AVAudioSessionCategoryOptionDefaultToSpeaker
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.AVAudioSessionModeVoiceChat
import platform.AVFAudio.AVAudioSessionPortOverrideNone
import platform.AVFAudio.AVAudioSessionPortOverrideSpeaker
import com.gettogether.app.util.IosFileLogger
import platform.Foundation.NSDate
import platform.Foundation.NSLog
import platform.Foundation.NSUUID
import platform.Foundation.timeIntervalSince1970

/**
 * iOS implementation of JamiBridge.
 *
 * This implementation connects to the native libjami daemon through the
 * SwiftJamiBridgeAdapter. If the native bridge is not available, it falls
 * back to mock behavior for UI testing.
 */
class IOSJamiBridge : JamiBridge, NativeBridgeCallback {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Event buffer size - match Android for consistency
    private val EVENT_BUFFER_SIZE = 512

    // Event flows with larger buffers and DROP_OLDEST overflow strategy
    private val _events = MutableSharedFlow<JamiEvent>(
        replay = 0,
        extraBufferCapacity = EVENT_BUFFER_SIZE,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val _accountEvents = MutableSharedFlow<JamiAccountEvent>(
        replay = 0,
        extraBufferCapacity = EVENT_BUFFER_SIZE,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val _callEvents = MutableSharedFlow<JamiCallEvent>(
        replay = 0,
        extraBufferCapacity = EVENT_BUFFER_SIZE,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val _conversationEvents = MutableSharedFlow<JamiConversationEvent>(
        replay = 0,
        extraBufferCapacity = EVENT_BUFFER_SIZE,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val _contactEvents = MutableSharedFlow<JamiContactEvent>(
        replay = 0,
        extraBufferCapacity = EVENT_BUFFER_SIZE,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    override val events: SharedFlow<JamiEvent> = _events.asSharedFlow()
    override val accountEvents: SharedFlow<JamiAccountEvent> = _accountEvents.asSharedFlow()
    override val callEvents: SharedFlow<JamiCallEvent> = _callEvents.asSharedFlow()
    override val conversationEvents: SharedFlow<JamiConversationEvent> = _conversationEvents.asSharedFlow()
    override val contactEvents: SharedFlow<JamiContactEvent> = _contactEvents.asSharedFlow()

    // Native bridge operations (set by Swift at startup)
    private val native: NativeBridgeOperations?
        get() = NativeBridgeProvider.operations

    private val isNativeAvailable: Boolean
        get() = NativeBridgeProvider.isInitialized

    // State
    private var _isDaemonRunning = false

    // Audio session for iOS
    private val audioSession by lazy { AVAudioSession.sharedInstance() }

    companion object {
        private const val TAG = "JamiBridge-iOS"
    }

    init {
        // Register this instance as the callback receiver
        NativeBridgeProvider.setCallback(this)
        IosFileLogger.i(TAG, "IOSJamiBridge initialized, native available: $isNativeAvailable")
    }

    // =========================================================================
    // NativeBridgeCallback Implementation - Events from Native to Kotlin
    // =========================================================================

    override fun onRegistrationStateChanged(accountId: String, state: Int, code: Int, detail: String) {
        IosFileLogger.i(TAG, "onRegistrationStateChanged: $accountId state=$state code=$code detail=$detail")
        val regState = when (state) {
            0 -> RegistrationState.UNREGISTERED
            1 -> RegistrationState.TRYING
            2 -> RegistrationState.REGISTERED
            3 -> RegistrationState.ERROR_GENERIC
            4 -> RegistrationState.ERROR_AUTH
            5 -> RegistrationState.ERROR_NETWORK
            6 -> RegistrationState.ERROR_HOST
            7 -> RegistrationState.ERROR_SERVICE_UNAVAILABLE
            8 -> RegistrationState.ERROR_NEED_MIGRATION
            9 -> RegistrationState.INITIALIZING
            else -> RegistrationState.ERROR_GENERIC
        }
        val event = JamiAccountEvent.RegistrationStateChanged(accountId, regState, code, detail)
        _accountEvents.tryEmit(event)
        _events.tryEmit(event)
    }

    override fun onAccountDetailsChanged(accountId: String, details: Map<String, String>) {
        val event = JamiAccountEvent.AccountDetailsChanged(accountId, details)
        _accountEvents.tryEmit(event)
        _events.tryEmit(event)
    }

    override fun onNameRegistrationEnded(accountId: String, state: Int, name: String) {
        val event = JamiAccountEvent.NameRegistrationEnded(accountId, state, name)
        _accountEvents.tryEmit(event)
        _events.tryEmit(event)
    }

    override fun onRegisteredNameFound(accountId: String, state: Int, address: String, name: String) {
        val lookupState = when (state) {
            0 -> LookupState.SUCCESS
            1 -> LookupState.NOT_FOUND
            2 -> LookupState.INVALID
            else -> LookupState.ERROR
        }
        val event = JamiAccountEvent.RegisteredNameFound(accountId, lookupState, address, name)
        _accountEvents.tryEmit(event)
        _events.tryEmit(event)
    }

    override fun onProfileReceived(accountId: String, from: String, displayName: String, avatarPath: String?) {
        IosFileLogger.d(TAG, "onProfileReceived: from=${from.take(8)}... displayName=$displayName avatarPath=$avatarPath")

        // The daemon's ProfileReceived signal passes a vCard FILE PATH as the third parameter
        // (not the display name itself). Match Android behavior: read file, parse vCard, emit event.
        val profile = if (displayName.contains("BEGIN:VCARD")) {
            // Raw vCard content (unlikely but handle it)
            VCardParser.parseString(displayName)
        } else if (displayName.contains("/")) {
            // File path to vCard - read and parse (this is the normal daemon behavior)
            try {
                val fileManager = platform.Foundation.NSFileManager.defaultManager
                if (fileManager.fileExistsAtPath(displayName)) {
                    val nsData = fileManager.contentsAtPath(displayName)
                    if (nsData != null && nsData.length.toInt() > 0 && nsData.bytes != null) {
                        val size = nsData.length.toInt()
                        val ptr = nsData.bytes!!.reinterpret<ByteVar>()
                        val bytes = ByteArray(size) { ptr[it] }
                        VCardParser.parse(bytes)
                    } else null
                } else {
                    IosFileLogger.w(TAG, "onProfileReceived: vCard file not found: $displayName")
                    null
                }
            } catch (e: Exception) {
                IosFileLogger.e(TAG, "onProfileReceived: Error reading vCard file: ${e.message}")
                null
            }
        } else {
            null
        }

        if (profile != null) {
            // Emit ContactProfileReceived with parsed vCard data (matches Android behavior)
            val event = JamiContactEvent.ContactProfileReceived(
                accountId = accountId,
                contactUri = from,
                displayName = profile.displayName,
                avatarBase64 = profile.photoBase64
            )
            _contactEvents.tryEmit(event)
            _events.tryEmit(event)
            IosFileLogger.i(TAG, "onProfileReceived: Parsed vCard - displayName='${profile.displayName}' hasPhoto=${profile.photoBase64 != null}")
        } else {
            IosFileLogger.w(TAG, "onProfileReceived: Could not parse profile, ignoring to avoid path leak")
        }
    }

    override fun onContactAdded(accountId: String, uri: String, confirmed: Boolean) {
        IosFileLogger.i(TAG, "onContactAdded: uri=${uri.take(8)}... confirmed=$confirmed")
        val event = JamiContactEvent.ContactAdded(accountId, uri, confirmed)
        _contactEvents.tryEmit(event)
        _events.tryEmit(event)
    }

    override fun onContactRemoved(accountId: String, uri: String, banned: Boolean) {
        IosFileLogger.i(TAG, "onContactRemoved: uri=${uri.take(8)}... banned=$banned")
        val event = JamiContactEvent.ContactRemoved(accountId, uri, banned)
        _contactEvents.tryEmit(event)
        _events.tryEmit(event)
    }

    override fun onIncomingTrustRequest(accountId: String, conversationId: String, from: String, received: Long) {
        IosFileLogger.i(TAG, "onIncomingTrustRequest: from=${from.take(8)}... convId=${conversationId.take(8)}... received=$received")
        // Note: payload is not provided by Swift bridge, using empty ByteArray
        val event = JamiContactEvent.IncomingTrustRequest(accountId, conversationId, from, ByteArray(0), received)
        _contactEvents.tryEmit(event)
        _events.tryEmit(event)
    }

    override fun onPresenceChanged(accountId: String, uri: String, isOnline: Boolean) {
        IosFileLogger.d(TAG, "onPresenceChanged: uri=${uri.take(8)}... isOnline=$isOnline")
        val event = JamiContactEvent.PresenceChanged(accountId, uri, isOnline)
        _contactEvents.tryEmit(event)
        _events.tryEmit(event)
    }

    override fun onConversationReady(accountId: String, conversationId: String) {
        IosFileLogger.i(TAG, "onConversationReady: convId=${conversationId.take(8)}...")
        val event = JamiConversationEvent.ConversationReady(accountId, conversationId)
        val emitted = _conversationEvents.tryEmit(event)
        _events.tryEmit(event)
        if (!emitted) {
            IosFileLogger.e(TAG, "onConversationReady: BUFFER OVERFLOW - event may be lost!")
        }
    }

    override fun onConversationRemoved(accountId: String, conversationId: String) {
        IosFileLogger.i(TAG, "onConversationRemoved: convId=${conversationId.take(8)}...")
        val event = JamiConversationEvent.ConversationRemoved(accountId, conversationId)
        val emitted = _conversationEvents.tryEmit(event)
        _events.tryEmit(event)
        if (!emitted) {
            IosFileLogger.e(TAG, "onConversationRemoved: BUFFER OVERFLOW - event may be lost!")
        }
    }

    override fun onConversationRequestReceived(accountId: String, conversationId: String, metadata: Map<String, String>) {
        IosFileLogger.i(TAG, "onConversationRequestReceived: convId=${conversationId.take(8)}... metadata=$metadata")
        val event = JamiConversationEvent.ConversationRequestReceived(accountId, conversationId, metadata)
        val emitted = _conversationEvents.tryEmit(event)
        _events.tryEmit(event)
        if (!emitted) {
            IosFileLogger.e(TAG, "onConversationRequestReceived: BUFFER OVERFLOW - request may be lost!")
        }
    }

    override fun onMessageReceived(accountId: String, conversationId: String, messageData: Map<String, Any?>) {
        IosFileLogger.i(TAG, "onMessageReceived: convId=${conversationId.take(8)}... msgId=${messageData["id"]}")
        val message = parseSwarmMessage(messageData)
        IosFileLogger.d(TAG, "onMessageReceived: parsed - id=${message.id}, author=${message.author.take(8)}..., body=${message.body}")
        val event = JamiConversationEvent.MessageReceived(accountId, conversationId, message)
        val emitted = _conversationEvents.tryEmit(event)
        _events.tryEmit(event)
        if (!emitted) {
            IosFileLogger.e(TAG, "onMessageReceived: BUFFER OVERFLOW - message ${message.id} may be lost!")
        }
    }

    override fun onMessageUpdated(accountId: String, conversationId: String, messageData: Map<String, Any?>) {
        IosFileLogger.d(TAG, "onMessageUpdated: convId=${conversationId.take(8)}... msgId=${messageData["id"]}")
        val message = parseSwarmMessage(messageData)
        val event = JamiConversationEvent.MessageUpdated(accountId, conversationId, message)
        val emitted = _conversationEvents.tryEmit(event)
        _events.tryEmit(event)
        if (!emitted) {
            IosFileLogger.e(TAG, "onMessageUpdated: BUFFER OVERFLOW - update for ${message.id} may be lost!")
        }
    }

    override fun onMessagesLoaded(requestId: Int, accountId: String, conversationId: String, messages: List<Map<String, Any?>>) {
        IosFileLogger.i(TAG, "onMessagesLoaded: convId=${conversationId.take(8)}... count=${messages.size} requestId=$requestId")
        val parsedMessages = messages.map { parseSwarmMessage(it) }
        val event = JamiConversationEvent.MessagesLoaded(requestId, accountId, conversationId, parsedMessages)
        val emitted = _conversationEvents.tryEmit(event)
        _events.tryEmit(event)
        if (!emitted) {
            IosFileLogger.e(TAG, "onMessagesLoaded: BUFFER OVERFLOW - ${messages.size} messages may be lost!")
        } else {
            IosFileLogger.d(TAG, "onMessagesLoaded: emitted ${parsedMessages.size} messages successfully")
        }
    }

    override fun onConversationMemberEvent(accountId: String, conversationId: String, memberUri: String, event: Int) {
        IosFileLogger.i(TAG, "onConversationMemberEvent: convId=${conversationId.take(8)}... member=${memberUri.take(8)}... event=$event")
        // Event codes match Android: 0,1=JOIN, 2=LEAVE, 3=UNBAN
        val eventType = when (event) {
            0, 1 -> MemberEventType.JOIN
            2 -> MemberEventType.LEAVE
            3 -> MemberEventType.UNBAN
            else -> MemberEventType.JOIN
        }
        val memberEvent = JamiConversationEvent.ConversationMemberEvent(accountId, conversationId, memberUri, eventType)
        val emitted = _conversationEvents.tryEmit(memberEvent)
        _events.tryEmit(memberEvent)
        if (!emitted) {
            IosFileLogger.e(TAG, "onConversationMemberEvent: BUFFER OVERFLOW - member event may be lost!")
        }
    }

    override fun onComposingStatusChanged(accountId: String, conversationId: String, from: String, isComposing: Boolean) {
        val event = JamiConversationEvent.ComposingStatusChanged(accountId, conversationId, from, isComposing)
        _conversationEvents.tryEmit(event)
        _events.tryEmit(event)
    }

    override fun onConversationProfileUpdated(accountId: String, conversationId: String, profile: Map<String, String>) {
        val event = JamiConversationEvent.ConversationProfileUpdated(accountId, conversationId, profile)
        _conversationEvents.tryEmit(event)
        _events.tryEmit(event)
    }

    override fun onIncomingCall(accountId: String, callId: String, peerId: String, peerDisplayName: String, hasVideo: Boolean) {
        IosFileLogger.i(TAG, "onIncomingCall: callId=$callId from=$peerId video=$hasVideo")
        val event = JamiCallEvent.IncomingCall(accountId, callId, peerId, peerDisplayName, hasVideo)
        _callEvents.tryEmit(event)
        _events.tryEmit(event)
    }

    override fun onCallStateChanged(accountId: String, callId: String, state: Int, code: Int) {
        IosFileLogger.i(TAG, "onCallStateChanged: callId=$callId state=$state code=$code")
        val callState = when (state) {
            0 -> CallState.INACTIVE
            1 -> CallState.INCOMING
            2 -> CallState.CONNECTING
            3 -> CallState.RINGING
            4 -> CallState.CURRENT
            5 -> CallState.HUNGUP
            6 -> CallState.BUSY
            7 -> CallState.FAILURE
            8 -> CallState.HOLD
            9 -> CallState.UNHOLD
            10 -> CallState.OVER
            else -> CallState.INACTIVE
        }
        val event = JamiCallEvent.CallStateChanged(accountId, callId, callState, code)
        _callEvents.tryEmit(event)
        _events.tryEmit(event)
    }

    override fun onAudioMuted(callId: String, muted: Boolean) {
        val event = JamiCallEvent.AudioMuted(callId, muted)
        _callEvents.tryEmit(event)
        _events.tryEmit(event)
    }

    override fun onVideoMuted(callId: String, muted: Boolean) {
        val event = JamiCallEvent.VideoMuted(callId, muted)
        _callEvents.tryEmit(event)
        _events.tryEmit(event)
    }

    override fun onConferenceCreated(accountId: String, conversationId: String, conferenceId: String) {
        val event = JamiCallEvent.ConferenceCreated(accountId, conversationId, conferenceId)
        _callEvents.tryEmit(event)
        _events.tryEmit(event)
    }

    override fun onConferenceChanged(accountId: String, conferenceId: String, state: String) {
        val event = JamiCallEvent.ConferenceChanged(accountId, conferenceId, state)
        _callEvents.tryEmit(event)
        _events.tryEmit(event)
    }

    override fun onConferenceRemoved(accountId: String, conferenceId: String) {
        val event = JamiCallEvent.ConferenceRemoved(accountId, conferenceId)
        _callEvents.tryEmit(event)
        _events.tryEmit(event)
    }

    // =========================================================================
    // Helper to parse SwarmMessage from dictionary
    // =========================================================================

    @Suppress("UNCHECKED_CAST")
    private fun parseSwarmMessage(data: Map<String, Any?>): SwarmMessage {
        val id = data["id"] as? String ?: ""
        val type = data["type"] as? String ?: "text/plain"
        val author = data["author"] as? String ?: ""
        val body = (data["body"] as? Map<String, String>) ?: emptyMap()
        val reactionsRaw = data["reactions"] as? List<Map<String, String>> ?: emptyList()
        val timestamp = (data["timestamp"] as? Number)?.toLong() ?: 0L
        val replyTo = data["replyTo"] as? String
        val statusRaw = data["status"] as? Map<String, Number> ?: emptyMap()
        val status = statusRaw.mapValues { it.value.toInt() }

        return SwarmMessage(
            id = id,
            type = type,
            author = author,
            body = body,
            reactions = reactionsRaw,
            timestamp = timestamp,
            replyTo = replyTo,
            status = status
        )
    }

    // =========================================================================
    // Daemon Lifecycle
    // =========================================================================

    override suspend fun initDaemon(dataPath: String) {
        withContext(Dispatchers.Default) {
            IosFileLogger.i(TAG, "initDaemon with path: $dataPath")
            if (native != null) {
                native?.initDaemon(dataPath)
            } else {
                IosFileLogger.w(TAG, "Native bridge not available")
            }
        }
    }

    override suspend fun startDaemon() {
        withContext(Dispatchers.Default) {
            IosFileLogger.i(TAG, "startDaemon")
            native?.startDaemon()
            _isDaemonRunning = native?.isDaemonRunning() ?: false
            IosFileLogger.i(TAG, "Daemon running: $_isDaemonRunning")
        }
    }

    override suspend fun stopDaemon() {
        withContext(Dispatchers.Default) {
            IosFileLogger.i(TAG, "stopDaemon")
            native?.stopDaemon()
            _isDaemonRunning = false
        }
    }

    override fun isDaemonRunning(): Boolean {
        return native?.isDaemonRunning() ?: _isDaemonRunning
    }

    // =========================================================================
    // Account Management
    // =========================================================================

    override suspend fun createAccount(displayName: String, password: String): String = withContext(Dispatchers.Default) {
        IosFileLogger.i(TAG, "createAccount: $displayName")
        val accountId = native?.createAccount(displayName, password) ?: generateId()
        IosFileLogger.i(TAG, "createAccount result: $accountId")
        accountId
    }

    override suspend fun importAccount(archivePath: String, password: String): String = withContext(Dispatchers.Default) {
        IosFileLogger.i(TAG, "importAccount from: $archivePath")
        val accountId = native?.importAccount(archivePath, password) ?: generateId()
        IosFileLogger.i(TAG, "importAccount result: $accountId")
        accountId
    }

    override suspend fun exportAccount(accountId: String, destinationPath: String, password: String): Boolean =
        withContext(Dispatchers.Default) {
            NSLog("$TAG: exportAccount: $accountId to $destinationPath")
            native?.exportAccount(accountId, destinationPath, password) ?: false
        }

    override suspend fun deleteAccount(accountId: String) {
        withContext(Dispatchers.Default) {
            NSLog("$TAG: deleteAccount: $accountId")
            native?.deleteAccount(accountId)
        }
    }

    override fun getAccountIds(): List<String> {
        return native?.getAccountIds() ?: emptyList()
    }

    override fun getAccountDetails(accountId: String): Map<String, String> {
        return native?.getAccountDetails(accountId) ?: emptyMap()
    }

    override fun getVolatileAccountDetails(accountId: String): Map<String, String> {
        return native?.getVolatileAccountDetails(accountId) ?: emptyMap()
    }

    override suspend fun setAccountDetails(accountId: String, details: Map<String, String>) {
        withContext(Dispatchers.Default) {
            NSLog("$TAG: setAccountDetails: $accountId")
            native?.setAccountDetails(accountId, details)
        }
    }

    override suspend fun setAccountActive(accountId: String, active: Boolean) {
        withContext(Dispatchers.Default) {
            NSLog("$TAG: setAccountActive: $accountId = $active")
            native?.setAccountActive(accountId, active)
        }
    }

    override suspend fun connectivityChanged() {
        withContext(Dispatchers.Default) {
            NSLog("$TAG: connectivityChanged")
            // Native handles this internally
        }
    }

    override suspend fun updateProfile(accountId: String, displayName: String, avatarPath: String?) {
        withContext(Dispatchers.Default) {
            NSLog("$TAG: updateProfile: $accountId, name=$displayName")
            native?.updateProfile(accountId, displayName, avatarPath)
        }
    }

    override suspend fun registerName(accountId: String, name: String, password: String): Boolean =
        withContext(Dispatchers.Default) {
            NSLog("$TAG: registerName: $name for $accountId")
            native?.registerName(accountId, name, password) ?: false
        }

    override suspend fun lookupName(accountId: String, name: String): LookupResult? {
        NSLog("$TAG: lookupName: $name")
        native?.lookupName(accountId, name)
        return null // Result comes via callback
    }

    override suspend fun lookupAddress(accountId: String, address: String): LookupResult? {
        NSLog("$TAG: lookupAddress: $address")
        native?.lookupAddress(accountId, address)
        return null // Result comes via callback
    }

    // =========================================================================
    // Contact Management
    // =========================================================================

    override fun getContacts(accountId: String): List<JamiContact> {
        val contactsData = native?.getContacts(accountId) ?: return emptyList()
        return contactsData.map { data ->
            JamiContact(
                uri = data["uri"] as? String ?: "",
                displayName = data["displayName"] as? String ?: "",
                avatarPath = data["avatarPath"] as? String,
                isConfirmed = data["isConfirmed"] as? Boolean ?: false,
                isBanned = data["isBanned"] as? Boolean ?: false
            )
        }
    }

    override suspend fun addContact(accountId: String, uri: String) {
        withContext(Dispatchers.Default) {
            IosFileLogger.i(TAG, "addContact: uri=$uri accountId=${accountId.take(8)}...")
            try {
                native?.addContact(accountId, uri)
                IosFileLogger.i(TAG, "addContact completed successfully")

                // Subscribe to presence updates for this contact (matches Android behavior)
                IosFileLogger.i(TAG, "addContact: subscribing to presence for $uri")
                native?.subscribeBuddy(accountId, uri, true)
                IosFileLogger.i(TAG, "addContact: presence subscription requested")
            } catch (e: Exception) {
                IosFileLogger.e(TAG, "addContact FAILED", e)
                throw e
            }
        }
    }

    override suspend fun removeContact(accountId: String, uri: String, ban: Boolean) {
        withContext(Dispatchers.Default) {
            NSLog("$TAG: removeContact: $uri, ban=$ban")
            native?.removeContact(accountId, uri, ban)
        }
    }

    override fun getContactDetails(accountId: String, uri: String): Map<String, String> {
        return native?.getContactDetails(accountId, uri) ?: emptyMap()
    }

    override suspend fun acceptTrustRequest(accountId: String, uri: String) {
        withContext(Dispatchers.Default) {
            NSLog("$TAG: acceptTrustRequest from: $uri")
            native?.acceptTrustRequest(accountId, uri)
        }
    }

    override suspend fun discardTrustRequest(accountId: String, uri: String) {
        withContext(Dispatchers.Default) {
            NSLog("$TAG: discardTrustRequest from: $uri")
            native?.discardTrustRequest(accountId, uri)
        }
    }

    override fun getTrustRequests(accountId: String): List<TrustRequest> {
        val requestsData = native?.getTrustRequests(accountId) ?: return emptyList()
        return requestsData.map { data ->
            TrustRequest(
                from = data["from"] as? String ?: "",
                conversationId = data["conversationId"] as? String ?: "",
                payload = ByteArray(0), // Payload not provided by Swift bridge
                received = (data["received"] as? Number)?.toLong() ?: 0L
            )
        }
    }

    override suspend fun subscribeBuddy(accountId: String, uri: String, flag: Boolean) {
        withContext(Dispatchers.Default) {
            IosFileLogger.i(TAG, "subscribeBuddy: uri=$uri flag=$flag")
            try {
                native?.subscribeBuddy(accountId, uri, flag)
                IosFileLogger.i(TAG, "subscribeBuddy completed")
            } catch (e: Exception) {
                IosFileLogger.e(TAG, "subscribeBuddy FAILED", e)
            }
        }
    }

    override suspend fun publishPresence(accountId: String, isOnline: Boolean, note: String) {
        withContext(Dispatchers.Default) {
            NSLog("$TAG: publishPresence: isOnline=$isOnline")
            // Presence handled by daemon
        }
    }

    // =========================================================================
    // Conversation Management
    // =========================================================================

    override fun getConversations(accountId: String): List<String> {
        IosFileLogger.d(TAG, "getConversations for account: ${accountId.take(8)}...")
        val result = native?.getConversations(accountId) ?: emptyList()
        IosFileLogger.d(TAG, "getConversations returned ${result.size} conversations")
        return result
    }

    override suspend fun startConversation(accountId: String): String = withContext(Dispatchers.Default) {
        IosFileLogger.i(TAG, "startConversation for account: ${accountId.take(8)}...")
        try {
            val conversationId = native?.startConversation(accountId) ?: generateId()
            IosFileLogger.i(TAG, "startConversation result: ${conversationId.take(8)}...")
            conversationId
        } catch (e: Exception) {
            IosFileLogger.e(TAG, "startConversation FAILED", e)
            throw e
        }
    }

    override suspend fun removeConversation(accountId: String, conversationId: String) {
        withContext(Dispatchers.Default) {
            NSLog("$TAG: removeConversation: $conversationId")
            native?.removeConversation(accountId, conversationId)
        }
    }

    override suspend fun clearConversationCache(accountId: String, conversationId: String) {
        withContext(Dispatchers.Default) {
            NSLog("$TAG: clearConversationCache: $conversationId")
            // Handled locally
        }
    }

    override fun getConversationInfo(accountId: String, conversationId: String): Map<String, String> {
        IosFileLogger.d(TAG, "getConversationInfo: ${conversationId.take(8)}...")
        try {
            val result = native?.getConversationInfo(accountId, conversationId) ?: emptyMap()
            IosFileLogger.d(TAG, "getConversationInfo returned ${result.size} entries")
            return result
        } catch (e: Exception) {
            IosFileLogger.e(TAG, "getConversationInfo FAILED", e)
            return emptyMap()
        }
    }

    override suspend fun updateConversationInfo(accountId: String, conversationId: String, info: Map<String, String>) {
        withContext(Dispatchers.Default) {
            NSLog("$TAG: updateConversationInfo: $conversationId")
            native?.updateConversationInfo(accountId, conversationId, info)
        }
    }

    override fun getConversationMembers(accountId: String, conversationId: String): List<ConversationMember> {
        IosFileLogger.d(TAG, "getConversationMembers: ${conversationId.take(8)}...")
        try {
            val membersData = native?.getConversationMembers(accountId, conversationId) ?: return emptyList()
            IosFileLogger.d(TAG, "getConversationMembers: got ${membersData.size} members data")
            return membersData.map { data ->
                val roleInt = (data["role"] as? Number)?.toInt() ?: 1
                val role = when (roleInt) {
                    0 -> MemberRole.ADMIN
                    1 -> MemberRole.MEMBER
                    2 -> MemberRole.INVITED
                    3 -> MemberRole.BANNED
                    else -> MemberRole.MEMBER
                }
                ConversationMember(
                    uri = data["uri"] as? String ?: "",
                    role = role
                )
            }
        } catch (e: Exception) {
            IosFileLogger.e(TAG, "getConversationMembers FAILED", e)
            return emptyList()
        }
    }

    override suspend fun addConversationMember(accountId: String, conversationId: String, contactUri: String) {
        withContext(Dispatchers.Default) {
            NSLog("$TAG: addConversationMember: $contactUri to $conversationId")
            native?.addConversationMember(accountId, conversationId, contactUri)
        }
    }

    override suspend fun removeConversationMember(accountId: String, conversationId: String, contactUri: String) {
        withContext(Dispatchers.Default) {
            NSLog("$TAG: removeConversationMember: $contactUri from $conversationId")
            native?.removeConversationMember(accountId, conversationId, contactUri)
        }
    }

    override suspend fun acceptConversationRequest(accountId: String, conversationId: String) {
        withContext(Dispatchers.Default) {
            NSLog("$TAG: acceptConversationRequest: $conversationId")
            native?.acceptConversationRequest(accountId, conversationId)
        }
    }

    override suspend fun declineConversationRequest(accountId: String, conversationId: String) {
        withContext(Dispatchers.Default) {
            NSLog("$TAG: declineConversationRequest: $conversationId")
            native?.declineConversationRequest(accountId, conversationId)
        }
    }

    override fun getConversationRequests(accountId: String): List<ConversationRequest> {
        val requestsData = native?.getConversationRequests(accountId) ?: return emptyList()
        return requestsData.mapNotNull { data ->
            @Suppress("UNCHECKED_CAST")
            ConversationRequest(
                conversationId = data["conversationId"] as? String ?: return@mapNotNull null,
                from = data["from"] as? String ?: "",
                metadata = (data["metadata"] as? Map<String, String>) ?: emptyMap(),
                received = (data["received"] as? Number)?.toLong() ?: 0L
            )
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
    ): String = withContext(Dispatchers.Default) {
        NSLog("$TAG: sendMessage to $conversationId")
        native?.sendMessage(accountId, conversationId, message, replyTo) ?: ""
    }

    override suspend fun loadConversationMessages(
        accountId: String,
        conversationId: String,
        fromMessage: String,
        count: Int
    ): Int = withContext(Dispatchers.Default) {
        NSLog("$TAG: loadConversationMessages: $conversationId, count=$count")
        native?.loadConversationMessages(accountId, conversationId, fromMessage, count) ?: 0
    }

    override suspend fun setIsComposing(accountId: String, conversationId: String, isComposing: Boolean) {
        withContext(Dispatchers.Default) {
            native?.setIsComposing(accountId, conversationId, isComposing)
        }
    }

    override suspend fun setMessageDisplayed(accountId: String, conversationId: String, messageId: String) {
        withContext(Dispatchers.Default) {
            native?.setMessageDisplayed(accountId, conversationId, messageId)
        }
    }

    // =========================================================================
    // Calls
    // =========================================================================

    override suspend fun placeCall(accountId: String, uri: String, withVideo: Boolean): String =
        withContext(Dispatchers.Default) {
            IosFileLogger.i(TAG, "placeCall to $uri, video=$withVideo")
            val callId = native?.placeCall(accountId, uri, withVideo) ?: ""
            IosFileLogger.i(TAG, "placeCall result: $callId")
            callId
        }

    override suspend fun acceptCall(accountId: String, callId: String, withVideo: Boolean) {
        withContext(Dispatchers.Default) {
            IosFileLogger.i(TAG, "acceptCall: $callId, video=$withVideo")
            native?.acceptCall(accountId, callId, withVideo)
        }
    }

    override suspend fun refuseCall(accountId: String, callId: String) {
        withContext(Dispatchers.Default) {
            IosFileLogger.i(TAG, "refuseCall: $callId")
            native?.refuseCall(accountId, callId)
        }
    }

    override suspend fun hangUp(accountId: String, callId: String) {
        withContext(Dispatchers.Default) {
            IosFileLogger.i(TAG, "hangUp: $callId")
            native?.hangUp(accountId, callId)
        }
    }

    override suspend fun holdCall(accountId: String, callId: String) {
        withContext(Dispatchers.Default) {
            NSLog("$TAG: holdCall: $callId")
            native?.holdCall(accountId, callId)
        }
    }

    override suspend fun unholdCall(accountId: String, callId: String) {
        withContext(Dispatchers.Default) {
            NSLog("$TAG: unholdCall: $callId")
            native?.unholdCall(accountId, callId)
        }
    }

    override suspend fun muteAudio(accountId: String, callId: String, muted: Boolean) {
        withContext(Dispatchers.Default) {
            NSLog("$TAG: muteAudio: $callId = $muted")
            native?.muteAudio(accountId, callId, muted)
        }
    }

    override suspend fun muteVideo(accountId: String, callId: String, muted: Boolean) {
        withContext(Dispatchers.Default) {
            NSLog("$TAG: muteVideo: $callId = $muted")
            native?.muteVideo(accountId, callId, muted)
        }
    }

    override fun getCallDetails(accountId: String, callId: String): Map<String, String> {
        return native?.getCallDetails(accountId, callId) ?: emptyMap()
    }

    override fun getActiveCalls(accountId: String): List<String> {
        return native?.getActiveCalls(accountId) ?: emptyList()
    }

    override suspend fun switchCamera() {
        withContext(Dispatchers.Default) {
            NSLog("$TAG: switchCamera")
            native?.switchCamera()
        }
    }

    override suspend fun switchAudioOutput(useSpeaker: Boolean) {
        withContext(Dispatchers.Default) {
            IosFileLogger.i(TAG, "switchAudioOutput: speaker=$useSpeaker")
            native?.switchAudioOutput(useSpeaker)
                ?: try {
                    val portOverride = if (useSpeaker) {
                        AVAudioSessionPortOverrideSpeaker
                    } else {
                        AVAudioSessionPortOverrideNone
                    }
                    audioSession.overrideOutputAudioPort(portOverride, null)
                } catch (e: Exception) {
                    IosFileLogger.e(TAG, "Failed to switch audio output", e)
                }
        }
    }

    // =========================================================================
    // Conference Calls
    // =========================================================================

    override suspend fun createConference(accountId: String, participantUris: List<String>): String =
        withContext(Dispatchers.Default) {
            NSLog("$TAG: createConference with ${participantUris.size} participants")
            generateId() // Conference ID comes via callback
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
        }
    }

    override fun getConferenceDetails(accountId: String, conferenceId: String): Map<String, String> = emptyMap()

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
        IosFileLogger.i(TAG, "sendFile: $displayName path=$filePath")
        native?.sendFile(accountId, conversationId, filePath, displayName) ?: generateId()
    }

    override suspend fun acceptFileTransfer(
        accountId: String,
        conversationId: String,
        interactionId: String,
        fileId: String,
        destinationPath: String
    ) {
        withContext(Dispatchers.Default) {
            IosFileLogger.i(TAG, "acceptFileTransfer: fileId=$fileId interactionId=$interactionId destPath=$destinationPath")
            native?.acceptFileTransfer(accountId, conversationId, interactionId, fileId, destinationPath)
        }
    }

    override suspend fun cancelFileTransfer(
        accountId: String,
        conversationId: String,
        fileId: String
    ) {
        withContext(Dispatchers.Default) {
            IosFileLogger.d(TAG, "cancelFileTransfer: $fileId")
            native?.cancelFileTransfer(accountId, conversationId, fileId)
        }
    }

    override fun getFileTransferInfo(
        accountId: String,
        conversationId: String,
        fileId: String
    ): FileTransferInfo? {
        val info = native?.getFileTransferInfo(accountId, conversationId, fileId) ?: return null
        val fileIdResult = info["fileId"] as? String ?: return null
        val path = info["path"] as? String ?: ""
        val totalSize = (info["totalSize"] as? Number)?.toLong() ?: 0L
        val progress = (info["progress"] as? Number)?.toLong() ?: 0L

        return FileTransferInfo(
            fileId = fileIdResult,
            path = path,
            displayName = path.substringAfterLast("/"),
            totalSize = totalSize,
            progress = progress,
            bytesPerSecond = 0L,
            author = "",
            flags = 0
        )
    }

    // =========================================================================
    // Video
    // =========================================================================

    override fun getVideoDevices(): List<String> {
        return native?.getVideoDevices() ?: listOf("front", "back")
    }

    override fun getCurrentVideoDevice(): String {
        return native?.getCurrentVideoDevice() ?: "front"
    }

    override suspend fun setVideoDevice(deviceId: String) {
        withContext(Dispatchers.Default) {
            NSLog("$TAG: setVideoDevice: $deviceId")
            native?.setVideoDevice(deviceId)
        }
    }

    override suspend fun startVideo() {
        withContext(Dispatchers.Default) {
            NSLog("$TAG: startVideo")
            native?.startVideo()
        }
    }

    override suspend fun stopVideo() {
        withContext(Dispatchers.Default) {
            NSLog("$TAG: stopVideo")
            native?.stopVideo()
        }
    }

    // =========================================================================
    // Audio Settings
    // =========================================================================

    override fun getAudioOutputDevices(): List<String> {
        return native?.getAudioOutputDevices() ?: listOf("Speaker", "Receiver")
    }

    override fun getAudioInputDevices(): List<String> {
        throw UnsupportedOperationException(
            "getAudioInputDevices() crashes with SIGSEGV on Android. " +
            "Use default audio input device instead."
        )
    }

    override suspend fun setAudioOutputDevice(index: Int) {
        withContext(Dispatchers.Default) {
            NSLog("$TAG: setAudioOutputDevice: $index")
            native?.setAudioOutputDevice(index)
        }
    }

    override suspend fun setAudioInputDevice(index: Int) {
        withContext(Dispatchers.Default) {
            NSLog("$TAG: setAudioInputDevice: $index (system managed)")
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private fun generateId(): String = NSUUID().UUIDString

    private fun currentTimestamp(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()
}

actual fun createJamiBridge(): JamiBridge = IOSJamiBridge()
