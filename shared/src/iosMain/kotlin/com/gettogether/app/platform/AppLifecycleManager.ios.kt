package com.gettogether.app.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.NSLog

/**
 * iOS implementation of AppLifecycleManager (stub).
 */
actual class AppLifecycleManager {

    private val _isInForeground = MutableStateFlow(true)
    actual val isInForeground: StateFlow<Boolean> = _isInForeground.asStateFlow()

    init {
        NSLog("[APP-LIFECYCLE-IOS] ⚠️ AppLifecycleManager not implemented for iOS (stub)")
    }

    actual fun setShutdownCallback(callback: () -> Unit) {
        NSLog("[APP-LIFECYCLE-IOS] ⚠️ setShutdownCallback not implemented for iOS (stub)")
    }
}
