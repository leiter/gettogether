package com.gettogether.app.presentation.state

data class SettingsState(
    val userProfile: UserProfile = UserProfile(),
    val notificationSettings: NotificationSettings = NotificationSettings(),
    val privacySettings: PrivacySettings = PrivacySettings(),
    val isLoading: Boolean = false,
    val isSigningOut: Boolean = false,
    val signOutComplete: Boolean = false,
    val error: String? = null,
    val showSignOutDialog: Boolean = false,
    val showEditProfileDialog: Boolean = false,
    val isUpdatingProfile: Boolean = false,
    val profileUpdateSuccess: String? = null,
    val selectedAvatarUri: String? = null,
    val isProcessingAvatar: Boolean = false,
    // Export account
    val showExportDialog: Boolean = false,
    val isExporting: Boolean = false,
    val exportSuccess: String? = null,
    val exportError: String? = null,
    // Logout options
    val showLogoutOptionsDialog: Boolean = false,
    val isLoggingOut: Boolean = false,
    val logoutComplete: Boolean = false
)

data class UserProfile(
    val accountId: String = "",
    val displayName: String = "",
    val username: String = "",
    val jamiId: String = "",
    val registrationState: String = "",
    val dhtStatus: String = "",
    val deviceStatus: String = "",
    val peerCount: String = "0",
    val avatarUri: String? = null
)

data class NotificationSettings(
    val enabled: Boolean = true,
    val messageNotifications: Boolean = true,
    val callNotifications: Boolean = true,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true
)

data class PrivacySettings(
    val shareOnlineStatus: Boolean = true,
    val readReceipts: Boolean = true,
    val typingIndicators: Boolean = true,
    val blockUnknownContacts: Boolean = false
)
