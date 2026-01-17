package com.gettogether.app.di

import org.koin.core.context.startKoin
import org.koin.mp.KoinPlatform
import platform.Foundation.NSLog

private var isKoinInitialized = false

fun initKoin() {
    if (isKoinInitialized) {
        NSLog("KoinInit: Koin already initialized, skipping")
        return
    }

    try {
        // Check if Koin is already started (e.g., by tests)
        try {
            KoinPlatform.getKoin()
            NSLog("KoinInit: Koin already exists, skipping")
            isKoinInitialized = true
            return
        } catch (_: Exception) {
            // Koin not started yet, continue with initialization
        }

        NSLog("KoinInit: Starting Koin initialization")
        startKoin {
            modules(sharedModule, platformModule)
        }
        isKoinInitialized = true
        NSLog("KoinInit: Koin initialized successfully")
    } catch (e: Exception) {
        NSLog("KoinInit: Error initializing Koin: ${e.message}")
        throw e
    }
}
