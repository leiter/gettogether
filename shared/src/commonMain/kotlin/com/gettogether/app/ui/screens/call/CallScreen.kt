package com.gettogether.app.ui.screens.call

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gettogether.app.presentation.state.CallState
import com.gettogether.app.presentation.state.CallStatus
import com.gettogether.app.presentation.viewmodel.CallViewModel

@Composable
fun CallScreen(
    contactId: String,
    isVideo: Boolean,
    isIncoming: Boolean = false,
    callId: String? = null,
    onCallEnded: () -> Unit,
    viewModel: CallViewModel
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(contactId) {
        if (isIncoming && callId != null) {
            viewModel.initializeIncomingCall(callId, contactId, isVideo)
        } else {
            viewModel.initializeOutgoingCall(contactId, isVideo)
        }
    }

    LaunchedEffect(state.callStatus) {
        if (state.callStatus == CallStatus.Ended || state.callStatus == CallStatus.Failed) {
            kotlinx.coroutines.delay(1000)
            onCallEnded()
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
        ) {
            when (state.callStatus) {
                CallStatus.Incoming -> IncomingCallContent(
                    state = state,
                    onAccept = { viewModel.acceptCall() },
                    onReject = { viewModel.rejectCall() }
                )
                CallStatus.Ended, CallStatus.Failed -> CallEndedContent(
                    state = state
                )
                else -> ActiveCallContent(
                    state = state,
                    onToggleMute = { viewModel.toggleMute() },
                    onToggleSpeaker = { viewModel.toggleSpeaker() },
                    onToggleVideo = { viewModel.toggleVideo() },
                    onSwitchCamera = { viewModel.switchCamera() },
                    onEndCall = { viewModel.endCall() }
                )
            }
        }
    }
}

