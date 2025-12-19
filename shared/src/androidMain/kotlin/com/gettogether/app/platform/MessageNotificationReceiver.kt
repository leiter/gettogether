package com.gettogether.app.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import co.touchlab.kermit.Logger

/**
 * Handles message notification actions (reply, mark as read)
 */
class MessageNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            NotificationConstants.ACTION_REPLY -> handleReply(context, intent)
            NotificationConstants.ACTION_MARK_READ -> handleMarkAsRead(context, intent)
        }
    }

    private fun handleReply(context: Context, intent: Intent) {
        val conversationId = intent.getStringExtra(NotificationConstants.EXTRA_CONVERSATION_ID) ?: return
        val contactId = intent.getStringExtra(NotificationConstants.EXTRA_CONTACT_ID) ?: return

        // Get the reply text from RemoteInput
        val remoteInput = RemoteInput.getResultsFromIntent(intent)
        val replyText = remoteInput?.getCharSequence(NotificationConstants.EXTRA_REPLY_TEXT)?.toString()

        if (replyText.isNullOrBlank()) {
            Logger.w("MessageNotificationReceiver") { "Empty reply text" }
            return
        }

        Logger.d("MessageNotificationReceiver") {
            "Reply to conversation $conversationId: $replyText"
        }

        // TODO: Send the reply via JamiBridge
        // For now, we'll just log it and update the notification

        // Update notification to show "Sending..." then dismiss
        val notificationManager = NotificationManagerCompat.from(context)

        // Cancel the notification after a brief delay to show the reply was sent
        // In a real implementation, you'd update the notification to show "Sent" status
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            // Cancel all notifications for this conversation
            notificationManager.activeNotifications
                .filter { it.notification.group == "messages_$conversationId" }
                .forEach { notificationManager.cancel(it.id) }
        }, 500)
    }

    private fun handleMarkAsRead(context: Context, intent: Intent) {
        val conversationId = intent.getStringExtra(NotificationConstants.EXTRA_CONVERSATION_ID) ?: return

        Logger.d("MessageNotificationReceiver") {
            "Mark conversation $conversationId as read"
        }

        // TODO: Mark conversation as read via repository

        // Cancel all notifications for this conversation
        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.activeNotifications
            .filter { it.notification.group == "messages_$conversationId" }
            .forEach { notificationManager.cancel(it.id) }
    }
}
