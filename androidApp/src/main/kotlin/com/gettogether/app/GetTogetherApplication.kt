package com.gettogether.app

import android.app.Application
import com.gettogether.app.di.platformModule
import com.gettogether.app.di.sharedModule
import com.gettogether.app.service.CallNotificationManager
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class GetTogetherApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@GetTogetherApplication)
            modules(sharedModule, platformModule)
        }

        // Create notification channels for calls
        CallNotificationManager(this).createNotificationChannels()
    }
}
