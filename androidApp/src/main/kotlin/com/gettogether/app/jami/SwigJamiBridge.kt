package com.gettogether.app.jami

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import net.jami.daemon.*

/**
 * Android implementation of JamiBridge using the SWIG-generated JamiService.
 * This class wraps the native Jami daemon via SWIG bindings.
 */
class SwigJamiBridge(private val context: Context) : JamiBridge {

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

    // SWIG Callback implementations
    private val configCallback = object : ConfigurationCallback() {
        override fun registrationStateChanged(accountId: String?, state: String?, code: Int, detail: String?) {
            val regState = parseRegistrationState(state ?: "")
            val event = JamiAccountEvent.RegistrationStateChanged(
                accountId ?: "",
                regState,
                code,
                detail ?: ""
            )
            _accountEvents.tryEmit(event)
            _events.tryEmit(event)
        }

        override fun accountsChanged() {
            Log.d(TAG, "Accounts changed")
        }

        override fun accountDetailsChanged(accountId: String?, details: StringMap?) {
            if (accountId != null && details != null) {
                val detailsMap = stringMapToKotlin(details)
                val event = JamiAccountEvent.AccountDetailsChanged(accountId, detailsMap)
                _accountEvents.tryEmit(event)
                _events.tryEmit(event)
            }
        }

        override fun profileReceived(accountId: String?, name: String?, photo: String?) {
            if (accountId != null) {
                val event = JamiAccountEvent.ProfileReceived(accountId, name ?: "", name ?: "", photo)
                _accountEvents.tryEmit(event)
                _events.tryEmit(event)
            }
        }

        override fun nameRegistrationEnded(accountId: String?, state: Int, name: String?) {
            if (accountId != null) {
                val event = JamiAccountEvent.NameRegistrationEnded(accountId, state, name ?: "")
                _accountEvents.tryEmit(event)
                _events.tryEmit(event)
            }
        }

        override fun incomingTrustRequest(accountId: String?, conversationId: String?, from: String?, payload: Blob?, received: Long) {
            Log.i(TAG, "incomingTrustRequest: accountId=$accountId, from=$from, conversationId=$conversationId")
            if (accountId != null && from != null) {
                val payloadBytes = if (payload != null) {
                    ByteArray(payload.size) { i -> payload[i] }
                } else {
                    ByteArray(0)
                }
                val event = JamiContactEvent.IncomingTrustRequest(accountId, conversationId ?: "", from, payloadBytes, received)
                val emitted = _contactEvents.tryEmit(event)
                Log.i(TAG, "incomingTrustRequest: event emitted=$emitted")
                _events.tryEmit(event)
            }
        }

        override fun contactAdded(accountId: String?, uri: String?, confirmed: Boolean) {
            if (accountId != null && uri != null) {
                val event = JamiContactEvent.ContactAdded(accountId, uri, confirmed)
                _contactEvents.tryEmit(event)
                _events.tryEmit(event)
            }
        }

        override fun contactRemoved(accountId: String?, uri: String?, banned: Boolean) {
            if (accountId != null && uri != null) {
                val event = JamiContactEvent.ContactRemoved(accountId, uri, banned)
                _contactEvents.tryEmit(event)
                _events.tryEmit(event)
            }
        }

        override fun registeredNameFound(accountId: String?, name: String?, state: Int, address: String?, publicKey: String?) {
            if (accountId != null) {
                val lookupState = when (state) {
                    0 -> LookupState.SUCCESS
                    1 -> LookupState.INVALID
                    2 -> LookupState.NOT_FOUND
                    else -> LookupState.ERROR
                }
                val event = JamiAccountEvent.RegisteredNameFound(accountId, lookupState, address ?: "", name ?: "")
                _accountEvents.tryEmit(event)
                _events.tryEmit(event)
            }
        }

        override fun knownDevicesChanged(accountId: String?, devices: StringMap?) {
            if (accountId != null && devices != null) {
                val event = JamiAccountEvent.KnownDevicesChanged(accountId, stringMapToKotlin(devices))
                _accountEvents.tryEmit(event)
                _events.tryEmit(event)
            }
        }

        override fun getAppDataPath(name: String?, result: StringVect?) {
            // Provide the data path to the daemon
            // This is critical for account data persistence
            val dataPath = context.filesDir.absolutePath
            Log.i(TAG, "getAppDataPath called with name: $name, returning: $dataPath")
            result?.add(dataPath)
        }

        override fun getDeviceName(result: StringVect?) {
            // Provide a device name for this device
            result?.add(android.os.Build.MODEL)
        }
    }

