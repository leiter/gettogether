package com.gettogether.app.ui.screens.contacts

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gettogether.app.presentation.state.ContactDetails
import com.gettogether.app.presentation.viewmodel.ContactDetailsViewModel
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactDetailsScreen(
    contactId: String,
    onNavigateBack: () -> Unit,
    onNavigateToChat: (String) -> Unit,
    onContactRemoved: () -> Unit,
    viewModel: ContactDetailsViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(contactId) {
        viewModel.loadContact(contactId)
    }

    LaunchedEffect(state.contactRemoved) {
        if (state.contactRemoved) {
            onContactRemoved()
        }
    }

    LaunchedEffect(state.conversationStarted) {
        state.conversationStarted?.let { conversationId ->
            viewModel.clearConversationStarted()
            onNavigateToChat(conversationId)
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    // Remove confirmation dialog
    if (state.showRemoveDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.hideRemoveDialog() },
            title = { Text("Remove Contact") },
            text = {
                Text("Are you sure you want to remove ${state.contact?.displayName}? " +
                     "You won't be able to message them until you add them again.")
            },
            confirmButton = {
                TextButton(onClick = { viewModel.removeContact() }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideRemoveDialog() }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Block confirmation dialog
    if (state.showBlockDialog) {
        val isBlocked = state.contact?.isBlocked == true
        AlertDialog(
            onDismissRequest = { viewModel.hideBlockDialog() },
            title = { Text(if (isBlocked) "Unblock Contact" else "Block Contact") },
            text = {
                Text(
                    if (isBlocked)
                        "Unblock ${state.contact?.displayName}? They will be able to contact you again."
                    else
                        "Block ${state.contact?.displayName}? They won't be able to contact you."
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.toggleBlock() }) {
                    Text(
                        if (isBlocked) "Unblock" else "Block",
                        color = if (isBlocked) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideBlockDialog() }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Contact Details") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            color = MaterialTheme.colorScheme.background
        ) {
            when {
                state.isLoading || state.isRemoving || state.isBlocking -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = when {
                                    state.isRemoving -> "Removing contact..."
                                    state.isBlocking -> "Updating..."
                                    else -> "Loading..."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                state.contact != null -> {
                    ContactDetailsContent(
                        contact = state.contact!!,
                        onMessageClick = { viewModel.startConversation() },
                        onCallClick = { /* TODO: Start call */ },
                        onVideoCallClick = { /* TODO: Start video call */ },
                        onBlockClick = { viewModel.showBlockDialog() },
                        onRemoveClick = { viewModel.showRemoveDialog() }
                    )
                }
                else -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Contact not found",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactDetailsContent(
    contact: ContactDetails,
    onMessageClick: () -> Unit,
    onCallClick: () -> Unit,
    onVideoCallClick: () -> Unit,
    onBlockClick: () -> Unit,
    onRemoveClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Profile header
        ProfileHeader(contact = contact)

        Spacer(modifier = Modifier.height(24.dp))

        // Action buttons
        ActionButtons(
            onMessageClick = onMessageClick,
            onCallClick = onCallClick,
            onVideoCallClick = onVideoCallClick
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Contact info card
        ContactInfoCard(contact = contact)

        Spacer(modifier = Modifier.height(16.dp))

        // Danger zone
        DangerZone(
            isBlocked = contact.isBlocked,
            onBlockClick = onBlockClick,
            onRemoveClick = onRemoveClick
        )

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun ProfileHeader(contact: ContactDetails) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Avatar
        Box {
            Surface(
                modifier = Modifier.size(120.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = contact.displayName.first().uppercase(),
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            // Online indicator
            Surface(
                modifier = Modifier
                    .size(28.dp)
                    .align(Alignment.BottomEnd),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surface
            ) {
                Box(modifier = Modifier.padding(4.dp)) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        shape = MaterialTheme.shapes.extraLarge,
                        color = if (contact.isOnline)
                            MaterialTheme.colorScheme.tertiary
                        else
                            MaterialTheme.colorScheme.outline
                    ) {}
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Name
        Text(
            text = contact.displayName,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )

        // Username
        Text(
            text = "@${contact.username}",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Status badges
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Online status
            StatusBadge(
                text = if (contact.isOnline) "Online" else contact.lastSeen ?: "Offline",
                isPositive = contact.isOnline
            )

            if (contact.isTrusted) {
                Spacer(modifier = Modifier.width(8.dp))
                StatusBadge(text = "Trusted", isPositive = true)
            }

            if (contact.isBlocked) {
                Spacer(modifier = Modifier.width(8.dp))
                StatusBadge(text = "Blocked", isPositive = false)
            }
        }
    }
}

@Composable
private fun StatusBadge(
    text: String,
    isPositive: Boolean
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (isPositive)
            MaterialTheme.colorScheme.tertiaryContainer
        else
            MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (isPositive)
                MaterialTheme.colorScheme.onTertiaryContainer
            else
                MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun ActionButtons(
    onMessageClick: () -> Unit,
    onCallClick: () -> Unit,
    onVideoCallClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        ActionButton(
            icon = Icons.AutoMirrored.Filled.Send,
            label = "Message",
            onClick = onMessageClick
        )
        ActionButton(
            icon = Icons.Default.Call,
            label = "Call",
            onClick = onCallClick
        )
        ActionButton(
            icon = Icons.Default.Person,
            label = "Video",
            onClick = onVideoCallClick
        )
    }
}

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            onClick = onClick,
            modifier = Modifier.size(56.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ContactInfoCard(contact: ContactDetails) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Contact Information",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            InfoRow(label = "Username", value = "@${contact.username}")
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            InfoRow(label = "Jami ID", value = contact.jamiId)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            InfoRow(label = "Added", value = contact.addedDate)
            if (!contact.isOnline && contact.lastSeen != null) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                InfoRow(label = "Last seen", value = contact.lastSeen)
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun DangerZone(
    isBlocked: Boolean,
    onBlockClick: () -> Unit,
    onRemoveClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = "Actions",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        OutlinedButton(
            onClick = onBlockClick,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = if (isBlocked)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.error
            )
        ) {
            Icon(
                imageVector = Icons.Default.Clear,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (isBlocked) "Unblock Contact" else "Block Contact")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onRemoveClick,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
            )
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Remove Contact")
        }
    }
}
