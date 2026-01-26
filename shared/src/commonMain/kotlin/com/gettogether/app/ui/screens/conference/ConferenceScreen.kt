package com.gettogether.app.ui.screens.conference

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.PresentToAll
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gettogether.app.presentation.state.ConferenceLayoutMode
import com.gettogether.app.presentation.state.ConferenceParticipant
import com.gettogether.app.presentation.state.ConferenceState
import com.gettogether.app.presentation.state.ConferenceStatus
import com.gettogether.app.presentation.state.ParticipantConnectionState
import com.gettogether.app.presentation.viewmodel.ConferenceViewModel

@Composable
fun ConferenceScreen(
    participantIds: List<String>,
    withVideo: Boolean,
    conferenceId: String? = null,
    onConferenceEnded: () -> Unit,
    viewModel: ConferenceViewModel
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(participantIds, conferenceId) {
        if (conferenceId != null) {
            viewModel.joinConference(conferenceId, withVideo)
        } else {
            viewModel.createConference(participantIds, withVideo)
        }
    }

    LaunchedEffect(state.status) {
        if (state.status == ConferenceStatus.Ended || state.status == ConferenceStatus.Failed) {
            kotlinx.coroutines.delay(1000)
            onConferenceEnded()
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
                .background(MaterialTheme.colorScheme.surface)
        ) {
            when (state.status) {
                ConferenceStatus.Idle,
                ConferenceStatus.Creating,
                ConferenceStatus.Joining,
                ConferenceStatus.Connecting -> ConnectingContent(state)

                ConferenceStatus.Connected,
                ConferenceStatus.Reconnecting -> ActiveConferenceContent(
                    state = state,
                    onToggleMute = { viewModel.toggleMute() },
                    onToggleSpeaker = { viewModel.toggleSpeaker() },
                    onToggleVideo = { viewModel.toggleVideo() },
                    onSwitchCamera = { viewModel.switchCamera() },
                    onSetLayoutMode = { viewModel.setLayoutMode(it) },
                    onMuteParticipant = { viewModel.muteParticipant(it) },
                    onRemoveParticipant = { viewModel.removeParticipant(it) },
                    onFocusParticipant = { viewModel.focusOnParticipant(it) },
                    onLeaveConference = { viewModel.leaveConference() }
                )

                ConferenceStatus.Ended,
                ConferenceStatus.Failed -> ConferenceEndedContent(state)
            }
        }
    }
}