    private val callCallback = object : Callback() {
        override fun callStateChanged(accountId: String?, callId: String?, state: String?, code: Int) {
            if (accountId != null && callId != null) {
                val callState = parseCallState(state ?: "")
                val event = JamiCallEvent.CallStateChanged(accountId, callId, callState, code)
                _callEvents.tryEmit(event)
                _events.tryEmit(event)
            }
        }

        override fun incomingCall(accountId: String?, callId: String?, from: String?, mediaList: VectMap?) {
            if (accountId != null && callId != null && from != null) {
                val hasVideo = if (mediaList != null) {
                    var found = false
                    for (i in 0 until mediaList.size) {
                        val media = mediaList[i]
                        if (media != null && media["MEDIA_TYPE"] == "MEDIA_TYPE_VIDEO") {
                            found = true
                            break
                        }
                    }
                    found
                } else false
                val event = JamiCallEvent.IncomingCall(accountId, callId, from, from, hasVideo)
                _callEvents.tryEmit(event)
                _events.tryEmit(event)
            }
        }

        override fun conferenceCreated(accountId: String?, conversationId: String?, confId: String?) {
            if (accountId != null && confId != null) {
                val event = JamiCallEvent.ConferenceCreated(accountId, conversationId ?: "", confId)
                _callEvents.tryEmit(event)
                _events.tryEmit(event)
            }
        }

        override fun conferenceChanged(accountId: String?, confId: String?, state: String?) {
            if (accountId != null && confId != null) {
                val event = JamiCallEvent.ConferenceChanged(accountId, confId, state ?: "")
                _callEvents.tryEmit(event)
                _events.tryEmit(event)
            }
        }

        override fun conferenceRemoved(accountId: String?, confId: String?) {
            if (accountId != null && confId != null) {
                val event = JamiCallEvent.ConferenceRemoved(accountId, confId)
                _callEvents.tryEmit(event)
                _events.tryEmit(event)
            }
        }
    }

    private val presenceCallback = object : PresenceCallback() {
        override fun newBuddyNotification(accountId: String?, buddyUri: String?, status: Int, lineStatus: String?) {
            Log.d(TAG, "=== newBuddyNotification (PRESENCE UPDATE) ===")
            Log.d(TAG, "  AccountId: $accountId")
            Log.d(TAG, "  BuddyUri: $buddyUri")
            Log.d(TAG, "  Status: $status (${if (status > 0) "ONLINE" else "OFFLINE"})")
            Log.d(TAG, "  LineStatus: $lineStatus")

            if (accountId != null && buddyUri != null) {
                val isOnline = status > 0
                Log.i(TAG, "→ Emitting PresenceChanged event: ${buddyUri.take(16)}... is ${if (isOnline) "ONLINE" else "OFFLINE"}")
                val event = JamiContactEvent.PresenceChanged(accountId, buddyUri, isOnline)
                val emitted = _contactEvents.tryEmit(event)
                _events.tryEmit(event)
                Log.i(TAG, "✓ PresenceChanged event emitted (success=$emitted)")
            } else {
                Log.w(TAG, "✗ Skipping presence event - null accountId or buddyUri")
            }
        }

        override fun nearbyPeerNotification(accountId: String?, buddyUri: String?, state: Int, displayName: String?) {
            Log.d(TAG, "=== nearbyPeerNotification ===")
            Log.d(TAG, "  AccountId: $accountId, BuddyUri: $buddyUri, State: $state, DisplayName: $displayName")
        }

        override fun newServerSubscriptionRequest(arg0: String?) {
            Log.d(TAG, "=== newServerSubscriptionRequest: $arg0 ===")
        }

        override fun serverError(arg0: String?, arg1: String?, arg2: String?) {
            Log.e(TAG, "=== Presence serverError: $arg0, $arg1, $arg2 ===")
        }
    }

    private val dataTransferCallback = object : DataTransferCallback() {}
    private val videoCallback = object : VideoCallback() {}

    private val conversationCallback = object : ConversationCallback() {
        override fun conversationReady(accountId: String?, conversationId: String?) {
            if (accountId != null && conversationId != null) {
                val event = JamiConversationEvent.ConversationReady(accountId, conversationId)
                _conversationEvents.tryEmit(event)
                _events.tryEmit(event)
            }
        }

        override fun swarmMessageReceived(accountId: String?, conversationId: String?, message: net.jami.daemon.SwarmMessage?) {
            Log.i(TAG, "swarmMessageReceived: accountId=$accountId, conversationId=$conversationId, message=${message?.id}")
            if (accountId != null && conversationId != null && message != null) {
                val swarmMsg = convertSwarmMessage(message)
                Log.i(TAG, "swarmMessageReceived: Converted message - id=${swarmMsg.id}, author=${swarmMsg.author}, body=${swarmMsg.body}")
                val event = JamiConversationEvent.MessageReceived(accountId, conversationId, swarmMsg)
                val emitted = _conversationEvents.tryEmit(event)
                _events.tryEmit(event)
                Log.i(TAG, "swarmMessageReceived: Event emitted=$emitted")
            } else {
                Log.w(TAG, "swarmMessageReceived: Null parameter - accountId=$accountId, conversationId=$conversationId, message=$message")
            }
        }

        override fun conversationRequestReceived(accountId: String?, conversationId: String?, metadata: StringMap?) {
            if (accountId != null && conversationId != null) {
                val event = JamiConversationEvent.ConversationRequestReceived(
                    accountId,
                    conversationId,
                    stringMapToKotlin(metadata)
                )
                _conversationEvents.tryEmit(event)
                _events.tryEmit(event)
            }
        }

        override fun conversationMemberEvent(accountId: String?, conversationId: String?, memberUri: String?, eventType: Int) {
            if (accountId != null && conversationId != null && memberUri != null) {
                val memberEventType = when (eventType) {
                    0 -> MemberEventType.JOIN
                    1 -> MemberEventType.LEAVE
                    2 -> MemberEventType.BAN
                    3 -> MemberEventType.UNBAN
                    else -> MemberEventType.JOIN
                }
                val event = JamiConversationEvent.ConversationMemberEvent(
                    accountId, conversationId, memberUri, memberEventType
                )
                _conversationEvents.tryEmit(event)
                _events.tryEmit(event)
            }
        }
    }

