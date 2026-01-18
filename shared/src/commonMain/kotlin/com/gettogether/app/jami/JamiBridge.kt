package com.gettogether.app.jami

import kotlinx.coroutines.flow.SharedFlow
import kotlin.time.Clock

/**
 * JamiBridge provides the platform-specific implementation to interface with
 * the Jami daemon. On Android this uses JNI, on iOS it uses Swift/ObjC interop.
 *
 * This is the core abstraction layer that enables the shared Kotlin code to
 * communicate with the native Jami daemon on each platform.
 */
interface JamiBridge {

    // =========================================================================
    // Daemon Lifecycle
    // =========================================================================

    /**
     * Initialize the Jami daemon with the given data path.
     * Must be called before any other daemon operations.
     */
    suspend fun initDaemon(dataPath: String)

    /**
     * Start the Jami daemon. Call after initDaemon().
     */
    suspend fun startDaemon()

    /**
     * Stop the Jami daemon and release all resources.
     */
    suspend fun stopDaemon()

    /**
     * Check if the daemon is currently running.
     */
    fun isDaemonRunning(): Boolean

    // =========================================================================
    // Account Management
    // =========================================================================

    /**
     * Create a new Jami account.
     * @param displayName The display name for the account
     * @param password Optional password to encrypt the account archive
     * @return The new account ID
     */
    suspend fun createAccount(displayName: String, password: String = ""): String

    /**
     * Import an existing account from archive file.
     * @param archivePath Path to the account archive (.gz file)
     * @param password Password to decrypt the archive
     * @return The imported account ID
     */
    suspend fun importAccount(archivePath: String, password: String): String

    /**
     * Export account to archive file for backup.
     * @param accountId The account to export
     * @param destinationPath Where to save the archive
     * @param password Password to encrypt the archive
     * @return True if successful
     */
    suspend fun exportAccount(accountId: String, destinationPath: String, password: String): Boolean

    /**
     * Delete an account permanently.
     */
    suspend fun deleteAccount(accountId: String)

    /**
     * Get list of all account IDs.
     */
    fun getAccountIds(): List<String>

    /**
     * Get detailed information about an account.
     */
    fun getAccountDetails(accountId: String): Map<String, String>

    /**
     * Get volatile (runtime) account details like registration state.
     */
    fun getVolatileAccountDetails(accountId: String): Map<String, String>

    /**
     * Update account settings.
     */
    suspend fun setAccountDetails(accountId: String, details: Map<String, String>)

    /**
     * Set the account active or inactive.
     */
    suspend fun setAccountActive(accountId: String, active: Boolean)

    /**
     * Update account profile (display name and avatar).
     */
    suspend fun updateProfile(accountId: String, displayName: String, avatarPath: String?)

    /**
     * Register a username on the Jami name server.
     */
    suspend fun registerName(accountId: String, name: String, password: String): Boolean

    /**
     * Look up a username on the name server.
     */
    suspend fun lookupName(accountId: String, name: String): LookupResult?

    /**
     * Look up an address on the name server.
     */
    suspend fun lookupAddress(accountId: String, address: String): LookupResult?

    // =========================================================================
    // Contact Management
    // =========================================================================

    /**
     * Get all contacts for an account.
     */
    fun getContacts(accountId: String): List<JamiContact>

    /**
     * Add a contact by their Jami ID (URI).
     */
    suspend fun addContact(accountId: String, uri: String)

    /**
     * Remove a contact.
     */
    suspend fun removeContact(accountId: String, uri: String, ban: Boolean = false)

    /**
     * Get contact details.
     */
    fun getContactDetails(accountId: String, uri: String): Map<String, String>

    /**
     * Accept an incoming trust request (contact request).
     */
    suspend fun acceptTrustRequest(accountId: String, uri: String)

    /**
     * Decline an incoming trust request.
     */
    suspend fun discardTrustRequest(accountId: String, uri: String)

    /**
     * Get pending trust requests.
     */
    fun getTrustRequests(accountId: String): List<TrustRequest>

