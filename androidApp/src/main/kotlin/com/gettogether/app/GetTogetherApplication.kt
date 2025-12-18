package com.gettogether.app

import android.app.Application
import com.gettogether.app.di.platformModule
import com.gettogether.app.di.sharedModule
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
    }
}
