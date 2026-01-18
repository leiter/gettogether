package com.gettogether.app.jami

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import net.jami.daemon.*

/**
 * Android implementation of JamiBridge using the SWIG-generated JamiService.
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
            // Accounts list changed - could emit an event
        }

        override fun accountDetailsChanged(accountId: String?, details: StringMap?) {
            if (accountId != null && details != null) {
                val detailsMap = stringMapToKotlin(details)
                val event = JamiAccountEvent.AccountDetailsChanged(accountId, detailsMap)
                _accountEvents.tryEmit(event)
                _events.tryEmit(event)
            }
        }

        override fun profileReceived(arg0: String?, arg1: String?, arg2: String?) {
            if (arg0 != null) {
                // arg0 = accountId, arg1 = from/displayName, arg2 = photo/avatar
                val event = JamiAccountEvent.ProfileReceived(arg0, arg1 ?: "", arg1 ?: "", arg2)
                _accountEvents.tryEmit(event)
                _events.tryEmit(event)
            }
        }

        override fun nameRegistrationEnded(arg0: String?, state: Int, arg2: String?) {
            if (arg0 != null) {
                val event = JamiAccountEvent.NameRegistrationEnded(arg0, state, arg2 ?: "")
                _accountEvents.tryEmit(event)
                _events.tryEmit(event)
            }
        }

        override fun incomingTrustRequest(arg0: String?, arg1: String?, arg2: String?, arg3: Blob?, received: Long) {
            if (arg0 != null && arg2 != null) {
                val payload = if (arg3 != null) {
                    ByteArray(arg3.size) { i -> arg3[i] }
                } else {
                    ByteArray(0)
                }
                val event = JamiContactEvent.IncomingTrustRequest(arg0, arg1 ?: "", arg2, payload, received)
                _contactEvents.tryEmit(event)
                _events.tryEmit(event)
            }
        }

        override fun contactAdded(arg0: String?, arg1: String?, confirmed: Boolean) {
            if (arg0 != null && arg1 != null) {
                val event = JamiContactEvent.ContactAdded(arg0, arg1, confirmed)
                _contactEvents.tryEmit(event)
                _events.tryEmit(event)
            }
        }

        override fun contactRemoved(arg0: String?, arg1: String?, banned: Boolean) {
            if (arg0 != null && arg1 != null) {
                val event = JamiContactEvent.ContactRemoved(arg0, arg1, banned)
                _contactEvents.tryEmit(event)
                _events.tryEmit(event)
            }
        }

        override fun registeredNameFound(arg0: String?, arg1: String?, state: Int, arg3: String?, arg4: String?) {
            if (arg0 != null) {
                val lookupState = when (state) {
                    0 -> LookupState.SUCCESS
                    1 -> LookupState.INVALID
                    2 -> LookupState.NOT_FOUND
                    else -> LookupState.ERROR
                }
                val event = JamiAccountEvent.RegisteredNameFound(arg0, lookupState, arg3 ?: "", arg4 ?: "")
                _accountEvents.tryEmit(event)
                _events.tryEmit(event)
            }
        }

        override fun knownDevicesChanged(arg0: String?, arg1: StringMap?) {
            if (arg0 != null && arg1 != null) {
                val event = JamiAccountEvent.KnownDevicesChanged(arg0, stringMapToKotlin(arg1))
                _accountEvents.tryEmit(event)
                _events.tryEmit(event)
            }
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
                // Check if video is in the media list
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
            if (accountId != null && buddyUri != null) {
                val event = JamiContactEvent.PresenceChanged(accountId, buddyUri, status > 0)
                _contactEvents.tryEmit(event)
                _events.tryEmit(event)
            }
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

        override fun conversationMemberEvent(accountId: String?, conversationId: String?, memberUri: String?, event: Int) {
            if (accountId != null && conversationId != null && memberUri != null) {
                val eventType = when (event) {
                    0 -> MemberEventType.JOIN
                    1 -> MemberEventType.LEAVE
                    2 -> MemberEventType.BAN
                    3 -> MemberEventType.UNBAN
                    else -> MemberEventType.JOIN
                }
                val memberEvent = JamiConversationEvent.ConversationMemberEvent(
                    accountId, conversationId, memberUri, eventType
                )
                _conversationEvents.tryEmit(memberEvent)
                _events.tryEmit(memberEvent)
            }
        }
    }

    companion object {
        private const val TAG = "JamiBridge"
        private var nativeLoaded = false

        init {
            try {
                System.loadLibrary("jami-core-jni")
                nativeLoaded = true
                Log.i(TAG, "Loaded libjami-core-jni.so")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load libjami-core-jni.so: ${e.message}")
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
        if (!nativeLoaded) {
            Log.e(TAG, "Cannot init daemon: native library not loaded")
            return@withContext
        }
        Log.i(TAG, "Initializing daemon with path: $dataPath")
        JamiService.init(
            configCallback,
            callCallback,
            presenceCallback,
            dataTransferCallback,
            videoCallback,
            conversationCallback
        )
    }

    override suspend fun startDaemon() = withContext(Dispatchers.IO) {
        if (!nativeLoaded) return@withContext
        _isDaemonRunning = true
        Log.i(TAG, "Daemon started")
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
        Log.i(TAG, "[ACCOUNT-EXPORT] exportAccount called for accountId=$accountId")
        Log.i(TAG, "[ACCOUNT-EXPORT] destinationPath=$destinationPath, password.length=${password.length}")

        if (!nativeLoaded) {
            Log.e(TAG, "[ACCOUNT-EXPORT] FAILED: Native library not loaded")
            return@withContext false
        }

        // Ensure account is active before export
        try {
            val details = JamiService.getAccountDetails(accountId)
            val isEnabled = details?.get("Account.enable")?.equals("true") == true
            Log.i(TAG, "[ACCOUNT-EXPORT] Account enabled: $isEnabled")

            if (!isEnabled) {
                Log.w(TAG, "[ACCOUNT-EXPORT] Account not enabled, activating temporarily for export")
                JamiService.setAccountActive(accountId, true)
                // Give daemon time to initialize
                delay(500)
            }
        } catch (e: Exception) {
            Log.w(TAG, "[ACCOUNT-EXPORT] Could not check/set account state: ${e.message}")
        }

        // Use empty scheme for better compatibility (password still encrypts the archive)
        val result = JamiService.exportToFile(accountId, destinationPath, "", password)
        Log.i(TAG, "[ACCOUNT-EXPORT] Export result: $result")

        if (!result) {
            Log.e(TAG, "[ACCOUNT-EXPORT] Export failed. Checking account details...")
            try {
                val volatileDetails = JamiService.getVolatileAccountDetails(accountId)
                Log.e(TAG, "[ACCOUNT-EXPORT] Registration status: ${volatileDetails?.get("Account.registrationStatus")}")
            } catch (e: Exception) {
                Log.e(TAG, "[ACCOUNT-EXPORT] Could not get volatile details: ${e.message}")
            }
        }

        result
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
        // Result comes via callback - return null for now, caller should listen to events
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
        if (!nativeLoaded) return@withContext
        JamiService.addContact(accountId, uri)
    }

    override suspend fun removeContact(accountId: String, uri: String, ban: Boolean) = withContext(Dispatchers.IO) {
        if (!nativeLoaded) return@withContext
        JamiService.removeContact(accountId, uri, ban)
    }

    override fun getContactDetails(accountId: String, uri: String): Map<String, String> {
        if (!nativeLoaded) return emptyMap()
        return stringMapToKotlin(JamiService.getContactDetails(accountId, uri))
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

    override suspend fun subscribeBuddy(accountId: String, uri: String, flag: Boolean) = withContext(Dispatchers.IO) {
        if (!nativeLoaded) return@withContext
        JamiService.subscribeBuddy(accountId, uri, flag)
    }

    override suspend fun publishPresence(accountId: String, isOnline: Boolean, note: String) = withContext(Dispatchers.IO) {
        if (!nativeLoaded) return@withContext
        JamiService.publish(accountId, isOnline, note)
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

    override suspend fun removeConversation(accountId: String, conversationId: String) {
        if (!nativeLoaded) return
        withContext(Dispatchers.IO) {
            JamiService.removeConversation(accountId, conversationId)
        }
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
        "" // Message ID returned via callback
    }

    override suspend fun loadConversationMessages(accountId: String, conversationId: String, fromMessage: String, count: Int): Int = withContext(Dispatchers.IO) {
        if (!nativeLoaded) return@withContext 0
        JamiService.loadConversation(accountId, conversationId, fromMessage, count.toLong()).toInt()
    }

    override suspend fun setIsComposing(accountId: String, conversationId: String, isComposing: Boolean) = withContext(Dispatchers.IO) {
        if (!nativeLoaded) return@withContext
        JamiService.setIsComposing(accountId, conversationId, isComposing)
    }

    override suspend fun setMessageDisplayed(accountId: String, conversationId: String, messageId: String) {
        if (!nativeLoaded) return
        withContext(Dispatchers.IO) {
            JamiService.setMessageDisplayed(accountId, conversationId, messageId, 3) // 3 = displayed
        }
    }

    // Calls
    override suspend fun placeCall(accountId: String, uri: String, withVideo: Boolean): String = withContext(Dispatchers.IO) {
        if (!nativeLoaded) return@withContext ""
        val mediaList = VectMap()
        // Audio
        val audioMedia = StringMap()
        audioMedia["MEDIA_TYPE"] = "MEDIA_TYPE_AUDIO"
        audioMedia["ENABLED"] = "true"
        audioMedia["MUTED"] = "false"
        audioMedia["SOURCE"] = ""
        mediaList.add(audioMedia)
        // Video
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
        // Video device switching not available in this version of libjami
        // Would need to use switchInput with camera source
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
        "" // Conference ID is returned via callback
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
        "" // File ID returned via callback
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

    // Video - Not all methods available in this version
    override fun getVideoDevices(): List<String> {
        if (!nativeLoaded) return emptyList()
        // Video device list not directly available, return empty
        return emptyList()
    }

    override fun getCurrentVideoDevice(): String {
        if (!nativeLoaded) return ""
        return ""
    }

    override suspend fun setVideoDevice(deviceId: String) = withContext(Dispatchers.IO) {
        if (!nativeLoaded) return@withContext
        // Not directly available in this version
    }

    override suspend fun startVideo() = withContext(Dispatchers.IO) {
        if (!nativeLoaded) return@withContext
        // Use startLocalMediaRecorder if needed
    }

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
        if (!nativeLoaded) return emptyList()
        return stringVectToList(JamiService.getAudioInputDeviceList())
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