    /**
     * Subscribe to presence updates for a contact (buddy).
     * @param accountId The account ID
     * @param uri The contact URI
     * @param flag true to subscribe, false to unsubscribe
     */
    suspend fun subscribeBuddy(accountId: String, uri: String, flag: Boolean)

    // =========================================================================
    // Conversation Management
    // =========================================================================

    /**
     * Get all conversation IDs for an account.
     */
    fun getConversations(accountId: String): List<String>

    /**
     * Start a new conversation (returns conversation ID).
     */
    suspend fun startConversation(accountId: String): String

    /**
     * Remove/leave a conversation.
     */
    suspend fun removeConversation(accountId: String, conversationId: String)

    /**
     * Get conversation info (title, description, avatar, etc.).
     */
    fun getConversationInfo(accountId: String, conversationId: String): Map<String, String>

    /**
     * Update conversation info.
     */
    suspend fun updateConversationInfo(accountId: String, conversationId: String, info: Map<String, String>)

    /**
     * Get members of a conversation.
     */
    fun getConversationMembers(accountId: String, conversationId: String): List<ConversationMember>

    /**
     * Add a member to a group conversation.
     */
    suspend fun addConversationMember(accountId: String, conversationId: String, contactUri: String)

    /**
     * Remove a member from a group conversation.
     */
    suspend fun removeConversationMember(accountId: String, conversationId: String, contactUri: String)

    /**
     * Accept a conversation request.
     */
    suspend fun acceptConversationRequest(accountId: String, conversationId: String)

    /**
     * Decline a conversation request.
     */
    suspend fun declineConversationRequest(accountId: String, conversationId: String)

    /**
     * Get pending conversation requests.
     */
    fun getConversationRequests(accountId: String): List<ConversationRequest>

    // =========================================================================
    // Messaging
    // =========================================================================

    /**
     * Send a text message in a conversation.
     * @param replyTo Optional message ID to reply to
     * @return The sent message ID
     */
    suspend fun sendMessage(
        accountId: String,
        conversationId: String,
        message: String,
        replyTo: String? = null
    ): String

    /**
     * Load conversation messages.
     * @param fromMessage Start loading from this message ID (for pagination)
     * @param count Number of messages to load
     * @return Request ID for the load operation
     */
    suspend fun loadConversationMessages(
        accountId: String,
        conversationId: String,
        fromMessage: String = "",
        count: Int = 50
    ): Int

    /**
     * Set typing/composing status.
     */
    suspend fun setIsComposing(accountId: String, conversationId: String, isComposing: Boolean)

    /**
     * Mark a message as read/displayed.
     */
    suspend fun setMessageDisplayed(accountId: String, conversationId: String, messageId: String)

    // =========================================================================
    // Calls
    // =========================================================================

    /**
     * Place an outgoing call.
     * @param uri The contact URI to call
     * @param withVideo Whether to include video
     * @return The call ID
     */
    suspend fun placeCall(accountId: String, uri: String, withVideo: Boolean): String

    /**
     * Accept an incoming call.
     */
    suspend fun acceptCall(accountId: String, callId: String, withVideo: Boolean)

    /**
     * Refuse/decline an incoming call.
     */
    suspend fun refuseCall(accountId: String, callId: String)

    /**
     * Hang up an active call.
     */
    suspend fun hangUp(accountId: String, callId: String)

    /**
     * Hold a call.
     */
    suspend fun holdCall(accountId: String, callId: String)

    /**
     * Unhold a call.
     */
    suspend fun unholdCall(accountId: String, callId: String)

    /**
     * Mute/unmute local audio.
     */
    suspend fun muteAudio(accountId: String, callId: String, muted: Boolean)

    /**
     * Mute/unmute local video.
     */
    suspend fun muteVideo(accountId: String, callId: String, muted: Boolean)

    /**
     * Get details of a call.
     */
    fun getCallDetails(accountId: String, callId: String): Map<String, String>

    /**
     * Get list of active calls.
     */
    fun getActiveCalls(accountId: String): List<String>

    /**
     * Switch between front and back camera.
     */
    suspend fun switchCamera()

