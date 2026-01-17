package com.gettogether.app.platform

/**
 * Platform-specific permission manager for handling runtime permissions.
 *
 * On Android: Checks and manages Android runtime permissions (RECORD_AUDIO, CAMERA, POST_NOTIFICATIONS)
 * On iOS: Can be extended for iOS permission handling
 */
expect class PermissionManager {
    /**
     * Check if all required permissions are granted.
     * @return true if all required permissions are granted, false otherwise
     */
    fun hasRequiredPermissions(): Boolean

    /**
     * Get list of required permissions for the platform.
     * @return List of permission strings (e.g., ["android.permission.RECORD_AUDIO"])
     */
    fun getRequiredPermissions(): List<String>
}
