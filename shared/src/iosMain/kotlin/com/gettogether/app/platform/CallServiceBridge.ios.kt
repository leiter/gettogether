package com.gettogether.app.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryOptionAllowBluetooth
import platform.AVFAudio.AVAudioSessionCategoryOptionDefaultToSpeaker
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.AVAudioSessionModeVoiceChat
import platform.AVFAudio.AVAudioSessionPortOverride
import platform.AVFAudio.AVAudioSessionPortOverrideNone
import platform.AVFAudio.AVAudioSessionPortOverrideSpeaker
import platform.CallKit.CXAnswerCallAction
import platform.CallKit.CXCallController
import platform.CallKit.CXCallUpdate
import platform.CallKit.CXEndCallAction
import platform.CallKit.CXHandle
import platform.CallKit.CXHandleTypeGeneric
import platform.CallKit.CXProvider
import platform.CallKit.CXProviderConfiguration
import platform.CallKit.CXSetMutedCallAction
import platform.CallKit.CXStartCallAction
import platform.CallKit.CXTransaction
import platform.Foundation.NSDate
import platform.Foundation.NSLog
import platform.Foundation.NSUUID

@OptIn(ExperimentalForeignApi::class)
actual class CallServiceBridge {

    companion object {
        private const val TAG = "CallServiceBridge"
    }

    private val provider: CXProvider
    private val callController = CXCallController()

    // Track active calls: callId -> UUID
    private val activeCalls = mutableMapOf<String, NSUUID>()
    private var currentCallId: String? = null

    // Audio state
    private var isMuted = false
    private var isSpeakerOn = false
    private var isVideoEnabled = false

    init {
        val configuration = CXProviderConfiguration()
        configuration.supportsVideo = true
        configuration.maximumCallsPerCallGroup = 1u
        configuration.maximumCallGroups = 1u
        configuration.includesCallsInRecents = true

        provider = CXProvider(configuration)
        configureAudioSession()

        NSLog("$TAG: CallServiceBridge initialized with CallKit")
    }

    private fun configureAudioSession() {
        try {
            val audioSession = AVAudioSession.sharedInstance()
            audioSession.setCategory(
                AVAudioSessionCategoryPlayAndRecord,
                mode = AVAudioSessionModeVoiceChat,
                options = AVAudioSessionCategoryOptionAllowBluetooth or
                         AVAudioSessionCategoryOptionDefaultToSpeaker,
                error = null
            )
            NSLog("$TAG: Audio session configured")
        } catch (e: Exception) {
            NSLog("$TAG: Failed to configure audio session: ${e.message}")
        }
    }

    actual fun startOutgoingCall(contactId: String, contactName: String, isVideo: Boolean) {
        val uuid = NSUUID()
        val callId = uuid.UUIDString
        activeCalls[callId] = uuid
        currentCallId = callId
        isVideoEnabled = isVideo

        val handle = CXHandle(CXHandleTypeGeneric, contactId)
        val startCallAction = CXStartCallAction(uuid, handle)
        startCallAction.setVideo(isVideo)
        startCallAction.contactIdentifier = contactName

        val transaction = CXTransaction(startCallAction)

        NSLog("$TAG: Starting outgoing call to $contactName (id: $contactId, video: $isVideo)")

        callController.requestTransaction(transaction) { error ->
            if (error != null) {
                NSLog("$TAG: Failed to start outgoing call: ${error.localizedDescription}")
                activeCalls.remove(callId)
                if (currentCallId == callId) currentCallId = null
            } else {
                NSLog("$TAG: Outgoing call started successfully")
                // Update the call display
                val update = CXCallUpdate()
                update.remoteHandle = handle
                update.localizedCallerName = contactName
                update.setHasVideo(isVideo)
                update.supportsGrouping = false
                update.supportsUngrouping = false
                update.supportsHolding = true
                update.supportsDTMF = false
                provider.reportCallWithUUID(uuid, update)
            }
        }
    }

    actual fun startIncomingCall(callId: String, contactId: String, contactName: String, isVideo: Boolean) {
        val uuid = NSUUID()
        activeCalls[callId] = uuid
        currentCallId = callId
        isVideoEnabled = isVideo

        val update = CXCallUpdate()
        update.remoteHandle = CXHandle(CXHandleTypeGeneric, contactId)
        update.localizedCallerName = contactName
        update.setHasVideo(isVideo)
        update.supportsGrouping = false
        update.supportsUngrouping = false
        update.supportsHolding = true
        update.supportsDTMF = false

        NSLog("$TAG: Reporting incoming call from $contactName (callId: $callId)")

        provider.reportNewIncomingCallWithUUID(uuid, update) { error ->
            if (error != null) {
                NSLog("$TAG: Failed to report incoming call: ${error.localizedDescription}")
                activeCalls.remove(callId)
                if (currentCallId == callId) currentCallId = null
            } else {
                NSLog("$TAG: Incoming call reported successfully")
                activateAudioSession()
            }
        }
    }

    private fun activateAudioSession() {
        // Audio session is automatically managed by CallKit via CXProviderDelegate
        // The provider will call didActivate/didDeactivate as needed
        NSLog("$TAG: Audio session activation requested (managed by CallKit)")
    }

    private fun deactivateAudioSession() {
        // Audio session is automatically managed by CallKit via CXProviderDelegate
        NSLog("$TAG: Audio session deactivation requested (managed by CallKit)")
    }

    actual fun answerCall() {
        val callId = currentCallId ?: run {
            NSLog("$TAG: No active call to answer")
            return
        }
        val uuid = activeCalls[callId] ?: run {
            NSLog("$TAG: No UUID found for callId: $callId")
            return
        }

        val answerAction = CXAnswerCallAction(uuid)
        val transaction = CXTransaction(answerAction)

        NSLog("$TAG: Answering call $callId")

        callController.requestTransaction(transaction) { error ->
            if (error != null) {
                NSLog("$TAG: Failed to answer call: ${error.localizedDescription}")
            } else {
                NSLog("$TAG: Call answered successfully")
                activateAudioSession()
            }
        }
    }

    actual fun declineCall() {
        endCallInternal("declining")
    }

    actual fun endCall() {
        endCallInternal("ending")
    }

    private fun endCallInternal(action: String) {
        val callId = currentCallId ?: run {
            NSLog("$TAG: No active call to end")
            return
        }
        val uuid = activeCalls[callId] ?: run {
            NSLog("$TAG: No UUID found for callId: $callId")
            return
        }

        val endAction = CXEndCallAction(uuid)
        val transaction = CXTransaction(endAction)

        NSLog("$TAG: $action call $callId")

        callController.requestTransaction(transaction) { error ->
            if (error != null) {
                NSLog("$TAG: Failed to end call: ${error.localizedDescription}")
            } else {
                NSLog("$TAG: Call ended successfully")
                activeCalls.remove(callId)
                currentCallId = null
                isMuted = false
                isSpeakerOn = false
                isVideoEnabled = false
                deactivateAudioSession()
            }
        }
    }

    actual fun toggleMute() {
        val callId = currentCallId ?: run {
            NSLog("$TAG: No active call to mute")
            return
        }
        val uuid = activeCalls[callId] ?: run {
            NSLog("$TAG: No UUID found for callId: $callId")
            return
        }

        isMuted = !isMuted
        val muteAction = CXSetMutedCallAction(uuid, isMuted)
        val transaction = CXTransaction(muteAction)

        NSLog("$TAG: Setting mute to $isMuted for call $callId")

        callController.requestTransaction(transaction) { error ->
            if (error != null) {
                NSLog("$TAG: Failed to set mute: ${error.localizedDescription}")
                isMuted = !isMuted // Revert on failure
            } else {
                NSLog("$TAG: Mute toggled successfully: $isMuted")
            }
        }
    }

    actual fun toggleSpeaker() {
        isSpeakerOn = !isSpeakerOn

        try {
            val audioSession = AVAudioSession.sharedInstance()
            val portOverride: AVAudioSessionPortOverride = if (isSpeakerOn) {
                AVAudioSessionPortOverrideSpeaker
            } else {
                AVAudioSessionPortOverrideNone
            }
            audioSession.overrideOutputAudioPort(portOverride, null)

            NSLog("$TAG: Speaker toggled: $isSpeakerOn")
        } catch (e: Exception) {
            NSLog("$TAG: Failed to toggle speaker: ${e.message}")
            isSpeakerOn = !isSpeakerOn // Revert on failure
        }
    }

    actual fun toggleVideo() {
        isVideoEnabled = !isVideoEnabled
        NSLog("$TAG: Video toggled: $isVideoEnabled")

        // Notify the provider about video state change
        currentCallId?.let { callId ->
            activeCalls[callId]?.let { uuid ->
                val update = CXCallUpdate()
                update.setHasVideo(isVideoEnabled)
                provider.reportCallWithUUID(uuid, update)
            }
        }
    }

    // Additional helper methods for call state reporting

    fun reportCallConnected() {
        currentCallId?.let { callId ->
            activeCalls[callId]?.let { uuid ->
                NSLog("$TAG: Reporting call connected: $callId")
                provider.reportOutgoingCallWithUUID(uuid, connectedAtDate = NSDate())
            }
        }
    }

    fun reportCallStartedConnecting() {
        currentCallId?.let { callId ->
            activeCalls[callId]?.let { uuid ->
                NSLog("$TAG: Reporting call started connecting: $callId")
                provider.reportOutgoingCallWithUUID(uuid, startedConnectingAtDate = NSDate())
            }
        }
    }

    fun hasActiveCall(): Boolean = currentCallId != null

    fun isMuted(): Boolean = isMuted

    fun isSpeakerOn(): Boolean = isSpeakerOn

    fun isVideoEnabled(): Boolean = isVideoEnabled
}