    /**
     * Switch audio output (speaker/earpiece).
     */
    suspend fun switchAudioOutput(useSpeaker: Boolean)

    // =========================================================================
    // Conference Calls
    // =========================================================================

    /**
     * Create a conference from multiple participants.
     */
    suspend fun createConference(accountId: String, participantUris: List<String>): String

    /**
     * Join two calls into a conference.
     */
    suspend fun joinParticipant(
        accountId: String,
        callId1: String,
        accountId2: String,
        callId2: String
    )

    /**
     * Add a participant to an existing conference.
     */
    suspend fun addParticipantToConference(
        accountId: String,
        callId: String,
        conferenceAccountId: String,
        conferenceId: String
    )

    /**
     * Hang up the entire conference.
     */
    suspend fun hangUpConference(accountId: String, conferenceId: String)

    /**
     * Get conference details.
     */
    fun getConferenceDetails(accountId: String, conferenceId: String): Map<String, String>

    /**
     * Get list of participants in a conference.
     */
    fun getConferenceParticipants(accountId: String, conferenceId: String): List<String>

    /**
     * Get detailed info about conference participants.
     */
    fun getConferenceInfos(accountId: String, conferenceId: String): List<Map<String, String>>

    /**
     * Set conference layout (grid, one-big, etc.).
     */
    suspend fun setConferenceLayout(accountId: String, conferenceId: String, layout: ConferenceLayout)

    /**
     * Mute a participant in the conference (moderator only).
     */
    suspend fun muteConferenceParticipant(
        accountId: String,
        conferenceId: String,
        participantUri: String,
        muted: Boolean
    )

    /**
     * Kick a participant from the conference (moderator only).
     */
    suspend fun hangUpConferenceParticipant(
        accountId: String,
        conferenceId: String,
        participantUri: String,
        deviceId: String
    )

    // =========================================================================
    // File Transfer
    // =========================================================================

    /**
     * Send a file in a conversation.
     * @return The file transfer ID
     */
    suspend fun sendFile(
        accountId: String,
        conversationId: String,
        filePath: String,
        displayName: String
    ): String

    /**
     * Accept an incoming file transfer.
     */
    suspend fun acceptFileTransfer(
        accountId: String,
        conversationId: String,
        fileId: String,
        destinationPath: String
    )

    /**
     * Cancel/decline a file transfer.
     */
    suspend fun cancelFileTransfer(
        accountId: String,
        conversationId: String,
        fileId: String
    )

    /**
     * Get file transfer info.
     */
    fun getFileTransferInfo(
        accountId: String,
        conversationId: String,
        fileId: String
    ): FileTransferInfo?

    // =========================================================================
    // Video
    // =========================================================================

    /**
     * Get available video devices (cameras).
     */
    fun getVideoDevices(): List<String>

    /**
     * Get current video device.
     */
    fun getCurrentVideoDevice(): String

    /**
     * Set video device to use.
     */
    suspend fun setVideoDevice(deviceId: String)

    /**
     * Start video capture.
     */
    suspend fun startVideo()

    /**
     * Stop video capture.
     */
    suspend fun stopVideo()

    // =========================================================================
    // Audio Settings
    // =========================================================================

    /**
     * Get available audio output devices.
     */
    fun getAudioOutputDevices(): List<String>

    /**
     * Get available audio input devices.
     *
     * **⚠️ WARNING: This method crashes with SIGSEGV on both emulator and hardware.**
     *
     * Native library bug in libjami-core.so's getAudioInputDeviceList function.
     * Tested on Pixel 7a (2025-12-21) - confirmed crash with null pointer dereference.
     *
     * **DO NOT USE** - Use [useDefaultAudioInputDevice] instead.
     *
     * @throws UnsupportedOperationException Always throws to prevent crashes
     * @see useDefaultAudioInputDevice
     */
    @Deprecated(
        message = "Crashes with SIGSEGV. Use useDefaultAudioInputDevice() instead.",
        level = DeprecationLevel.ERROR
    )
    fun getAudioInputDevices(): List<String>

    /**
     * Set audio output device.
     */
    suspend fun setAudioOutputDevice(index: Int)

