package com.gettogether.app.ui.util

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription

/**
 * Accessibility utilities for better screen reader support
 */

/**
 * Mark an element as a heading for accessibility
 */
fun Modifier.accessibilityHeading(): Modifier = this.semantics {
    heading()
}

/**
 * Add a content description for accessibility
 */
fun Modifier.accessibilityDescription(description: String): Modifier = this.semantics {
    contentDescription = description
}

/**
 * Add a state description (e.g., "selected", "expanded")
 */
fun Modifier.accessibilityState(state: String): Modifier = this.semantics {
    stateDescription = state
}

/**
 * Combine multiple accessibility attributes
 */
fun Modifier.accessible(
    description: String? = null,
    state: String? = null,
    isHeading: Boolean = false
): Modifier = this.semantics {
    description?.let { contentDescription = it }
    state?.let { stateDescription = it }
    if (isHeading) heading()
}

/**
 * Format duration for accessibility announcement
 * Example: "1:23:45" -> "1 hour, 23 minutes, 45 seconds"
 */
fun formatDurationForAccessibility(durationSeconds: Long): String {
    val hours = durationSeconds / 3600
    val minutes = (durationSeconds % 3600) / 60
    val seconds = durationSeconds % 60

    return buildString {
        if (hours > 0) {
            append("$hours hour${if (hours != 1L) "s" else ""}")
        }
        if (minutes > 0) {
            if (isNotEmpty()) append(", ")
            append("$minutes minute${if (minutes != 1L) "s" else ""}")
        }
        if (seconds > 0 || isEmpty()) {
            if (isNotEmpty()) append(", ")
            append("$seconds second${if (seconds != 1L) "s" else ""}")
        }
    }
}

/**
 * Format participant count for accessibility
 */
fun formatParticipantCountForAccessibility(count: Int): String {
    return when (count) {
        0 -> "No participants"
        1 -> "1 participant"
        else -> "$count participants"
    }
}

/**
 * Format online status for accessibility
 */
fun formatOnlineStatusForAccessibility(isOnline: Boolean): String {
    return if (isOnline) "Online" else "Offline"
}

/**
 * Format call status for accessibility
 */
fun formatCallStatusForAccessibility(
    contactName: String,
    isVideo: Boolean,
    isIncoming: Boolean,
    isConnected: Boolean,
    isMuted: Boolean
): String {
    return buildString {
        if (isIncoming) {
            append("Incoming ")
        }
        append(if (isVideo) "video" else "voice")
        append(" call ")
        if (isConnected) {
            append("connected ")
        }
        append("with $contactName")
        if (isMuted) {
            append(", microphone muted")
        }
    }
}

/**
 * Format message notification for accessibility
 */
fun formatMessageNotificationForAccessibility(
    senderName: String,
    messagePreview: String,
    unreadCount: Int
): String {
    return buildString {
        append("New message from $senderName: $messagePreview")
        if (unreadCount > 1) {
            append(". $unreadCount unread messages total")
        }
    }
}

/**
 * Describe button action for accessibility
 */
object ButtonDescriptions {
    const val MUTE = "Mute microphone"
    const val UNMUTE = "Unmute microphone"
    const val SPEAKER_ON = "Turn on speaker"
    const val SPEAKER_OFF = "Turn off speaker"
    const val VIDEO_ON = "Turn on camera"
    const val VIDEO_OFF = "Turn off camera"
    const val END_CALL = "End call"
    const val ANSWER_CALL = "Answer call"
    const val DECLINE_CALL = "Decline call"
    const val SWITCH_CAMERA = "Switch between front and back camera"
    const val SEND_MESSAGE = "Send message"
    const val ATTACH_FILE = "Attach file"
    const val START_CALL = "Start call"
    const val START_VIDEO_CALL = "Start video call"
    const val ADD_CONTACT = "Add new contact"
    const val SEARCH = "Search"
    const val SETTINGS = "Open settings"
    const val BACK = "Go back"
    const val CLOSE = "Close"
    const val MORE_OPTIONS = "More options"
}
