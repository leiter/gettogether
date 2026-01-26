package com.gettogether.app.di

import com.gettogether.app.jami.DaemonManager
import com.gettogether.app.util.IosFileLogger
import org.koin.core.context.startKoin
import org.koin.mp.KoinPlatform

private var isKoinInitialized = false
private const val TAG = "KoinInit"

fun initKoin() {
    // Initialize file logger first for crash-safe logging
    IosFileLogger.initialize()

    if (isKoinInitialized) {
        IosFileLogger.i(TAG, "Koin already initialized, skipping")
        return
    }

    try {
        // Check if Koin is already started (e.g., by tests)
        try {
            KoinPlatform.getKoin()
            IosFileLogger.i(TAG, "Koin already exists, skipping")
            isKoinInitialized = true
            return
        } catch (_: Exception) {
            // Koin not started yet, continue with initialization
        }

        IosFileLogger.i(TAG, "Starting Koin initialization")
        startKoin {
            modules(sharedModule, platformModule)
        }
        isKoinInitialized = true
        IosFileLogger.i(TAG, "Koin initialized successfully")

        // Start the Jami daemon after Koin is ready
        startDaemon()
    } catch (e: Exception) {
        IosFileLogger.e(TAG, "Error initializing Koin", e)
        throw e
    }
}

private fun startDaemon() {
    try {
        IosFileLogger.i(TAG, "Starting Jami daemon...")
        val daemonManager: DaemonManager = KoinPlatform.getKoin().get()
        daemonManager.start()
        IosFileLogger.i(TAG, "Daemon start initiated")
    } catch (e: Exception) {
        IosFileLogger.e(TAG, "Error starting daemon", e)
    }
}
