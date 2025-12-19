package com.gettogether.app.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import co.touchlab.kermit.Logger

/**
 * Handles call notification actions (answer, decline, end call, mute, call back)
 */
class CallNotificationReceiver : BroadcastReceiver() {

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

        Logger.d("CallNotificationReceiver") {
            "Answer call: $callId from $contactId (video: $isVideo)"
        }

        // Cancel incoming call notification
        NotificationManagerCompat.from(context)
            .cancel(NotificationConstants.INCOMING_CALL_NOTIFICATION_ID)

        // Launch the app to the call screen
        val launchIntent = Intent(context, Class.forName("com.gettogether.app.MainActivity")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            action = "ANSWER_CALL"
            putExtra(NotificationConstants.EXTRA_CALL_ID, callId)
            putExtra(NotificationConstants.EXTRA_CONTACT_ID, contactId)
            putExtra(NotificationConstants.EXTRA_IS_VIDEO, isVideo)
        }
        context.startActivity(launchIntent)

        // TODO: Also signal the CallService to answer
        val serviceIntent = Intent(context, Class.forName("com.gettogether.app.service.CallService")).apply {
            action = "com.gettogether.app.ANSWER_CALL"
        }
        try {
            context.startService(serviceIntent)
        } catch (e: Exception) {
            Logger.e("CallNotificationReceiver") { "Failed to start CallService: ${e.message}" }
        }
    }

    private fun handleDeclineCall(context: Context, intent: Intent) {
        val callId = intent.getStringExtra(NotificationConstants.EXTRA_CALL_ID) ?: return

        Logger.d("CallNotificationReceiver") { "Decline call: $callId" }

        // Cancel incoming call notification
        NotificationManagerCompat.from(context)
            .cancel(NotificationConstants.INCOMING_CALL_NOTIFICATION_ID)

        // Signal CallService to decline
        val serviceIntent = Intent(context, Class.forName("com.gettogether.app.service.CallService")).apply {
            action = "com.gettogether.app.DECLINE_CALL"
        }
        try {
            context.startService(serviceIntent)
        } catch (e: Exception) {
            Logger.e("CallNotificationReceiver") { "Failed to start CallService: ${e.message}" }
        }
    }

    private fun handleEndCall(context: Context, intent: Intent) {
        val callId = intent.getStringExtra(NotificationConstants.EXTRA_CALL_ID) ?: return

        Logger.d("CallNotificationReceiver") { "End call: $callId" }

        // Signal CallService to end call
        val serviceIntent = Intent(context, Class.forName("com.gettogether.app.service.CallService")).apply {
            action = "com.gettogether.app.END_CALL"
        }
        try {
            context.startService(serviceIntent)
        } catch (e: Exception) {
            Logger.e("CallNotificationReceiver") { "Failed to start CallService: ${e.message}" }
        }
    }

    private fun handleMuteCall(context: Context, intent: Intent) {
        val callId = intent.getStringExtra(NotificationConstants.EXTRA_CALL_ID) ?: return

        Logger.d("CallNotificationReceiver") { "Toggle mute for call: $callId" }

        // Signal CallService to toggle mute
        val serviceIntent = Intent(context, Class.forName("com.gettogether.app.service.CallService")).apply {
            action = "com.gettogether.app.TOGGLE_MUTE"
        }
        try {
            context.startService(serviceIntent)
        } catch (e: Exception) {
            Logger.e("CallNotificationReceiver") { "Failed to start CallService: ${e.message}" }
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
