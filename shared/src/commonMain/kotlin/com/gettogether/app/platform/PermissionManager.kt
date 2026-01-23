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

    /**
     * Check if storage write permission is granted.
     * On Android 10+, returns true (MediaStore doesn't need permission).
     * On Android 9 and below, checks WRITE_EXTERNAL_STORAGE.
     * On iOS, returns true (handled differently).
     */
    fun hasStorageWritePermission(): Boolean

    /**
     * Get the storage write permission string if needed.
     * @return Permission string on Android 9 and below, null on Android 10+ or iOS
     */
    fun getStorageWritePermission(): String?
}
