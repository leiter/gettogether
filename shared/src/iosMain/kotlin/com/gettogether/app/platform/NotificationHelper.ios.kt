package com.gettogether.app.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSLog
import platform.Foundation.NSNumber
import platform.Foundation.NSUUID
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationAction
import platform.UserNotifications.UNNotificationActionOptionDestructive
import platform.UserNotifications.UNNotificationActionOptionForeground
import platform.UserNotifications.UNNotificationActionOptions
import platform.UserNotifications.UNNotificationCategory
import platform.UserNotifications.UNNotificationCategoryOptionNone
import platform.UserNotifications.UNNotificationCategoryOptions
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNTextInputNotificationAction
import platform.UserNotifications.UNUserNotificationCenter

/**
 * iOS implementation of NotificationHelper
 * Uses UserNotifications framework for local notifications
 */
@OptIn(ExperimentalForeignApi::class)
actual class NotificationHelper {

    companion object {
        private const val TAG = "NotificationHelper"

        // Category identifiers
        private const val CATEGORY_MESSAGE = "MESSAGE"
        private const val CATEGORY_CALL = "CALL"
        private const val CATEGORY_MISSED_CALL = "MISSED_CALL"

        // Action identifiers
        private const val ACTION_REPLY = "REPLY_ACTION"
        private const val ACTION_MARK_READ = "MARK_READ_ACTION"
        private const val ACTION_ANSWER = "ANSWER_ACTION"
        private const val ACTION_DECLINE = "DECLINE_ACTION"
        private const val ACTION_CALL_BACK = "CALL_BACK_ACTION"
    }

    private val notificationCenter = UNUserNotificationCenter.currentNotificationCenter()

    // Track notification identifiers for cancellation
    private val conversationNotifications = mutableMapOf<String, MutableList<String>>()
    private var incomingCallNotificationId: String? = null

    actual fun initialize() {
        NSLog("$TAG: Initializing notifications")

        // Request authorization
        val options = UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge
        notificationCenter.requestAuthorizationWithOptions(options) { granted, error ->
            if (granted) {
                NSLog("$TAG: Notification authorization granted")
            } else {
                NSLog("$TAG: Notification authorization denied: ${error?.localizedDescription ?: "unknown"}")
            }
        }

        // Set up notification categories with actions
        setupNotificationCategories()
    }

    private fun setupNotificationCategories() {
        // Message category with reply and mark as read actions
        val noOptions: UNNotificationActionOptions = 0u

        val replyAction = UNTextInputNotificationAction.actionWithIdentifier(
            identifier = ACTION_REPLY,
            title = "Reply",
            options = noOptions,
            textInputButtonTitle = "Send",
            textInputPlaceholder = "Type a message..."
        )

        val markReadAction = UNNotificationAction.actionWithIdentifier(
            identifier = ACTION_MARK_READ,
            title = "Mark as Read",
            options = noOptions
        )

        val messageCategory = UNNotificationCategory.categoryWithIdentifier(
            identifier = CATEGORY_MESSAGE,
            actions = listOf(replyAction, markReadAction),
            intentIdentifiers = emptyList<String>(),
            options = UNNotificationCategoryOptionNone
        )

        // Call category with answer and decline actions
        val answerAction = UNNotificationAction.actionWithIdentifier(
            identifier = ACTION_ANSWER,
            title = "Answer",
            options = UNNotificationActionOptionForeground
        )

        val declineAction = UNNotificationAction.actionWithIdentifier(
            identifier = ACTION_DECLINE,
            title = "Decline",
            options = UNNotificationActionOptionDestructive
        )

        val callCategory = UNNotificationCategory.categoryWithIdentifier(
            identifier = CATEGORY_CALL,
            actions = listOf(answerAction, declineAction),
            intentIdentifiers = emptyList<String>(),
            options = UNNotificationCategoryOptionNone
        )

        // Missed call category with call back action
        val callBackAction = UNNotificationAction.actionWithIdentifier(
            identifier = ACTION_CALL_BACK,
            title = "Call Back",
            options = UNNotificationActionOptionForeground
        )

        val missedCallCategory = UNNotificationCategory.categoryWithIdentifier(
            identifier = CATEGORY_MISSED_CALL,
            actions = listOf(callBackAction),
            intentIdentifiers = emptyList<String>(),
            options = UNNotificationCategoryOptionNone
        )

        // Register categories
        notificationCenter.setNotificationCategories(
            setOf(messageCategory, callCategory, missedCallCategory)
        )

        NSLog("$TAG: Notification categories registered")
    }

    actual fun showMessageNotification(
        notificationId: Int,
        contactId: String,
        contactName: String,
        message: String,
        conversationId: String,
        timestamp: Long,
        avatarPath: String?
    ) {
        // Note: avatarPath is not used on iOS - avatar support requires different approach via Contacts framework
        val content = UNMutableNotificationContent()
        content.setTitle(contactName)
        content.setBody(message)
        content.setSound(UNNotificationSound.defaultSound())
        content.setThreadIdentifier(conversationId)
        content.setCategoryIdentifier(CATEGORY_MESSAGE)
        content.setBadge(NSNumber(int = 1))

        // Add user info for handling actions
        content.setUserInfo(
            mapOf(
                "notification_id" to notificationId,
                "contact_id" to contactId,
                "contact_name" to contactName,
                "conversation_id" to conversationId,
                "timestamp" to timestamp,
                "type" to "message"
            )
        )

        val identifier = "message_${notificationId}_${NSUUID().UUIDString}"
        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = identifier,
            content = content,
            trigger = null // Show immediately
        )

        notificationCenter.addNotificationRequest(request) { error ->
            if (error != null) {
                NSLog("$TAG: Failed to show message notification: ${error.localizedDescription}")
            } else {
                NSLog("$TAG: Message notification shown: $identifier")
                // Track notification for conversation
                val notifications = conversationNotifications.getOrPut(conversationId) { mutableListOf() }
                notifications.add(identifier)
            }
        }
    }

    actual fun showIncomingCallNotification(
        callId: String,
        contactId: String,
        contactName: String,
        isVideo: Boolean,
        avatarPath: String?
    ) {
        // Note: On iOS, incoming calls should primarily use CallKit
        // This is a fallback notification for VoIP pushes or when CallKit is unavailable
        // Note: avatarPath is not used on iOS - avatar support requires different approach via Contacts framework

        val content = UNMutableNotificationContent()
        val callType = if (isVideo) "Video Call" else "Voice Call"
        content.setTitle("Incoming $callType")
        content.setBody("From $contactName")
        content.setSound(UNNotificationSound.defaultCriticalSound())
        content.setCategoryIdentifier(CATEGORY_CALL)

        // Add user info for handling actions
        content.setUserInfo(
            mapOf(
                "call_id" to callId,
                "contact_id" to contactId,
                "contact_name" to contactName,
                "is_video" to isVideo,
                "type" to "incoming_call"
            )
        )

        val identifier = "incoming_call_$callId"
        incomingCallNotificationId = identifier

        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = identifier,
            content = content,
            trigger = null
        )

        notificationCenter.addNotificationRequest(request) { error ->
            if (error != null) {
                NSLog("$TAG: Failed to show incoming call notification: ${error.localizedDescription}")
            } else {
                NSLog("$TAG: Incoming call notification shown: $identifier")
            }
        }
    }

    actual fun showOngoingCallNotification(
        callId: String,
        contactName: String,
        duration: String,
        isMuted: Boolean,
        isVideo: Boolean
    ) {
        // On iOS, ongoing calls are shown via CallKit in the system UI
        // No separate notification needed - CallKit handles this
        NSLog("$TAG: Ongoing call notification not needed on iOS (CallKit handles this)")
    }

    actual fun showMissedCallNotification(
        notificationId: Int,
        contactId: String,
        contactName: String,
        isVideo: Boolean,
        timestamp: Long
    ) {
        val content = UNMutableNotificationContent()
        val callType = if (isVideo) "video call" else "call"
        content.setTitle("Missed $callType")
        content.setBody("From $contactName")
        content.setSound(UNNotificationSound.defaultSound())
        content.setCategoryIdentifier(CATEGORY_MISSED_CALL)

        // Add user info for handling actions
        content.setUserInfo(
            mapOf(
                "notification_id" to notificationId,
                "contact_id" to contactId,
                "contact_name" to contactName,
                "is_video" to isVideo,
                "timestamp" to timestamp,
                "type" to "missed_call"
            )
        )

        val identifier = "missed_call_${notificationId}_${NSUUID().UUIDString}"
        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = identifier,
            content = content,
            trigger = null
        )

        notificationCenter.addNotificationRequest(request) { error ->
            if (error != null) {
                NSLog("$TAG: Failed to show missed call notification: ${error.localizedDescription}")
            } else {
                NSLog("$TAG: Missed call notification shown: $identifier")
            }
        }
    }

    actual fun showGroupNotificationSummary(
        groupName: String,
        messageCount: Int,
        latestMessage: String
    ) {
        val content = UNMutableNotificationContent()
        content.setTitle(groupName)
        content.setSubtitle("$messageCount new messages")
        content.setBody(latestMessage)
        content.setSound(UNNotificationSound.defaultSound())
        content.setThreadIdentifier("group_$groupName")
        content.setCategoryIdentifier(CATEGORY_MESSAGE)
        content.setBadge(NSNumber(int = messageCount))

        // Add user info
        content.setUserInfo(
            mapOf(
                "group_name" to groupName,
                "message_count" to messageCount,
                "type" to "group_summary"
            )
        )

        val identifier = "group_summary_${groupName}_${NSUUID().UUIDString}"
        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = identifier,
            content = content,
            trigger = null
        )

        notificationCenter.addNotificationRequest(request) { error ->
            if (error != null) {
                NSLog("$TAG: Failed to show group notification: ${error.localizedDescription}")
            } else {
                NSLog("$TAG: Group notification shown: $identifier")
            }
        }
    }

    actual fun cancelNotification(notificationId: Int) {
        // Find and remove notifications with this ID
        notificationCenter.getDeliveredNotificationsWithCompletionHandler { notifications ->
            val toRemove = notifications
                ?.mapNotNull { it as? platform.UserNotifications.UNNotification }
                ?.filter { notification ->
                    val userInfo = notification.request.content.userInfo
                    (userInfo["notification_id"] as? Int) == notificationId
                }
                ?.map { it.request.identifier }
                ?: emptyList()

            if (toRemove.isNotEmpty()) {
                notificationCenter.removeDeliveredNotificationsWithIdentifiers(toRemove)
                NSLog("$TAG: Cancelled notification(s) for ID: $notificationId")
            }
        }
    }

    actual fun cancelConversationNotifications(conversationId: String) {
        // Remove all tracked notifications for this conversation
        val notifications = conversationNotifications.remove(conversationId)
        if (notifications != null && notifications.isNotEmpty()) {
            notificationCenter.removeDeliveredNotificationsWithIdentifiers(notifications)
            NSLog("$TAG: Cancelled ${notifications.size} notifications for conversation: $conversationId")
        }

        // Also remove by thread identifier
        notificationCenter.getDeliveredNotificationsWithCompletionHandler { deliveredNotifications ->
            val toRemove = deliveredNotifications
                ?.mapNotNull { it as? platform.UserNotifications.UNNotification }
                ?.filter { it.request.content.threadIdentifier == conversationId }
                ?.map { it.request.identifier }
                ?: emptyList()

            if (toRemove.isNotEmpty()) {
                notificationCenter.removeDeliveredNotificationsWithIdentifiers(toRemove)
                NSLog("$TAG: Cancelled ${toRemove.size} additional notifications for thread: $conversationId")
            }
        }
    }

    actual fun cancelIncomingCallNotification() {
        incomingCallNotificationId?.let { identifier ->
            notificationCenter.removeDeliveredNotificationsWithIdentifiers(listOf(identifier))
            notificationCenter.removePendingNotificationRequestsWithIdentifiers(listOf(identifier))
            NSLog("$TAG: Cancelled incoming call notification: $identifier")
            incomingCallNotificationId = null
        }
    }

    actual fun cancelAllNotifications() {
        notificationCenter.removeAllDeliveredNotifications()
        notificationCenter.removeAllPendingNotificationRequests()
        conversationNotifications.clear()
        incomingCallNotificationId = null
        NSLog("$TAG: Cancelled all notifications")
    }
}
