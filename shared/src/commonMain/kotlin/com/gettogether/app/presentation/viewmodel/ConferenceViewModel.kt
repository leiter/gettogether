package com.gettogether.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gettogether.app.data.repository.AccountRepository
import com.gettogether.app.jami.JamiBridge
import com.gettogether.app.jami.JamiCallEvent
import com.gettogether.app.presentation.state.ConferenceLayoutMode
import com.gettogether.app.presentation.state.ConferenceParticipant
import com.gettogether.app.presentation.state.ConferenceState
import com.gettogether.app.presentation.state.ConferenceStatus
import com.gettogether.app.presentation.state.ParticipantConnectionState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlin.random.Random

class ConferenceViewModel(
    private val jamiBridge: JamiBridge,
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ConferenceState())
    val state: StateFlow<ConferenceState> = _state.asStateFlow()

    private var durationJob: Job? = null
    private var simulationJob: Job? = null

    init {
        // Listen to call events for conference updates
        viewModelScope.launch {
            jamiBridge.callEvents.collect { event ->
                handleCallEvent(event)
            }
        }
    }

    private fun handleCallEvent(event: JamiCallEvent) {
        when (event) {
            is JamiCallEvent.ConferenceCreated -> {
                _state.update { it.copy(conferenceId = event.conferenceId) }
            }
            is JamiCallEvent.ConferenceChanged -> {
                // Conference state changed - could update participants here
            }
            is JamiCallEvent.ConferenceRemoved -> {
                if (event.conferenceId == _state.value.conferenceId) {
                    durationJob?.cancel()
                    _state.update { it.copy(status = ConferenceStatus.Ended) }
                }
            }
            else -> { /* Handle other events */ }
        }
    }

    fun createConference(participantIds: List<String>, withVideo: Boolean) {
        val conferenceId = generateConferenceId()

        _state.update {
            it.copy(
                conferenceId = conferenceId,
                conferenceName = "Group Call",
                status = ConferenceStatus.Creating,
                isHost = true,
                isLocalVideoEnabled = withVideo,
                localParticipant = ConferenceParticipant(
                    id = "local",
                    name = "You",
                    isHost = true,
                    isVideoEnabled = withVideo
                )
            )
        }

        viewModelScope.launch {
            // Simulate conference creation
            delay(500)
            _state.update { it.copy(status = ConferenceStatus.Connecting) }

            // Simulate participants joining
            delay(1000)
            addSimulatedParticipants(participantIds, withVideo)

            _state.update { it.copy(status = ConferenceStatus.Connected) }
            startDurationTimer()
            startSpeakingSimulation()
        }
    }

    fun joinConference(conferenceId: String, withVideo: Boolean) {
        _state.update {
            it.copy(
                conferenceId = conferenceId,
                status = ConferenceStatus.Joining,
                isHost = false,
                isLocalVideoEnabled = withVideo,
                localParticipant = ConferenceParticipant(
                    id = "local",
                    name = "You",
                    isHost = false,
                    isVideoEnabled = withVideo
                )
            )
        }

        viewModelScope.launch {
            delay(500)
            _state.update { it.copy(status = ConferenceStatus.Connecting) }

            delay(1000)
            // Simulate existing participants
            val existingParticipants = listOf(
                ConferenceParticipant(
                    id = "host",
                    name = "Host",
                    isHost = true,
                    isVideoEnabled = true
                ),
                ConferenceParticipant(
                    id = "participant1",
                    name = "Alice",
                    isVideoEnabled = true
                )
            )
            _state.update {
                it.copy(
                    status = ConferenceStatus.Connected,
                    participants = existingParticipants,
                    conferenceName = "Group Call"
                )
            }
            startDurationTimer()
            startSpeakingSimulation()
        }
    }

    fun leaveConference() {
        durationJob?.cancel()
        simulationJob?.cancel()

        viewModelScope.launch {
            try {
                val accountId = accountRepository.currentAccountId.value
                val conferenceId = _state.value.conferenceId
                if (accountId != null && conferenceId.isNotEmpty()) {
                    jamiBridge.hangUpConference(accountId, conferenceId)
                }
            } catch (e: Exception) {
                // Ignore errors on leave
            }
            _state.update { it.copy(status = ConferenceStatus.Ended) }
        }
    }

    fun toggleMute() {
        val newMuteState = !_state.value.isMuted
        _state.update {
            it.copy(
                isMuted = newMuteState,
                localParticipant = it.localParticipant?.copy(isMuted = newMuteState)
            )
        }
    }

    fun toggleSpeaker() {
        _state.update { it.copy(isSpeakerOn = !it.isSpeakerOn) }
    }

    fun toggleVideo() {
        val newVideoState = !_state.value.isLocalVideoEnabled
        _state.update {
            it.copy(
                isLocalVideoEnabled = newVideoState,
                localParticipant = it.localParticipant?.copy(isVideoEnabled = newVideoState)
            )
        }
    }

    fun switchCamera() {
        _state.update { it.copy(isFrontCamera = !it.isFrontCamera) }
    }

    fun setLayoutMode(mode: ConferenceLayoutMode) {
        _state.update { it.copy(layoutMode = mode) }
    }

    fun muteParticipant(participantId: String) {
        if (!_state.value.isHost) return

        _state.update { state ->
            state.copy(
                participants = state.participants.map { participant ->
                    if (participant.id == participantId) {
                        participant.copy(isMuted = true)
                    } else {
                        participant
                    }
                }
            )
        }
    }

    fun removeParticipant(participantId: String) {
        if (!_state.value.isHost) return

        _state.update { state ->
            state.copy(
                participants = state.participants.filter { it.id != participantId }
            )
        }
    }

    fun focusOnParticipant(participantId: String) {
        _state.update {
            it.copy(
                layoutMode = ConferenceLayoutMode.Speaker,
                activeSpeakerId = participantId
            )
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    private fun addSimulatedParticipants(participantIds: List<String>, withVideo: Boolean) {
        val names = listOf("Alice", "Bob", "Charlie", "Diana", "Eve", "Frank")
        val participants = participantIds.mapIndexed { index, id ->
            ConferenceParticipant(
                id = id,
                name = names.getOrElse(index) { "Participant ${index + 1}" },
                isVideoEnabled = withVideo,
                connectionState = ParticipantConnectionState.Connected
            )
        }
        _state.update { it.copy(participants = participants) }
    }

    private fun startDurationTimer() {
        durationJob?.cancel()
        durationJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                _state.update { it.copy(duration = it.duration + 1) }
            }
        }
    }

    private fun startSpeakingSimulation() {
        simulationJob?.cancel()
        simulationJob = viewModelScope.launch {
            while (isActive) {
                delay(2000 + (Random.nextDouble() * 3000).toLong())

                val allParticipants = _state.value.participants + listOfNotNull(_state.value.localParticipant)
                if (allParticipants.isNotEmpty()) {
                    val speakerId = allParticipants.random().id

                    // Set speaking state
                    _state.update { state ->
                        state.copy(
                            activeSpeakerId = speakerId,
                            participants = state.participants.map { p ->
                                p.copy(isSpeaking = p.id == speakerId)
                            },
                            localParticipant = state.localParticipant?.copy(
                                isSpeaking = state.localParticipant.id == speakerId
                            )
                        )
                    }

                    // Clear speaking state after a moment
                    delay(1500)
                    _state.update { state ->
                        state.copy(
                            participants = state.participants.map { p ->
                                p.copy(isSpeaking = false)
                            },
                            localParticipant = state.localParticipant?.copy(isSpeaking = false)
                        )
                    }
                }
            }
        }
    }

    private fun generateConferenceId(): String {
        return "conf_${Clock.System.now().toEpochMilliseconds()}"
    }

    override fun onCleared() {
        super.onCleared()
        durationJob?.cancel()
        simulationJob?.cancel()
    }
}
