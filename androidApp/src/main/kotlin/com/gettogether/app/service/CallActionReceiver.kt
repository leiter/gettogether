package com.gettogether.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.gettogether.app.data.repository.AccountRepository
import com.gettogether.app.jami.JamiBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class CallActionReceiver : BroadcastReceiver(), KoinComponent {

    private val jamiBridge: JamiBridge by inject()
    private val accountRepository: AccountRepository by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val callId = intent.getStringExtra(CallNotificationManager.EXTRA_CALL_ID) ?: return

        when (intent.action) {
            CallNotificationManager.ACTION_ANSWER_CALL -> {
                val contactId = intent.getStringExtra(CallNotificationManager.EXTRA_CONTACT_ID) ?: ""
                val contactName = intent.getStringExtra(CallNotificationManager.EXTRA_CONTACT_NAME) ?: "Unknown"
                val isVideo = intent.getBooleanExtra(CallNotificationManager.EXTRA_IS_VIDEO, false)
                handleAnswerCall(context, callId, contactId, contactName, isVideo)
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

    private fun handleAnswerCall(context: Context, callId: String, contactId: String, contactName: String, isVideo: Boolean) {
        Log.i("CallActionReceiver", "handleAnswerCall: callId=$callId, contactId=$contactId, contactName=$contactName, isVideo=$isVideo")
        val serviceIntent = Intent(context, CallService::class.java).apply {
            action = CallService.ACTION_ANSWER_CALL
            putExtra(CallNotificationManager.EXTRA_CALL_ID, callId)
            putExtra(CallNotificationManager.EXTRA_CONTACT_ID, contactId)
            putExtra(CallNotificationManager.EXTRA_CONTACT_NAME, contactName)
            putExtra(CallNotificationManager.EXTRA_IS_VIDEO, isVideo)
        }
        // Use startForegroundService since answering a call starts a foreground service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }

    private fun handleDeclineCall(context: Context, callId: String) {
        val serviceIntent = Intent(context, CallService::class.java).apply {
            action = CallService.ACTION_DECLINE_CALL
            putExtra(CallNotificationManager.EXTRA_CALL_ID, callId)
        }
        context.startService(serviceIntent)
    }

    private fun handleEndCall(context: Context, callId: String) {
        Log.i("CallActionReceiver", "handleEndCall: callId=$callId")

        // First, hang up the call with the daemon
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val accountId = accountRepository.currentAccountId.value
                if (accountId != null) {
                    Log.i("CallActionReceiver", "Calling jamiBridge.hangUp()")
                    jamiBridge.hangUp(accountId, callId)
                    Log.i("CallActionReceiver", "hangUp completed")
                } else {
                    Log.w("CallActionReceiver", "No account ID available")
                }
            } catch (e: Exception) {
                Log.e("CallActionReceiver", "Failed to hang up call: ${e.message}")
            }
        }

        // Also stop the CallService
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
