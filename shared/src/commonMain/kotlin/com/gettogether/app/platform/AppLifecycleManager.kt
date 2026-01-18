package com.gettogether.app.platform

import kotlinx.coroutines.flow.StateFlow

/**
 * Platform-specific app lifecycle manager.
 * Tracks foreground/background state and provides shutdown hooks.
 */
expect class AppLifecycleManager {
    /**
     * Flow emitting true when app is in foreground, false when in background.
     */
    val isInForeground: StateFlow<Boolean>

    /**
     * Register a callback to be invoked when the app is shutting down.
     * This is best-effort and won't fire on force kills or crashes.
     */
    fun setShutdownCallback(callback: () -> Unit)
}