    /**
     * Set audio input device by index.
     *
     * **✅ WORKS on Pixel 7a** - Tested and confirmed (2025-12-21)
     *
     * This method works when called directly without enumerating devices first.
     * Use index 0 for default microphone.
     *
     * **Note:** Cannot enumerate available devices due to native library bug.
     * Use fixed indices (0 = default, 1 = secondary, etc.)
     *
     * @param index Device index (0 = default microphone)
     */
    suspend fun setAudioInputDevice(index: Int)

    /**
     * Use the default audio input device (microphone).
     *
     * **✅ SAFE** - Workaround for getAudioInputDevices() crash
     *
     * This is equivalent to calling `setAudioInputDevice(0)` but with a clearer intent.
     * Use this method instead of enumerating and selecting devices.
     *
     * **Tested on:** Pixel 7a hardware (2025-12-21) - Works correctly
     */
    suspend fun useDefaultAudioInputDevice() {
        setAudioInputDevice(0)
    }

    // =========================================================================
    // Events (Observable streams)
    // =========================================================================

    /**
     * Flow of all Jami events.
     */
    val events: SharedFlow<JamiEvent>

    /**
     * Flow of account-related events.
     */
    val accountEvents: SharedFlow<JamiAccountEvent>

    /**
     * Flow of call-related events.
     */
    val callEvents: SharedFlow<JamiCallEvent>

    /**
     * Flow of conversation/message events.
     */
    val conversationEvents: SharedFlow<JamiConversationEvent>

    /**
     * Flow of contact/presence events.
     */
    val contactEvents: SharedFlow<JamiContactEvent>
}

// =============================================================================
// Data Classes
// =============================================================================

data class JamiContact(
    val uri: String,
    val displayName: String,
    val avatarPath: String?,
    val isConfirmed: Boolean,
    val isBanned: Boolean
)

data class TrustRequest(
    val from: String,
    val conversationId: String,
    val payload: ByteArray,
    val received: Long
)

data class ConversationMember(
    val uri: String,
    val role: MemberRole
)

enum class MemberRole {
    ADMIN, MEMBER, INVITED, BANNED
}

data class ConversationRequest(
    val conversationId: String,
    val from: String,
    val metadata: Map<String, String>,
    val received: Long
)

data class LookupResult(
    val address: String,
    val name: String,
    val state: LookupState
)

enum class LookupState {
    SUCCESS, NOT_FOUND, INVALID, ERROR
}

data class FileTransferInfo(
    val fileId: String,
    val path: String,
    val displayName: String,
    val totalSize: Long,
    val progress: Long,
    val bytesPerSecond: Long,
    val author: String,
    val flags: Int
)

enum class ConferenceLayout {
    GRID,        // All participants in a grid
    ONE_BIG,     // One big + others small
    ONE_BIG_SMALL // One big with smaller tiles
}

// =============================================================================
// Events
// =============================================================================

sealed class JamiEvent {
    abstract val timestamp: Long
}

sealed class JamiAccountEvent : JamiEvent() {
    data class RegistrationStateChanged(
        val accountId: String,
        val state: RegistrationState,
        val code: Int,
        val detail: String,
        override val timestamp: Long = Clock.System.now().toEpochMilliseconds()
    ) : JamiAccountEvent()

    data class AccountDetailsChanged(
        val accountId: String,
        val details: Map<String, String>,
        override val timestamp: Long = Clock.System.now().toEpochMilliseconds()
    ) : JamiAccountEvent()

    data class ProfileReceived(
        val accountId: String,
        val from: String,
        val displayName: String,
        val avatarPath: String?,
        override val timestamp: Long = Clock.System.now().toEpochMilliseconds()
    ) : JamiAccountEvent()

    data class NameRegistrationEnded(
        val accountId: String,
        val state: Int,
        val name: String,
        override val timestamp: Long = Clock.System.now().toEpochMilliseconds()
    ) : JamiAccountEvent()