@Composable
private fun IncomingCallContent(
    state: CallState,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        // Caller info
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PulsingAvatar(name = state.contactName)

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = state.contactName,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (state.isVideo) "Incoming video call..." else "Incoming call...",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Accept/Reject buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Reject button
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FloatingActionButton(
                    onClick = onReject,
                    modifier = Modifier.size(72.dp),
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ) {
                    Icon(
                        imageVector = Icons.Default.CallEnd,
                        contentDescription = "Reject",
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Decline",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Accept button
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FloatingActionButton(
                    onClick = onAccept,
                    modifier = Modifier.size(72.dp),
                    containerColor = Color(0xFF4CAF50),
                    contentColor = Color.White
                ) {
                    Icon(
                        imageVector = if (state.isVideo) Icons.Default.Videocam else Icons.Default.Call,
                        contentDescription = "Accept",
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Accept",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun ActiveCallContent(
    state: CallState,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleVideo: () -> Unit,
    onSwitchCamera: () -> Unit,
    onEndCall: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        // Video preview placeholder (when video is enabled)
        if (state.isVideo && state.isLocalVideoEnabled) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                // Remote video would go here
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ContactAvatar(
                        name = state.contactName,
                        size = 120
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = state.contactName,
                        style = MaterialTheme.typography.headlineSmall
                    )
                }

                // Local video preview (small overlay)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .size(100.dp, 140.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.inverseSurface),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "You",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.inverseOnSurface
                    )
                }
            }
        } else {
            // Audio call or video off - show avatar
            Spacer(modifier = Modifier.weight(0.3f))

            ContactAvatar(
                name = state.contactName,
                size = 160
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = state.contactName,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.weight(0.3f))
        }

        // Call status and duration
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 16.dp)
        ) {
            Text(
                text = when (state.callStatus) {
                    CallStatus.Initiating -> "Calling..."
                    CallStatus.Ringing -> "Ringing..."
                    CallStatus.Connecting -> "Connecting..."
                    CallStatus.Connected -> state.formattedDuration
                    CallStatus.Reconnecting -> "Reconnecting..."
                    else -> ""
                },
                style = MaterialTheme.typography.titleLarge,
                color = if (state.callStatus == CallStatus.Connected)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (state.callStatus != CallStatus.Connected) {
                Spacer(modifier = Modifier.height(8.dp))
                AnimatedDots()
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Call controls
        CallControls(
            state = state,
            onToggleMute = onToggleMute,
            onToggleSpeaker = onToggleSpeaker,
            onToggleVideo = onToggleVideo,
            onSwitchCamera = onSwitchCamera,
            onEndCall = onEndCall
        )

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun CallControls(
    state: CallState,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleVideo: () -> Unit,
    onSwitchCamera: () -> Unit,
    onEndCall: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top row of controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Mute button
            ControlButton(
                icon = if (state.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                label = if (state.isMuted) "Unmute" else "Mute",
                isActive = state.isMuted,
                onClick = onToggleMute,
                enabled = state.canToggleControls
            )

            // Speaker button
            ControlButton(
                icon = if (state.isSpeakerOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                label = if (state.isSpeakerOn) "Speaker" else "Speaker",
                isActive = state.isSpeakerOn,
                onClick = onToggleSpeaker,
                enabled = state.canToggleControls
            )

            // Video toggle (only for video calls)
            if (state.isVideo) {
                ControlButton(
                    icon = if (state.isLocalVideoEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                    label = if (state.isLocalVideoEnabled) "Video On" else "Video Off",
                    isActive = !state.isLocalVideoEnabled,
                    onClick = onToggleVideo,
                    enabled = state.canToggleVideo
                )
            }

            // Switch camera (only for video calls with video enabled)
            if (state.isVideo && state.isLocalVideoEnabled) {
                ControlButton(
                    icon = Icons.Default.Cameraswitch,
                    label = "Flip",
                    isActive = false,
                    onClick = onSwitchCamera,
                    enabled = state.canToggleVideo
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // End call button
        FloatingActionButton(
            onClick = onEndCall,
            modifier = Modifier.size(72.dp),
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 6.dp
            )
        ) {
            Icon(
                imageVector = Icons.Default.CallEnd,
                contentDescription = "End Call",
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "End Call",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun ControlButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(56.dp),
            shape = CircleShape,
            color = when {
                !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                isActive -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    modifier = Modifier.size(24.dp),
                    tint = when {
                        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        isActive -> MaterialTheme.colorScheme.onPrimaryContainer
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled)
                MaterialTheme.colorScheme.onSurfaceVariant
            else
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun CallEndedContent(state: CallState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        ContactAvatar(name = state.contactName, size = 120)

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = state.contactName,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (state.callStatus == CallStatus.Failed) "Call Failed" else "Call Ended",
            style = MaterialTheme.typography.titleMedium,
            color = if (state.callStatus == CallStatus.Failed)
                MaterialTheme.colorScheme.error
            else
                MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (state.callDuration > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Duration: ${state.formattedDuration}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ContactAvatar(
    name: String,
    size: Int
) {
    Surface(
        modifier = Modifier.size(size.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = name.firstOrNull()?.uppercase() ?: "?",
                style = when {
                    size >= 160 -> MaterialTheme.typography.displayLarge
                    size >= 120 -> MaterialTheme.typography.displayMedium
                    else -> MaterialTheme.typography.displaySmall
                },
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun PulsingAvatar(name: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = Modifier.scale(scale),
        contentAlignment = Alignment.Center
    ) {
        // Outer ring
        Surface(
            modifier = Modifier.size(160.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        ) {}

        // Inner avatar
        ContactAvatar(name = name, size = 140)
    }
}

@Composable
private fun AnimatedDots() {
    val infiniteTransition = rememberInfiniteTransition(label = "dots")
    val alpha1 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha1"
    )
    val alpha2 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, delayMillis = 150),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha2"
    )
    val alpha3 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, delayMillis = 300),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha3"
    )

    Row {
        listOf(alpha1, alpha2, alpha3).forEach { alpha ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha))
            )
        }
    }
}
