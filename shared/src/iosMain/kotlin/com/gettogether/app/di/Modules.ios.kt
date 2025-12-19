package com.gettogether.app.di

import com.gettogether.app.jami.DaemonManager
import com.gettogether.app.jami.DataPathProvider
import com.gettogether.app.jami.IOSJamiBridge
import com.gettogether.app.jami.JamiBridge
import com.gettogether.app.platform.CallServiceBridge
import com.gettogether.app.platform.NotificationHelper
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    // iOS-specific dependencies
    single { CallServiceBridge() }
    single { NotificationHelper() }

    // Jami daemon bridge and lifecycle
    single { DataPathProvider() }
    single<JamiBridge> { IOSJamiBridge() }
    single { DaemonManager(get(), get()) }
}
