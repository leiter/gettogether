package com.gettogether.app.jami

import android.content.Context
import android.util.Log
import com.gettogether.app.data.util.VCardParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong
import net.jami.daemon.*

/**
 * Android implementation of JamiBridge using the SWIG-generated JamiService.
 * This class wraps the native Jami daemon via SWIG bindings.
 */
class SwigJamiBridge(private val context: Context) : JamiBridge {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Event buffer size - increased from 64 to 512 to prevent event loss during rapid sync operations
    private val EVENT_BUFFER_SIZE = 512

    // Overflow counters for monitoring - helps detect sync issues
    private val eventsOverflowCount = AtomicLong(0)
    private val accountEventsOverflowCount = AtomicLong(0)
    private val callEventsOverflowCount = AtomicLong(0)
    private val conversationEventsOverflowCount = AtomicLong(0)
    private val contactEventsOverflowCount = AtomicLong(0)

    // Event flows with larger buffers and DROP_OLDEST overflow strategy
    // DROP_OLDEST prevents suspension but may lose events if buffer fills up
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

    private var _isDaemonRunning = false

    // Cache for active calls with their media lists (used for proper media negotiation)
    private val activeCallsMediaCache = mutableMapOf<String, List<Map<String, String>>>()

    // SWIG Callback implementations
    private val configCallback = object : ConfigurationCallback() {
        override fun registrationStateChanged(accountId: String?, state: String?, code: Int, detail: String?) {
            Log.i(TAG, "[ACCOUNT-EVENT] registrationStateChanged: accountId=$accountId, state=$state, code=$code, detail=$detail")
            val regState = parseRegistrationState(state ?: "")
            Log.i(TAG, "[ACCOUNT-EVENT] Parsed registration state: $regState")
            val event = JamiAccountEvent.RegistrationStateChanged(
                accountId ?: "",
                regState,
                code,
                detail ?: ""
            )
            val emitted = _accountEvents.tryEmit(event)
            Log.i(TAG, "[ACCOUNT-EVENT] RegistrationStateChanged event emitted: $emitted")
            _events.tryEmit(event)
        }

        override fun accountsChanged() {
            Log.i(TAG, "[ACCOUNT-EVENT] accountsChanged callback triggered")
            Log.i(TAG, "[ACCOUNT-EVENT] This indicates accounts list was modified (add/remove)")
        }

        override fun accountDetailsChanged(accountId: String?, details: StringMap?) {
            Log.i(TAG, "[ACCOUNT-EVENT] accountDetailsChanged: accountId=$accountId")
            if (accountId != null && details != null) {
                val detailsMap = stringMapToKotlin(details)
                Log.i(TAG, "[ACCOUNT-EVENT] Changed details count: ${detailsMap.size}")
                detailsMap.forEach { (key, value) ->
                    val safeValue = if (key.contains("password", ignoreCase = true)) "***" else value
                    Log.i(TAG, "[ACCOUNT-EVENT]   $key = $safeValue")
                }
                val event = JamiAccountEvent.AccountDetailsChanged(accountId, detailsMap)
                val emitted = _accountEvents.tryEmit(event)
                Log.i(TAG, "[ACCOUNT-EVENT] AccountDetailsChanged event emitted: $emitted")
                _events.tryEmit(event)
            }
        }

        override fun profileReceived(accountId: String?, from: String?, vcardPath: String?) {
            // This callback is for CONTACT profiles (when we receive a contact's vCard)
            // Parameters:
            //   - accountId: our account
            //   - from: the contact's URI whose profile we received
            //   - vcardPath: path to the contact's vCard file
            Log.d(TAG, "profileReceived: accountId=$accountId, from=$from, vcardPath=$vcardPath")

            if (accountId == null || from == null || vcardPath.isNullOrBlank()) {
                Log.w(TAG, "profileReceived: Missing required parameters")
                return
            }

            try {
                // Read and parse the vCard file
                val vcardFile = java.io.File(vcardPath)
                if (!vcardFile.exists()) {
                    Log.w(TAG, "profileReceived: vCard file not found: $vcardPath")
                    return
                }

                val vcardBytes = vcardFile.readBytes()
                val profile = VCardParser.parse(vcardBytes)

                if (profile != null) {
                    Log.i(TAG, "profileReceived: Parsed contact profile - displayName='${profile.displayName}', hasPhoto=${profile.photoBase64 != null}")

                    // Emit contact profile event
                    val event = JamiContactEvent.ContactProfileReceived(
                        accountId = accountId,
                        contactUri = from,
                        displayName = profile.displayName,
                        avatarBase64 = profile.photoBase64
                    )
                    val emitted = _contactEvents.tryEmit(event)
                    Log.i(TAG, "profileReceived: ContactProfileReceived event emitted=$emitted")
                    _events.tryEmit(event)
                } else {
                    Log.w(TAG, "profileReceived: Failed to parse vCard from $vcardPath")
                }
            } catch (e: Exception) {
                Log.e(TAG, "profileReceived: Error reading/parsing vCard: ${e.message}", e)
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

        override fun getHardwareAudioFormat(result: IntVect?) {
            // CRITICAL: Provide native audio format to the daemon
            // This prevents crashes when OpenSL ES can't create streams at non-native sample rates
            var sampleRate = 44100  // Default fallback
            var bufferSize = 256    // Default fallback

            try {
                val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
                val nativeSampleRate = audioManager.getProperty(android.media.AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
                val nativeBufferSize = audioManager.getProperty(android.media.AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)

                if (nativeSampleRate != null) {
                    sampleRate = nativeSampleRate.toInt()
                }
                if (nativeBufferSize != null) {
                    bufferSize = nativeBufferSize.toInt()
                }

                Log.i(TAG, "[AUDIO] Native hardware audio format: sampleRate=$sampleRate, bufferSize=$bufferSize")
            } catch (e: Exception) {
                Log.w(TAG, "[AUDIO] Failed to get native audio format, using defaults: ${e.message}")
            }

            result?.add(sampleRate)
            result?.add(bufferSize)
        }
    }

    private val callCallback = object : Callback() {
        override fun callStateChanged(accountId: String?, callId: String?, state: String?, code: Int) {
            if (accountId != null && callId != null) {
                Log.i(TAG, "[CALL] callStateChanged: callId=$callId, state=$state, code=$code")

                val callState = parseCallState(state ?: "")

                // Clean up media cache when call ends
                if (callState == CallState.OVER || callState == CallState.HUNGUP || callState == CallState.FAILURE) {
                    activeCallsMediaCache.remove(callId)
                    Log.d(TAG, "[CALL] Cleaned up media cache for call $callId")
                }

                val event = JamiCallEvent.CallStateChanged(accountId, callId, callState, code)
                _callEvents.tryEmit(event)
                _events.tryEmit(event)
            }
        }

        override fun incomingCall(accountId: String?, callId: String?, from: String?, mediaList: VectMap?) {
            if (accountId != null && callId != null && from != null) {
                Log.i(TAG, "[CALL] incomingCall: callId=$callId, from=${from.take(16)}..., mediaCount=${mediaList?.size ?: 0}")

                // Convert VectMap to List<Map<String, String>> and store for later use
                val mediaListConverted = mutableListOf<Map<String, String>>()
                var hasVideo = false

                if (mediaList != null) {
                    for (i in 0 until mediaList.size) {
                        val media = mediaList[i]
                        if (media != null) {
                            val mediaMap = mutableMapOf<String, String>()
                            val keys = media.keys()
                            for (j in 0 until keys.size) {
                                val key = keys[j]
                                mediaMap[key] = media[key] ?: ""
                            }
                            mediaListConverted.add(mediaMap)
                            Log.i(TAG, "[CALL]   Media[$i]: $mediaMap")

                            if (mediaMap["MEDIA_TYPE"] == "MEDIA_TYPE_VIDEO") {
                                hasVideo = true
                            }
                        }
                    }
                }

                // Store media list in cache for use when accepting the call
                activeCallsMediaCache[callId] = mediaListConverted
                Log.i(TAG, "[CALL] Stored ${mediaListConverted.size} media entries for call $callId")

                val event = JamiCallEvent.IncomingCall(
                    accountId = accountId,
                    callId = callId,
                    peerId = from,
                    peerDisplayName = from,
                    hasVideo = hasVideo,
                    mediaList = mediaListConverted
                )
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
            Log.d(TAG, "  LineStatus (note): '$lineStatus'")

            // Log if this is from our timestamped presence updates
            if (lineStatus?.startsWith("online:") == true) {
                Log.i(TAG, "  ✓ Detected ONLINE presence with timestamp: ${lineStatus.substringAfter(":")}")
            } else if (lineStatus?.startsWith("heartbeat:") == true) {
                Log.i(TAG, "  ✓ Detected HEARTBEAT presence with timestamp: ${lineStatus.substringAfter(":")}")
            } else if (lineStatus?.startsWith("offline:") == true) {
                Log.i(TAG, "  ✓ Detected OFFLINE presence with timestamp: ${lineStatus.substringAfter(":")}")
            }

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

    private val dataTransferCallback = object : DataTransferCallback() {
        override fun dataTransferEvent(accountId: String?, conversationId: String?, interactionId: String?, fileId: String?, eventCode: Int) {
            Log.i(TAG, "┌─── dataTransferEvent CALLBACK ───")
            Log.i(TAG, "│ accountId: $accountId")
            Log.i(TAG, "│ conversationId: $conversationId")
            Log.i(TAG, "│ interactionId (messageId): $interactionId")
            Log.i(TAG, "│ fileId: $fileId")
            Log.i(TAG, "│ eventCode: $eventCode (${getEventCodeName(eventCode)})")
            Log.i(TAG, "└─── End dataTransferEvent ───")

            // Emit file transfer event for UI updates
            if (accountId != null && conversationId != null) {
                val event = JamiConversationEvent.FileTransferProgressUpdated(
                    accountId = accountId,
                    conversationId = conversationId,
                    interactionId = interactionId ?: "",
                    fileId = fileId ?: "",
                    eventCode = eventCode
                )
                val emitted = _conversationEvents.tryEmit(event)
                _events.tryEmit(event)
                Log.i(TAG, "FileTransferProgressUpdated event emitted: $emitted")
            }
        }

        private fun getEventCodeName(code: Int): String = when (code) {
            0 -> "INVALID"
            1 -> "CREATED"
            2 -> "UNSUPPORTED"
            3 -> "WAIT_PEER_ACCEPTANCE"
            4 -> "WAIT_HOST_ACCEPTANCE"
            5 -> "ONGOING"
            6 -> "FINISHED"
            7 -> "CLOSED_BY_HOST"
            8 -> "CLOSED_BY_PEER"
            9 -> "INVALID_PATHNAME"
            10 -> "UNJOINABLE_PEER"
            11 -> "TIMEOUT_EXPIRED"
            else -> "UNKNOWN($code)"
        }
    }
    private val videoCallback = object : VideoCallback() {}

    private val conversationCallback = object : ConversationCallback() {
        override fun conversationReady(accountId: String?, conversationId: String?) {
            Log.i(TAG, "┌─── conversationReady CALLBACK ───")
            Log.i(TAG, "│ accountId: $accountId")
            Log.i(TAG, "│ conversationId: $conversationId")
            Log.i(TAG, "└─── End conversationReady ───")
            if (accountId != null && conversationId != null) {
                val event = JamiConversationEvent.ConversationReady(accountId, conversationId)
                _conversationEvents.tryEmit(event)
                _events.tryEmit(event)
            }
        }

        override fun swarmLoaded(requestId: Long, accountId: String?, conversationId: String?, messages: net.jami.daemon.SwarmMessageVect?) {
            Log.i(TAG, "┌─── swarmLoaded CALLBACK ───")
            Log.i(TAG, "│ requestId: $requestId")
            Log.i(TAG, "│ accountId: $accountId")
            Log.i(TAG, "│ conversationId: $conversationId")
            Log.i(TAG, "│ messages count: ${messages?.size ?: 0}")
            Log.i(TAG, "└─── End swarmLoaded ───")

            if (accountId != null && conversationId != null && messages != null) {
                val swarmMessages = mutableListOf<SwarmMessage>()
                for (i in 0 until messages.size.toInt()) {
                    val msg = messages[i]
                    swarmMessages.add(convertSwarmMessage(msg))
                }
                Log.i(TAG, "swarmLoaded: Converted ${swarmMessages.size} messages")

                val event = JamiConversationEvent.MessagesLoaded(
                    requestId = requestId.toInt(),
                    accountId = accountId,
                    conversationId = conversationId,
                    messages = swarmMessages
                )
                val emitted = _conversationEvents.tryEmit(event)
                _events.tryEmit(event)
                Log.i(TAG, "swarmLoaded: Event emitted=$emitted")
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
            Log.i(TAG, "┌─── conversationRequestReceived CALLBACK ───")
            Log.i(TAG, "│ accountId: $accountId")
            Log.i(TAG, "│ conversationId: $conversationId")
            if (metadata != null) {
                val metaMap = stringMapToKotlin(metadata)
                Log.i(TAG, "│ Metadata fields:")
                metaMap.forEach { (key, value) ->
                    Log.i(TAG, "│   $key = $value")
                }
            } else {
                Log.i(TAG, "│ Metadata: null")
            }
            Log.i(TAG, "└─── End conversationRequestReceived ───")

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

        override fun conversationRemoved(accountId: String?, conversationId: String?) {
            Log.i(TAG, "┌─── conversationRemoved CALLBACK ───")
            Log.i(TAG, "│ accountId: $accountId")
            Log.i(TAG, "│ conversationId: $conversationId")
            Log.i(TAG, "└─── End conversationRemoved ───")
            if (accountId != null && conversationId != null) {
                val event = JamiConversationEvent.ConversationRemoved(accountId, conversationId)
                _conversationEvents.tryEmit(event)
                _events.tryEmit(event)
            }
        }
    }

    companion object {
        private const val TAG = "SwigJamiBridge"
        private var nativeLoaded = false

        // Retry configuration for daemon operations
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val INITIAL_RETRY_DELAY_MS = 1000L
        private const val MAX_RETRY_DELAY_MS = 30000L
        private const val RETRY_BACKOFF_MULTIPLIER = 2.0

        /**
         * Executes a daemon operation with exponential backoff retry.
         * Useful for operations that may fail due to transient network or daemon issues.
         *
         * @param operationName Name of the operation for logging
         * @param maxAttempts Maximum number of retry attempts (default: MAX_RETRY_ATTEMPTS)
         * @param block The operation to execute
         * @return Result of the operation, or null if all retries failed
         */
        suspend fun <T> withRetry(
            operationName: String,
            maxAttempts: Int = MAX_RETRY_ATTEMPTS,
            block: suspend () -> T
        ): T? {
            var currentDelay = INITIAL_RETRY_DELAY_MS
            var lastException: Exception? = null

            repeat(maxAttempts) { attempt ->
                try {
                    return block()
                } catch (e: Exception) {
                    lastException = e
                    Log.w(TAG, "[RETRY] $operationName failed (attempt ${attempt + 1}/$maxAttempts): ${e.message}")

                    if (attempt < maxAttempts - 1) {
                        Log.d(TAG, "[RETRY] Waiting ${currentDelay}ms before retry...")
                        kotlinx.coroutines.delay(currentDelay)
                        currentDelay = minOf(
                            (currentDelay * RETRY_BACKOFF_MULTIPLIER).toLong(),
                            MAX_RETRY_DELAY_MS
                        )
                    }
                }
            }

            Log.e(TAG, "[RETRY] $operationName failed after $maxAttempts attempts: ${lastException?.message}")
            return null
        }

        /**
         * Executes a daemon operation with exponential backoff retry, returning a Result.
         * Use this variant when you need to distinguish between success and failure.
         */
        suspend fun <T> withRetryResult(
            operationName: String,
            maxAttempts: Int = MAX_RETRY_ATTEMPTS,
            block: suspend () -> T
        ): Result<T> {
            var currentDelay = INITIAL_RETRY_DELAY_MS
            var lastException: Exception? = null

            repeat(maxAttempts) { attempt ->
                try {
                    return Result.success(block())
                } catch (e: Exception) {
                    lastException = e
                    Log.w(TAG, "[RETRY] $operationName failed (attempt ${attempt + 1}/$maxAttempts): ${e.message}")

                    if (attempt < maxAttempts - 1) {
                        Log.d(TAG, "[RETRY] Waiting ${currentDelay}ms before retry...")
                        kotlinx.coroutines.delay(currentDelay)
                        currentDelay = minOf(
                            (currentDelay * RETRY_BACKOFF_MULTIPLIER).toLong(),
                            MAX_RETRY_DELAY_MS
                        )
                    }
                }
            }

            Log.e(TAG, "[RETRY] $operationName failed after $maxAttempts attempts")
            return Result.failure(lastException ?: Exception("$operationName failed after $maxAttempts attempts"))
        }

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

        // Debug log to see message structure
        val msgType = msg.type ?: ""
        val msgBody = bodyMap["body"] ?: ""
        if (msgBody.endsWith(".png") || msgBody.endsWith(".jpg") || msgBody.endsWith(".jpeg") ||
            msgType.contains("data-transfer") || bodyMap.containsKey("fileId") || bodyMap.containsKey("tid")) {
            Log.d(TAG, "convertSwarmMessage: File-like message detected!")
            Log.d(TAG, "  msg.type='$msgType'")
            Log.d(TAG, "  bodyMap=$bodyMap")
        }

        // Parse timestamp - daemon sends Unix timestamp in SECONDS, we need milliseconds
        val rawTimestamp = bodyMap["timestamp"]?.toLongOrNull() ?: System.currentTimeMillis()
        // If timestamp is less than 10^12, it's in seconds (before year 2001 in ms, but valid as seconds until 2286)
        val timestampMs = if (rawTimestamp < 1_000_000_000_000L) {
            rawTimestamp * 1000L  // Convert seconds to milliseconds
        } else {
            rawTimestamp  // Already in milliseconds
        }

        return SwarmMessage(
            id = msg.id ?: "",
            type = msg.type ?: "",
            author = bodyMap["author"] ?: "",
            body = bodyMap,
            reactions = emptyList(),
            timestamp = timestampMs,
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
        Log.i(TAG, "[ACCOUNT-CREATE] === createAccount() called ===")
        Log.i(TAG, "[ACCOUNT-CREATE] displayName='$displayName', password.length=${password.length}")

        if (!nativeLoaded) {
            Log.e(TAG, "[ACCOUNT-CREATE] FAILED: Native library not loaded!")
            return@withContext ""
        }

        val detailsMap = mapOf(
            "Account.type" to "RING",
            "Account.alias" to displayName,
            "Account.displayName" to displayName,
            "Account.archivePassword" to password,
            // Enable DHT proxy to allow communication through Jami's relay servers
            // This is essential for devices behind NAT (like emulators) that can't establish direct P2P connections
            "Account.proxyEnabled" to "true",
            // Disable UPnP - it often fails and causes unnecessary connection attempts
            "Account.upnpEnabled" to "false",
            // Enable TURN - required for cross-network connectivity (WiFi <-> Mobile)
            // TURN relay is the fallback when direct/STUN connections fail across NAT/CGNAT
            "TURN.enable" to "true",
            "TURN.server" to "turn.jami.net",
            "TURN.username" to "ring",
            "TURN.password" to "ring"
        )
        Log.i(TAG, "[ACCOUNT-CREATE] Account details map: $detailsMap")

        val details = kotlinToStringMap(detailsMap)
        Log.i(TAG, "[ACCOUNT-CREATE] Calling JamiService.addAccount()...")

        val result = JamiService.addAccount(details)
        Log.i(TAG, "[ACCOUNT-CREATE] JamiService.addAccount() returned: '$result'")
        Log.i(TAG, "[ACCOUNT-CREATE] Note: Registration events will follow asynchronously")
        result
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
        Log.i(TAG, "[ACCOUNT-PERSIST] getAccountIds() called")
        if (!nativeLoaded) {
            Log.e(TAG, "[ACCOUNT-PERSIST] getAccountIds() FAILED: Native not loaded")
            return emptyList()
        }
        val accountIds = stringVectToList(JamiService.getAccountList())
        Log.i(TAG, "[ACCOUNT-PERSIST] Found ${accountIds.size} accounts: $accountIds")
        return accountIds
    }

    override fun getAccountDetails(accountId: String): Map<String, String> {
        Log.i(TAG, "[ACCOUNT-PERSIST] getAccountDetails() called for accountId=$accountId")
        if (!nativeLoaded) {
            Log.e(TAG, "[ACCOUNT-PERSIST] getAccountDetails() FAILED: Native not loaded")
            return emptyMap()
        }
        val details = stringMapToKotlin(JamiService.getAccountDetails(accountId))
        Log.i(TAG, "[ACCOUNT-PERSIST] Account details for $accountId:")
        details.forEach { (key, value) ->
            // Don't log sensitive values
            val safeValue = if (key.contains("password", ignoreCase = true) || key.contains("secret", ignoreCase = true)) "***" else value
            Log.i(TAG, "[ACCOUNT-PERSIST]   $key = $safeValue")
        }
        return details
    }

    override fun getVolatileAccountDetails(accountId: String): Map<String, String> {
        Log.i(TAG, "[ACCOUNT-PERSIST] getVolatileAccountDetails() called for accountId=$accountId")
        if (!nativeLoaded) {
            Log.e(TAG, "[ACCOUNT-PERSIST] getVolatileAccountDetails() FAILED: Native not loaded")
            return emptyMap()
        }
        val details = stringMapToKotlin(JamiService.getVolatileAccountDetails(accountId))
        Log.i(TAG, "[ACCOUNT-PERSIST] Volatile details for $accountId:")
        details.forEach { (key, value) ->
            Log.i(TAG, "[ACCOUNT-PERSIST]   $key = $value")
        }
        return details
    }

    override suspend fun setAccountDetails(accountId: String, details: Map<String, String>) = withContext(Dispatchers.IO) {
        if (!nativeLoaded) return@withContext
        JamiService.setAccountDetails(accountId, kotlinToStringMap(details))
    }

    override suspend fun setAccountActive(accountId: String, active: Boolean) = withContext(Dispatchers.IO) {
        if (!nativeLoaded) return@withContext
        JamiService.setAccountActive(accountId, active)
    }

    override suspend fun connectivityChanged() = withContext(Dispatchers.IO) {
        if (!nativeLoaded) return@withContext
        Log.i(TAG, "[CONNECTIVITY] Notifying daemon about network change...")
        JamiService.connectivityChanged()
        Log.i(TAG, "[CONNECTIVITY] Daemon notified")
    }

    override suspend fun updateProfile(accountId: String, displayName: String, avatarPath: String?) = withContext(Dispatchers.IO) {
        if (!nativeLoaded) return@withContext
        // Determine MIME type based on file extension
        // IMPORTANT: If avatarPath is null/empty, pass empty mimeType to preserve existing photo
        // The daemon removes existing photo when fileType is non-empty but avatar path is invalid
        val mimeType = when {
            avatarPath.isNullOrEmpty() -> "" // Preserve existing photo - don't enter photo processing block
            avatarPath.endsWith(".jpg", ignoreCase = true) -> "image/jpeg"
            avatarPath.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
            avatarPath.endsWith(".png", ignoreCase = true) -> "image/png"
            else -> "image/jpeg" // Default for valid paths without recognized extension
        }
        Log.d(TAG, "updateProfile: accountId=$accountId, displayName=$displayName, avatarPath=$avatarPath, mimeType=$mimeType")
        JamiService.updateProfile(accountId, displayName, avatarPath ?: "", mimeType, 0)
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
        Log.i(TAG, "getContacts: Fetching contacts for account $accountId")
        val contacts = JamiService.getContacts(accountId)
        Log.i(TAG, "getContacts: Jami returned ${contacts.size} contacts")
        val result = mutableListOf<JamiContact>()
        for (i in 0 until contacts.size) {
            val map = stringMapToKotlin(contacts[i])
            Log.i(TAG, "getContacts: Contact $i raw data: $map")
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
        val details = stringMapToKotlin(JamiService.getContactDetails(accountId, uri))
        Log.i(TAG, "getContactDetails: uri=${uri.take(16)}... details=$details")
        return details
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

    override suspend fun publishPresence(accountId: String, isOnline: Boolean, note: String) = withContext(Dispatchers.IO) {
        if (!nativeLoaded) {
            Log.w(TAG, "publishPresence: native library not loaded")
            return@withContext
        }
        Log.d(TAG, "[PRESENCE-PUBLISH] Publishing presence for account ${accountId.take(16)}...")
        Log.d(TAG, "[PRESENCE-PUBLISH]   Status: ${if (isOnline) "ONLINE" else "OFFLINE"}")
        Log.d(TAG, "[PRESENCE-PUBLISH]   Note: ${note.ifEmpty { "(none)" }}")
        try {
            JamiService.publish(accountId, isOnline, note)
            Log.i(TAG, "[PRESENCE-PUBLISH] ✓ Presence published successfully")
        } catch (e: Exception) {
            Log.e(TAG, "[PRESENCE-PUBLISH] ✗ Failed to publish presence: ${e.message}")
            e.printStackTrace()
        }
    }

    override suspend fun acceptTrustRequest(accountId: String, uri: String) = withContext(Dispatchers.IO) {
        if (!nativeLoaded) return@withContext
        JamiService.acceptTrustRequest(accountId, uri)
    }

    override suspend fun discardTrustRequest(accountId: String, uri: String) = withContext(Dispatchers.IO) {
        if (!nativeLoaded) {
            Log.w(TAG, "discardTrustRequest: native library not loaded")
            return@withContext
        }
        Log.i(TAG, "[TRUST-DISCARD] Calling JamiService.discardTrustRequest(accountId=$accountId, uri=$uri)")
        val result = JamiService.discardTrustRequest(accountId, uri)
        Log.i(TAG, "[TRUST-DISCARD] Result: $result")
        if (!result) {
            Log.w(TAG, "[TRUST-DISCARD] ⚠️ discardTrustRequest returned false - request may not have been removed!")
        }
    }

    override fun getTrustRequests(accountId: String): List<TrustRequest> {
        if (!nativeLoaded) return emptyList()
        Log.i(TAG, "[TRUST-GET] getTrustRequests called for accountId=$accountId")
        val requests = JamiService.getTrustRequests(accountId)
        Log.i(TAG, "[TRUST-GET] JamiService returned ${requests.size} trust requests")
        val result = mutableListOf<TrustRequest>()
        for (i in 0 until requests.size) {
            val map = stringMapToKotlin(requests[i])
            val from = map["from"] ?: ""
            val conversationId = map["conversationId"] ?: ""
            val received = map["received"]?.toLongOrNull() ?: 0L
            Log.i(TAG, "[TRUST-GET]   [$i] from=$from, conversationId=$conversationId, received=$received")
            result.add(TrustRequest(
                from = from,
                conversationId = conversationId,
                payload = ByteArray(0),
                received = received
            ))
        }
        Log.i(TAG, "[TRUST-GET] Returning ${result.size} trust requests")
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

    override suspend fun clearConversationCache(accountId: String, conversationId: String) = withContext(Dispatchers.IO) {
        if (!nativeLoaded) return@withContext
        Log.d(TAG, "clearConversationCache: Clearing cache for conversation ${conversationId.take(8)}...")
        JamiService.clearCache(accountId, conversationId)
        Log.d(TAG, "clearConversationCache: Cache cleared successfully")
    }

    override fun getConversationInfo(accountId: String, conversationId: String): Map<String, String> {
        if (!nativeLoaded) return emptyMap()
        val info = stringMapToKotlin(JamiService.conversationInfos(accountId, conversationId))
        Log.d(TAG, "getConversationInfo: convId=${conversationId.take(8)}... info=$info")
        return info
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
            Log.d(TAG, "getConversationMembers: member[$i] = $map")
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

        // Use retry for conversation request acceptance (critical for sync)
        withRetry("acceptConversationRequest") {
            JamiService.acceptConversationRequest(accountId, conversationId)
        }
        Unit
    }

    override suspend fun declineConversationRequest(accountId: String, conversationId: String) = withContext(Dispatchers.IO) {
        if (!nativeLoaded) return@withContext
        JamiService.declineConversationRequest(accountId, conversationId)
    }

    override fun getConversationRequests(accountId: String): List<ConversationRequest> {
        if (!nativeLoaded) return emptyList()
        Log.i(TAG, "┌─── getConversationRequests ───")
        Log.i(TAG, "│ accountId: $accountId")
        val requests = JamiService.getConversationRequests(accountId)
        Log.i(TAG, "│ Raw request count: ${requests.size}")
        val result = mutableListOf<ConversationRequest>()
        for (i in 0 until requests.size) {
            val map = stringMapToKotlin(requests[i])
            Log.i(TAG, "│ ┌─ Request [$i] raw fields:")
            map.forEach { (key, value) ->
                Log.i(TAG, "│ │   $key = $value")
            }
            Log.i(TAG, "│ └─ End Request [$i]")
            result.add(ConversationRequest(
                conversationId = map["id"] ?: "",
                from = map["from"] ?: "",
                metadata = map,
                received = map["received"]?.toLongOrNull() ?: 0L
            ))
        }
        Log.i(TAG, "└─── End getConversationRequests (${result.size} requests) ───")
        return result
    }

    // Messaging
    override suspend fun sendMessage(accountId: String, conversationId: String, message: String, replyTo: String?): String = withContext(Dispatchers.IO) {
        if (!nativeLoaded) {
            Log.w(TAG, "sendMessage: Native library not loaded")
            return@withContext ""
        }
        Log.i(TAG, "sendMessage: accountId=$accountId, conversationId=$conversationId, message='$message'")

        // Use retry with exponential backoff for reliability
        val result = withRetry("sendMessage") {
            JamiService.sendMessage(accountId, conversationId, message, replyTo ?: "", 0)
            Log.i(TAG, "sendMessage: JamiService.sendMessage called successfully")
            ""
        }

        result ?: ""
    }

    override suspend fun loadConversationMessages(accountId: String, conversationId: String, fromMessage: String, count: Int): Int = withContext(Dispatchers.IO) {
        if (!nativeLoaded) return@withContext 0

        // Use retry with exponential backoff for message loading (critical for sync)
        withRetry("loadConversationMessages") {
            JamiService.loadConversation(accountId, conversationId, fromMessage, count.toLong()).toInt()
        } ?: 0
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

        Log.i(TAG, "[CALL] placeCall: uri=${uri.take(16)}..., withVideo=$withVideo")

        val mediaList = VectMap()

        // Audio media descriptor - all required fields
        val audioMedia = StringMap()
        audioMedia["MEDIA_TYPE"] = "MEDIA_TYPE_AUDIO"
        audioMedia["ENABLED"] = "true"
        audioMedia["MUTED"] = "false"
        audioMedia["SOURCE"] = ""
        audioMedia["LABEL"] = "audio_0"
        audioMedia["ON_HOLD"] = "false"
        mediaList.add(audioMedia)

        if (withVideo) {
            // Video media descriptor - all required fields
            val videoMedia = StringMap()
            videoMedia["MEDIA_TYPE"] = "MEDIA_TYPE_VIDEO"
            videoMedia["ENABLED"] = "true"
            videoMedia["MUTED"] = "false"
            videoMedia["SOURCE"] = "camera://0"
            videoMedia["LABEL"] = "video_0"
            videoMedia["ON_HOLD"] = "false"
            mediaList.add(videoMedia)
        }

        val callId = JamiService.placeCallWithMedia(accountId, uri, mediaList)
        Log.i(TAG, "[CALL] placeCall returned callId=$callId")
        callId
    }

    override suspend fun acceptCall(accountId: String, callId: String, withVideo: Boolean) = withContext(Dispatchers.IO) {
        if (!nativeLoaded) return@withContext

        Log.i(TAG, "[CALL] acceptCall: callId=$callId, withVideo=$withVideo")

        // Try to use stored media list from daemon (proper media negotiation)
        val storedMedia = activeCallsMediaCache[callId]

        if (storedMedia != null && storedMedia.isNotEmpty()) {
            Log.i(TAG, "[CALL] Using stored media list (${storedMedia.size} entries)")

            val mediaList = VectMap()
            storedMedia.forEach { mediaMap ->
                val media = StringMap()
                mediaMap.forEach { (k, v) ->
                    media[k] = v
                }
                // Mute video if not accepting with video
                if (!withVideo && mediaMap["MEDIA_TYPE"] == "MEDIA_TYPE_VIDEO") {
                    media["MUTED"] = "true"
                    Log.i(TAG, "[CALL]   Muting video media")
                }
                mediaList.add(media)
            }

            JamiService.acceptWithMedia(accountId, callId, mediaList)
            Log.i(TAG, "[CALL] Call accepted with stored media")
        } else {
            Log.w(TAG, "[CALL] No stored media found, using fallback")
            // Fallback: construct media list (less reliable)
            if (withVideo) {
                val mediaList = VectMap()

                val audioMedia = StringMap()
                audioMedia["MEDIA_TYPE"] = "MEDIA_TYPE_AUDIO"
                audioMedia["ENABLED"] = "true"
                audioMedia["MUTED"] = "false"
                audioMedia["SOURCE"] = ""
                audioMedia["LABEL"] = "audio_0"
                audioMedia["ON_HOLD"] = "false"
                mediaList.add(audioMedia)

                val videoMedia = StringMap()
                videoMedia["MEDIA_TYPE"] = "MEDIA_TYPE_VIDEO"
                videoMedia["ENABLED"] = "true"
                videoMedia["MUTED"] = "false"
                videoMedia["SOURCE"] = "camera://0"
                videoMedia["LABEL"] = "video_0"
                videoMedia["ON_HOLD"] = "false"
                mediaList.add(videoMedia)

                JamiService.acceptWithMedia(accountId, callId, mediaList)
            } else {
                // Audio only - simple accept
                JamiService.accept(accountId, callId)
            }
        }

        // Clean up cache entry after accepting
        activeCallsMediaCache.remove(callId)
    }

    override suspend fun refuseCall(accountId: String, callId: String) = withContext(Dispatchers.IO) {
        if (!nativeLoaded) return@withContext
        JamiService.refuse(accountId, callId)
    }

    override suspend fun hangUp(accountId: String, callId: String) = withContext(Dispatchers.IO) {
        if (!nativeLoaded) {
            Log.w(TAG, "[CALL] hangUp: Native not loaded!")
            return@withContext
        }
        Log.i(TAG, "[CALL] hangUp: accountId=${accountId.take(8)}..., callId=$callId")
        JamiService.hangUp(accountId, callId)
        Log.i(TAG, "[CALL] hangUp: completed")
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
        // TODO: Implement camera switching - research JamiService method for this
        Log.d(TAG, "[CALL] switchCamera - not yet implemented")
    }

    override suspend fun answerMediaChangeRequest(accountId: String, callId: String, mediaList: List<Map<String, String>>) = withContext(Dispatchers.IO) {
        if (!nativeLoaded) return@withContext

        Log.i(TAG, "[CALL] answerMediaChangeRequest: callId=$callId, mediaCount=${mediaList.size}")

        val vectMapMedia = VectMap()
        mediaList.forEach { mediaMap ->
            val media = StringMap()
            mediaMap.forEach { (k, v) ->
                media[k] = v
            }
            vectMapMedia.add(media)
        }

        JamiService.answerMediaChangeRequest(accountId, callId, vectMapMedia)
        Log.i(TAG, "[CALL] Media change request answered")
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
        Log.i(TAG, "┌─── sendFile ───")
        Log.i(TAG, "│ accountId: ${accountId.take(8)}...")
        Log.i(TAG, "│ conversationId: ${conversationId.take(8)}...")
        Log.i(TAG, "│ filePath: $filePath")
        Log.i(TAG, "│ displayName: $displayName")

        // Check if file exists
        val file = java.io.File(filePath)
        Log.i(TAG, "│ file.exists: ${file.exists()}")
        Log.i(TAG, "│ file.length: ${file.length()} bytes")
        Log.i(TAG, "│ file.canRead: ${file.canRead()}")

        if (!nativeLoaded) {
            Log.e(TAG, "│ ERROR: Native library not loaded!")
            Log.i(TAG, "└─── sendFile FAILED ───")
            return@withContext ""
        }

        // Use retry for file transfer (network-dependent operation)
        val result = withRetry("sendFile") {
            JamiService.sendFile(accountId, conversationId, filePath, displayName, "")
            Log.i(TAG, "│ JamiService.sendFile() called successfully")
            Log.i(TAG, "└─── sendFile SUCCESS ───")
            ""
        }

        if (result == null) {
            Log.i(TAG, "└─── sendFile FAILED after retries ───")
        }

        result ?: ""
    }

    override suspend fun acceptFileTransfer(accountId: String, conversationId: String, interactionId: String, fileId: String, destinationPath: String) = withContext(Dispatchers.IO) {
        Log.i(TAG, "┌─── acceptFileTransfer ───")
        Log.i(TAG, "│ accountId: ${accountId.take(8)}...")
        Log.i(TAG, "│ conversationId: ${conversationId.take(8)}...")
        Log.i(TAG, "│ interactionId: $interactionId")
        Log.i(TAG, "│ fileId: $fileId")
        Log.i(TAG, "│ destinationPath: $destinationPath")

        if (!nativeLoaded) {
            Log.e(TAG, "│ ERROR: Native library not loaded!")
            Log.i(TAG, "└─── acceptFileTransfer FAILED ───")
            return@withContext
        }

        // Check destination directory exists
        val destFile = java.io.File(destinationPath)
        val destDir = destFile.parentFile
        Log.i(TAG, "│ destDir: ${destDir?.absolutePath}")
        Log.i(TAG, "│ destDir.exists: ${destDir?.exists()}")

        if (destDir != null && !destDir.exists()) {
            val created = destDir.mkdirs()
            Log.i(TAG, "│ Created destDir: $created")
        }

        try {
            // Pass interactionId (messageId) and fileId to daemon - both are required
            JamiService.downloadFile(accountId, conversationId, interactionId, fileId, destinationPath)
            Log.i(TAG, "│ JamiService.downloadFile() called")
            Log.i(TAG, "└─── acceptFileTransfer SUCCESS ───")
        } catch (e: Exception) {
            Log.e(TAG, "│ ERROR: ${e.message}")
            e.printStackTrace()
            Log.i(TAG, "└─── acceptFileTransfer FAILED ───")
        }
    }

    override suspend fun cancelFileTransfer(accountId: String, conversationId: String, fileId: String) = withContext(Dispatchers.IO) {
        Log.i(TAG, "cancelFileTransfer: accountId=${accountId.take(8)}, convId=${conversationId.take(8)}, fileId=$fileId")
        if (!nativeLoaded) return@withContext
        JamiService.cancelDataTransfer(accountId, conversationId, fileId)
    }

    override fun getFileTransferInfo(accountId: String, conversationId: String, fileId: String): FileTransferInfo? {
        if (!nativeLoaded) return null
        val pathOut = arrayOf("")
        val totalOut = longArrayOf(0L)
        val progressOut = longArrayOf(0L)
        JamiService.fileTransferInfo(accountId, conversationId, fileId, pathOut, totalOut, progressOut)

        // Only log occasionally to avoid spam (every 10 seconds based on progress)
        if (progressOut[0] == 0L || progressOut[0] == totalOut[0]) {
            Log.d(TAG, "getFileTransferInfo: fileId=$fileId, path=${pathOut[0]}, total=${totalOut[0]}, progress=${progressOut[0]}")
        }

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
