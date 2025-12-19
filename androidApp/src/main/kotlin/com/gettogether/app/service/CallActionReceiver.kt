package com.gettogether.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class CallActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val callId = intent.getStringExtra(CallNotificationManager.EXTRA_CALL_ID) ?: return

        when (intent.action) {
            CallNotificationManager.ACTION_ANSWER_CALL -> {
                val contactId = intent.getStringExtra(CallNotificationManager.EXTRA_CONTACT_ID) ?: ""
                val isVideo = intent.getBooleanExtra(CallNotificationManager.EXTRA_IS_VIDEO, false)
                handleAnswerCall(context, callId, contactId, isVideo)
            }
            CallNotificationManager.ACTION_DECLINE_CALL -> {
                handleDeclineCall(context, callId)
            }
            CallNotificationManager.ACTION_END_CALL -> {
                handleEndCall(context, callId)
            }
            CallNotificationManager.ACTION_MUTE_CALL -> {
                handleMuteCall(context, callId)
            }
        }
    }

    private fun handleAnswerCall(context: Context, callId: String, contactId: String, isVideo: Boolean) {
        val serviceIntent = Intent(context, CallService::class.java).apply {
            action = CallService.ACTION_ANSWER_CALL
            putExtra(CallNotificationManager.EXTRA_CALL_ID, callId)
            putExtra(CallNotificationManager.EXTRA_CONTACT_ID, contactId)
            putExtra(CallNotificationManager.EXTRA_IS_VIDEO, isVideo)
        }
        context.startService(serviceIntent)
    }

    private fun handleDeclineCall(context: Context, callId: String) {
        val serviceIntent = Intent(context, CallService::class.java).apply {
            action = CallService.ACTION_DECLINE_CALL
            putExtra(CallNotificationManager.EXTRA_CALL_ID, callId)
        }
        context.startService(serviceIntent)
    }

    private fun handleEndCall(context: Context, callId: String) {
        val serviceIntent = Intent(context, CallService::class.java).apply {
            action = CallService.ACTION_END_CALL
            putExtra(CallNotificationManager.EXTRA_CALL_ID, callId)
        }
        context.startService(serviceIntent)
    }

    private fun handleMuteCall(context: Context, callId: String) {
        val serviceIntent = Intent(context, CallService::class.java).apply {
            action = CallService.ACTION_TOGGLE_MUTE
            putExtra(CallNotificationManager.EXTRA_CALL_ID, callId)
        }
        context.startService(serviceIntent)
    }
}
