package com.gettogether.app.presentation.state

data class ConferenceState(
    val conferenceId: String = "",
    val conferenceName: String = "",
    val status: ConferenceStatus = ConferenceStatus.Idle,
    val participants: List<ConferenceParticipant> = emptyList(),
    val localParticipant: ConferenceParticipant? = null,
    val isHost: Boolean = false,
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val isLocalVideoEnabled: Boolean = true,
    val isFrontCamera: Boolean = true,
    val layoutMode: ConferenceLayoutMode = ConferenceLayoutMode.Grid,
    val activeSpeakerId: String? = null,
    val duration: Long = 0L,
    val error: String? = null
) {
    val formattedDuration: String
        get() {
            val hours = duration / 3600
            val minutes = (duration % 3600) / 60
            val seconds = duration % 60
            return if (hours > 0) {
                "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
            } else {
                "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
            }
        }

    val participantCount: Int
        get() = participants.size + (if (localParticipant != null) 1 else 0)

    val remoteParticipants: List<ConferenceParticipant>
        get() = participants.filter { it.id != localParticipant?.id }

    val isConferenceActive: Boolean
        get() = status == ConferenceStatus.Connected

    val canManageParticipants: Boolean
        get() = isHost && isConferenceActive
}

data class ConferenceParticipant(
    val id: String,
    val name: String,
    val avatarUrl: String? = null,
    val isMuted: Boolean = false,
    val isVideoEnabled: Boolean = true,
    val isSpeaking: Boolean = false,
    val isHost: Boolean = false,
    val connectionState: ParticipantConnectionState = ParticipantConnectionState.Connected
) {
    val initials: String
        get() = name.split(" ")
            .take(2)
            .mapNotNull { it.firstOrNull()?.uppercase() }
            .joinToString("")
            .ifEmpty { "?" }
}

enum class ConferenceStatus {
    Idle,
    Creating,       // Creating a new conference
    Joining,        // Joining an existing conference
    Connecting,     // Establishing connection
    Connected,      // Conference is active
    Reconnecting,   // Temporarily disconnected
    Ended,          // Conference has ended
    Failed          // Conference failed
}

enum class ConferenceLayoutMode {
    Grid,           // Equal-sized tiles for all participants
    Speaker,        // Large view for active speaker, small tiles for others
    Filmstrip       // Main video with horizontal strip of participants
}

enum class ParticipantConnectionState {
    Connecting,
    Connected,
    Reconnecting,
    Disconnected
}
