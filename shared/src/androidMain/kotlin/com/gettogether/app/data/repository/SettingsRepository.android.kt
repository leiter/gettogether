package com.gettogether.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.gettogether.app.presentation.state.NotificationSettings
import com.gettogether.app.presentation.state.PrivacySettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Android implementation of SettingsRepository using SharedPreferences.
 */
class AndroidSettingsRepository(context: Context) : SettingsRepository {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    private val _notificationSettings = MutableStateFlow(loadNotificationSettings())
    override val notificationSettings: StateFlow<NotificationSettings> = _notificationSettings.asStateFlow()

    private val _privacySettings = MutableStateFlow(loadPrivacySettings())
    override val privacySettings: StateFlow<PrivacySettings> = _privacySettings.asStateFlow()

    private fun loadNotificationSettings(): NotificationSettings {
        return NotificationSettings(
            enabled = prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true),
            messageNotifications = prefs.getBoolean(KEY_MESSAGE_NOTIFICATIONS, true),
            callNotifications = prefs.getBoolean(KEY_CALL_NOTIFICATIONS, true),
            soundEnabled = prefs.getBoolean(KEY_SOUND_ENABLED, true),
            vibrationEnabled = prefs.getBoolean(KEY_VIBRATION_ENABLED, true)
        )
    }

    private fun loadPrivacySettings(): PrivacySettings {
        return PrivacySettings(
            shareOnlineStatus = prefs.getBoolean(KEY_SHARE_ONLINE_STATUS, true),
            readReceipts = prefs.getBoolean(KEY_READ_RECEIPTS, true),
            typingIndicators = prefs.getBoolean(KEY_TYPING_INDICATORS, true),
            blockUnknownContacts = prefs.getBoolean(KEY_BLOCK_UNKNOWN_CONTACTS, false)
        )
    }

    override suspend fun updateNotificationSettings(settings: NotificationSettings) {
        prefs.edit().apply {
            putBoolean(KEY_NOTIFICATIONS_ENABLED, settings.enabled)
            putBoolean(KEY_MESSAGE_NOTIFICATIONS, settings.messageNotifications)
            putBoolean(KEY_CALL_NOTIFICATIONS, settings.callNotifications)
            putBoolean(KEY_SOUND_ENABLED, settings.soundEnabled)
            putBoolean(KEY_VIBRATION_ENABLED, settings.vibrationEnabled)
            apply()
        }
        _notificationSettings.value = settings
    }

    override suspend fun updatePrivacySettings(settings: PrivacySettings) {
        prefs.edit().apply {
            putBoolean(KEY_SHARE_ONLINE_STATUS, settings.shareOnlineStatus)
            putBoolean(KEY_READ_RECEIPTS, settings.readReceipts)
            putBoolean(KEY_TYPING_INDICATORS, settings.typingIndicators)
            putBoolean(KEY_BLOCK_UNKNOWN_CONTACTS, settings.blockUnknownContacts)
            apply()
        }
        _privacySettings.value = settings
    }

    companion object {
        private const val PREFS_NAME = "gettogether_settings"
        private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
        private const val KEY_MESSAGE_NOTIFICATIONS = "message_notifications"
        private const val KEY_CALL_NOTIFICATIONS = "call_notifications"
        private const val KEY_SOUND_ENABLED = "sound_enabled"
        private const val KEY_VIBRATION_ENABLED = "vibration_enabled"
        private const val KEY_SHARE_ONLINE_STATUS = "share_online_status"
        private const val KEY_READ_RECEIPTS = "read_receipts"
        private const val KEY_TYPING_INDICATORS = "typing_indicators"
        private const val KEY_BLOCK_UNKNOWN_CONTACTS = "block_unknown_contacts"
    }
}

// Platform-specific instance holder for expect/actual
private var settingsRepositoryInstance: SettingsRepository? = null

/**
 * Sets the settings repository instance (called from Koin module).
 */
fun setSettingsRepository(repository: SettingsRepository) {
    settingsRepositoryInstance = repository
}

actual fun createSettingsRepository(): SettingsRepository {
    return settingsRepositoryInstance
        ?: throw IllegalStateException("SettingsRepository not initialized. Call setSettingsRepository() first.")
}
