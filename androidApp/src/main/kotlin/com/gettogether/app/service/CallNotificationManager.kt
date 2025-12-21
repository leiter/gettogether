package com.gettogether.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.gettogether.app.MainActivity
import com.gettogether.app.R

/**
 * @deprecated Use NotificationHelper from shared/platform instead for consistency across the app.
 * This class will be removed in a future version.
 */
@Deprecated(
    message = "Use NotificationHelper instead for consistency across the app",
    replaceWith = ReplaceWith("NotificationHelper", "com.gettogether.app.platform.NotificationHelper"),
    level = DeprecationLevel.WARNING
)
class CallNotificationManager(private val context: Context) {

    companion object {
        const val CHANNEL_ID_CALL = "call_channel"
        const val CHANNEL_ID_INCOMING = "incoming_call_channel"
        const val NOTIFICATION_ID_ONGOING_CALL = 1001
        const val NOTIFICATION_ID_INCOMING_CALL = 1002

        const val ACTION_ANSWER_CALL = "com.gettogether.app.ACTION_ANSWER_CALL"
        const val ACTION_DECLINE_CALL = "com.gettogether.app.ACTION_DECLINE_CALL"
        const val ACTION_END_CALL = "com.gettogether.app.ACTION_END_CALL"
        const val ACTION_MUTE_CALL = "com.gettogether.app.ACTION_MUTE_CALL"

        const val EXTRA_CALL_ID = "call_id"
        const val EXTRA_CONTACT_ID = "contact_id"
        const val EXTRA_CONTACT_NAME = "contact_name"
        const val EXTRA_IS_VIDEO = "is_video"
    }

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Ongoing call channel
            val callChannel = NotificationChannel(
                CHANNEL_ID_CALL,
                "Ongoing Calls",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifications for active calls"
                setShowBadge(false)
            }

            // Incoming call channel (high priority)
            val incomingChannel = NotificationChannel(
                CHANNEL_ID_INCOMING,
                "Incoming Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for incoming calls"
                setShowBadge(true)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 500, 1000)
            }

            notificationManager.createNotificationChannels(listOf(callChannel, incomingChannel))
        }
    }

    fun createOngoingCallNotification(
        contactName: String,
        callDuration: String,
        isMuted: Boolean,
        isVideo: Boolean,
        callId: String
    ): Notification {
        val contentIntent = createContentIntent()

        val endCallIntent = Intent(context, CallActionReceiver::class.java).apply {
            action = ACTION_END_CALL
            putExtra(EXTRA_CALL_ID, callId)
        }
        val endCallPendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            endCallIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val muteIntent = Intent(context, CallActionReceiver::class.java).apply {
            action = ACTION_MUTE_CALL
            putExtra(EXTRA_CALL_ID, callId)
        }
        val mutePendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            muteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val callType = if (isVideo) "Video call" else "Voice call"
        val muteText = if (isMuted) "Unmute" else "Mute"

        return NotificationCompat.Builder(context, CHANNEL_ID_CALL)
            .setContentTitle(contactName)
            .setContentText("$callType - $callDuration")
            .setSmallIcon(R.drawable.ic_call_notification)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentIntent)
            .addAction(0, muteText, mutePendingIntent)
            .addAction(0, "End", endCallPendingIntent)
            .setUsesChronometer(true)
            .setWhen(System.currentTimeMillis())
            .build()
    }

    fun createIncomingCallNotification(
        contactName: String,
        isVideo: Boolean,
        callId: String,
        contactId: String
    ): Notification {
        val fullScreenIntent = createFullScreenIntent(callId, contactId, contactName, isVideo)
        val contentIntent = createContentIntent()

        val answerIntent = Intent(context, CallActionReceiver::class.java).apply {
            action = ACTION_ANSWER_CALL
            putExtra(EXTRA_CALL_ID, callId)
            putExtra(EXTRA_CONTACT_ID, contactId)
            putExtra(EXTRA_IS_VIDEO, isVideo)
        }
        val answerPendingIntent = PendingIntent.getBroadcast(
            context,
            2,
            answerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val declineIntent = Intent(context, CallActionReceiver::class.java).apply {
            action = ACTION_DECLINE_CALL
            putExtra(EXTRA_CALL_ID, callId)
        }
        val declinePendingIntent = PendingIntent.getBroadcast(
            context,
            3,
            declineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val callType = if (isVideo) "Incoming video call" else "Incoming call"

        return NotificationCompat.Builder(context, CHANNEL_ID_INCOMING)
            .setContentTitle(contactName)
            .setContentText(callType)
            .setSmallIcon(R.drawable.ic_call_notification)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setFullScreenIntent(fullScreenIntent, true)
            .setContentIntent(contentIntent)
            .addAction(0, "Answer", answerPendingIntent)
            .addAction(0, "Decline", declinePendingIntent)
            .build()
    }

    fun showIncomingCallNotification(
        contactName: String,
        isVideo: Boolean,
        callId: String,
        contactId: String
    ) {
        val notification = createIncomingCallNotification(contactName, isVideo, callId, contactId)
        notificationManager.notify(NOTIFICATION_ID_INCOMING_CALL, notification)
    }

    fun cancelIncomingCallNotification() {
        notificationManager.cancel(NOTIFICATION_ID_INCOMING_CALL)
    }

    fun cancelOngoingCallNotification() {
        notificationManager.cancel(NOTIFICATION_ID_ONGOING_CALL)
    }

    private fun createContentIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createFullScreenIntent(
        callId: String,
        contactId: String,
        contactName: String,
        isVideo: Boolean
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_CALL_ID, callId)
            putExtra(EXTRA_CONTACT_ID, contactId)
            putExtra(EXTRA_CONTACT_NAME, contactName)
            putExtra(EXTRA_IS_VIDEO, isVideo)
            putExtra("incoming_call", true)
        }
        return PendingIntent.getActivity(
            context,
            4,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
