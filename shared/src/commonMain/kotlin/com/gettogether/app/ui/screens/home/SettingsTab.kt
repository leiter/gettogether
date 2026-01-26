package com.gettogether.app.ui.screens.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.gettogether.app.platform.ImagePickerResult
import com.gettogether.app.platform.provideClipboardManager
import com.gettogether.app.platform.provideImagePicker
import com.gettogether.app.presentation.state.UserProfile
import com.gettogether.app.presentation.viewmodel.SettingsViewModel
import com.gettogether.app.ui.components.AvatarImage
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SettingsTab(
    onSignedOut: () -> Unit,
    viewModel: SettingsViewModel
) {
    val state by viewModel.state.collectAsState()
    var expandedSection by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.signOutComplete) {
        if (state.signOutComplete) {
            onSignedOut()
        }
    }

    LaunchedEffect(state.profileUpdateSuccess) {
        state.profileUpdateSuccess?.let { message ->
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short
            )
            viewModel.clearProfileUpdateSuccess()
        }
    }

    // Handle logout completion (different from signOutComplete which deletes account)
    LaunchedEffect(state.logoutComplete) {
        if (state.logoutComplete) {
            onSignedOut()
        }
    }

    // Handle export success
    LaunchedEffect(state.exportSuccess) {
        state.exportSuccess?.let { message ->
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Long
            )
            viewModel.clearExportSuccess()
        }
    }

    // Export account dialog
    if (state.showExportDialog) {
        ExportAccountDialog(
            isExporting = state.isExporting,
            error = state.exportError,
            onDismiss = { viewModel.hideExportDialog() },
            onExport = { password -> viewModel.exportAccount(password) }
        )
    }

    // Logout options dialog
    if (state.showLogoutOptionsDialog) {
        LogoutOptionsDialog(
            onDismiss = { viewModel.hideLogoutOptionsDialog() },
            onLogoutKeepData = { viewModel.logoutKeepData() },
            onDeleteAccount = {
                viewModel.hideLogoutOptionsDialog()
                viewModel.showSignOutDialog()
            },
            onExportFirst = {
                viewModel.hideLogoutOptionsDialog()
                viewModel.showExportDialog()
            }
        )
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

    // Edit profile dialog
    if (state.showEditProfileDialog) {
        val imagePicker = provideImagePicker()

        EditProfileDialog(
            currentDisplayName = state.userProfile.displayName,
            currentAvatarUri = state.userProfile.avatarUri,
            selectedAvatarUri = state.selectedAvatarUri,
            avatarCleared = state.avatarCleared,
            isUpdating = state.isUpdatingProfile,
            isProcessingAvatar = state.isProcessingAvatar,
            error = state.error,
            onDismiss = { viewModel.hideEditProfileDialog() },
            onConfirm = { displayName, avatarUri ->
                viewModel.updateProfileWithAvatar(displayName, avatarUri)
            },
            onSelectAvatar = {
                imagePicker.pickImage { result ->
                    when (result) {
                        is ImagePickerResult.Success -> viewModel.selectAvatar(result.uri)
                        is ImagePickerResult.Error -> viewModel.clearError() // Error will be shown by picker
                        ImagePickerResult.Cancelled -> { /* Do nothing */ }
                    }
                }
            },
            onClearAvatar = { viewModel.clearSelectedAvatar() }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                    onClick = { viewModel.showEditProfileDialog() }
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
                    AccountDetails(profile = state.userProfile, snackbarHostState = snackbarHostState)
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

                // Network Debug section
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = "Network Debug",
                    subtitle = "DHT: ${state.userProfile.dhtStatus}, Peers: ${state.userProfile.peerCount}",
                    onClick = { expandedSection = if (expandedSection == "network") null else "network" }
                )

                if (expandedSection == "network") {
                    NetworkDebugSection(profile = state.userProfile)
                }

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

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Export account
                SettingsItem(
                    icon = Icons.Default.Share,
                    title = "Export Account",
                    subtitle = "Backup your account to a file",
                    onClick = { viewModel.showExportDialog() }
                )

                // Sign out (shows options dialog)
                SettingsItem(
                    icon = Icons.AutoMirrored.Filled.ExitToApp,
                    title = "Sign Out",
                    subtitle = "Logout or delete your account",
                    onClick = { viewModel.showLogoutOptionsDialog() },
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

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
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
            AvatarImage(
                avatarUri = profile.avatarUri,
                displayName = profile.displayName,
                size = 64.dp
            )

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
private fun AccountDetails(profile: UserProfile, snackbarHostState: SnackbarHostState) {
    val clipboardManager = remember { provideClipboardManager() }
    val scope = rememberCoroutineScope()

    val onCopy: (String, String) -> Unit = { label, text ->
        clipboardManager.copyToClipboard(text)
        scope.launch {
            snackbarHostState.showSnackbar(
                message = "$label copied to clipboard",
                duration = SnackbarDuration.Short
            )
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 56.dp, vertical = 12.dp)
        ) {
            DetailRow("Display Name", profile.displayName)
            DetailRow("Username", profile.username)
            DetailRow("Jami ID", profile.jamiId, copyable = true, onCopy = onCopy)
            DetailRow("Account ID", profile.accountId, copyable = true, onCopy = onCopy)
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    copyable: Boolean = false,
    onCopy: ((String, String) -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .padding(vertical = 4.dp)
            .then(
                if (copyable && onCopy != null) {
                    Modifier.combinedClickable(
                        onClick = {},
                        onLongClick = { onCopy(label, value) }
                    )
                } else Modifier
            )
    ) {
        Text(
            text = label + if (copyable) " (long-press to copy)" else "",
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
private fun NetworkDebugSection(profile: UserProfile) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 56.dp, vertical = 12.dp)
        ) {
            Text(
                text = "Network Status",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            DetailRow("DHT Status", profile.dhtStatus)
            DetailRow("Device Status", profile.deviceStatus)
            DetailRow("Connected Peers", profile.peerCount)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (profile.dhtStatus.contains("connected", ignoreCase = true)) {
                    "✓ Network connected - trust requests should work"
                } else {
                    "⚠ Network issue detected - this may prevent trust requests from working"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (profile.dhtStatus.contains("connected", ignoreCase = true))
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.error
            )
        }
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

@Composable
private fun EditProfileDialog(
    currentDisplayName: String,
    currentAvatarUri: String?,
    selectedAvatarUri: String?,
    avatarCleared: Boolean,
    isUpdating: Boolean,
    isProcessingAvatar: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: (String, String?) -> Unit,
    onSelectAvatar: () -> Unit,
    onClearAvatar: () -> Unit
) {
    var displayName by remember { mutableStateOf(currentDisplayName) }
    // Show avatar preview: new selection > current (unless cleared) > null
    val avatarToShow = when {
        selectedAvatarUri != null -> selectedAvatarUri
        avatarCleared -> null  // User clicked Remove - show no avatar
        else -> currentAvatarUri
    }

    AlertDialog(
        onDismissRequest = { if (!isUpdating) onDismiss() },
        title = { Text("Edit Profile") },
        text = {
            Column {
                // Avatar selection
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        AvatarImage(
                            avatarUri = avatarToShow,
                            displayName = displayName.ifEmpty { "?" },
                            size = 80.dp
                        )

                        // Edit button overlay
                        Surface(
                            modifier = Modifier
                                .size(28.dp)
                                .clickable(enabled = !isUpdating) { onSelectAvatar() },
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Change avatar",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }

                    // Show Remove button only if there's an avatar to remove (and not already cleared)
                    if (!avatarCleared && (selectedAvatarUri != null || currentAvatarUri != null)) {
                        Spacer(modifier = Modifier.width(16.dp))
                        TextButton(
                            onClick = onClearAvatar,
                            enabled = !isUpdating
                        ) {
                            Text("Remove")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Display Name") },
                    enabled = !isUpdating,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (isUpdating || isProcessingAvatar) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (isProcessingAvatar) "Processing image..." else "Updating...",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Note: Profile update may fail due to a known Jami library issue. A fallback method will be used if needed.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(displayName, selectedAvatarUri) },
                enabled = !isUpdating && displayName.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isUpdating
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ExportAccountDialog(
    isExporting: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onExport: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!isExporting) onDismiss() },
        title = { Text("Export Account") },
        text = {
            Column {
                Text(
                    text = "Create a backup of your account. You can use this to restore your account on another device.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password (optional)") },
                    placeholder = { Text("Encrypt backup with password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    enabled = !isExporting,
                    modifier = Modifier.fillMaxWidth()
                )
                if (password.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("Confirm Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        enabled = !isExporting,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
                if (isExporting) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Exporting...")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onExport(password) },
                enabled = !isExporting && (password.isEmpty() || password == confirmPassword)
            ) {
                Text("Export")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isExporting) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun LogoutOptionsDialog(
    onDismiss: () -> Unit,
    onLogoutKeepData: () -> Unit,
    onDeleteAccount: () -> Unit,
    onExportFirst: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sign Out Options") },
        text = {
            Column {
                Text(
                    text = "Choose how you want to sign out:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Logout option (keep data)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onLogoutKeepData() },
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Logout (Keep Data)", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "Your account stays on this device. You can relogin later.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Delete account option
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onDeleteAccount() },
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Delete Account",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "Permanently remove account from this device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                TextButton(onClick = onExportFirst) {
                    Text("Export backup first")
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
