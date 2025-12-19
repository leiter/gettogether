package com.gettogether.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gettogether.app.jami.JamiBridge
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
    private val jamiBridge: JamiBridge
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        loadUserProfile()
    }

    private fun loadUserProfile() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                // TODO: Load actual account from JamiBridge
                // val accountIds = jamiBridge.getAccountIds()
                // val accountDetails = if (accountIds.isNotEmpty()) {
                //     jamiBridge.getAccountDetails(accountIds.first())
                // } else emptyMap()

                // Demo profile
                val profile = UserProfile(
                    accountId = "demo_account_123",
                    displayName = "TestUser",
                    username = "testuser",
                    jamiId = "ring:abc123def456",
                    registrationState = "REGISTERED"
                )

                _state.update {
                    it.copy(
                        isLoading = false,
                        userProfile = profile
                    )
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
                // TODO: Update via JamiBridge
                // jamiBridge.setAccountDetails(state.value.userProfile.accountId, mapOf("Account.displayName" to name))

                _state.update {
                    it.copy(userProfile = it.userProfile.copy(displayName = name))
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun updateNotificationSettings(settings: NotificationSettings) {
        _state.update { it.copy(notificationSettings = settings) }
        // TODO: Persist notification settings
    }

    fun toggleNotifications(enabled: Boolean) {
        _state.update {
            it.copy(notificationSettings = it.notificationSettings.copy(enabled = enabled))
        }
    }

    fun toggleMessageNotifications(enabled: Boolean) {
        _state.update {
            it.copy(notificationSettings = it.notificationSettings.copy(messageNotifications = enabled))
        }
    }

    fun toggleCallNotifications(enabled: Boolean) {
        _state.update {
            it.copy(notificationSettings = it.notificationSettings.copy(callNotifications = enabled))
        }
    }

    fun toggleSound(enabled: Boolean) {
        _state.update {
            it.copy(notificationSettings = it.notificationSettings.copy(soundEnabled = enabled))
        }
    }

    fun toggleVibration(enabled: Boolean) {
        _state.update {
            it.copy(notificationSettings = it.notificationSettings.copy(vibrationEnabled = enabled))
        }
    }

    fun updatePrivacySettings(settings: PrivacySettings) {
        _state.update { it.copy(privacySettings = settings) }
        // TODO: Persist privacy settings
    }

    fun toggleShareOnlineStatus(enabled: Boolean) {
        _state.update {
            it.copy(privacySettings = it.privacySettings.copy(shareOnlineStatus = enabled))
        }
    }

    fun toggleReadReceipts(enabled: Boolean) {
        _state.update {
            it.copy(privacySettings = it.privacySettings.copy(readReceipts = enabled))
        }
    }

    fun toggleTypingIndicators(enabled: Boolean) {
        _state.update {
            it.copy(privacySettings = it.privacySettings.copy(typingIndicators = enabled))
        }
    }

    fun toggleBlockUnknownContacts(enabled: Boolean) {
        _state.update {
            it.copy(privacySettings = it.privacySettings.copy(blockUnknownContacts = enabled))
        }
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
                // TODO: Delete account via JamiBridge
                // jamiBridge.deleteAccount(state.value.userProfile.accountId)

                // Simulate sign out delay
                kotlinx.coroutines.delay(500)

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
}
