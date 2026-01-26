@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.gettogether.app.jami

import platform.Foundation.NSLog
import platform.darwin.NSObject

/**
 * Protocol for native bridge operations.
 * Swift will implement this protocol and Kotlin will call through it.
 *
 * This interface is exported to ObjC as a protocol that Swift can conform to.
 */
interface NativeBridgeOperations {
    // Daemon lifecycle
    fun initDaemon(dataPath: String)
    fun startDaemon()
    fun stopDaemon()
    fun isDaemonRunning(): Boolean

    // Account management
    fun createAccount(displayName: String, password: String): String
    fun importAccount(archivePath: String, password: String): String
    fun exportAccount(accountId: String, destinationPath: String, password: String): Boolean
    fun deleteAccount(accountId: String)
    fun getAccountIds(): List<String>
    fun getAccountDetails(accountId: String): Map<String, String>
    fun getVolatileAccountDetails(accountId: String): Map<String, String>
    fun setAccountDetails(accountId: String, details: Map<String, String>)
    fun setAccountActive(accountId: String, active: Boolean)
    fun updateProfile(accountId: String, displayName: String, avatarPath: String?)
    fun registerName(accountId: String, name: String, password: String): Boolean
    fun lookupName(accountId: String, name: String)
    fun lookupAddress(accountId: String, address: String)

    // Contact management
    fun getContacts(accountId: String): List<Map<String, Any?>>
    fun addContact(accountId: String, uri: String)
    fun removeContact(accountId: String, uri: String, ban: Boolean)
    fun getContactDetails(accountId: String, uri: String): Map<String, String>
    fun acceptTrustRequest(accountId: String, uri: String)
    fun discardTrustRequest(accountId: String, uri: String)
    fun getTrustRequests(accountId: String): List<Map<String, Any?>>

    // Conversation management
    fun getConversations(accountId: String): List<String>
    fun startConversation(accountId: String): String
    fun removeConversation(accountId: String, conversationId: String)
    fun getConversationInfo(accountId: String, conversationId: String): Map<String, String>
    fun updateConversationInfo(accountId: String, conversationId: String, info: Map<String, String>)
    fun getConversationMembers(accountId: String, conversationId: String): List<Map<String, Any?>>
    fun addConversationMember(accountId: String, conversationId: String, contactUri: String)
    fun removeConversationMember(accountId: String, conversationId: String, contactUri: String)
    fun acceptConversationRequest(accountId: String, conversationId: String)
    fun declineConversationRequest(accountId: String, conversationId: String)
    fun getConversationRequests(accountId: String): List<Map<String, Any?>>

    // Messaging
    fun sendMessage(accountId: String, conversationId: String, message: String, replyTo: String?): String
    fun loadConversationMessages(accountId: String, conversationId: String, fromMessage: String, count: Int): Int
    fun setIsComposing(accountId: String, conversationId: String, isComposing: Boolean)
    fun setMessageDisplayed(accountId: String, conversationId: String, messageId: String)

    // Calls
    fun placeCall(accountId: String, uri: String, withVideo: Boolean): String
    fun acceptCall(accountId: String, callId: String, withVideo: Boolean)
    fun refuseCall(accountId: String, callId: String)
    fun hangUp(accountId: String, callId: String)
    fun holdCall(accountId: String, callId: String)
    fun unholdCall(accountId: String, callId: String)
    fun muteAudio(accountId: String, callId: String, muted: Boolean)
    fun muteVideo(accountId: String, callId: String, muted: Boolean)
    fun getCallDetails(accountId: String, callId: String): Map<String, String>
    fun getActiveCalls(accountId: String): List<String>
    fun switchCamera()
    fun switchAudioOutput(useSpeaker: Boolean)

    // Video/Audio
    fun getVideoDevices(): List<String>
    fun getCurrentVideoDevice(): String
    fun setVideoDevice(deviceId: String)
    fun startVideo()
    fun stopVideo()
    fun getAudioOutputDevices(): List<String>
    fun setAudioOutputDevice(index: Int)
}

/**
 * Callback interface for native bridge events.
 * Kotlin implements this (IOSJamiBridge), and Swift calls these methods when events occur.
 */
interface NativeBridgeCallback {
    // Account events
    fun onRegistrationStateChanged(accountId: String, state: Int, code: Int, detail: String)
    fun onAccountDetailsChanged(accountId: String, details: Map<String, String>)
    fun onNameRegistrationEnded(accountId: String, state: Int, name: String)
    fun onRegisteredNameFound(accountId: String, state: Int, address: String, name: String)
    fun onProfileReceived(accountId: String, from: String, displayName: String, avatarPath: String?)

    // Contact events
    fun onContactAdded(accountId: String, uri: String, confirmed: Boolean)
    fun onContactRemoved(accountId: String, uri: String, banned: Boolean)
    fun onIncomingTrustRequest(accountId: String, conversationId: String, from: String, received: Long)
    fun onPresenceChanged(accountId: String, uri: String, isOnline: Boolean)

    // Conversation events
    fun onConversationReady(accountId: String, conversationId: String)
    fun onConversationRemoved(accountId: String, conversationId: String)
    fun onConversationRequestReceived(accountId: String, conversationId: String, metadata: Map<String, String>)
    fun onMessageReceived(accountId: String, conversationId: String, messageData: Map<String, Any?>)
    fun onMessageUpdated(accountId: String, conversationId: String, messageData: Map<String, Any?>)
    fun onMessagesLoaded(requestId: Int, accountId: String, conversationId: String, messages: List<Map<String, Any?>>)
    fun onConversationMemberEvent(accountId: String, conversationId: String, memberUri: String, event: Int)
    fun onComposingStatusChanged(accountId: String, conversationId: String, from: String, isComposing: Boolean)
    fun onConversationProfileUpdated(accountId: String, conversationId: String, profile: Map<String, String>)

    // Call events
    fun onIncomingCall(accountId: String, callId: String, peerId: String, peerDisplayName: String, hasVideo: Boolean)
    fun onCallStateChanged(accountId: String, callId: String, state: Int, code: Int)
    fun onAudioMuted(callId: String, muted: Boolean)
    fun onVideoMuted(callId: String, muted: Boolean)
    fun onConferenceCreated(accountId: String, conversationId: String, conferenceId: String)
    fun onConferenceChanged(accountId: String, conferenceId: String, state: String)
    fun onConferenceRemoved(accountId: String, conferenceId: String)
}

/**
 * Global provider for the native bridge.
 * Swift sets the operations at app startup, and Kotlin reads from it.
 * Kotlin sets the callback when IOSJamiBridge is created.
 */
object NativeBridgeProvider {
    private var _operations: NativeBridgeOperations? = null
    private var _callback: NativeBridgeCallback? = null
    private var _initialized = false

    val operations: NativeBridgeOperations?
        get() = _operations

    val callback: NativeBridgeCallback?
        get() = _callback

    val isInitialized: Boolean
        get() = _initialized && _operations != null

    /**
     * Called by Swift at app startup to provide the native operations implementation.
     */
    fun setOperations(ops: NativeBridgeOperations) {
        NSLog("[NativeBridgeProvider] Setting native operations")
        _operations = ops
        _initialized = true
    }

    /**
     * Called by IOSJamiBridge on init to register itself as the callback receiver.
     */
    fun setCallback(cb: NativeBridgeCallback) {
        NSLog("[NativeBridgeProvider] Setting callback")
        _callback = cb
    }

    fun clear() {
        _operations = null
        _callback = null
        _initialized = false
    }
}
