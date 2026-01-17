package com.gettogether.app.platform

import android.content.Context
import android.content.Intent
import android.os.Build

actual class CallServiceBridge(private val context: Context) {

    actual fun startOutgoingCall(contactId: String, contactName: String, isVideo: Boolean) {
        val intent = Intent(context, Class.forName("com.gettogether.app.service.CallService")).apply {
            action = "com.gettogether.app.START_OUTGOING_CALL"
            putExtra("contact_id", contactId)
            putExtra("contact_name", contactName)
            putExtra("is_video", isVideo)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    actual fun startIncomingCall(callId: String, contactId: String, contactName: String, isVideo: Boolean) {
        val intent = Intent(context, Class.forName("com.gettogether.app.service.CallService")).apply {
            action = "com.gettogether.app.START_INCOMING_CALL"
            putExtra("call_id", callId)
            putExtra("contact_id", contactId)
            putExtra("contact_name", contactName)
            putExtra("is_video", isVideo)
        }
        // Use foreground service for incoming calls (Android O+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    actual fun answerCall() {
        val intent = Intent(context, Class.forName("com.gettogether.app.service.CallService")).apply {
            action = "com.gettogether.app.ANSWER_CALL"
        }
        context.startService(intent)
    }

    actual fun declineCall() {
        val intent = Intent(context, Class.forName("com.gettogether.app.service.CallService")).apply {
            action = "com.gettogether.app.DECLINE_CALL"
        }
        context.startService(intent)
    }

    actual fun endCall() {
        val intent = Intent(context, Class.forName("com.gettogether.app.service.CallService")).apply {
            action = "com.gettogether.app.END_CALL"
        }
        context.startService(intent)
    }

    actual fun toggleMute() {
        val intent = Intent(context, Class.forName("com.gettogether.app.service.CallService")).apply {
            action = "com.gettogether.app.TOGGLE_MUTE"
        }
        context.startService(intent)
    }

    actual fun toggleSpeaker() {
        val intent = Intent(context, Class.forName("com.gettogether.app.service.CallService")).apply {
            action = "com.gettogether.app.TOGGLE_SPEAKER"
        }
        context.startService(intent)
    }

    actual fun toggleVideo() {
        val intent = Intent(context, Class.forName("com.gettogether.app.service.CallService")).apply {
            action = "com.gettogether.app.TOGGLE_VIDEO"
        }
        context.startService(intent)
    }
}
