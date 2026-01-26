package com.gettogether.app.platform

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import java.io.File
import android.util.Base64

actual class NotificationHelper(
    private val context: Context
) {
    private val notificationManager = NotificationManagerCompat.from(context)

    actual fun initialize() {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Messages channel
            val messagesChannel = NotificationChannel(
                NotificationConstants.CHANNEL_MESSAGES,
                "Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "New message notifications"
                enableLights(true)
                lightColor = Color.BLUE
                enableVibration(true)
                setShowBadge(true)
            }
            manager.createNotificationChannel(messagesChannel)

            // Incoming calls channel (high priority with ringtone)
            val incomingCallsChannel = NotificationChannel(
                NotificationConstants.CHANNEL_INCOMING_CALLS,
                "Incoming Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Incoming call notifications"
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                enableLights(true)
                lightColor = Color.GREEN
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 500, 1000)
                setBypassDnd(true)
            }
            manager.createNotificationChannel(incomingCallsChannel)

            // Ongoing calls channel (low priority, silent)
            val ongoingCallsChannel = NotificationChannel(
                NotificationConstants.CHANNEL_CALLS,
                "Ongoing Calls",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Ongoing call notifications"
                setSound(null, null)
                enableVibration(false)
            }
            manager.createNotificationChannel(ongoingCallsChannel)

            // Missed calls channel
            val missedCallsChannel = NotificationChannel(
                NotificationConstants.CHANNEL_MISSED_CALLS,
                "Missed Calls",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Missed call notifications"
                enableLights(true)
                lightColor = Color.RED
            }
            manager.createNotificationChannel(missedCallsChannel)
        }
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
        if (!hasNotificationPermission()) return

        // Load avatar bitmap if available
        val avatarIcon: IconCompat? = avatarPath?.let { path ->
            try {
                val file = File(path)
                if (file.exists()) {
                    val bitmap = if (path.endsWith(".vcf", ignoreCase = true)) {
                        // vCard file - extract base64 photo data
                        extractAvatarFromVCard(file)
                    } else {
                        // Direct image file
                        BitmapFactory.decodeFile(path)
                    }
                    if (bitmap != null) {
                        // Create circular bitmap for avatar
                        val circularBitmap = createCircularBitmap(bitmap)
                        IconCompat.createWithBitmap(circularBitmap)
                    } else null
                } else null
            } catch (e: Exception) {
                null
            }
        }

        // Create person for messaging style with avatar
        val senderBuilder = Person.Builder()
            .setName(contactName)
            .setKey(contactId)
        avatarIcon?.let { senderBuilder.setIcon(it) }
        val sender = senderBuilder.build()

        // Content intent - open the conversation
        val contentIntent = createMainActivityIntent().apply {
            putExtra(NotificationConstants.EXTRA_CONVERSATION_ID, conversationId)
            putExtra(NotificationConstants.EXTRA_CONTACT_ID, contactId)
            action = "OPEN_CONVERSATION"
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Quick reply action
        val remoteInput = RemoteInput.Builder(NotificationConstants.EXTRA_REPLY_TEXT)
            .setLabel("Reply")
            .build()

        val replyIntent = Intent(context, MessageNotificationReceiver::class.java).apply {
            action = NotificationConstants.ACTION_REPLY
            putExtra(NotificationConstants.EXTRA_CONVERSATION_ID, conversationId)
            putExtra(NotificationConstants.EXTRA_CONTACT_ID, contactId)
        }
        val replyPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send,
            "Reply",
            replyPendingIntent
        )
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(true)
            .build()

        // Mark as read action
        val markReadIntent = Intent(context, MessageNotificationReceiver::class.java).apply {
            action = NotificationConstants.ACTION_MARK_READ
            putExtra(NotificationConstants.EXTRA_CONVERSATION_ID, conversationId)
        }
        val markReadPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 1000,
            markReadIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val markReadAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_view,
            "Mark as read",
            markReadPendingIntent
        ).build()

        // Build messaging style notification
        val messagingStyle = NotificationCompat.MessagingStyle(
            Person.Builder().setName("You").build()
        )
            .setConversationTitle(contactName)
            .addMessage(message, timestamp, sender)

        val notification = NotificationCompat.Builder(context, NotificationConstants.CHANNEL_MESSAGES)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setStyle(messagingStyle)
            .setContentIntent(contentPendingIntent)
            .addAction(replyAction)
            .addAction(markReadAction)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setGroup("messages_$conversationId")
            .build()

        notificationManager.notify(notificationId, notification)
    }

    actual fun showIncomingCallNotification(
        callId: String,
        contactId: String,
        contactName: String,
        isVideo: Boolean,
        avatarPath: String?
    ) {
        android.util.Log.i("NotificationHelper", "[CALL-NOTIF] showIncomingCallNotification: callId=$callId, contact=$contactName, avatar=$avatarPath")

        if (!hasNotificationPermission()) {
            android.util.Log.w("NotificationHelper", "[CALL-NOTIF] No notification permission!")
            return
        }

        // Load avatar bitmap if available
        val avatarBitmap: Bitmap? = avatarPath?.let { path ->
            try {
                val file = File(path)
                if (file.exists()) {
                    val bitmap = if (path.endsWith(".vcf", ignoreCase = true)) {
                        extractAvatarFromVCard(file)
                    } else {
                        BitmapFactory.decodeFile(path)
                    }
                    if (bitmap != null) {
                        createCircularBitmap(bitmap)
                    } else null
                } else null
            } catch (e: Exception) {
                android.util.Log.e("NotificationHelper", "[CALL-NOTIF] Error loading avatar: ${e.message}")
                null
            }
        }

        // Full-screen intent for incoming call
        val fullScreenIntent = createMainActivityIntent().apply {
            putExtra(NotificationConstants.EXTRA_CALL_ID, callId)
            putExtra(NotificationConstants.EXTRA_CONTACT_ID, contactId)
            putExtra(NotificationConstants.EXTRA_IS_VIDEO, isVideo)
            action = "INCOMING_CALL"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            0,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Answer action
        val answerIntent = Intent(context, CallNotificationReceiver::class.java).apply {
            action = NotificationConstants.ACTION_ANSWER_CALL
            putExtra(NotificationConstants.EXTRA_CALL_ID, callId)
            putExtra(NotificationConstants.EXTRA_CONTACT_ID, contactId)
            putExtra(NotificationConstants.EXTRA_CONTACT_NAME, contactName)
            putExtra(NotificationConstants.EXTRA_IS_VIDEO, isVideo)
        }
        val answerPendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            answerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Decline action
        val declineIntent = Intent(context, CallNotificationReceiver::class.java).apply {
            action = NotificationConstants.ACTION_DECLINE_CALL
            putExtra(NotificationConstants.EXTRA_CALL_ID, callId)
        }
        val declinePendingIntent = PendingIntent.getBroadcast(
            context,
            2,
            declineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val callType = if (isVideo) "Video call" else "Voice call"

        val notificationBuilder = NotificationCompat.Builder(context, NotificationConstants.CHANNEL_INCOMING_CALLS)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle("Incoming $callType")
            .setContentText(contactName)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Decline", declinePendingIntent)
            .addAction(android.R.drawable.ic_menu_call, "Answer", answerPendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .setTimeoutAfter(60000) // Auto-dismiss after 60 seconds

        // Add large icon (avatar) if available
        if (avatarBitmap != null) {
            notificationBuilder.setLargeIcon(avatarBitmap)
        }

        notificationManager.notify(NotificationConstants.INCOMING_CALL_NOTIFICATION_ID, notificationBuilder.build())
        android.util.Log.i("NotificationHelper", "[CALL-NOTIF] Incoming call notification posted successfully")
    }

    actual fun showOngoingCallNotification(
        callId: String,
        contactName: String,
        duration: String,
        isMuted: Boolean,
        isVideo: Boolean
    ) {
        if (!hasNotificationPermission()) return

        val contentIntent = createMainActivityIntent().apply {
            putExtra(NotificationConstants.EXTRA_CALL_ID, callId)
            action = "ONGOING_CALL"
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // End call action
        val endCallIntent = Intent(context, CallNotificationReceiver::class.java).apply {
            action = NotificationConstants.ACTION_END_CALL
            putExtra(NotificationConstants.EXTRA_CALL_ID, callId)
        }
        val endCallPendingIntent = PendingIntent.getBroadcast(
            context,
            3,
            endCallIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Mute toggle action
        val muteIntent = Intent(context, CallNotificationReceiver::class.java).apply {
            action = NotificationConstants.ACTION_MUTE_CALL
            putExtra(NotificationConstants.EXTRA_CALL_ID, callId)
        }
        val mutePendingIntent = PendingIntent.getBroadcast(
            context,
            4,
            muteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val callType = if (isVideo) "Video call" else "Voice call"
        val muteText = if (isMuted) "Unmute" else "Mute"

        val notification = NotificationCompat.Builder(context, NotificationConstants.CHANNEL_CALLS)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle("$callType with $contactName")
            .setContentText(duration)
            .setContentIntent(contentPendingIntent)
            .addAction(android.R.drawable.ic_lock_silent_mode, muteText, mutePendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "End", endCallPendingIntent)
            .setOngoing(true)
            .setUsesChronometer(true)
            .setWhen(System.currentTimeMillis())
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .build()

        notificationManager.notify(NotificationConstants.ONGOING_CALL_NOTIFICATION_ID, notification)
    }

    actual fun showMissedCallNotification(
        notificationId: Int,
        contactId: String,
        contactName: String,
        isVideo: Boolean,
        timestamp: Long
    ) {
        if (!hasNotificationPermission()) return

        val contentIntent = createMainActivityIntent().apply {
            putExtra(NotificationConstants.EXTRA_CONTACT_ID, contactId)
            action = "CONTACT_DETAILS"
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Call back action
        val callBackIntent = Intent(context, CallNotificationReceiver::class.java).apply {
            action = NotificationConstants.ACTION_CALL_BACK
            putExtra(NotificationConstants.EXTRA_CONTACT_ID, contactId)
            putExtra(NotificationConstants.EXTRA_IS_VIDEO, isVideo)
        }
        val callBackPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            callBackIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val callType = if (isVideo) "video call" else "call"

        val notification = NotificationCompat.Builder(context, NotificationConstants.CHANNEL_MISSED_CALLS)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle("Missed $callType")
            .setContentText("From $contactName")
            .setContentIntent(contentPendingIntent)
            .addAction(android.R.drawable.ic_menu_call, "Call back", callBackPendingIntent)
            .setAutoCancel(true)
            .setWhen(timestamp)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
            .build()

        notificationManager.notify(notificationId, notification)
    }

    actual fun showGroupNotificationSummary(
        groupName: String,
        messageCount: Int,
        latestMessage: String
    ) {
        if (!hasNotificationPermission()) return

        val contentIntent = createMainActivityIntent().apply {
            action = "OPEN_HOME"
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, NotificationConstants.CHANNEL_MESSAGES)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle("$messageCount new messages")
            .setContentText(latestMessage)
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .setGroup("messages_group")
            .setGroupSummary(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(NotificationConstants.GROUP_SUMMARY_NOTIFICATION_ID, notification)
    }

    actual fun cancelNotification(notificationId: Int) {
        notificationManager.cancel(notificationId)
    }

    actual fun cancelConversationNotifications(conversationId: String) {
        // Cancel all notifications in the conversation group
        notificationManager.activeNotifications
            .filter { it.notification.group == "messages_$conversationId" }
            .forEach { notificationManager.cancel(it.id) }
    }

    actual fun cancelIncomingCallNotification() {
        notificationManager.cancel(NotificationConstants.INCOMING_CALL_NOTIFICATION_ID)
    }

    actual fun cancelAllNotifications() {
        notificationManager.cancelAll()
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun createMainActivityIntent(): Intent {
        return Intent(context, Class.forName("com.gettogether.app.MainActivity")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
    }

    /**
     * Extract avatar bitmap from a vCard file.
     * vCard files contain PHOTO field with base64-encoded image data.
     */
    private fun extractAvatarFromVCard(file: File): Bitmap? {
        return try {
            val content = file.readText()

            // Extract PHOTO field - format: PHOTO;ENCODING=BASE64;TYPE=PNG:base64data...
            val photoPattern = Regex(
                "(?i)PHOTO;[^:]*:([A-Za-z0-9+/=\\s]+?)(?=\r?\n[A-Z]|\r?\nEND:)",
                RegexOption.DOT_MATCHES_ALL
            )

            val match = photoPattern.find(content)
            if (match != null) {
                // Remove whitespace from base64 data
                val base64Data = match.groupValues[1].replace(Regex("\\s"), "")
                val imageBytes = Base64.decode(base64Data, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Create a circular bitmap from a source bitmap for notification avatar.
     */
    private fun createCircularBitmap(source: Bitmap): Bitmap {
        val size = minOf(source.width, source.height)
        val x = (source.width - size) / 2
        val y = (source.height - size) / 2

        val squaredBitmap = Bitmap.createBitmap(source, x, y, size, size)
        if (squaredBitmap != source) {
            source.recycle()
        }

        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint().apply {
            isAntiAlias = true
            shader = android.graphics.BitmapShader(
                squaredBitmap,
                android.graphics.Shader.TileMode.CLAMP,
                android.graphics.Shader.TileMode.CLAMP
            )
        }

        val radius = size / 2f
        canvas.drawCircle(radius, radius, radius, paint)

        squaredBitmap.recycle()
        return bitmap
    }
}
