package com.gettogether.app.platform

import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVMediaTypeAudio
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType

/**
 * iOS implementation of PermissionManager.
 * Manages runtime permissions required for calls and notifications.
 */
actual class PermissionManager {

    /**
     * Check if all required permissions are granted.
     * On iOS, checks AVFoundation authorization status for audio and video.
     */
    actual fun hasRequiredPermissions(): Boolean {
        val audioStatus = AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeAudio)
        val videoStatus = AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)

        return audioStatus == AVAuthorizationStatusAuthorized &&
               videoStatus == AVAuthorizationStatusAuthorized
    }

    /**
     * Get list of required permissions for iOS.
     * Returns human-readable permission names since iOS doesn't use manifest permissions.
     */
    actual fun getRequiredPermissions(): List<String> {
        return listOf(
            "Microphone",
            "Camera"
        )
    }

    /**
     * Check if storage write permission is granted.
     * On iOS, photo library access is handled differently (PHPhotoLibrary).
     * For now, return true as a stub.
     */
    actual fun hasStorageWritePermission(): Boolean = true

    /**
     * Get the storage write permission string if needed.
     * On iOS, returns null as permissions work differently.
     */
    actual fun getStorageWritePermission(): String? = null
}
