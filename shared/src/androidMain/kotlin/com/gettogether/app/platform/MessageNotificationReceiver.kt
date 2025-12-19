package com.gettogether.app.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import co.touchlab.kermit.Logger
import com.gettogether.app.data.repository.AccountRepository
import com.gettogether.app.data.repository.ConversationRepositoryImpl
import com.gettogether.app.jami.JamiBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Handles message notification actions (reply, mark as read)
 */
class MessageNotificationReceiver : BroadcastReceiver(), KoinComponent {

    private val jamiBridge: JamiBridge by inject()
    private val accountRepository: AccountRepository by inject()
    private val conversationRepository: ConversationRepositoryImpl by inject()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            NotificationConstants.ACTION_REPLY -> handleReply(context, intent)
            NotificationConstants.ACTION_MARK_READ -> handleMarkAsRead(context, intent)
        }
    }

    private fun handleReply(context: Context, intent: Intent) {
        val conversationId = intent.getStringExtra(NotificationConstants.EXTRA_CONVERSATION_ID) ?: return

        // Get the reply text from RemoteInput
        val remoteInput = RemoteInput.getResultsFromIntent(intent)
        val replyText = remoteInput?.getCharSequence(NotificationConstants.EXTRA_REPLY_TEXT)?.toString()

        if (replyText.isNullOrBlank()) {
            Logger.w("MessageNotificationReceiver") { "Empty reply text" }
            return
        }

        val accountId = accountRepository.currentAccountId.value
        if (accountId == null) {
            Logger.w("MessageNotificationReceiver") { "No active account" }
            return
        }

        Logger.d("MessageNotificationReceiver") {
            "Reply to conversation $conversationId: $replyText"
        }

        // Send the reply via JamiBridge
        scope.launch {
            try {
                jamiBridge.sendMessage(accountId, conversationId, replyText)
                Logger.d("MessageNotificationReceiver") { "Message sent successfully" }
            } catch (e: Exception) {
                Logger.e("MessageNotificationReceiver") { "Failed to send message: ${e.message}" }
            }
        }

        // Update notification to show reply was sent
        val notificationManager = NotificationManagerCompat.from(context)

        // Cancel the notification after a brief delay
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            notificationManager.activeNotifications
                .filter { it.notification.group == "messages_$conversationId" }
                .forEach { notificationManager.cancel(it.id) }
        }, 500)
    }

    private fun handleMarkAsRead(context: Context, intent: Intent) {
        val conversationId = intent.getStringExtra(NotificationConstants.EXTRA_CONVERSATION_ID) ?: return

        val accountId = accountRepository.currentAccountId.value
        if (accountId == null) {
            Logger.w("MessageNotificationReceiver") { "No active account" }
            return
        }

        Logger.d("MessageNotificationReceiver") {
            "Mark conversation $conversationId as read"
        }

        // Mark conversation as read via repository
        scope.launch {
            try {
                conversationRepository.markAsRead(accountId, conversationId)
                Logger.d("MessageNotificationReceiver") { "Conversation marked as read" }
            } catch (e: Exception) {
                Logger.e("MessageNotificationReceiver") { "Failed to mark as read: ${e.message}" }
            }
        }

        // Cancel all notifications for this conversation
        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.activeNotifications
            .filter { it.notification.group == "messages_$conversationId" }
            .forEach { notificationManager.cancel(it.id) }
    }
}
