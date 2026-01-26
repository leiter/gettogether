package com.gettogether.app.platform

/**
 * Cross-platform notification helper using expect/actual pattern.
 * Handles message notifications, call notifications, and general alerts.
 */
expect class NotificationHelper {
    /**
     * Initialize notification channels (Android-specific, no-op on iOS)
     */
    fun initialize()

    /**
     * Show a message notification
     */
    fun showMessageNotification(
        notificationId: Int,
        contactId: String,
        contactName: String,
        message: String,
        conversationId: String,
        timestamp: Long,
        avatarPath: String? = null
    )

    /**
     * Show an incoming call notification (high priority, full-screen intent)
     */
    fun showIncomingCallNotification(
        callId: String,
        contactId: String,
        contactName: String,
        isVideo: Boolean,
        avatarPath: String? = null
    )

    /**
     * Show an ongoing call notification (foreground service)
     */
    fun showOngoingCallNotification(
        callId: String,
        contactName: String,
        duration: String,
        isMuted: Boolean,
        isVideo: Boolean
    )

    /**
     * Show a missed call notification
     */
    fun showMissedCallNotification(
        notificationId: Int,
        contactId: String,
        contactName: String,
        isVideo: Boolean,
        timestamp: Long
    )

    /**
     * Show a group notification summary
     */
    fun showGroupNotificationSummary(
        groupName: String,
        messageCount: Int,
        latestMessage: String
    )

    /**
     * Cancel a specific notification
     */
    fun cancelNotification(notificationId: Int)

    /**
     * Cancel all notifications for a conversation
     */
    fun cancelConversationNotifications(conversationId: String)

    /**
     * Cancel the incoming call notification
     */
    fun cancelIncomingCallNotification()

    /**
     * Cancel all notifications
     */
    fun cancelAllNotifications()
}

/**
 * Notification IDs and channel constants
 */
object NotificationConstants {
    // Notification IDs
    const val INCOMING_CALL_NOTIFICATION_ID = 1001
    const val ONGOING_CALL_NOTIFICATION_ID = 1002
    const val MESSAGE_NOTIFICATION_BASE_ID = 2000
    const val MISSED_CALL_NOTIFICATION_BASE_ID = 3000
    const val GROUP_SUMMARY_NOTIFICATION_ID = 4000

    // Channel IDs (Android)
    const val CHANNEL_MESSAGES = "messages_channel"
    const val CHANNEL_CALLS = "calls_channel"
    const val CHANNEL_INCOMING_CALLS = "incoming_calls_channel"
    const val CHANNEL_MISSED_CALLS = "missed_calls_channel"

    // Action constants
    const val ACTION_REPLY = "com.gettogether.app.ACTION_REPLY"
    const val ACTION_MARK_READ = "com.gettogether.app.ACTION_MARK_READ"
    const val ACTION_ANSWER_CALL = "com.gettogether.app.ACTION_ANSWER_CALL"
    const val ACTION_DECLINE_CALL = "com.gettogether.app.ACTION_DECLINE_CALL"
    const val ACTION_END_CALL = "com.gettogether.app.ACTION_END_CALL"
    const val ACTION_MUTE_CALL = "com.gettogether.app.ACTION_MUTE_CALL"
    const val ACTION_CALL_BACK = "com.gettogether.app.ACTION_CALL_BACK"

    // Extra keys
    const val EXTRA_CONVERSATION_ID = "conversation_id"
    const val EXTRA_CONTACT_ID = "contact_id"
    const val EXTRA_CONTACT_NAME = "contact_name"
    const val EXTRA_CALL_ID = "call_id"
    const val EXTRA_REPLY_TEXT = "reply_text"
    const val EXTRA_IS_VIDEO = "is_video"
}
