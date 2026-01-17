package com.gettogether.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gettogether.app.data.repository.AccountRepository
import com.gettogether.app.data.repository.SettingsRepository
import com.gettogether.app.jami.JamiBridge
import com.gettogether.app.jami.RegistrationState
import com.gettogether.app.platform.ExportPathProvider
import com.gettogether.app.platform.ImageProcessor
import com.gettogether.app.platform.ImageProcessingResult
import com.gettogether.app.platform.generateExportFilename
import com.gettogether.app.presentation.state.NotificationSettings
import com.gettogether.app.presentation.state.PrivacySettings
import com.gettogether.app.presentation.state.SettingsState
import com.gettogether.app.presentation.state.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val jamiBridge: JamiBridge,
    private val accountRepository: AccountRepository,
    private val settingsRepository: SettingsRepository,
    private val imageProcessor: ImageProcessor,
    private val exportPathProvider: ExportPathProvider
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        loadUserProfile()
        observeAccountState()
        observeSettings()
    }

    private fun observeSettings() {
        viewModelScope.launch {
            settingsRepository.notificationSettings.collect { settings ->
                _state.update { it.copy(notificationSettings = settings) }
            }
        }
        viewModelScope.launch {
            settingsRepository.privacySettings.collect { settings ->
                _state.update { it.copy(privacySettings = settings) }
            }
        }
        viewModelScope.launch {
            settingsRepository.avatarPath.collect { path ->
                _state.update { it.copy(userProfile = it.userProfile.copy(avatarUri = path)) }
            }
        }
    }

    private fun observeAccountState() {
        viewModelScope.launch {
            accountRepository.accountState.collect { accountState ->
                if (accountState.isLoaded && accountState.accountId != null) {
                    _state.update {
                        it.copy(
                            userProfile = it.userProfile.copy(
                                accountId = accountState.accountId,
                                displayName = accountState.displayName,
                                username = accountState.username,
                                jamiId = accountState.jamiId,
                                registrationState = accountState.registrationState.toDisplayString()
                            )
                        )
                    }
                }
            }
        }
    }

    private fun loadUserProfile() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val accountId = accountRepository.currentAccountId.value

                if (accountId != null) {
                    val accountDetails = jamiBridge.getAccountDetails(accountId)
                    val volatileDetails = jamiBridge.getVolatileAccountDetails(accountId)

                    // Log all account details to see DHT configuration
                    println("=== ACCOUNT DETAILS ===")
                    accountDetails.forEach { (key, value) ->
                        if (key.contains("DHT", ignoreCase = true) ||
                            key.contains("bootstrap", ignoreCase = true) ||
                            key.contains("turn", ignoreCase = true) ||
                            key.contains("stun", ignoreCase = true) ||
                            key.contains("upnp", ignoreCase = true)) {
                            println("  $key = $value")
                        }
                    }
                    println("=== END ACCOUNT DETAILS ===")

                    // Log volatile details
                    println("=== VOLATILE ACCOUNT DETAILS ===")
                    volatileDetails.forEach { (key, value) ->
                        println("  $key = $value")
                    }
                    println("=== END VOLATILE DETAILS ===")

                    val profile = UserProfile(
                        accountId = accountId,
                        displayName = accountDetails["Account.displayName"] ?: "",
                        username = accountDetails["Account.username"] ?: "",
                        jamiId = accountDetails["Account.username"] ?: accountId,
                        registrationState = volatileDetails["Account.registrationStatus"] ?: "REGISTERED",
                        dhtStatus = if (volatileDetails["Account.deviceAnnounced"] == "true") "announced" else "not announced",
                        deviceStatus = volatileDetails["Account.active"] ?: "unknown",
                        peerCount = volatileDetails["Account.dhtBoundPort"] ?: "no port"
                    )

                    _state.update {
                        it.copy(
                            isLoading = false,
                            userProfile = profile
                        )
                    }
                } else {
                    // Demo profile when no account exists
                    val profile = UserProfile(
                        accountId = "",
                        displayName = "No Account",
                        username = "",
                        jamiId = "",
                        registrationState = "UNREGISTERED"
                    )

                    _state.update {
                        it.copy(
                            isLoading = false,
                            userProfile = profile
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load profile"
                    )
                }
            }
        }
    }

    fun updateDisplayName(name: String) {
        viewModelScope.launch {
            try {
                accountRepository.updateDisplayName(name)
                _state.update {
                    it.copy(userProfile = it.userProfile.copy(displayName = name))
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    /**
     * Update user profile (display name and avatar).
     * Note: This function may cause native crashes in libjami::updateProfile.
     * Error handling is in place to gracefully handle the issue.
     */
    fun updateProfile(displayName: String, avatarPath: String? = null) {
        viewModelScope.launch {
            _state.update { it.copy(isUpdatingProfile = true, profileUpdateSuccess = null, error = null) }
            try {
                accountRepository.updateProfile(displayName, avatarPath)
                _state.update {
                    it.copy(
                        userProfile = it.userProfile.copy(displayName = displayName),
                        isUpdatingProfile = false,
                        showEditProfileDialog = false,
                        profileUpdateSuccess = "Profile updated successfully"
                    )
                }
            } catch (e: Exception) {
                // Known issue: updateProfile may cause native crash
                // Fallback to updateDisplayName which uses setAccountDetails
                try {
                    accountRepository.updateDisplayName(displayName)
                    _state.update {
                        it.copy(
                            userProfile = it.userProfile.copy(displayName = displayName),
                            isUpdatingProfile = false,
                            showEditProfileDialog = false,
                            profileUpdateSuccess = "Display name updated (profile update unavailable)"
                        )
                    }
                } catch (fallbackError: Exception) {
                    _state.update {
                        it.copy(
                            isUpdatingProfile = false,
                            error = "Failed to update profile: ${fallbackError.message}. This may be a Jami library issue."
                        )
                    }
                }
            }
        }
    }

    fun showEditProfileDialog() {
        _state.update { it.copy(showEditProfileDialog = true) }
    }

    fun hideEditProfileDialog() {
        _state.update { it.copy(showEditProfileDialog = false, error = null) }
    }

    private fun RegistrationState.toDisplayString(): String {
        return when (this) {
            RegistrationState.REGISTERED -> "REGISTERED"
            RegistrationState.UNREGISTERED -> "UNREGISTERED"
            RegistrationState.TRYING -> "TRYING"
            RegistrationState.ERROR_GENERIC, RegistrationState.ERROR_AUTH,
            RegistrationState.ERROR_NETWORK, RegistrationState.ERROR_HOST,
            RegistrationState.ERROR_SERVICE_UNAVAILABLE, RegistrationState.ERROR_NEED_MIGRATION -> "ERROR"
            RegistrationState.INITIALIZING -> "INITIALIZING"
        }
    }

    fun updateNotificationSettings(settings: NotificationSettings) {
        viewModelScope.launch {
            settingsRepository.updateNotificationSettings(settings)
        }
    }

    fun toggleNotifications(enabled: Boolean) {
        val newSettings = _state.value.notificationSettings.copy(enabled = enabled)
        updateNotificationSettings(newSettings)
    }

    fun toggleMessageNotifications(enabled: Boolean) {
        val newSettings = _state.value.notificationSettings.copy(messageNotifications = enabled)
        updateNotificationSettings(newSettings)
    }

    fun toggleCallNotifications(enabled: Boolean) {
        val newSettings = _state.value.notificationSettings.copy(callNotifications = enabled)
        updateNotificationSettings(newSettings)
    }

    fun toggleSound(enabled: Boolean) {
        val newSettings = _state.value.notificationSettings.copy(soundEnabled = enabled)
        updateNotificationSettings(newSettings)
    }

    fun toggleVibration(enabled: Boolean) {
        val newSettings = _state.value.notificationSettings.copy(vibrationEnabled = enabled)
        updateNotificationSettings(newSettings)
    }

    fun updatePrivacySettings(settings: PrivacySettings) {
        viewModelScope.launch {
            settingsRepository.updatePrivacySettings(settings)
        }
    }

    fun toggleShareOnlineStatus(enabled: Boolean) {
        val newSettings = _state.value.privacySettings.copy(shareOnlineStatus = enabled)
        updatePrivacySettings(newSettings)
    }

    fun toggleReadReceipts(enabled: Boolean) {
        val newSettings = _state.value.privacySettings.copy(readReceipts = enabled)
        updatePrivacySettings(newSettings)
    }

    fun toggleTypingIndicators(enabled: Boolean) {
        val newSettings = _state.value.privacySettings.copy(typingIndicators = enabled)
        updatePrivacySettings(newSettings)
    }

    fun toggleBlockUnknownContacts(enabled: Boolean) {
        val newSettings = _state.value.privacySettings.copy(blockUnknownContacts = enabled)
        updatePrivacySettings(newSettings)
    }

    fun showSignOutDialog() {
        _state.update { it.copy(showSignOutDialog = true) }
    }

    fun hideSignOutDialog() {
        _state.update { it.copy(showSignOutDialog = false) }
    }

    fun signOut() {
        viewModelScope.launch {
            _state.update { it.copy(isSigningOut = true, showSignOutDialog = false) }
            try {
                accountRepository.deleteCurrentAccount()

                _state.update {
                    it.copy(
                        isSigningOut = false,
                        signOutComplete = true
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isSigningOut = false,
                        error = e.message ?: "Failed to sign out"
                    )
                }
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun selectAvatar(uri: String) {
        _state.update { it.copy(selectedAvatarUri = uri, error = null) }
    }

    fun clearSelectedAvatar() {
        _state.update { it.copy(selectedAvatarUri = null) }
    }

    /**
     * Process and update profile with avatar
     */
    fun updateProfileWithAvatar(displayName: String, avatarUri: String?) {
        viewModelScope.launch {
            _state.update { it.copy(isUpdatingProfile = true, isProcessingAvatar = true, error = null) }

            var processedAvatarPath: String? = null

            // Process avatar if provided
            if (avatarUri != null) {
                when (val result = imageProcessor.processImage(avatarUri)) {
                    is ImageProcessingResult.Success -> {
                        processedAvatarPath = result.filePath
                        println("Avatar processed: ${result.filePath}, size: ${result.sizeBytes} bytes")
                    }
                    is ImageProcessingResult.Error -> {
                        _state.update {
                            it.copy(
                                isUpdatingProfile = false,
                                isProcessingAvatar = false,
                                error = "Avatar processing failed: ${result.message}"
                            )
                        }
                        return@launch
                    }
                }
            }

            _state.update { it.copy(isProcessingAvatar = false) }

            // Update profile with processed avatar
            try {
                accountRepository.updateProfile(displayName, processedAvatarPath)
                // Persist avatar path so it survives app restarts
                settingsRepository.updateAvatarPath(processedAvatarPath)
                _state.update {
                    it.copy(
                        userProfile = it.userProfile.copy(
                            displayName = displayName,
                            avatarUri = processedAvatarPath
                        ),
                        isUpdatingProfile = false,
                        showEditProfileDialog = false,
                        selectedAvatarUri = null,
                        profileUpdateSuccess = "Profile updated successfully"
                    )
                }
            } catch (e: Exception) {
                // Fallback to updateDisplayName if updateProfile crashes
                try {
                    accountRepository.updateDisplayName(displayName)
                    _state.update {
                        it.copy(
                            userProfile = it.userProfile.copy(displayName = displayName),
                            isUpdatingProfile = false,
                            showEditProfileDialog = false,
                            selectedAvatarUri = null,
                            profileUpdateSuccess = "Display name updated (avatar update unavailable)"
                        )
                    }
                } catch (fallbackError: Exception) {
                    _state.update {
                        it.copy(
                            isUpdatingProfile = false,
                            error = "Failed to update profile: ${fallbackError.message}"
                        )
                    }
                }
            }
        }
    }

    // ========== Export Account ==========

    fun showExportDialog() {
        _state.update { it.copy(showExportDialog = true, exportError = null, exportSuccess = null) }
    }

    fun hideExportDialog() {
        _state.update { it.copy(showExportDialog = false, exportError = null) }
    }

    fun exportAccount(password: String) {
        viewModelScope.launch {
            _state.update { it.copy(isExporting = true, exportError = null) }
            try {
                val exportDir = exportPathProvider.getExportDirectory()
                val filename = generateExportFilename()
                val destinationPath = "$exportDir/$filename"

                val success = accountRepository.exportAccount(destinationPath, password)

                if (success) {
                    _state.update {
                        it.copy(
                            isExporting = false,
                            showExportDialog = false,
                            exportSuccess = "Account exported to: $filename"
                        )
                    }
                } else {
                    _state.update {
                        it.copy(
                            isExporting = false,
                            exportError = "Export failed. Please try again."
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isExporting = false,
                        exportError = e.message ?: "Export failed"
                    )
                }
            }
        }
    }

    fun clearExportSuccess() {
        _state.update { it.copy(exportSuccess = null) }
    }

    // ========== Logout Options ==========

    fun showLogoutOptionsDialog() {
        _state.update { it.copy(showLogoutOptionsDialog = true) }
    }

    fun hideLogoutOptionsDialog() {
        _state.update { it.copy(showLogoutOptionsDialog = false) }
    }

    /**
     * Logout while preserving account data.
     * The account can be relogged into later.
     */
    fun logoutKeepData() {
        viewModelScope.launch {
            _state.update { it.copy(isLoggingOut = true, showLogoutOptionsDialog = false) }
            try {
                accountRepository.logoutCurrentAccount()
                _state.update { it.copy(isLoggingOut = false, logoutComplete = true) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoggingOut = false,
                        error = e.message ?: "Logout failed"
                    )
                }
            }
        }
    }
}
