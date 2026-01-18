package com.gettogether.app.platform

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages app-level lifecycle state (foreground/background).
 * Uses ProcessLifecycleOwner to track when the entire app goes to background/foreground.
 */
actual class AppLifecycleManager(application: Application) {

    private val _isInForeground = MutableStateFlow(true) // Start as true
    actual val isInForeground: StateFlow<Boolean> = _isInForeground.asStateFlow()

    private var onAppShutdown: (() -> Unit)? = null

    init {
        println("[APP-LIFECYCLE] Initializing AppLifecycleManager")

        // Observe process lifecycle for app-level foreground/background state
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                println("[APP-LIFECYCLE] App came to FOREGROUND")
                _isInForeground.value = true
            }

            override fun onStop(owner: LifecycleOwner) {
                println("[APP-LIFECYCLE] App went to BACKGROUND")
                _isInForeground.value = false

                // Trigger shutdown callback when app goes to background
                // This is the most reliable point to publish offline status
                // because it fires before the process is killed (even when swiped away)
                println("[APP-LIFECYCLE] App backgrounded, calling shutdown callback to publish offline")
                onAppShutdown?.invoke()
            }
        })
    }

    /**
     * Register a callback to be invoked when the app is shutting down.
     * This is best-effort and won't fire on force kills or crashes.
     */
    actual fun setShutdownCallback(callback: () -> Unit) {
        println("[APP-LIFECYCLE] Shutdown callback registered")
        onAppShutdown = callback
    }
}
