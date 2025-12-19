package com.gettogether.app.ui.screens.home

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.gettogether.app.presentation.state.UserProfile
import com.gettogether.app.presentation.viewmodel.SettingsViewModel
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTab(
    onSignedOut: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    var expandedSection by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.signOutComplete) {
        if (state.signOutComplete) {
            onSignedOut()
        }
    }

    // Sign out confirmation dialog
    if (state.showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.hideSignOutDialog() },
            title = { Text("Sign Out") },
            text = { Text("Are you sure you want to sign out? You'll need to import your account or create a new one to use the app again.") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.signOut() }
                ) {
                    Text("Sign Out", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideSignOutDialog() }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Settings") }
        )

        if (state.isLoading || state.isSigningOut) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (state.isSigningOut) "Signing out..." else "Loading...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                // Profile section
                ProfileSection(
                    profile = state.userProfile,
                    onClick = { expandedSection = if (expandedSection == "profile") null else "profile" }
                )

                HorizontalDivider()

                // Account section
                SettingsItem(
                    icon = Icons.Default.Person,
                    title = "Account",
                    subtitle = state.userProfile.jamiId.take(20) + "...",
                    onClick = { expandedSection = if (expandedSection == "account") null else "account" }
                )

                if (expandedSection == "account") {
                    AccountDetails(profile = state.userProfile)
                }

                // Notifications section
                SettingsItem(
                    icon = Icons.Default.Notifications,
                    title = "Notifications",
                    subtitle = if (state.notificationSettings.enabled) "Enabled" else "Disabled",
                    onClick = { expandedSection = if (expandedSection == "notifications") null else "notifications" }
                )

                if (expandedSection == "notifications") {
                    NotificationSettingsSection(
                        enabled = state.notificationSettings.enabled,
                        messageNotifications = state.notificationSettings.messageNotifications,
                        callNotifications = state.notificationSettings.callNotifications,
                        soundEnabled = state.notificationSettings.soundEnabled,
                        vibrationEnabled = state.notificationSettings.vibrationEnabled,
                        onToggleEnabled = viewModel::toggleNotifications,
                        onToggleMessages = viewModel::toggleMessageNotifications,
                        onToggleCalls = viewModel::toggleCallNotifications,
                        onToggleSound = viewModel::toggleSound,
                        onToggleVibration = viewModel::toggleVibration
                    )
                }

                // Privacy section
                SettingsItem(
                    icon = Icons.Default.Lock,
                    title = "Privacy & Security",
                    subtitle = "Manage your privacy settings",
                    onClick = { expandedSection = if (expandedSection == "privacy") null else "privacy" }
                )

                if (expandedSection == "privacy") {
                    PrivacySettingsSection(
                        shareOnlineStatus = state.privacySettings.shareOnlineStatus,
                        readReceipts = state.privacySettings.readReceipts,
                        typingIndicators = state.privacySettings.typingIndicators,
                        blockUnknownContacts = state.privacySettings.blockUnknownContacts,
                        onToggleOnlineStatus = viewModel::toggleShareOnlineStatus,
                        onToggleReadReceipts = viewModel::toggleReadReceipts,
                        onToggleTypingIndicators = viewModel::toggleTypingIndicators,
                        onToggleBlockUnknown = viewModel::toggleBlockUnknownContacts
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // About section
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = "About",
                    subtitle = "Get-Together v1.0.0",
                    onClick = { expandedSection = if (expandedSection == "about") null else "about" }
                )

                if (expandedSection == "about") {
                    AboutSection()
                }

                // Sign out
                SettingsItem(
                    icon = Icons.AutoMirrored.Filled.ExitToApp,
                    title = "Sign Out",
                    subtitle = "Sign out of your account",
                    onClick = { viewModel.showSignOutDialog() },
                    isDestructive = true
                )

                Spacer(modifier = Modifier.height(32.dp))

                // App info
                Text(
                    text = "Get-Together v1.0.0\nBuilt with Jami",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                )
            }
        }
    }
}

@Composable
private fun ProfileSection(
    profile: UserProfile,
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
            // Avatar
            Surface(
                modifier = Modifier.size(64.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = profile.displayName.firstOrNull()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.displayName.ifEmpty { "Unknown" },
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = if (profile.username.isNotEmpty()) "@${profile.username}" else "Tap to view profile",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (profile.registrationState.isNotEmpty()) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = if (profile.registrationState == "REGISTERED")
                            MaterialTheme.colorScheme.tertiaryContainer
                        else
                            MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            text = profile.registrationState,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (profile.registrationState == "REGISTERED")
                                MaterialTheme.colorScheme.onTertiaryContainer
                            else
                                MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountDetails(profile: UserProfile) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 56.dp, vertical = 12.dp)
        ) {
            DetailRow("Display Name", profile.displayName)
            DetailRow("Username", profile.username)
            DetailRow("Jami ID", profile.jamiId)
            DetailRow("Account ID", profile.accountId)
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value.ifEmpty { "Not set" },
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun NotificationSettingsSection(
    enabled: Boolean,
    messageNotifications: Boolean,
    callNotifications: Boolean,
    soundEnabled: Boolean,
    vibrationEnabled: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onToggleMessages: (Boolean) -> Unit,
    onToggleCalls: (Boolean) -> Unit,
    onToggleSound: (Boolean) -> Unit,
    onToggleVibration: (Boolean) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 56.dp, vertical = 8.dp)
        ) {
            SettingsSwitchRow("Enable Notifications", enabled, onToggleEnabled)
            SettingsSwitchRow("Message Notifications", messageNotifications, onToggleMessages, enabled)
            SettingsSwitchRow("Call Notifications", callNotifications, onToggleCalls, enabled)
            SettingsSwitchRow("Sound", soundEnabled, onToggleSound, enabled)
            SettingsSwitchRow("Vibration", vibrationEnabled, onToggleVibration, enabled)
        }
    }
}

@Composable
private fun PrivacySettingsSection(
    shareOnlineStatus: Boolean,
    readReceipts: Boolean,
    typingIndicators: Boolean,
    blockUnknownContacts: Boolean,
    onToggleOnlineStatus: (Boolean) -> Unit,
    onToggleReadReceipts: (Boolean) -> Unit,
    onToggleTypingIndicators: (Boolean) -> Unit,
    onToggleBlockUnknown: (Boolean) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 56.dp, vertical = 8.dp)
        ) {
            SettingsSwitchRow("Share Online Status", shareOnlineStatus, onToggleOnlineStatus)
            SettingsSwitchRow("Read Receipts", readReceipts, onToggleReadReceipts)
            SettingsSwitchRow("Typing Indicators", typingIndicators, onToggleTypingIndicators)
            SettingsSwitchRow("Block Unknown Contacts", blockUnknownContacts, onToggleBlockUnknown)
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled)
                MaterialTheme.colorScheme.onSurface
            else
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}

@Composable
private fun AboutSection() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 56.dp, vertical = 12.dp)
        ) {
            Text(
                text = "Get-Together",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Version 1.0.0",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "A secure, decentralized communication app built on Jami technology. " +
                        "Your messages are end-to-end encrypted and never stored on any server.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Powered by Jami (GNU Ring)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    isDestructive: Boolean = false
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
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isDestructive)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isDestructive)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
