package com.gettogether.app.di

import com.gettogether.app.data.repository.SettingsRepository
import com.gettogether.app.data.repository.createSettingsRepository
import com.gettogether.app.jami.DaemonManager
import com.gettogether.app.jami.DataPathProvider
import com.gettogether.app.platform.CallServiceBridge
import com.gettogether.app.platform.NotificationHelper
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    // iOS-specific dependencies
    single { CallServiceBridge() }
    single { NotificationHelper() }
    single<SettingsRepository> { createSettingsRepository() }

    // Jami daemon bridge and lifecycle
    // Note: JamiBridge is declared in sharedModule via createJamiBridge()
    single { DataPathProvider() }
    single { DaemonManager(get(), get()) }
}
