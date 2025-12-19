package com.gettogether.app.di

import com.gettogether.app.platform.CallServiceBridge
import com.gettogether.app.platform.NotificationHelper
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    // Android-specific dependencies
    single { CallServiceBridge(androidContext()) }
    single { NotificationHelper(androidContext()) }
}
