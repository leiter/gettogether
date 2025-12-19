package com.gettogether.app.data.repository

import com.gettogether.app.presentation.state.NotificationSettings
import com.gettogether.app.presentation.state.PrivacySettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Repository for persisting user settings.
 */
interface SettingsRepository {
    val notificationSettings: StateFlow<NotificationSettings>
    val privacySettings: StateFlow<PrivacySettings>

    suspend fun updateNotificationSettings(settings: NotificationSettings)
    suspend fun updatePrivacySettings(settings: PrivacySettings)
}

/**
 * Platform-specific factory for creating SettingsRepository.
 */
expect fun createSettingsRepository(): SettingsRepository
