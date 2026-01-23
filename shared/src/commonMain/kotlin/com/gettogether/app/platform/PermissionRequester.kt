package com.gettogether.app.platform

import androidx.compose.runtime.Composable

/**
 * Platform-specific permission requester abstraction.
 * Used for requesting runtime permissions like storage access.
 */
expect class PermissionRequester {
    /**
     * Request a permission.
     * @param permission The permission string to request
     * @param onResult Callback with true if granted, false if denied
     */
    fun requestPermission(permission: String, onResult: (Boolean) -> Unit)
}

/**
 * Provides platform-specific PermissionRequester instance.
 * Must be called from a Composable context to access Activity on Android.
 */
@Composable
expect fun providePermissionRequester(): PermissionRequester
