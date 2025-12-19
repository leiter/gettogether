package com.gettogether.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gettogether.app.data.repository.AccountRepository
import com.gettogether.app.jami.JamiBridge
import com.gettogether.app.jami.RegistrationState
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
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        loadUserProfile()
        observeAccountState()
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

                    val profile = UserProfile(
                        accountId = accountId,
                        displayName = accountDetails["Account.displayName"] ?: "",
                        username = accountDetails["Account.username"] ?: "",
                        jamiId = accountDetails["Account.username"] ?: accountId,
                        registrationState = volatileDetails["Account.registrationStatus"] ?: "UNREGISTERED"
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
}
