package com.gettogether.app.di

import com.gettogether.app.platform.CallServiceBridge
import com.gettogether.app.platform.NotificationHelper
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    // iOS-specific dependencies
    single { CallServiceBridge() }
    single { NotificationHelper() }
}
