package com.gettogether.app.data.repository

import com.gettogether.app.presentation.state.NotificationSettings
import com.gettogether.app.presentation.state.PrivacySettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * iOS implementation of SettingsRepository.
 * TODO: Implement using NSUserDefaults when iOS development starts.
 */
class IosSettingsRepository : SettingsRepository {

    private val _notificationSettings = MutableStateFlow(NotificationSettings())
    override val notificationSettings: StateFlow<NotificationSettings> = _notificationSettings.asStateFlow()

    private val _privacySettings = MutableStateFlow(PrivacySettings())
    override val privacySettings: StateFlow<PrivacySettings> = _privacySettings.asStateFlow()

    override suspend fun updateNotificationSettings(settings: NotificationSettings) {
        // TODO: Persist to NSUserDefaults
        _notificationSettings.value = settings
    }

    override suspend fun updatePrivacySettings(settings: PrivacySettings) {
        // TODO: Persist to NSUserDefaults
        _privacySettings.value = settings
    }
}

actual fun createSettingsRepository(): SettingsRepository {
    return IosSettingsRepository()
}
