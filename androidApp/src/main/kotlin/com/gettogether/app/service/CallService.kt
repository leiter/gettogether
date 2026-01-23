package com.gettogether.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class CallService : Service() {

    companion object {
        private const val TAG = "CallService"
        const val ACTION_START_OUTGOING_CALL = "com.gettogether.app.START_OUTGOING_CALL"
        const val ACTION_START_INCOMING_CALL = "com.gettogether.app.START_INCOMING_CALL"
        const val ACTION_ANSWER_CALL = "com.gettogether.app.ANSWER_CALL"
        const val ACTION_DECLINE_CALL = "com.gettogether.app.DECLINE_CALL"
        const val ACTION_END_CALL = "com.gettogether.app.END_CALL"
        const val ACTION_TOGGLE_MUTE = "com.gettogether.app.TOGGLE_MUTE"
        const val ACTION_TOGGLE_SPEAKER = "com.gettogether.app.TOGGLE_SPEAKER"
        const val ACTION_TOGGLE_VIDEO = "com.gettogether.app.TOGGLE_VIDEO"
    }

    private val binder = CallBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var durationJob: Job? = null

    private lateinit var notificationManager: CallNotificationManager
    private lateinit var audioManager: AudioManager
    private var previousAudioMode: Int = AudioManager.MODE_NORMAL

    private val _callState = MutableStateFlow(ServiceCallState())
    val callState: StateFlow<ServiceCallState> = _callState.asStateFlow()

    inner class CallBinder : Binder() {
        fun getService(): CallService = this@CallService
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = CallNotificationManager(this)
        notificationManager.createNotificationChannels()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    /**
     * Prepare audio system for a call.
     * Sets the audio mode to MODE_IN_COMMUNICATION which helps the native
     * audio layer initialize correctly.
     */
    private fun prepareAudioForCall() {
        try {
            previousAudioMode = audioManager.mode
            Log.i(TAG, "[AUDIO] Preparing audio for call. Previous mode: $previousAudioMode")

            // Set audio mode to voice communication
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

            // Request audio focus
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_VOICE_CALL,
                AudioManager.AUDIOFOCUS_GAIN
            )

            Log.i(TAG, "[AUDIO] Audio mode set to MODE_IN_COMMUNICATION")
        } catch (e: Exception) {
            Log.e(TAG, "[AUDIO] Failed to prepare audio: ${e.message}")
        }
    }

    /**
     * Release audio system after a call ends.
     */
    private fun releaseAudio() {
        try {
            Log.i(TAG, "[AUDIO] Releasing audio. Restoring mode to: $previousAudioMode")

            // Abandon audio focus
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)

            // Restore previous audio mode
            audioManager.mode = previousAudioMode

            // Ensure speaker is off
            audioManager.isSpeakerphoneOn = false

            Log.i(TAG, "[AUDIO] Audio released")
        } catch (e: Exception) {
            Log.e(TAG, "[AUDIO] Failed to release audio: ${e.message}")
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_OUTGOING_CALL -> {
                val contactId = intent.getStringExtra(CallNotificationManager.EXTRA_CONTACT_ID) ?: ""
                val contactName = intent.getStringExtra(CallNotificationManager.EXTRA_CONTACT_NAME) ?: "Unknown"
                val isVideo = intent.getBooleanExtra(CallNotificationManager.EXTRA_IS_VIDEO, false)
                startOutgoingCall(contactId, contactName, isVideo)
            }
            ACTION_START_INCOMING_CALL -> {
                val callId = intent.getStringExtra(CallNotificationManager.EXTRA_CALL_ID) ?: ""
                val contactId = intent.getStringExtra(CallNotificationManager.EXTRA_CONTACT_ID) ?: ""
                val contactName = intent.getStringExtra(CallNotificationManager.EXTRA_CONTACT_NAME) ?: "Unknown"
                val isVideo = intent.getBooleanExtra(CallNotificationManager.EXTRA_IS_VIDEO, false)
                startIncomingCall(callId, contactId, contactName, isVideo)
            }
            ACTION_ANSWER_CALL -> {
                answerCall()
            }
            ACTION_DECLINE_CALL -> {
                declineCall()
            }
            ACTION_END_CALL -> {
                endCall()
            }
            ACTION_TOGGLE_MUTE -> {
                toggleMute()
            }
            ACTION_TOGGLE_SPEAKER -> {
                toggleSpeaker()
            }
            ACTION_TOGGLE_VIDEO -> {
                toggleVideo()
            }
        }
        return START_NOT_STICKY
    }

    private fun startOutgoingCall(contactId: String, contactName: String, isVideo: Boolean) {
        val callId = generateCallId()

        // Prepare audio BEFORE starting the call
        prepareAudioForCall()

        _callState.value = ServiceCallState(
            callId = callId,
            contactId = contactId,
            contactName = contactName,
            isVideo = isVideo,
            status = CallStatus.INITIATING,
            isOutgoing = true
        )

        startForegroundWithNotification()

        // Simulate call progression
        serviceScope.launch {
            delay(500)
            _callState.value = _callState.value.copy(status = CallStatus.RINGING)
            updateNotification()

            // Simulate call being answered
            delay(2000)
            _callState.value = _callState.value.copy(status = CallStatus.CONNECTED)
            startDurationTimer()
            updateNotification()
        }
    }

    private fun startIncomingCall(callId: String, contactId: String, contactName: String, isVideo: Boolean) {
        _callState.value = ServiceCallState(
            callId = callId,
            contactId = contactId,
            contactName = contactName,
            isVideo = isVideo,
            status = CallStatus.INCOMING,
            isOutgoing = false
        )

        // Show incoming call notification
        notificationManager.showIncomingCallNotification(
            contactName = contactName,
            isVideo = isVideo,
            callId = callId,
            contactId = contactId
        )
    }

    private fun answerCall() {
        notificationManager.cancelIncomingCallNotification()

        // Prepare audio BEFORE answering the call
        prepareAudioForCall()

        _callState.value = _callState.value.copy(status = CallStatus.CONNECTING)
        startForegroundWithNotification()

        serviceScope.launch {
            delay(500)
            _callState.value = _callState.value.copy(status = CallStatus.CONNECTED)
            startDurationTimer()
            updateNotification()
        }
    }

    private fun declineCall() {
        notificationManager.cancelIncomingCallNotification()
        _callState.value = _callState.value.copy(status = CallStatus.ENDED)
        releaseAudio()
        stopSelf()
    }

    private fun endCall() {
        durationJob?.cancel()
        _callState.value = _callState.value.copy(status = CallStatus.ENDED)
        releaseAudio()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun toggleMute() {
        _callState.value = _callState.value.copy(isMuted = !_callState.value.isMuted)
        updateNotification()
    }

    private fun toggleSpeaker() {
        _callState.value = _callState.value.copy(isSpeakerOn = !_callState.value.isSpeakerOn)
    }

    private fun toggleVideo() {
        _callState.value = _callState.value.copy(isLocalVideoEnabled = !_callState.value.isLocalVideoEnabled)
    }

    private fun startForegroundWithNotification() {
        val notification = notificationManager.createOngoingCallNotification(
            contactName = _callState.value.contactName,
            callDuration = formatDuration(_callState.value.durationSeconds),
            isMuted = _callState.value.isMuted,
            isVideo = _callState.value.isVideo,
            callId = _callState.value.callId
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Use phone call + camera + microphone foreground service types
            // These must match what's declared in AndroidManifest.xml
            val serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            startForeground(
                CallNotificationManager.NOTIFICATION_ID_ONGOING_CALL,
                notification,
                serviceType
            )
        } else {
            startForeground(CallNotificationManager.NOTIFICATION_ID_ONGOING_CALL, notification)
        }
    }

    private fun updateNotification() {
        if (_callState.value.status == CallStatus.CONNECTED ||
            _callState.value.status == CallStatus.CONNECTING ||
            _callState.value.status == CallStatus.RINGING ||
            _callState.value.status == CallStatus.INITIATING
        ) {
            val notification = notificationManager.createOngoingCallNotification(
                contactName = _callState.value.contactName,
                callDuration = formatDuration(_callState.value.durationSeconds),
                isMuted = _callState.value.isMuted,
                isVideo = _callState.value.isVideo,
                callId = _callState.value.callId
            )
            val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
            nm.notify(CallNotificationManager.NOTIFICATION_ID_ONGOING_CALL, notification)
        }
    }

    private fun startDurationTimer() {
        durationJob?.cancel()
        durationJob = serviceScope.launch {
            while (isActive) {
                delay(1000)
                _callState.value = _callState.value.copy(
                    durationSeconds = _callState.value.durationSeconds + 1
                )
                updateNotification()
            }
        }
    }

    private fun formatDuration(seconds: Long): String {
        val minutes = seconds / 60
        val secs = seconds % 60
        return "%02d:%02d".format(minutes, secs)
    }

    private fun generateCallId(): String {
        return "call_${System.currentTimeMillis()}"
    }

    override fun onDestroy() {
        super.onDestroy()
        durationJob?.cancel()
        serviceScope.cancel()
    }
}

data class ServiceCallState(
    val callId: String = "",
    val contactId: String = "",
    val contactName: String = "",
    val isVideo: Boolean = false,
    val status: CallStatus = CallStatus.IDLE,
    val isOutgoing: Boolean = true,
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val isLocalVideoEnabled: Boolean = true,
    val durationSeconds: Long = 0
)

enum class CallStatus {
    IDLE,
    INITIATING,
    RINGING,
    INCOMING,
    CONNECTING,
    CONNECTED,
    ENDED
}
