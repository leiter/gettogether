package com.gettogether.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gettogether.app.jami.JamiBridge
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
    private val callServiceBridge: CallServiceBridge? = null
) : ViewModel() {

    private val _state = MutableStateFlow(CallState())
    val state: StateFlow<CallState> = _state.asStateFlow()

    private var durationJob: Job? = null

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

            // Start call via service bridge (for foreground service) or simulate
            callServiceBridge?.startOutgoingCall(
                contactId = contactId,
                contactName = _state.value.contactName,
                isVideo = withVideo
            )

            // Simulate initiating call
            delay(500)
            _state.update { it.copy(callStatus = CallStatus.Ringing) }

            // TODO: Actually start call via JamiBridge
            // jamiBridge.startCall(contactId, withVideo)

            // Simulate call being answered (for demo)
            delay(2000)
            onCallConnected()
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
                // TODO: Actually accept call via JamiBridge
                // jamiBridge.acceptCall(state.value.callId)

                delay(500)
                onCallConnected()
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
                // TODO: Actually reject call via JamiBridge
                // jamiBridge.rejectCall(state.value.callId)

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

                // TODO: Actually hang up via JamiBridge
                // jamiBridge.hangUp(state.value.callId)

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
            // TODO: Actually toggle mute via JamiBridge
            // jamiBridge.toggleMute(state.value.callId)
        }
    }

    fun toggleSpeaker() {
        val newSpeakerState = !_state.value.isSpeakerOn
        _state.update { it.copy(isSpeakerOn = newSpeakerState) }

        viewModelScope.launch {
            // TODO: Actually toggle speaker via JamiBridge
            // jamiBridge.toggleSpeaker(state.value.callId)
        }
    }

    fun toggleVideo() {
        if (!_state.value.canToggleVideo) return

        val newVideoState = !_state.value.isLocalVideoEnabled
        _state.update { it.copy(isLocalVideoEnabled = newVideoState) }

        viewModelScope.launch {
            // TODO: Actually toggle video via JamiBridge
            // jamiBridge.toggleVideo(state.value.callId)
        }
    }

    fun switchCamera() {
        val newCameraState = !_state.value.isFrontCamera
        _state.update { it.copy(isFrontCamera = newCameraState) }

        viewModelScope.launch {
            // TODO: Actually switch camera via JamiBridge
            // jamiBridge.switchCamera(state.value.callId)
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
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
        // TODO: Load actual contact info from repository
        // For now, use demo data
        val contactName = when (contactId) {
            "1" -> "Alice"
            "2" -> "Bob"
            "3" -> "Charlie"
            else -> "Contact"
        }

        _state.update { it.copy(contactName = contactName) }
    }

    override fun onCleared() {
        super.onCleared()
        durationJob?.cancel()
    }
}
