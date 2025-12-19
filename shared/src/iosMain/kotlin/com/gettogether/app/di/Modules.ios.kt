package com.gettogether.app.di

import com.gettogether.app.platform.CallServiceBridge
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    // iOS-specific dependencies
    single { CallServiceBridge() }
}