    data class RegisteredNameFound(
        val accountId: String,
        val state: LookupState,
        val address: String,
        val name: String,
        override val timestamp: Long = Clock.System.now().toEpochMilliseconds()
    ) : JamiAccountEvent()

    data class KnownDevicesChanged(
        val accountId: String,
        val devices: Map<String, String>,
        override val timestamp: Long = Clock.System.now().toEpochMilliseconds()
    ) : JamiAccountEvent()
}

enum class RegistrationState {
    UNREGISTERED,
    TRYING,
    REGISTERED,
    ERROR_GENERIC,
    ERROR_AUTH,
    ERROR_NETWORK,
    ERROR_HOST,
    ERROR_SERVICE_UNAVAILABLE,
    ERROR_NEED_MIGRATION,
    INITIALIZING
}

sealed class JamiCallEvent : JamiEvent() {
    data class IncomingCall(
        val accountId: String,
        val callId: String,
        val peerId: String,
        val peerDisplayName: String,
        val hasVideo: Boolean,
        override val timestamp: Long = Clock.System.now().toEpochMilliseconds()
    ) : JamiCallEvent()

    data class CallStateChanged(
        val accountId: String,
        val callId: String,
        val state: CallState,
        val code: Int,
        override val timestamp: Long = Clock.System.now().toEpochMilliseconds()
    ) : JamiCallEvent()

    data class MediaChangeRequested(
        val accountId: String,
        val callId: String,
        val mediaList: List<Map<String, String>>,
        override val timestamp: Long = Clock.System.now().toEpochMilliseconds()
    ) : JamiCallEvent()

    data class AudioMuted(
        val callId: String,
        val muted: Boolean,
        override val timestamp: Long = Clock.System.now().toEpochMilliseconds()
    ) : JamiCallEvent()

    data class VideoMuted(
        val callId: String,
        val muted: Boolean,
        override val timestamp: Long = Clock.System.now().toEpochMilliseconds()
    ) : JamiCallEvent()

    data class ConferenceCreated(
        val accountId: String,
        val conversationId: String,
        val conferenceId: String,
        override val timestamp: Long = Clock.System.now().toEpochMilliseconds()
    ) : JamiCallEvent()

    data class ConferenceChanged(
        val accountId: String,
        val conferenceId: String,
        val state: String,
        override val timestamp: Long = Clock.System.now().toEpochMilliseconds()
    ) : JamiCallEvent()

    data class ConferenceRemoved(
        val accountId: String,
        val conferenceId: String,
        override val timestamp: Long = Clock.System.now().toEpochMilliseconds()
    ) : JamiCallEvent()

    data class ConferenceInfoUpdated(
        val conferenceId: String,
        val participantInfos: List<Map<String, String>>,
        override val timestamp: Long = Clock.System.now().toEpochMilliseconds()
    ) : JamiCallEvent()
}

enum class CallState {
    INACTIVE,
    INCOMING,
    CONNECTING,
    RINGING,
    CURRENT,
    HUNGUP,
    BUSY,
    FAILURE,
    HOLD,
    UNHOLD,
    OVER
}

sealed class JamiConversationEvent : JamiEvent() {
    data class ConversationReady(
        val accountId: String,
        val conversationId: String,
        override val timestamp: Long = Clock.System.now().toEpochMilliseconds()
    ) : JamiConversationEvent()

    data class ConversationRemoved(
        val accountId: String,
        val conversationId: String,
        override val timestamp: Long = Clock.System.now().toEpochMilliseconds()
    ) : JamiConversationEvent()

    data class ConversationRequestReceived(
        val accountId: String,
        val conversationId: String,
        val metadata: Map<String, String>,
        override val timestamp: Long = Clock.System.now().toEpochMilliseconds()
    ) : JamiConversationEvent()

    data class MessageReceived(
        val accountId: String,
        val conversationId: String,
        val message: SwarmMessage,
        override val timestamp: Long = Clock.System.now().toEpochMilliseconds()
    ) : JamiConversationEvent()

    data class MessageUpdated(
        val accountId: String,
        val conversationId: String,
        val message: SwarmMessage,
        override val timestamp: Long = Clock.System.now().toEpochMilliseconds()
    ) : JamiConversationEvent()