@Composable
private fun ConnectingContent(state: ConferenceState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(120.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = state.conferenceName.ifEmpty { "Group Call" },
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = when (state.status) {
                ConferenceStatus.Creating -> "Creating conference..."
                ConferenceStatus.Joining -> "Joining conference..."
                ConferenceStatus.Connecting -> "Connecting..."
                else -> "Starting..."
            },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ActiveConferenceContent(
    state: ConferenceState,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleVideo: () -> Unit,
    onSwitchCamera: () -> Unit,
    onSetLayoutMode: (ConferenceLayoutMode) -> Unit,
    onMuteParticipant: (String) -> Unit,
    onRemoveParticipant: (String) -> Unit,
    onFocusParticipant: (String) -> Unit,
    onLeaveConference: () -> Unit
) {
    var showControls by remember { mutableStateOf(true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable { showControls = !showControls }
    ) {
        // Participant grid/layout
        when (state.layoutMode) {
            ConferenceLayoutMode.Grid -> GridLayout(
                state = state,
                onParticipantClick = onFocusParticipant,
                onMuteParticipant = onMuteParticipant,
                onRemoveParticipant = onRemoveParticipant
            )
            ConferenceLayoutMode.Speaker -> SpeakerLayout(
                state = state,
                onParticipantClick = onFocusParticipant,
                onMuteParticipant = onMuteParticipant,
                onRemoveParticipant = onRemoveParticipant
            )
            ConferenceLayoutMode.Filmstrip -> FilmstripLayout(
                state = state,
                onParticipantClick = onFocusParticipant,
                onMuteParticipant = onMuteParticipant,
                onRemoveParticipant = onRemoveParticipant
            )
        }

        // Top bar with conference info
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically()
        ) {
            ConferenceTopBar(
                state = state,
                currentLayout = state.layoutMode,
                onSetLayoutMode = onSetLayoutMode
            )
        }

        // Bottom controls
        AnimatedVisibility(
            visible = showControls,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it }
        ) {
            ConferenceControls(
                state = state,
                onToggleMute = onToggleMute,
                onToggleSpeaker = onToggleSpeaker,
                onToggleVideo = onToggleVideo,
                onSwitchCamera = onSwitchCamera,
                onLeaveConference = onLeaveConference
            )
        }
    }
}

@Composable
private fun ConferenceTopBar(
    state: ConferenceState,
    currentLayout: ConferenceLayoutMode,
    onSetLayoutMode: (ConferenceLayoutMode) -> Unit
) {
    var showLayoutMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = state.conferenceName.ifEmpty { "Group Call" },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = state.formattedDuration,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = " • ${state.participantCount} participants",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Box {
                IconButton(onClick = { showLayoutMenu = true }) {
                    Icon(
                        imageVector = when (currentLayout) {
                            ConferenceLayoutMode.Grid -> Icons.Default.GridView
                            ConferenceLayoutMode.Speaker -> Icons.Default.PresentToAll
                            ConferenceLayoutMode.Filmstrip -> Icons.Default.ViewStream
                        },
                        contentDescription = "Change layout"
                    )
                }

                DropdownMenu(
                    expanded = showLayoutMenu,
                    onDismissRequest = { showLayoutMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Grid") },
                        onClick = {
                            onSetLayoutMode(ConferenceLayoutMode.Grid)
                            showLayoutMenu = false
                        },
                        leadingIcon = {
                            Icon(Icons.Default.GridView, contentDescription = null)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Speaker") },
                        onClick = {
                            onSetLayoutMode(ConferenceLayoutMode.Speaker)
                            showLayoutMenu = false
                        },
                        leadingIcon = {
                            Icon(Icons.Default.PresentToAll, contentDescription = null)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Filmstrip") },
                        onClick = {
                            onSetLayoutMode(ConferenceLayoutMode.Filmstrip)
                            showLayoutMenu = false
                        },
                        leadingIcon = {
                            Icon(Icons.Default.ViewStream, contentDescription = null)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun GridLayout(
    state: ConferenceState,
    onParticipantClick: (String) -> Unit,
    onMuteParticipant: (String) -> Unit,
    onRemoveParticipant: (String) -> Unit
) {
    val allParticipants = buildList {
        state.localParticipant?.let { add(it) }
        addAll(state.participants)
    }

    val columnCount = when {
        allParticipants.size <= 1 -> 1
        allParticipants.size <= 4 -> 2
        else -> 3
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(columnCount),
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 72.dp, bottom = 120.dp),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(allParticipants, key = { it.id }) { participant ->
            ParticipantTile(
                participant = participant,
                isActiveSpeaker = participant.id == state.activeSpeakerId,
                isLocalUser = participant.id == state.localParticipant?.id,
                canManage = state.canManageParticipants && participant.id != state.localParticipant?.id,
                onClick = { onParticipantClick(participant.id) },
                onMute = { onMuteParticipant(participant.id) },
                onRemove = { onRemoveParticipant(participant.id) }
            )
        }
    }
}

@Composable
private fun SpeakerLayout(
    state: ConferenceState,
    onParticipantClick: (String) -> Unit,
    onMuteParticipant: (String) -> Unit,
    onRemoveParticipant: (String) -> Unit
) {
    val allParticipants = buildList {
        state.localParticipant?.let { add(it) }
        addAll(state.participants)
    }

    val activeSpeaker = allParticipants.find { it.id == state.activeSpeakerId }
        ?: allParticipants.firstOrNull()
    val otherParticipants = allParticipants.filter { it.id != activeSpeaker?.id }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 72.dp, bottom = 120.dp)
    ) {
        // Main speaker view
        activeSpeaker?.let { speaker ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(8.dp)
            ) {
                ParticipantTile(
                    participant = speaker,
                    isActiveSpeaker = true,
                    isLocalUser = speaker.id == state.localParticipant?.id,
                    canManage = state.canManageParticipants && speaker.id != state.localParticipant?.id,
                    onClick = { },
                    onMute = { onMuteParticipant(speaker.id) },
                    onRemove = { onRemoveParticipant(speaker.id) },
                    isLargeView = true
                )
            }
        }

        // Small tiles for other participants
        if (otherParticipants.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                contentPadding = PaddingValues(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(otherParticipants, key = { it.id }) { participant ->
                    Box(
                        modifier = Modifier
                            .width(140.dp)
                            .fillMaxHeight()
                    ) {
                        ParticipantTile(
                            participant = participant,
                            isActiveSpeaker = false,
                            isLocalUser = participant.id == state.localParticipant?.id,
                            canManage = state.canManageParticipants && participant.id != state.localParticipant?.id,
                            onClick = { onParticipantClick(participant.id) },
                            onMute = { onMuteParticipant(participant.id) },
                            onRemove = { onRemoveParticipant(participant.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilmstripLayout(
    state: ConferenceState,
    onParticipantClick: (String) -> Unit,
    onMuteParticipant: (String) -> Unit,
    onRemoveParticipant: (String) -> Unit
) {
    val allParticipants = buildList {
        state.localParticipant?.let { add(it) }
        addAll(state.participants)
    }

    val activeSpeaker = allParticipants.find { it.id == state.activeSpeakerId }
        ?: allParticipants.firstOrNull()

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 72.dp, bottom = 120.dp, start = 8.dp, end = 8.dp)
    ) {
        // Vertical strip on the left
        LazyRow(
            modifier = Modifier
                .width(100.dp)
                .fillMaxHeight(),
            contentPadding = PaddingValues(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(allParticipants, key = { it.id }) { participant ->
                Box(
                    modifier = Modifier
                        .width(100.dp)
                        .aspectRatio(0.75f)
                ) {
                    SmallParticipantTile(
                        participant = participant,
                        isActive = participant.id == state.activeSpeakerId,
                        onClick = { onParticipantClick(participant.id) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Main view
        activeSpeaker?.let { speaker ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                ParticipantTile(
                    participant = speaker,
                    isActiveSpeaker = true,
                    isLocalUser = speaker.id == state.localParticipant?.id,
                    canManage = state.canManageParticipants && speaker.id != state.localParticipant?.id,
                    onClick = { },
                    onMute = { onMuteParticipant(speaker.id) },
                    onRemove = { onRemoveParticipant(speaker.id) },
                    isLargeView = true
                )
            }
        }
    }
}

@Composable
private fun ParticipantTile(
    participant: ConferenceParticipant,
    isActiveSpeaker: Boolean,
    isLocalUser: Boolean,
    canManage: Boolean,
    onClick: () -> Unit,
    onMute: () -> Unit,
    onRemove: () -> Unit,
    isLargeView: Boolean = false
) {
    var showMenu by remember { mutableStateOf(false) }

    val borderColor = when {
        isActiveSpeaker && participant.isSpeaking -> MaterialTheme.colorScheme.primary
        isActiveSpeaker -> MaterialTheme.colorScheme.primaryContainer
        else -> Color.Transparent
    }

    val borderWidth = animateFloatAsState(
        targetValue = if (participant.isSpeaking) 3f else if (isActiveSpeaker) 2f else 0f,
        label = "border"
    )

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (borderWidth.value > 0) {
                    Modifier.border(
                        width = borderWidth.value.dp,
                        color = borderColor,
                        shape = RoundedCornerShape(12.dp)
                    )
                } else Modifier
            )
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // Video placeholder / Avatar
            if (participant.isVideoEnabled) {
                // Video would go here
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.inverseSurface),
                    contentAlignment = Alignment.Center
                ) {
                    ParticipantAvatar(
                        participant = participant,
                        size = if (isLargeView) 120 else 64
                    )
                }
            } else {
                ParticipantAvatar(
                    participant = participant,
                    size = if (isLargeView) 120 else 64
                )
            }

            // Connection state overlay
            if (participant.connectionState != ParticipantConnectionState.Connected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when (participant.connectionState) {
                            ParticipantConnectionState.Connecting -> "Connecting..."
                            ParticipantConnectionState.Reconnecting -> "Reconnecting..."
                            ParticipantConnectionState.Disconnected -> "Disconnected"
                            else -> ""
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White
                    )
                }
            }

            // Participant info overlay
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                        )
                    )
                    .padding(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isLocalUser) "${participant.name} (You)" else participant.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (participant.isHost) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = "Host",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Status icons (top right)
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (participant.isMuted) {
                    StatusIcon(
                        icon = Icons.Default.MicOff,
                        backgroundColor = MaterialTheme.colorScheme.error
                    )
                }
                if (!participant.isVideoEnabled) {
                    StatusIcon(
                        icon = Icons.Default.VideocamOff,
                        backgroundColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }

            // Host controls menu
            if (canManage) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                ) {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More options",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Mute") },
                            onClick = {
                                onMute()
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(Icons.Default.MicOff, contentDescription = null)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Remove") },
                            onClick = {
                                onRemove()
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.PersonRemove,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallParticipantTile(
    participant: ConferenceParticipant,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (isActive) {
                    Modifier.border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(8.dp)
                    )
                } else Modifier
            )
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            ParticipantAvatar(participant = participant, size = 40)

            // Name label
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(4.dp)
            ) {
                Text(
                    text = participant.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Muted indicator
            if (participant.isMuted) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                ) {
                    StatusIcon(
                        icon = Icons.Default.MicOff,
                        backgroundColor = MaterialTheme.colorScheme.error,
                        size = 16
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusIcon(
    icon: ImageVector,
    backgroundColor: Color,
    size: Int = 24
) {
    Surface(
        shape = CircleShape,
        color = backgroundColor.copy(alpha = 0.9f),
        modifier = Modifier.size(size.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size((size * 0.6f).dp),
                tint = Color.White
            )
        }
    }
}

@Composable
private fun ParticipantAvatar(
    participant: ConferenceParticipant,
    size: Int
) {
    Surface(
        modifier = Modifier.size(size.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = participant.initials,
                style = when {
                    size >= 120 -> MaterialTheme.typography.displayMedium
                    size >= 64 -> MaterialTheme.typography.headlineMedium
                    size >= 40 -> MaterialTheme.typography.titleMedium
                    else -> MaterialTheme.typography.labelMedium
                },
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun ConferenceControls(
    state: ConferenceState,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleVideo: () -> Unit,
    onSwitchCamera: () -> Unit,
    onLeaveConference: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Control buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ControlButton(
                    icon = if (state.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                    label = if (state.isMuted) "Unmute" else "Mute",
                    isActive = state.isMuted,
                    onClick = onToggleMute
                )

                ControlButton(
                    icon = if (state.isSpeakerOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                    label = "Speaker",
                    isActive = state.isSpeakerOn,
                    onClick = onToggleSpeaker
                )

                ControlButton(
                    icon = if (state.isLocalVideoEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                    label = "Video",
                    isActive = !state.isLocalVideoEnabled,
                    onClick = onToggleVideo
                )

                if (state.isLocalVideoEnabled) {
                    ControlButton(
                        icon = Icons.Default.Cameraswitch,
                        label = "Flip",
                        isActive = false,
                        onClick = onSwitchCamera
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Leave button
            FloatingActionButton(
                onClick = onLeaveConference,
                modifier = Modifier.size(64.dp),
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CallEnd,
                    contentDescription = "Leave Conference",
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Leave",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun ControlButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            onClick = onClick,
            modifier = Modifier.size(52.dp),
            shape = CircleShape,
            color = if (isActive)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    modifier = Modifier.size(24.dp),
                    tint = if (isActive)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ConferenceEndedContent(state: ConferenceState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(100.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.CallEnd,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = if (state.status == ConferenceStatus.Failed) "Conference Failed" else "Conference Ended",
            style = MaterialTheme.typography.headlineMedium,
            color = if (state.status == ConferenceStatus.Failed)
                MaterialTheme.colorScheme.error
            else
                MaterialTheme.colorScheme.onSurface
        )

        if (state.duration > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Duration: ${state.formattedDuration}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "${state.participantCount} participants",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