    companion object {
        private const val TAG = "SwigJamiBridge"
        private var nativeLoaded = false

        init {
            Log.d(TAG, "=== SwigJamiBridge: Attempting to load native library ===")
            try {
                System.loadLibrary("jami-core-jni")
                nativeLoaded = true
                Log.i(TAG, "✓ Successfully loaded libjami-core-jni.so")
                Log.i(TAG, "  Native library path: ${System.getProperty("java.library.path")}")
            } catch (e: UnsatisfiedLinkError) {
                nativeLoaded = false
                Log.e(TAG, "✗ FAILED to load libjami-core-jni.so")
                Log.e(TAG, "  Error: ${e.message}")
                Log.e(TAG, "  Library path: ${System.getProperty("java.library.path")}")
                Log.e(TAG, "  This means the Jami daemon will NOT run and all contacts will appear offline!")
                e.printStackTrace()
            }
        }
    }

    // Helper functions
    private fun stringMapToKotlin(map: StringMap?): Map<String, String> {
        if (map == null) return emptyMap()
        val result = mutableMapOf<String, String>()
        val keys = map.keys()
        for (i in 0 until keys.size) {
            val key = keys[i]
            result[key] = map[key] ?: ""
        }
        return result
    }

    private fun kotlinToStringMap(map: Map<String, String>): StringMap {
        val stringMap = StringMap()
        map.forEach { (key, value) ->
            stringMap[key] = value
        }
        return stringMap
    }

    private fun stringVectToList(vect: StringVect?): List<String> {
        if (vect == null) return emptyList()
        val result = mutableListOf<String>()
        for (i in 0 until vect.size) {
            result.add(vect[i])
        }
        return result
    }

    private fun convertSwarmMessage(msg: net.jami.daemon.SwarmMessage): SwarmMessage {
        val body = msg.body
        val bodyMap = if (body != null) stringMapToKotlin(body) else emptyMap()
        return SwarmMessage(
            id = msg.id ?: "",
            type = msg.type ?: "",
            author = bodyMap["author"] ?: "",
            body = bodyMap,
            reactions = emptyList(),
            timestamp = bodyMap["timestamp"]?.toLongOrNull() ?: System.currentTimeMillis(),
            replyTo = msg.linearizedParent,
            status = emptyMap()
        )
    }

    private fun parseRegistrationState(state: String): RegistrationState {
        return when (state.uppercase()) {
            "REGISTERED" -> RegistrationState.REGISTERED
            "UNREGISTERED" -> RegistrationState.UNREGISTERED
            "TRYING" -> RegistrationState.TRYING
            "ERROR_AUTH" -> RegistrationState.ERROR_AUTH
            "ERROR_NETWORK" -> RegistrationState.ERROR_NETWORK
            "ERROR_HOST" -> RegistrationState.ERROR_HOST
            "ERROR_SERVICE_UNAVAILABLE" -> RegistrationState.ERROR_SERVICE_UNAVAILABLE
            "ERROR_NEED_MIGRATION" -> RegistrationState.ERROR_NEED_MIGRATION
            "ERROR_GENERIC", "ERROR" -> RegistrationState.ERROR_GENERIC
            "INITIALIZING" -> RegistrationState.INITIALIZING
            else -> RegistrationState.UNREGISTERED
        }
    }