    data class MessagesLoaded(
        val requestId: Int,
        val accountId: String,
        val conversationId: String,
        val messages: List<SwarmMessage>,
        override val timestamp: Long = Clock.System.now().toEpochMilliseconds()
    ) : JamiConversationEvent()

    data class ConversationMemberEvent(
        val accountId: String,
        val conversationId: String,
        val memberUri: String,
        val event: MemberEventType,
        override val timestamp: Long = Clock.System.now().toEpochMilliseconds()
    ) : JamiConversationEvent()

    data class ComposingStatusChanged(
        val accountId: String,
        val conversationId: String,
        val from: String,
        val isComposing: Boolean,
        override val timestamp: Long = Clock.System.now().toEpochMilliseconds()
    ) : JamiConversationEvent()

    data class ConversationProfileUpdated(
        val accountId: String,
        val conversationId: String,
        val profile: Map<String, String>,
        override val timestamp: Long = Clock.System.now().toEpochMilliseconds()
    ) : JamiConversationEvent()

    data class ReactionAdded(
        val accountId: String,
        val conversationId: String,
        val messageId: String,
        val reaction: Map<String, String>,
        override val timestamp: Long = Clock.System.now().toEpochMilliseconds()
    ) : JamiConversationEvent()

    data class ReactionRemoved(
        val accountId: String,
        val conversationId: String,
        val messageId: String,
        val reactionId: String,
        override val timestamp: Long = Clock.System.now().toEpochMilliseconds()
    ) : JamiConversationEvent()
}

data class SwarmMessage(
    val id: String,
    val type: String,
    val author: String,
    val body: Map<String, String>,
    val reactions: List<Map<String, String>>,
    val timestamp: Long,
    val replyTo: String?,
    val status: Map<String, Int>
)

enum class MemberEventType {
    JOIN, LEAVE, BAN, UNBAN
}

sealed class JamiContactEvent : JamiEvent() {
    data class ContactAdded(
        val accountId: String,
        val uri: String,
        val confirmed: Boolean,
        override val timestamp: Long = Clock.System.now().toEpochMilliseconds()
    ) : JamiContactEvent()

    data class ContactRemoved(
        val accountId: String,
        val uri: String,
        val banned: Boolean,
        override val timestamp: Long = Clock.System.now().toEpochMilliseconds()
    ) : JamiContactEvent()

    data class IncomingTrustRequest(
        val accountId: String,
        val conversationId: String,
        val from: String,
        val payload: ByteArray,
        val received: Long,
        override val timestamp: Long = Clock.System.now().toEpochMilliseconds()
    ) : JamiContactEvent() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as IncomingTrustRequest

            if (received != other.received) return false
            if (timestamp != other.timestamp) return false
            if (accountId != other.accountId) return false
            if (conversationId != other.conversationId) return false
            if (from != other.from) return false
            if (!payload.contentEquals(other.payload)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = received.hashCode()
            result = 31 * result + timestamp.hashCode()
            result = 31 * result + accountId.hashCode()
            result = 31 * result + conversationId.hashCode()
            result = 31 * result + from.hashCode()
            result = 31 * result + payload.contentHashCode()
            return result
        }
    }

    data class PresenceChanged(
        val accountId: String,
        val uri: String,
        val isOnline: Boolean,
        override val timestamp: Long = Clock.System.now().toEpochMilliseconds()
    ) : JamiContactEvent()

    /**
     * Emitted when a contact's profile (vCard) is received or updated.
     * This happens when:
     * - Initial contact profile is fetched after adding contact
     * - Contact updates their display name or avatar
     */
    data class ContactProfileReceived(
        val accountId: String,
        val contactUri: String,
        val displayName: String?,
        val avatarBase64: String?,
        override val timestamp: Long = Clock.System.now().toEpochMilliseconds()
    ) : JamiContactEvent()
}

// =============================================================================
// Factory Function
// =============================================================================

/**
 * Create a platform-specific JamiBridge implementation.
 */
expect fun createJamiBridge(): JamiBridge
