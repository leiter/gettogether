package com.gettogether.app.data.repository

import com.gettogether.app.presentation.state.NotificationSettings
import com.gettogether.app.presentation.state.PrivacySettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.NSLog
import platform.Foundation.NSUserDefaults

/**
 * iOS implementation of SettingsRepository using NSUserDefaults.
 */
class IosSettingsRepository : SettingsRepository {

    companion object {
        private const val TAG = "IosSettingsRepository"

        // NSUserDefaults keys
        private const val KEY_NOTIFICATIONS_ENABLED = "gettogether_notifications_enabled"
        private const val KEY_MESSAGE_NOTIFICATIONS = "gettogether_message_notifications"
        private const val KEY_CALL_NOTIFICATIONS = "gettogether_call_notifications"
        private const val KEY_SOUND_ENABLED = "gettogether_sound_enabled"
        private const val KEY_VIBRATION_ENABLED = "gettogether_vibration_enabled"

        private const val KEY_SHARE_ONLINE_STATUS = "gettogether_share_online_status"
        private const val KEY_READ_RECEIPTS = "gettogether_read_receipts"
        private const val KEY_TYPING_INDICATORS = "gettogether_typing_indicators"
        private const val KEY_BLOCK_UNKNOWN_CONTACTS = "gettogether_block_unknown_contacts"

        // Key to track if defaults have been set
        private const val KEY_DEFAULTS_INITIALIZED = "gettogether_defaults_initialized"
    }

    private val defaults = NSUserDefaults.standardUserDefaults

    private val _notificationSettings = MutableStateFlow(loadNotificationSettings())
    override val notificationSettings: StateFlow<NotificationSettings> = _notificationSettings.asStateFlow()

    private val _privacySettings = MutableStateFlow(loadPrivacySettings())
    override val privacySettings: StateFlow<PrivacySettings> = _privacySettings.asStateFlow()

    init {
        // Initialize default values if not already set
        initializeDefaultsIfNeeded()
        NSLog("$TAG: Settings repository initialized")
    }

    private fun initializeDefaultsIfNeeded() {
        if (!defaults.boolForKey(KEY_DEFAULTS_INITIALIZED)) {
            NSLog("$TAG: Initializing default settings")

            // Set notification defaults
            defaults.setBool(true, KEY_NOTIFICATIONS_ENABLED)
            defaults.setBool(true, KEY_MESSAGE_NOTIFICATIONS)
            defaults.setBool(true, KEY_CALL_NOTIFICATIONS)
            defaults.setBool(true, KEY_SOUND_ENABLED)
            defaults.setBool(true, KEY_VIBRATION_ENABLED)

            // Set privacy defaults
            defaults.setBool(true, KEY_SHARE_ONLINE_STATUS)
            defaults.setBool(true, KEY_READ_RECEIPTS)
            defaults.setBool(true, KEY_TYPING_INDICATORS)
            defaults.setBool(false, KEY_BLOCK_UNKNOWN_CONTACTS)

            // Mark defaults as initialized
            defaults.setBool(true, KEY_DEFAULTS_INITIALIZED)
            defaults.synchronize()
        }
    }

    private fun loadNotificationSettings(): NotificationSettings {
        return NotificationSettings(
            enabled = defaults.boolForKey(KEY_NOTIFICATIONS_ENABLED),
            messageNotifications = defaults.boolForKey(KEY_MESSAGE_NOTIFICATIONS),
            callNotifications = defaults.boolForKey(KEY_CALL_NOTIFICATIONS),
            soundEnabled = defaults.boolForKey(KEY_SOUND_ENABLED),
            vibrationEnabled = defaults.boolForKey(KEY_VIBRATION_ENABLED)
        ).also {
            NSLog("$TAG: Loaded notification settings: enabled=${it.enabled}, messages=${it.messageNotifications}, calls=${it.callNotifications}")
        }
    }

    private fun loadPrivacySettings(): PrivacySettings {
        return PrivacySettings(
            shareOnlineStatus = defaults.boolForKey(KEY_SHARE_ONLINE_STATUS),
            readReceipts = defaults.boolForKey(KEY_READ_RECEIPTS),
            typingIndicators = defaults.boolForKey(KEY_TYPING_INDICATORS),
            blockUnknownContacts = defaults.boolForKey(KEY_BLOCK_UNKNOWN_CONTACTS)
        ).also {
            NSLog("$TAG: Loaded privacy settings: readReceipts=${it.readReceipts}, typing=${it.typingIndicators}")
        }
    }

    override suspend fun updateNotificationSettings(settings: NotificationSettings) {
        NSLog("$TAG: Updating notification settings")

        defaults.setBool(settings.enabled, KEY_NOTIFICATIONS_ENABLED)
        defaults.setBool(settings.messageNotifications, KEY_MESSAGE_NOTIFICATIONS)
        defaults.setBool(settings.callNotifications, KEY_CALL_NOTIFICATIONS)
        defaults.setBool(settings.soundEnabled, KEY_SOUND_ENABLED)
        defaults.setBool(settings.vibrationEnabled, KEY_VIBRATION_ENABLED)
        defaults.synchronize()

        _notificationSettings.value = settings

        NSLog("$TAG: Notification settings saved: enabled=${settings.enabled}, messages=${settings.messageNotifications}, calls=${settings.callNotifications}")
    }

    override suspend fun updatePrivacySettings(settings: PrivacySettings) {
        NSLog("$TAG: Updating privacy settings")

        defaults.setBool(settings.shareOnlineStatus, KEY_SHARE_ONLINE_STATUS)
        defaults.setBool(settings.readReceipts, KEY_READ_RECEIPTS)
        defaults.setBool(settings.typingIndicators, KEY_TYPING_INDICATORS)
        defaults.setBool(settings.blockUnknownContacts, KEY_BLOCK_UNKNOWN_CONTACTS)
        defaults.synchronize()

        _privacySettings.value = settings

        NSLog("$TAG: Privacy settings saved: readReceipts=${settings.readReceipts}, typing=${settings.typingIndicators}")
    }
}

actual fun createSettingsRepository(): SettingsRepository {
    return IosSettingsRepository()
}
