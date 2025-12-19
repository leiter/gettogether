package com.gettogether.app.presentation.state

import com.gettogether.app.domain.model.CallState as DomainCallState

data class CallState(
    val callId: String = "",
    val contactId: String = "",
    val contactName: String = "",
    val contactAvatar: String? = null,
    val callStatus: CallStatus = CallStatus.Idle,
    val isVideo: Boolean = false,
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val isFrontCamera: Boolean = true,
    val isLocalVideoEnabled: Boolean = true,
    val isRemoteVideoEnabled: Boolean = false,
    val callDuration: Long = 0L, // Duration in seconds
    val error: String? = null
) {
    val formattedDuration: String
        get() {
            val minutes = callDuration / 60
            val seconds = callDuration % 60
            return "%02d:%02d".format(minutes, seconds)
        }

    val isCallActive: Boolean
        get() = callStatus == CallStatus.Connected

    val canToggleVideo: Boolean
        get() = callStatus == CallStatus.Connected

    val canToggleControls: Boolean
        get() = callStatus == CallStatus.Connected || callStatus == CallStatus.Connecting
}

enum class CallStatus {
    Idle,
    Initiating,      // Outgoing call being set up
    Ringing,         // Outgoing call ringing on remote end
    Incoming,        // Incoming call notification
    Connecting,      // Call being connected
    Connected,       // Call is active
    Reconnecting,    // Call temporarily disconnected
    Ended,           // Call has ended
    Failed           // Call failed
}

// Extension to map domain CallState to presentation CallStatus
fun DomainCallState.toCallStatus(): CallStatus = when (this) {
    DomainCallState.IDLE -> CallStatus.Idle
    DomainCallState.INCOMING -> CallStatus.Incoming
    DomainCallState.OUTGOING -> CallStatus.Initiating
    DomainCallState.CONNECTING -> CallStatus.Connecting
    DomainCallState.RINGING -> CallStatus.Ringing
    DomainCallState.CURRENT -> CallStatus.Connected
    DomainCallState.HOLD -> CallStatus.Connected
    DomainCallState.ENDED -> CallStatus.Ended
}
