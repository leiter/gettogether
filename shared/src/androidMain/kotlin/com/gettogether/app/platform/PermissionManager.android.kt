package com.gettogether.app.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Android implementation of PermissionManager.
 * Manages runtime permissions required for calls and notifications.
 */
actual class PermissionManager(private val context: Context) {

    /**
     * Check if all required permissions are granted.
     */
    actual fun hasRequiredPermissions(): Boolean {
        return getRequiredPermissions().all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Get list of required permissions for the current Android version.
     *
     * Required permissions:
     * - RECORD_AUDIO: For microphone access during calls
     * - CAMERA: For video calls
     * - POST_NOTIFICATIONS: For showing call/message notifications (Android 13+)
     */
    actual fun getRequiredPermissions(): List<String> {
        return buildList {
            add(Manifest.permission.RECORD_AUDIO)
            add(Manifest.permission.CAMERA)

            // POST_NOTIFICATIONS is only required on Android 13+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }

            // READ_CONTACTS could be added if contact sync is implemented
            // add(Manifest.permission.READ_CONTACTS)
        }
    }
}
