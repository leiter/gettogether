package com.gettogether.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

actual class PermissionRequester {
    actual fun requestPermission(permission: String, onResult: (Boolean) -> Unit) {
        // iOS handles permissions differently (e.g., PHPhotoLibrary for photos)
        // For now, return true as a stub
        onResult(true)
    }
}

@Composable
actual fun providePermissionRequester(): PermissionRequester {
    return remember { PermissionRequester() }
}
