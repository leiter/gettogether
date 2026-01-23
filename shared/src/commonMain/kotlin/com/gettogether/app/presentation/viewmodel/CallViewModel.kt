package com.gettogether.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gettogether.app.data.repository.AccountRepository
import com.gettogether.app.domain.repository.ContactRepository
import com.gettogether.app.jami.CallState as JamiCallState
import com.gettogether.app.jami.JamiBridge
import com.gettogether.app.jami.JamiCallEvent
import com.gettogether.app.platform.CallServiceBridge
import com.gettogether.app.platform.PermissionManager
import com.gettogether.app.presentation.state.CallState
import com.gettogether.app.presentation.state.CallStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class CallViewModel(
    private val jamiBridge: JamiBridge,
    private val accountRepository: AccountRepository,
    private val contactRepository: ContactRepository,
    private val callServiceBridge: CallServiceBridge? = null,
    private val permissionManager: PermissionManager
) : ViewModel() {

    private val _state = MutableStateFlow(CallState())
    val state: StateFlow<CallState> = _state.asStateFlow()

    private var durationJob: Job? = null

    init {
        // Listen to call events
        viewModelScope.launch {
            jamiBridge.callEvents.collect { event ->
                handleCallEvent(event)
            }
        }
    }

    fun initializeOutgoingCall(contactId: String, withVideo: Boolean) {
        // Check permissions before initiating call
        if (!permissionManager.hasRequiredPermissions()) {
            _state.update {
                it.copy(
                    contactId = contactId,
                    isVideo = withVideo,
                    callStatus = CallStatus.Failed,
                    error = "Required permissions not granted. Please grant microphone and camera permissions in settings."
                )
            }
            return
        }

        _state.update {
            it.copy(
                contactId = contactId,
                isVideo = withVideo,
                callStatus = CallStatus.Initiating,
                isLocalVideoEnabled = withVideo
            )
        }

        viewModelScope.launch {
            // Load contact info first
            loadContactInfo(contactId)

            // Start call via service bridge (for foreground service)
            // Use loaded name or fallback to truncated ID
            val displayName = _state.value.contactName.ifEmpty { contactId.substringBefore("@").take(8) }
            callServiceBridge?.startOutgoingCall(
                contactId = contactId,
                contactName = displayName,
                isVideo = withVideo
            )

            try {
                val accountId = accountRepository.currentAccountId.value
                if (accountId != null) {
                    // Initialize audio system before call
                    initializeAudioSystem()

                    // Start call via JamiBridge
                    val callId = jamiBridge.placeCall(accountId, contactId, withVideo)
                    _state.update { it.copy(callId = callId, callStatus = CallStatus.Ringing) }
                } else {
                    // Demo mode - simulate call
                    delay(500)
                    _state.update { it.copy(callStatus = CallStatus.Ringing) }
                    delay(2000)
                    onCallConnected()
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        callStatus = CallStatus.Failed,
                        error = e.message ?: "Failed to place call"
                    )
                }
            }
        }
    }

    fun initializeIncomingCall(callId: String, contactId: String, withVideo: Boolean) {
        _state.update {
            it.copy(
                callId = callId,
                contactId = contactId,
                isVideo = withVideo,
                callStatus = CallStatus.Incoming,
                isLocalVideoEnabled = withVideo
            )
        }

        viewModelScope.launch {
            loadContactInfo(contactId)
        }
    }

    /**
     * Initialize for a call that is already in progress (e.g., returning from notification).
     * Works for both outgoing and incoming calls that are already active.
     * Does NOT restart services - just syncs the UI with the current call state.
     */
    fun initializeAcceptedCall(callId: String, contactId: String, withVideo: Boolean) {
        println("CallViewModel: initializeAcceptedCall - callId=$callId, contactId=$contactId, withVideo=$withVideo")

        // Strip @ring.dht suffix for cleaner ID
        val cleanContactId = contactId.substringBefore("@")

        _state.update {
            it.copy(
                callId = callId,
                contactId = cleanContactId,
                isVideo = withVideo,
                callStatus = CallStatus.Connecting, // Start as connecting, will check actual state
                isLocalVideoEnabled = withVideo
            )
        }

        viewModelScope.launch {
            loadContactInfo(contactId)

            // Query the current call state from daemon
            // Do NOT restart CallService - it should already be running for this call
            try {
                val accountId = accountRepository.currentAccountId.value
                if (accountId != null) {
                    val callDetails = jamiBridge.getCallDetails(accountId, callId)
                    val currentState = callDetails["CALL_STATE"] ?: ""
                    println("CallViewModel: Current call state from daemon: '$currentState'")

                    when (currentState) {
                        "CURRENT" -> {
                            println("CallViewModel: Call is CURRENT, transitioning to Connected")
                            onCallConnected()
                        }
                        "RINGING" -> {
                            println("CallViewModel: Call is RINGING")
                            _state.update { it.copy(callStatus = CallStatus.Ringing) }
                        }
                        "CONNECTING" -> {
                            println("CallViewModel: Call is CONNECTING")
                            _state.update { it.copy(callStatus = CallStatus.Connecting) }
                        }
                        "", "OVER", "HUNGUP" -> {
                            println("CallViewModel: Call has ended or not found: '$currentState'")
                            _state.update { it.copy(callStatus = CallStatus.Ended) }
                        }
                        else -> {
                            println("CallViewModel: Unknown call state: '$currentState'")
                        }
                    }
                }
            } catch (e: Exception) {
                println("CallViewModel: Warning - Failed to query call state: ${e.message}")
            }
        }
    }

    fun acceptCall() {
        // Check permissions before accepting call
        if (!permissionManager.hasRequiredPermissions()) {
            _state.update {
                it.copy(
                    callStatus = CallStatus.Failed,
                    error = "Required permissions not granted. Please grant microphone and camera permissions in settings."
                )
            }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(callStatus = CallStatus.Connecting) }

            try {
                val accountId = accountRepository.currentAccountId.value
                val callId = _state.value.callId
                if (accountId != null && callId.isNotEmpty()) {
                    // Initialize audio system before accepting call
                    initializeAudioSystem()

                    jamiBridge.acceptCall(accountId, callId, _state.value.isVideo)
                }
                // Call state will be updated via call events
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        callStatus = CallStatus.Failed,
                        error = e.message ?: "Failed to accept call"
                    )
                }
            }
        }
    }

    fun rejectCall() {
        viewModelScope.launch {
            try {
                val accountId = accountRepository.currentAccountId.value
                val callId = _state.value.callId
                if (accountId != null && callId.isNotEmpty()) {
                    jamiBridge.refuseCall(accountId, callId)
                }
                _state.update { it.copy(callStatus = CallStatus.Ended) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        callStatus = CallStatus.Failed,
                        error = e.message ?: "Failed to reject call"
                    )
                }
            }
        }
    }

    fun endCall() {
        durationJob?.cancel()
        viewModelScope.launch {
            try {
                // End call via service bridge
                callServiceBridge?.endCall()

                val accountId = accountRepository.currentAccountId.value
                val callId = _state.value.callId
                println("CallViewModel: endCall - accountId=$accountId, callId=$callId")

                if (accountId != null && callId.isNotEmpty()) {
                    println("CallViewModel: Calling jamiBridge.hangUp()")
                    jamiBridge.hangUp(accountId, callId)
                    println("CallViewModel: hangUp() completed")
                } else {
                    println("CallViewModel: Cannot hangUp - accountId=$accountId, callId=$callId")
                }

                _state.update { it.copy(callStatus = CallStatus.Ended) }
            } catch (e: Exception) {
                println("CallViewModel: endCall error: ${e.message}")
                _state.update {
                    it.copy(
                        callStatus = CallStatus.Failed,
                        error = e.message ?: "Failed to end call"
                    )
                }
            }
        }
    }

    fun toggleMute() {
        val newMuteState = !_state.value.isMuted
        _state.update { it.copy(isMuted = newMuteState) }

        viewModelScope.launch {
            val accountId = accountRepository.currentAccountId.value
            val callId = _state.value.callId
            if (accountId != null && callId.isNotEmpty()) {
                jamiBridge.muteAudio(accountId, callId, newMuteState)
            }
        }
    }

    fun toggleSpeaker() {
        val newSpeakerState = !_state.value.isSpeakerOn
        _state.update { it.copy(isSpeakerOn = newSpeakerState) }

        viewModelScope.launch {
            jamiBridge.switchAudioOutput(newSpeakerState)
        }
    }

    fun toggleVideo() {
        if (!_state.value.canToggleVideo) return

        val newVideoState = !_state.value.isLocalVideoEnabled
        _state.update { it.copy(isLocalVideoEnabled = newVideoState) }

        viewModelScope.launch {
            val accountId = accountRepository.currentAccountId.value
            val callId = _state.value.callId
            if (accountId != null && callId.isNotEmpty()) {
                jamiBridge.muteVideo(accountId, callId, !newVideoState)
            }
        }
    }

    fun switchCamera() {
        val newCameraState = !_state.value.isFrontCamera
        _state.update { it.copy(isFrontCamera = newCameraState) }

        viewModelScope.launch {
            jamiBridge.switchCamera()
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    private fun handleCallEvent(event: JamiCallEvent) {
        val currentCallId = _state.value.callId
        when (event) {
            is JamiCallEvent.CallStateChanged -> {
                println("CallViewModel: CallStateChanged - eventCallId=${event.callId}, currentCallId=$currentCallId, state=${event.state}")
                if (event.callId == currentCallId || currentCallId.isEmpty()) {
                    println("CallViewModel: Processing state change to ${event.state}")
                    when (event.state) {
                        JamiCallState.CURRENT -> onCallConnected()
                        JamiCallState.RINGING -> _state.update { it.copy(callStatus = CallStatus.Ringing) }
                        JamiCallState.CONNECTING -> _state.update { it.copy(callStatus = CallStatus.Connecting) }
                        JamiCallState.OVER, JamiCallState.HUNGUP -> {
                            println("CallViewModel: Call ended (${event.state}), setting status to Ended")
                            durationJob?.cancel()
                            // Stop the foreground service when call ends (including remote hangup)
                            callServiceBridge?.endCall()
                            _state.update { it.copy(callStatus = CallStatus.Ended) }
                            println("CallViewModel: State updated to Ended, current status=${_state.value.callStatus}")
                        }
                        JamiCallState.BUSY -> {
                            _state.update { it.copy(callStatus = CallStatus.Failed, error = "User is busy") }
                        }
                        else -> { /* Handle other states */ }
                    }
                } else {
                    println("CallViewModel: Ignoring event for different call (event=${event.callId}, current=$currentCallId)")
                }
            }
            is JamiCallEvent.MediaChangeRequested -> {
                // Auto-accept media changes from the peer
                if (event.callId == currentCallId) {
                    viewModelScope.launch {
                        val accountId = accountRepository.currentAccountId.value
                        if (accountId != null) {
                            jamiBridge.answerMediaChangeRequest(accountId, event.callId, event.mediaList)
                        }
                    }
                }
            }
            else -> { /* Handle other events */ }
        }
    }

    private fun onCallConnected() {
        _state.update { it.copy(callStatus = CallStatus.Connected) }
        startDurationTimer()
    }

    private fun startDurationTimer() {
        durationJob?.cancel()
        durationJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                _state.update { it.copy(callDuration = it.callDuration + 1) }
            }
        }
    }

    /**
     * Initialize audio system before starting or accepting a call.
     *
     * Note: We avoid calling setAudioInputDevice() as it can cause SIGSEGV crashes
     * in the native daemon on some devices. The daemon handles audio device
     * selection automatically during call setup.
     */
    private suspend fun initializeAudioSystem() {
        try {
            // Only set audio output to earpiece (not speaker) for calls
            // Do NOT call setAudioInputDevice - causes native crashes on some devices
            jamiBridge.setAudioOutputDevice(0)
        } catch (e: Exception) {
            // Log error but don't fail the call
            println("Warning: Audio initialization error: ${e.message}")
        }
    }

    private suspend fun loadContactInfo(contactId: String) {
        try {
            val accountId = accountRepository.currentAccountId.value
            if (accountId != null) {
                // Strip @ring.dht suffix if present
                val cleanContactId = contactId.substringBefore("@")
                println("CallViewModel: loadContactInfo - original=$contactId, clean=$cleanContactId")

                // Try to get contact from ContactRepository first (has cached display name and avatar)
                val contact = contactRepository.getContactById(accountId, cleanContactId).firstOrNull()

                if (contact != null) {
                    val contactName = contact.getEffectiveName().ifBlank { cleanContactId.take(8) }
                    val avatarUri = contact.avatarUri
                    println("CallViewModel: Found contact - name=$contactName, avatar=$avatarUri")
                    _state.update {
                        it.copy(
                            contactName = contactName,
                            contactAvatar = avatarUri
                        )
                    }
                } else {
                    // Fallback to jamiBridge if contact not in repository
                    println("CallViewModel: Contact not in repository, using jamiBridge")
                    val contactDetails = jamiBridge.getContactDetails(accountId, cleanContactId)
                    val contactName = contactDetails["displayName"]?.takeIf { it.isNotBlank() }
                        ?: contactDetails["username"]?.takeIf { it.isNotBlank() }
                        ?: cleanContactId.take(8)
                    println("CallViewModel: resolved contactName=$contactName")
                    _state.update { it.copy(contactName = contactName) }
                }
            } else {
                // Demo data fallback
                val contactName = when (contactId) {
                    "1" -> "Alice"
                    "2" -> "Bob"
                    "3" -> "Charlie"
                    else -> "Contact"
                }
                _state.update { it.copy(contactName = contactName) }
            }
        } catch (e: Exception) {
            println("CallViewModel: loadContactInfo error: ${e.message}")
            // Fallback to simple name
            _state.update { it.copy(contactName = contactId.substringBefore("@").take(8)) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        durationJob?.cancel()
    }
}
