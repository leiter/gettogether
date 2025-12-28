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
}
