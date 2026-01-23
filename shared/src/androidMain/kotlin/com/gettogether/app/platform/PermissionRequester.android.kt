package com.gettogether.app.platform

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

actual class PermissionRequester(
    private val launchRequest: (permission: String, onResult: (Boolean) -> Unit) -> Unit
) {
    actual fun requestPermission(permission: String, onResult: (Boolean) -> Unit) {
        launchRequest(permission, onResult)
    }
}

@Composable
actual fun providePermissionRequester(): PermissionRequester {
    // Create a mutable holder for the callback and pending permission
    val callbackHolder = remember { mutableListOf<((Boolean) -> Unit)?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted: Boolean ->
        val callback = callbackHolder.getOrNull(0)
        callbackHolder[0] = null
        callback?.invoke(granted)
    }

    return remember(launcher) {
        PermissionRequester { permission, onResult ->
            callbackHolder[0] = onResult
            try {
                launcher.launch(permission)
            } catch (e: Exception) {
                callbackHolder[0] = null
                onResult(false)
            }
        }
    }
}
