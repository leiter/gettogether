package com.gettogether.app.ui.screens.home

import androidx.compose.foundation.clickable
import com.gettogether.app.ui.components.ContactAvatarImage
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gettogether.app.presentation.viewmodel.ContactUiItem
import com.gettogether.app.presentation.viewmodel.ContactsViewModel
import com.gettogether.app.presentation.viewmodel.TrustRequestUiItem
import com.gettogether.app.presentation.viewmodel.TrustRequestsViewModel
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsTab(
    onContactClick: (String) -> Unit,
    onAddContact: () -> Unit,
    onNavigateToBlockedContacts: () -> Unit,
    viewModel: ContactsViewModel = koinViewModel(),
    trustRequestsViewModel: TrustRequestsViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val trustRequestsState by trustRequestsViewModel.state.collectAsState()

    var showMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Contacts") },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More options"
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Blocked") },
                                onClick = {
                                    showMenu = false
                                    onNavigateToBlockedContacts()
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Block,
                                        contentDescription = null
                                    )
                                }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (state.hasAccount) {
                FloatingActionButton(onClick = onAddContact) {
                    Icon(
                        imageVector = Icons.Default.PersonAdd,
                        contentDescription = "Add contact"
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when {
                state.isLoading && state.contacts.isEmpty() -> {
                    LoadingPlaceholder()
                }
                !state.hasAccount -> {
                    NoAccountPlaceholder()
                }
                state.contacts.isEmpty() && trustRequestsState.requests.isEmpty() -> {
                    PullToRefreshBox(
                        isRefreshing = state.isLoading,
                        onRefresh = {
                            viewModel.refresh()
                            trustRequestsViewModel.refresh()
                        },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            item {
                                EmptyContactsPlaceholder()
                            }
                        }
                    }
                }
                else -> {
                    PullToRefreshBox(
                        isRefreshing = state.isLoading,
                        onRefresh = {
                            viewModel.refresh()
                            trustRequestsViewModel.refresh()
                        },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            // Trust requests section
                            if (trustRequestsState.requests.isNotEmpty()) {
                                item(key = "trust_requests_header") {
                                    TrustRequestsHeader(count = trustRequestsState.requests.size)
                                }

                                items(
                                    trustRequestsState.requests,
                                    key = { "trust_request_${it.from}" }
                                ) { request ->
                                    TrustRequestItem(
                                        request = request,
                                        onAccept = { trustRequestsViewModel.acceptRequest(request.from) },
                                        onReject = { trustRequestsViewModel.rejectRequest(request.from) }
                                    )
                                }

                                item(key = "divider") {
                                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                                }
                            }

                            // Contacts section
                            items(state.contacts, key = { it.id }) { contact ->
                                ContactItem(
                                    contact = contact,
                                    onClick = { onContactClick(contact.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

}

@Composable
private fun ContactItem(
    contact: ContactUiItem,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar with online indicator
            Box {
                ContactAvatarImage(
                    avatarUri = contact.avatarUri,
                    displayName = contact.name,
                    size = 48.dp
                )
                // Online indicator
                if (contact.isOnline) {
                    Surface(
                        modifier = Modifier
                            .size(14.dp)
                            .align(Alignment.BottomEnd),
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Box(
                            modifier = Modifier.padding(2.dp)
                        ) {
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                shape = MaterialTheme.shapes.extraLarge,
                                color = MaterialTheme.colorScheme.tertiary
                            ) {}
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = contact.name,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = if (contact.isOnline) "Online" else "Offline",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (contact.isOnline)
                        MaterialTheme.colorScheme.tertiary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LoadingPlaceholder() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun NoAccountPlaceholder() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "No account",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Create an account to add contacts",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun EmptyContactsPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(600.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "No contacts yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Add contacts to start chatting",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun TrustRequestsHeader(count: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Contact Requests",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Badge {
                Text(text = count.toString())
            }
        }
    }
}

@Composable
private fun TrustRequestItem(
    request: TrustRequestUiItem,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar
                ContactAvatarImage(
                    avatarUri = null,  // Trust requests don't have avatars yet
                    displayName = request.displayName,
                    size = 48.dp
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = request.displayName,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = request.from.take(16) + "...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (request.isProcessing) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = onAccept,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Accept")
                    }

                    OutlinedButton(
                        onClick = onReject,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Decline")
                    }
                }
            }
        }
    }
}

