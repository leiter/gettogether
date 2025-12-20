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
        android.util.Log.d("GetTogetherApp", "=== Application onCreate() ===")

        android.util.Log.d("GetTogetherApp", "→ Starting Koin...")
        startKoin {
            androidLogger()
            androidContext(this@GetTogetherApplication)
            allowOverride(true)
            modules(sharedModule, platformModule, jamiBridgeModule)
        }
        android.util.Log.i("GetTogetherApp", "✓ Koin started successfully")

        // Initialize notification channels
        android.util.Log.d("GetTogetherApp", "→ Initializing notification helper...")
        val notificationHelper: NotificationHelper by inject()
        notificationHelper.initialize()
        android.util.Log.i("GetTogetherApp", "✓ Notification helper initialized")

        // Create notification channels for calls (legacy)
        android.util.Log.d("GetTogetherApp", "→ Creating call notification channels...")
        CallNotificationManager(this).createNotificationChannels()
        android.util.Log.i("GetTogetherApp", "✓ Call notification channels created")

        // Start Jami daemon
        android.util.Log.d("GetTogetherApp", "→ Starting Jami daemon...")
        val daemonManager: DaemonManager by inject()
        daemonManager.start()
        android.util.Log.i("GetTogetherApp", "✓ Daemon start initiated (check DaemonManager logs for status)")
        android.util.Log.d("GetTogetherApp", "=== Application onCreate() completed ===")
    }
}
