package com.gettogether.app.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import co.touchlab.kermit.Logger
import com.gettogether.app.data.repository.AccountRepository
import com.gettogether.app.jami.JamiBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Handles call notification actions (answer, decline, end call, mute, call back)
 */
class CallNotificationReceiver : BroadcastReceiver(), KoinComponent {

    private val jamiBridge: JamiBridge by inject()
    private val accountRepository: AccountRepository by inject()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        Logger.d("CallNotificationReceiver") { "Received action: ${intent.action}" }

        when (intent.action) {
            NotificationConstants.ACTION_ANSWER_CALL -> handleAnswerCall(context, intent)
            NotificationConstants.ACTION_DECLINE_CALL -> handleDeclineCall(context, intent)
            NotificationConstants.ACTION_END_CALL -> handleEndCall(context, intent)
            NotificationConstants.ACTION_MUTE_CALL -> handleMuteCall(context, intent)
            NotificationConstants.ACTION_CALL_BACK -> handleCallBack(context, intent)
        }
    }

    private fun handleAnswerCall(context: Context, intent: Intent) {
        val callId = intent.getStringExtra(NotificationConstants.EXTRA_CALL_ID) ?: return
        val contactId = intent.getStringExtra(NotificationConstants.EXTRA_CONTACT_ID) ?: return
        val isVideo = intent.getBooleanExtra(NotificationConstants.EXTRA_IS_VIDEO, false)

        val accountId = accountRepository.currentAccountId.value
        if (accountId == null) {
            Logger.w("CallNotificationReceiver") { "No active account" }
            return
        }

        Logger.d("CallNotificationReceiver") {
            "Answer call: $callId from $contactId (video: $isVideo)"
        }

        // Cancel incoming call notification
        NotificationManagerCompat.from(context)
            .cancel(NotificationConstants.INCOMING_CALL_NOTIFICATION_ID)

        // Accept call via JamiBridge
        scope.launch {
            try {
                jamiBridge.acceptCall(accountId, callId, isVideo)
                Logger.d("CallNotificationReceiver") { "Call accepted via JamiBridge" }
            } catch (e: Exception) {
                Logger.e("CallNotificationReceiver") { "Failed to accept call: ${e.message}" }
            }
        }

        // Launch the app to the call screen
        val launchIntent = Intent(context, Class.forName("com.gettogether.app.MainActivity")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            action = "ANSWER_CALL"
            putExtra(NotificationConstants.EXTRA_CALL_ID, callId)
            putExtra(NotificationConstants.EXTRA_CONTACT_ID, contactId)
            putExtra(NotificationConstants.EXTRA_IS_VIDEO, isVideo)
        }
        context.startActivity(launchIntent)
    }

    private fun handleDeclineCall(context: Context, intent: Intent) {
        val callId = intent.getStringExtra(NotificationConstants.EXTRA_CALL_ID) ?: return

        val accountId = accountRepository.currentAccountId.value
        if (accountId == null) {
            Logger.w("CallNotificationReceiver") { "No active account" }
            return
        }

        Logger.d("CallNotificationReceiver") { "Decline call: $callId" }

        // Cancel incoming call notification
        NotificationManagerCompat.from(context)
            .cancel(NotificationConstants.INCOMING_CALL_NOTIFICATION_ID)

        // Decline call via JamiBridge
        scope.launch {
            try {
                jamiBridge.refuseCall(accountId, callId)
                Logger.d("CallNotificationReceiver") { "Call declined via JamiBridge" }
            } catch (e: Exception) {
                Logger.e("CallNotificationReceiver") { "Failed to decline call: ${e.message}" }
            }
        }
    }

    private fun handleEndCall(context: Context, intent: Intent) {
        val callId = intent.getStringExtra(NotificationConstants.EXTRA_CALL_ID) ?: return

        val accountId = accountRepository.currentAccountId.value
        if (accountId == null) {
            Logger.w("CallNotificationReceiver") { "No active account" }
            return
        }

        Logger.d("CallNotificationReceiver") { "End call: $callId" }

        // End call via JamiBridge
        scope.launch {
            try {
                jamiBridge.hangUp(accountId, callId)
                Logger.d("CallNotificationReceiver") { "Call ended via JamiBridge" }
            } catch (e: Exception) {
                Logger.e("CallNotificationReceiver") { "Failed to end call: ${e.message}" }
            }
        }
    }

    private fun handleMuteCall(context: Context, intent: Intent) {
        val callId = intent.getStringExtra(NotificationConstants.EXTRA_CALL_ID) ?: return

        val accountId = accountRepository.currentAccountId.value
        if (accountId == null) {
            Logger.w("CallNotificationReceiver") { "No active account" }
            return
        }

        Logger.d("CallNotificationReceiver") { "Toggle mute for call: $callId" }

        // Toggle mute via JamiBridge
        scope.launch {
            try {
                jamiBridge.muteAudio(accountId, callId, true) // TODO: Track mute state to toggle
                Logger.d("CallNotificationReceiver") { "Call muted via JamiBridge" }
            } catch (e: Exception) {
                Logger.e("CallNotificationReceiver") { "Failed to mute call: ${e.message}" }
            }
        }
    }

    private fun handleCallBack(context: Context, intent: Intent) {
        val contactId = intent.getStringExtra(NotificationConstants.EXTRA_CONTACT_ID) ?: return
        val isVideo = intent.getBooleanExtra(NotificationConstants.EXTRA_IS_VIDEO, false)

        Logger.d("CallNotificationReceiver") {
            "Call back contact: $contactId (video: $isVideo)"
        }

        // Launch the app to initiate a call
        val launchIntent = Intent(context, Class.forName("com.gettogether.app.MainActivity")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            action = "START_CALL"
            putExtra(NotificationConstants.EXTRA_CONTACT_ID, contactId)
            putExtra(NotificationConstants.EXTRA_IS_VIDEO, isVideo)
        }
        context.startActivity(launchIntent)
    }
}
