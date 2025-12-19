package com.gettogether.app.platform

/**
 * iOS implementation of NotificationHelper
 * Uses UserNotifications framework for local notifications
 * and APNs for push notifications
 */
actual class NotificationHelper {

    actual fun initialize() {
        // TODO: Request notification permissions
        // UNUserNotificationCenter.current().requestAuthorization
    }

    actual fun showMessageNotification(
        notificationId: Int,
        contactId: String,
        contactName: String,
        message: String,
        conversationId: String,
        timestamp: Long
    ) {
        // TODO: Implement using UNUserNotificationCenter
        // Create UNMutableNotificationContent
        // Add reply action with UNTextInputNotificationAction
    }

    actual fun showIncomingCallNotification(
        callId: String,
        contactId: String,
        contactName: String,
        isVideo: Boolean
    ) {
        // Note: On iOS, incoming calls should use CallKit
        // This is a fallback for when CallKit is not available
        // TODO: Implement using UNUserNotificationCenter with critical alert
    }

    actual fun showOngoingCallNotification(
        callId: String,
        contactName: String,
        duration: String,
        isMuted: Boolean,
        isVideo: Boolean
    ) {
        // Note: On iOS, ongoing calls are shown via CallKit
        // No separate notification needed
    }

    actual fun showMissedCallNotification(
        notificationId: Int,
        contactId: String,
        contactName: String,
        isVideo: Boolean,
        timestamp: Long
    ) {
        // TODO: Implement using UNUserNotificationCenter
    }

    actual fun showGroupNotificationSummary(
        groupName: String,
        messageCount: Int,
        latestMessage: String
    ) {
        // TODO: Implement using thread identifier for grouping
    }

    actual fun cancelNotification(notificationId: Int) {
        // TODO: UNUserNotificationCenter.current().removeDeliveredNotifications
    }

    actual fun cancelConversationNotifications(conversationId: String) {
        // TODO: Remove notifications with matching thread identifier
    }

    actual fun cancelIncomingCallNotification() {
        // TODO: Cancel call notification
    }

    actual fun cancelAllNotifications() {
        // TODO: UNUserNotificationCenter.current().removeAllDeliveredNotifications()
    }
}