    private fun parseCallState(state: String): CallState {
        return when (state.uppercase()) {
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

    // =========================================================================
    // JamiBridge Implementation
    // =========================================================================

    override suspend fun initDaemon(dataPath: String) = withContext(Dispatchers.IO) {
        Log.d(TAG, "=== initDaemon() called ===")
        Log.d(TAG, "  Data path: $dataPath")
        Log.d(TAG, "  Native loaded: $nativeLoaded")

        if (!nativeLoaded) {
            Log.e(TAG, "✗ Cannot init daemon: native library not loaded!")
            Log.e(TAG, "  The daemon will NOT be initialized.")
            Log.e(TAG, "  All Jami functionality will be unavailable.")
            return@withContext
        }

        try {
            Log.i(TAG, "→ Calling JamiService.init() with callbacks...")
            JamiService.init(
                configCallback,
                callCallback,
                presenceCallback,
                dataTransferCallback,
                videoCallback,
                conversationCallback
            )
            Log.i(TAG, "✓ JamiService.init() completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "✗ JamiService.init() FAILED with exception: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    override suspend fun startDaemon() = withContext(Dispatchers.IO) {
        Log.d(TAG, "=== startDaemon() called ===")
        Log.d(TAG, "  Native loaded: $nativeLoaded")

        if (!nativeLoaded) {
            Log.e(TAG, "✗ Cannot start daemon: native library not loaded!")
            return@withContext
        }

        try {
            Log.i(TAG, "→ Starting Jami daemon...")
            _isDaemonRunning = true
            Log.i(TAG, "✓ Daemon marked as running (isDaemonRunning=$_isDaemonRunning)")
            Log.i(TAG, "  Note: Actual daemon start depends on JamiService implementation")
        } catch (e: Exception) {
            Log.e(TAG, "✗ startDaemon() FAILED: ${e.message}")
            e.printStackTrace()
            _isDaemonRunning = false
            throw e
        }
    }

    override suspend fun stopDaemon() = withContext(Dispatchers.IO) {
        if (!nativeLoaded) return@withContext
        JamiService.fini()
        _isDaemonRunning = false
        Log.i(TAG, "Daemon stopped")
    }

    override fun isDaemonRunning(): Boolean = _isDaemonRunning && nativeLoaded

    // Account Management
    override suspend fun createAccount(displayName: String, password: String): String = withContext(Dispatchers.IO) {
        if (!nativeLoaded) return@withContext ""
        val details = kotlinToStringMap(mapOf(
            "Account.type" to "RING",
            "Account.alias" to displayName,
            "Account.displayName" to displayName,
            "Account.archivePassword" to password
        ))
        JamiService.addAccount(details)
    }

    override suspend fun importAccount(archivePath: String, password: String): String = withContext(Dispatchers.IO) {
        if (!nativeLoaded) return@withContext ""
        val details = kotlinToStringMap(mapOf(
            "Account.type" to "RING",
            "Account.archivePath" to archivePath,
            "Account.archivePassword" to password
        ))
        JamiService.addAccount(details)
    }

    override suspend fun exportAccount(accountId: String, destinationPath: String, password: String): Boolean = withContext(Dispatchers.IO) {
        if (!nativeLoaded) return@withContext false
        JamiService.exportToFile(accountId, destinationPath, "password", password)
    }

    override suspend fun deleteAccount(accountId: String) = withContext(Dispatchers.IO) {
        if (!nativeLoaded) return@withContext
        JamiService.removeAccount(accountId)
    }

    override fun getAccountIds(): List<String> {
        if (!nativeLoaded) return emptyList()
        return stringVectToList(JamiService.getAccountList())
    }

    override fun getAccountDetails(accountId: String): Map<String, String> {
        if (!nativeLoaded) return emptyMap()
        return stringMapToKotlin(JamiService.getAccountDetails(accountId))
    }

    override fun getVolatileAccountDetails(accountId: String): Map<String, String> {
        if (!nativeLoaded) return emptyMap()
        return stringMapToKotlin(JamiService.getVolatileAccountDetails(accountId))
    }

    override suspend fun setAccountDetails(accountId: String, details: Map<String, String>) = withContext(Dispatchers.IO) {
        if (!nativeLoaded) return@withContext
        JamiService.setAccountDetails(accountId, kotlinToStringMap(details))
    }

    override suspend fun setAccountActive(accountId: String, active: Boolean) = withContext(Dispatchers.IO) {
        if (!nativeLoaded) return@withContext
        JamiService.setAccountActive(accountId, active)
    }

    override suspend fun updateProfile(accountId: String, displayName: String, avatarPath: String?) = withContext(Dispatchers.IO) {
        if (!nativeLoaded) return@withContext
        JamiService.updateProfile(accountId, displayName, avatarPath ?: "", "image/png", 0)
    }

    override suspend fun registerName(accountId: String, name: String, password: String): Boolean = withContext(Dispatchers.IO) {
        if (!nativeLoaded) return@withContext false
        JamiService.registerName(accountId, name, "password", password)
    }

    override suspend fun lookupName(accountId: String, name: String): LookupResult? = withContext(Dispatchers.IO) {
        if (!nativeLoaded) return@withContext null
        JamiService.lookupName(accountId, "", name)
        null
    }

    override suspend fun lookupAddress(accountId: String, address: String): LookupResult? = withContext(Dispatchers.IO) {
        if (!nativeLoaded) return@withContext null
        JamiService.lookupAddress(accountId, "", address)
        null
    }

    // Contacts
    override fun getContacts(accountId: String): List<JamiContact> {
        if (!nativeLoaded) return emptyList()
        val contacts = JamiService.getContacts(accountId)
        val result = mutableListOf<JamiContact>()
        for (i in 0 until contacts.size) {
            val map = stringMapToKotlin(contacts[i])
            result.add(JamiContact(
                uri = map["id"] ?: "",
                displayName = map["displayName"] ?: "",
                avatarPath = map["avatar"],
                isConfirmed = map["confirmed"] == "true",
                isBanned = map["banned"] == "true"
            ))
        }
        return result
    }

    override suspend fun addContact(accountId: String, uri: String) = withContext(Dispatchers.IO) {
        if (!nativeLoaded) {
            Log.e(TAG, "addContact: native library not loaded")
            return@withContext
        }
        Log.i(TAG, "→ addContact: accountId=$accountId, uri=$uri")
        try {
            JamiService.addContact(accountId, uri)
            Log.i(TAG, "✓ addContact completed")

            // Subscribe to presence updates for this contact
            Log.i(TAG, "→ Subscribing to presence for contact: ${uri.take(16)}...")
            JamiService.subscribeBuddy(accountId, uri, true)
            Log.i(TAG, "✓ Presence subscription requested")
        } catch (e: Exception) {
            Log.e(TAG, "✗ addContact failed: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    override suspend fun removeContact(accountId: String, uri: String, ban: Boolean) = withContext(Dispatchers.IO) {
        if (!nativeLoaded) return@withContext
        JamiService.removeContact(accountId, uri, ban)
    }

    override fun getContactDetails(accountId: String, uri: String): Map<String, String> {
        if (!nativeLoaded) return emptyMap()
        return stringMapToKotlin(JamiService.getContactDetails(accountId, uri))
    }

    override suspend fun subscribeBuddy(accountId: String, uri: String, flag: Boolean) = withContext(Dispatchers.IO) {
        if (!nativeLoaded) {
            Log.w(TAG, "subscribeBuddy: native library not loaded")
            return@withContext
        }
        Log.d(TAG, "subscribeBuddy: accountId=$accountId, uri=${uri.take(16)}..., flag=$flag")
        try {
            JamiService.subscribeBuddy(accountId, uri, flag)
            Log.i(TAG, "✓ subscribeBuddy completed")
        } catch (e: Exception) {
            Log.e(TAG, "✗ subscribeBuddy failed: ${e.message}")
            e.printStackTrace()
        }
    }

    override suspend fun acceptTrustRequest(accountId: String, uri: String) = withContext(Dispatchers.IO) {
        if (!nativeLoaded) return@withContext
        JamiService.acceptTrustRequest(accountId, uri)
    }

    override suspend fun discardTrustRequest(accountId: String, uri: String) = withContext(Dispatchers.IO) {
        if (!nativeLoaded) return@withContext
        JamiService.discardTrustRequest(accountId, uri)
    }

    override fun getTrustRequests(accountId: String): List<TrustRequest> {
        if (!nativeLoaded) return emptyList()
        val requests = JamiService.getTrustRequests(accountId)
        val result = mutableListOf<TrustRequest>()
        for (i in 0 until requests.size) {
            val map = stringMapToKotlin(requests[i])
            result.add(TrustRequest(
                from = map["from"] ?: "",
                conversationId = map["conversationId"] ?: "",
                payload = ByteArray(0),
                received = map["received"]?.toLongOrNull() ?: 0L
            ))
        }
        return result
    }

    // Conversations
    override fun getConversations(accountId: String): List<String> {
        if (!nativeLoaded) return emptyList()
        return stringVectToList(JamiService.getConversations(accountId))
    }

    override suspend fun startConversation(accountId: String): String = withContext(Dispatchers.IO) {
        if (!nativeLoaded) return@withContext ""
        JamiService.startConversation(accountId)
    }

    override suspend fun removeConversation(accountId: String, conversationId: String) = withContext(Dispatchers.IO) {
        if (!nativeLoaded) return@withContext
        JamiService.removeConversation(accountId, conversationId)
        Unit
    }

    override fun getConversationInfo(accountId: String, conversationId: String): Map<String, String> {
        if (!nativeLoaded) return emptyMap()
        return stringMapToKotlin(JamiService.conversationInfos(accountId, conversationId))
    }

    override suspend fun updateConversationInfo(accountId: String, conversationId: String, info: Map<String, String>) = withContext(Dispatchers.IO) {
        if (!nativeLoaded) return@withContext
        JamiService.updateConversationInfos(accountId, conversationId, kotlinToStringMap(info))
    }

    override fun getConversationMembers(accountId: String, conversationId: String): List<ConversationMember> {
        if (!nativeLoaded) return emptyList()
        val members = JamiService.getConversationMembers(accountId, conversationId)
        val result = mutableListOf<ConversationMember>()
        for (i in 0 until members.size) {
            val map = stringMapToKotlin(members[i])
            val role = when (map["role"]?.uppercase()) {
                "ADMIN" -> MemberRole.ADMIN
                "MEMBER" -> MemberRole.MEMBER
                "INVITED" -> MemberRole.INVITED
                "BANNED" -> MemberRole.BANNED
                else -> MemberRole.MEMBER
            }
            result.add(ConversationMember(uri = map["uri"] ?: "", role = role))
        }
        return result
    }

    override suspend fun addConversationMember(accountId: String, conversationId: String, contactUri: String) = withContext(Dispatchers.IO) {
        if (!nativeLoaded) return@withContext
        JamiService.addConversationMember(accountId, conversationId, contactUri)
    }

    override suspend fun removeConversationMember(accountId: String, conversationId: String, contactUri: String) = withContext(Dispatchers.IO) {
        if (!nativeLoaded) return@withContext
        JamiService.removeConversationMember(accountId, conversationId, contactUri)
    }

    override suspend fun acceptConversationRequest(accountId: String, conversationId: String) = withContext(Dispatchers.IO) {
        if (!nativeLoaded) return@withContext
        JamiService.acceptConversationRequest(accountId, conversationId)
    }

    override suspend fun declineConversationRequest(accountId: String, conversationId: String) = withContext(Dispatchers.IO) {
        if (!nativeLoaded) return@withContext
        JamiService.declineConversationRequest(accountId, conversationId)
    }

    override fun getConversationRequests(accountId: String): List<ConversationRequest> {
        if (!nativeLoaded) return emptyList()
        val requests = JamiService.getConversationRequests(accountId)
        val result = mutableListOf<ConversationRequest>()
        for (i in 0 until requests.size) {
            val map = stringMapToKotlin(requests[i])
            result.add(ConversationRequest(
                conversationId = map["id"] ?: "",
                from = map["from"] ?: "",
                metadata = map,
                received = map["received"]?.toLongOrNull() ?: 0L
            ))
        }
        return result
    }

    // Messaging
    override suspend fun sendMessage(accountId: String, conversationId: String, message: String, replyTo: String?): String = withContext(Dispatchers.IO) {
        if (!nativeLoaded) {
            Log.w(TAG, "sendMessage: Native library not loaded")
            return@withContext ""
        }
        Log.i(TAG, "sendMessage: accountId=$accountId, conversationId=$conversationId, message='$message'")
        try {
            JamiService.sendMessage(accountId, conversationId, message, replyTo ?: "", 0)
            Log.i(TAG, "sendMessage: JamiService.sendMessage called successfully")
        } catch (e: Exception) {
            Log.e(TAG, "sendMessage: Exception - ${e.message}", e)
        }
        ""
    }

    override suspend fun loadConversationMessages(accountId: String, conversationId: String, fromMessage: String, count: Int): Int = withContext(Dispatchers.IO) {
        if (!nativeLoaded) return@withContext 0
        JamiService.loadConversation(accountId, conversationId, fromMessage, count.toLong()).toInt()
    }

    override suspend fun setIsComposing(accountId: String, conversationId: String, isComposing: Boolean) = withContext(Dispatchers.IO) {
        if (!nativeLoaded) return@withContext
        JamiService.setIsComposing(accountId, conversationId, isComposing)
    }

    override suspend fun setMessageDisplayed(accountId: String, conversationId: String, messageId: String) = withContext(Dispatchers.IO) {
        if (!nativeLoaded) return@withContext
        JamiService.setMessageDisplayed(accountId, conversationId, messageId, 3)
        Unit
    }

    // Calls
    override suspend fun placeCall(accountId: String, uri: String, withVideo: Boolean): String = withContext(Dispatchers.IO) {
        if (!nativeLoaded) return@withContext ""
        val mediaList = VectMap()
        val audioMedia = StringMap()
        audioMedia["MEDIA_TYPE"] = "MEDIA_TYPE_AUDIO"
        audioMedia["ENABLED"] = "true"
        audioMedia["MUTED"] = "false"
        audioMedia["SOURCE"] = ""
        mediaList.add(audioMedia)
        if (withVideo) {
            val videoMedia = StringMap()
            videoMedia["MEDIA_TYPE"] = "MEDIA_TYPE_VIDEO"
            videoMedia["ENABLED"] = "true"
            videoMedia["MUTED"] = "false"
            videoMedia["SOURCE"] = "camera://0"
            mediaList.add(videoMedia)
        }
        JamiService.placeCallWithMedia(accountId, uri, mediaList)
    }

    override suspend fun acceptCall(accountId: String, callId: String, withVideo: Boolean) = withContext(Dispatchers.IO) {
        if (!nativeLoaded) return@withContext
        if (withVideo) {
            val mediaList = VectMap()
            val audioMedia = StringMap()
            audioMedia["MEDIA_TYPE"] = "MEDIA_TYPE_AUDIO"
            audioMedia["ENABLED"] = "true"
            mediaList.add(audioMedia)
            val videoMedia = StringMap()
            videoMedia["MEDIA_TYPE"] = "MEDIA_TYPE_VIDEO"
            videoMedia["ENABLED"] = "true"
            mediaList.add(videoMedia)
            JamiService.acceptWithMedia(accountId, callId, mediaList)
        } else {
            JamiService.accept(accountId, callId)
        }
    }

    override suspend fun refuseCall(accountId: String, callId: String) = withContext(Dispatchers.IO) {
        if (!nativeLoaded) return@withContext
        JamiService.refuse(accountId, callId)
    }

    override suspend fun hangUp(accountId: String, callId: String) = withContext(Dispatchers.IO) {
        if (!nativeLoaded) return@withContext
        JamiService.hangUp(accountId, callId)
    }

    override suspend fun holdCall(accountId: String, callId: String) = withContext(Dispatchers.IO) {
        if (!nativeLoaded) return@withContext
        JamiService.hold(accountId, callId)
    }

    override suspend fun unholdCall(accountId: String, callId: String) = withContext(Dispatchers.IO) {
        if (!nativeLoaded) return@withContext
        JamiService.unhold(accountId, callId)
    }

    override suspend fun muteAudio(accountId: String, callId: String, muted: Boolean) = withContext(Dispatchers.IO) {
        if (!nativeLoaded) return@withContext
        JamiService.muteLocalMedia(accountId, callId, "MEDIA_TYPE_AUDIO", muted)
    }

    override suspend fun muteVideo(accountId: String, callId: String, muted: Boolean) = withContext(Dispatchers.IO) {
        if (!nativeLoaded) return@withContext
        JamiService.muteLocalMedia(accountId, callId, "MEDIA_TYPE_VIDEO", muted)
    }

    override suspend fun switchCamera() = withContext(Dispatchers.IO) {
        if (!nativeLoaded) return@withContext
    }

    override suspend fun switchAudioOutput(useSpeaker: Boolean) = withContext(Dispatchers.IO) {
        if (!nativeLoaded) return@withContext
        val devices = stringVectToList(JamiService.getAudioOutputDeviceList())
        val targetIndex = if (useSpeaker) {
            devices.indexOfFirst { it.contains("speaker", ignoreCase = true) }
        } else {
            devices.indexOfFirst { it.contains("earpiece", ignoreCase = true) || !it.contains("speaker", ignoreCase = true) }
        }
        if (targetIndex >= 0) {
            JamiService.setAudioOutputDevice(targetIndex)
        }
    }

    override fun getCallDetails(accountId: String, callId: String): Map<String, String> {
        if (!nativeLoaded) return emptyMap()
        return stringMapToKotlin(JamiService.getCallDetails(accountId, callId))
    }

    override fun getActiveCalls(accountId: String): List<String> {
        if (!nativeLoaded) return emptyList()
        return stringVectToList(JamiService.getCallList(accountId))
    }

    // Conference
    override suspend fun createConference(accountId: String, participantUris: List<String>): String = withContext(Dispatchers.IO) {
        if (!nativeLoaded) return@withContext ""
        val participants = StringVect()
        participantUris.forEach { participants.add(it) }
        JamiService.createConfFromParticipantList(accountId, participants)
        ""
    }

    override suspend fun joinParticipant(accountId: String, callId1: String, accountId2: String, callId2: String) = withContext(Dispatchers.IO) {
        if (!nativeLoaded) return@withContext
        JamiService.joinParticipant(accountId, callId1, accountId2, callId2)
    }

    override suspend fun addParticipantToConference(accountId: String, callId: String, conferenceAccountId: String, conferenceId: String) = withContext(Dispatchers.IO) {
        if (!nativeLoaded) return@withContext
        JamiService.addParticipant(accountId, callId, conferenceAccountId, conferenceId)
    }

    override suspend fun hangUpConference(accountId: String, conferenceId: String) = withContext(Dispatchers.IO) {
        if (!nativeLoaded) return@withContext
        JamiService.hangUpConference(accountId, conferenceId)
    }

    override fun getConferenceDetails(accountId: String, conferenceId: String): Map<String, String> {
        if (!nativeLoaded) return emptyMap()
        return stringMapToKotlin(JamiService.getConferenceDetails(accountId, conferenceId))
    }

    override fun getConferenceParticipants(accountId: String, conferenceId: String): List<String> {
        if (!nativeLoaded) return emptyList()
        return stringVectToList(JamiService.getParticipantList(accountId, conferenceId))
    }

    override fun getConferenceInfos(accountId: String, conferenceId: String): List<Map<String, String>> {
        if (!nativeLoaded) return emptyList()
        val infos = JamiService.getConferenceInfos(accountId, conferenceId)
        val result = mutableListOf<Map<String, String>>()
        for (i in 0 until infos.size) {
            result.add(stringMapToKotlin(infos[i]))
        }
        return result
    }

    override suspend fun setConferenceLayout(accountId: String, conferenceId: String, layout: ConferenceLayout) = withContext(Dispatchers.IO) {
        if (!nativeLoaded) return@withContext
        val layoutInt = when (layout) {
            ConferenceLayout.GRID -> 0
            ConferenceLayout.ONE_BIG -> 1
            ConferenceLayout.ONE_BIG_SMALL -> 2
        }
        JamiService.setConferenceLayout(accountId, conferenceId, layoutInt)
    }

    override suspend fun muteConferenceParticipant(accountId: String, conferenceId: String, participantUri: String, muted: Boolean) = withContext(Dispatchers.IO) {
        if (!nativeLoaded) return@withContext
        JamiService.muteParticipant(accountId, conferenceId, participantUri, muted)
    }

    override suspend fun hangUpConferenceParticipant(accountId: String, conferenceId: String, participantUri: String, deviceId: String) = withContext(Dispatchers.IO) {
        if (!nativeLoaded) return@withContext
        JamiService.hangupParticipant(accountId, conferenceId, participantUri, deviceId)
    }

    // File Transfer
    override suspend fun sendFile(accountId: String, conversationId: String, filePath: String, displayName: String): String = withContext(Dispatchers.IO) {
        if (!nativeLoaded) return@withContext ""
        JamiService.sendFile(accountId, conversationId, filePath, displayName, "")
        ""
    }

    override suspend fun acceptFileTransfer(accountId: String, conversationId: String, fileId: String, destinationPath: String) = withContext(Dispatchers.IO) {
        if (!nativeLoaded) return@withContext
        JamiService.downloadFile(accountId, conversationId, "", fileId, destinationPath)
    }

    override suspend fun cancelFileTransfer(accountId: String, conversationId: String, fileId: String) = withContext(Dispatchers.IO) {
        if (!nativeLoaded) return@withContext
        JamiService.cancelDataTransfer(accountId, conversationId, fileId)
    }

    override fun getFileTransferInfo(accountId: String, conversationId: String, fileId: String): FileTransferInfo? {
        if (!nativeLoaded) return null
        val pathOut = arrayOf("")
        val totalOut = longArrayOf(0L)
        val progressOut = longArrayOf(0L)
        JamiService.fileTransferInfo(accountId, conversationId, fileId, pathOut, totalOut, progressOut)
        return FileTransferInfo(
            fileId = fileId,
            path = pathOut[0],
            displayName = pathOut[0].substringAfterLast("/"),
            totalSize = totalOut[0],
            progress = progressOut[0],
            bytesPerSecond = 0L,
            author = "",
            flags = 0
        )
    }

    // Video
    override fun getVideoDevices(): List<String> {
        return emptyList()
    }

    override fun getCurrentVideoDevice(): String {
        return ""
    }

    override suspend fun setVideoDevice(deviceId: String) = withContext(Dispatchers.IO) {}

    override suspend fun startVideo() = withContext(Dispatchers.IO) {}

    override suspend fun stopVideo() = withContext(Dispatchers.IO) {
        if (!nativeLoaded) return@withContext
        JamiService.stopLocalRecorder("")
    }

    // Audio
    override fun getAudioOutputDevices(): List<String> {
        if (!nativeLoaded) return emptyList()
        return stringVectToList(JamiService.getAudioOutputDeviceList())
    }

    override fun getAudioInputDevices(): List<String> {
        // ⚠️ CRASH PREVENTION: This native call causes SIGSEGV
        // See: doc/audio-input-crash-analysis-pixel7a.md
        throw UnsupportedOperationException(
            "getAudioInputDevices() crashes with SIGSEGV in native library. " +
            "Use useDefaultAudioInputDevice() instead. " +
            "See doc/audio-input-crash-analysis-pixel7a.md for details."
        )
        // Original implementation (DO NOT UNCOMMENT):
        // if (!nativeLoaded) return emptyList()
        // return stringVectToList(JamiService.getAudioInputDeviceList())
    }

    override suspend fun setAudioOutputDevice(index: Int) = withContext(Dispatchers.IO) {
        if (!nativeLoaded) return@withContext
        JamiService.setAudioOutputDevice(index)
    }

    override suspend fun setAudioInputDevice(index: Int) = withContext(Dispatchers.IO) {
        if (!nativeLoaded) return@withContext
        JamiService.setAudioInputDevice(index)
    }
}
