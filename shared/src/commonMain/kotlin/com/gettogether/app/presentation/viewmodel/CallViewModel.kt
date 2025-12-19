package com.gettogether.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gettogether.app.data.repository.AccountRepository
import com.gettogether.app.jami.CallState as JamiCallState
import com.gettogether.app.jami.JamiBridge
import com.gettogether.app.jami.JamiCallEvent
import com.gettogether.app.platform.CallServiceBridge
import com.gettogether.app.presentation.state.CallState
import com.gettogether.app.presentation.state.CallStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class CallViewModel(
    private val jamiBridge: JamiBridge,
    private val accountRepository: AccountRepository,
    private val callServiceBridge: CallServiceBridge? = null
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
        _state.update {
            it.copy(
                contactId = contactId,
                isVideo = withVideo,
                callStatus = CallStatus.Initiating,
                isLocalVideoEnabled = withVideo
            )
        }

        viewModelScope.launch {
            // Load contact info
            loadContactInfo(contactId)

            // Start call via service bridge (for foreground service)
            callServiceBridge?.startOutgoingCall(
                contactId = contactId,
                contactName = _state.value.contactName,
                isVideo = withVideo
            )

            try {
                val accountId = accountRepository.currentAccountId.value
                if (accountId != null) {
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

    fun acceptCall() {
        viewModelScope.launch {
            _state.update { it.copy(callStatus = CallStatus.Connecting) }

            try {
                val accountId = accountRepository.currentAccountId.value
                val callId = _state.value.callId
                if (accountId != null && callId.isNotEmpty()) {
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
                if (accountId != null && callId.isNotEmpty()) {
                    jamiBridge.hangUp(accountId, callId)
                }

                _state.update { it.copy(callStatus = CallStatus.Ended) }
            } catch (e: Exception) {
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
                if (event.callId == currentCallId || currentCallId.isEmpty()) {
                    when (event.state) {
                        JamiCallState.CURRENT -> onCallConnected()
                        JamiCallState.RINGING -> _state.update { it.copy(callStatus = CallStatus.Ringing) }
                        JamiCallState.CONNECTING -> _state.update { it.copy(callStatus = CallStatus.Connecting) }
                        JamiCallState.OVER, JamiCallState.HUNGUP -> {
                            durationJob?.cancel()
                            _state.update { it.copy(callStatus = CallStatus.Ended) }
                        }
                        JamiCallState.BUSY -> {
                            _state.update { it.copy(callStatus = CallStatus.Failed, error = "User is busy") }
                        }
                        else -> { /* Handle other states */ }
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

    private suspend fun loadContactInfo(contactId: String) {
        try {
            val accountId = accountRepository.currentAccountId.value
            if (accountId != null) {
                val contactDetails = jamiBridge.getContactDetails(accountId, contactId)
                val contactName = contactDetails["displayName"]
                    ?: contactDetails["username"]
                    ?: contactId.take(8)
                _state.update { it.copy(contactName = contactName) }
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
            // Fallback to simple name
            _state.update { it.copy(contactName = contactId.take(8)) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        durationJob?.cancel()
    }
}
