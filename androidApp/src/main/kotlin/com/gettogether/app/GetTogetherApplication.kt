package com.gettogether.app

import android.app.Application
import com.gettogether.app.di.jamiBridgeModule
import com.gettogether.app.di.platformModule
import com.gettogether.app.di.sharedModule
import com.gettogether.app.jami.DaemonManager
import com.gettogether.app.platform.NotificationHelper
import com.gettogether.app.service.CallNotificationManager
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class GetTogetherApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@GetTogetherApplication)
            allowOverride(true)
            modules(sharedModule, platformModule, jamiBridgeModule)
        }

        // Initialize notification channels
        val notificationHelper: NotificationHelper by inject()
        notificationHelper.initialize()

        // Create notification channels for calls (legacy)
        CallNotificationManager(this).createNotificationChannels()

        // Start Jami daemon
        val daemonManager: DaemonManager by inject()
        daemonManager.start()
    }
}
