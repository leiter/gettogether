package com.gettogether.app.di

import com.gettogether.app.data.persistence.ContactPersistence
import com.gettogether.app.data.persistence.ConversationPersistence
import com.gettogether.app.data.persistence.IosContactPersistence
import com.gettogether.app.data.persistence.IosConversationPersistence
import com.gettogether.app.data.persistence.setContactPersistence
import com.gettogether.app.data.repository.SettingsRepository
import com.gettogether.app.data.repository.createSettingsRepository
import com.gettogether.app.jami.DaemonManager
import com.gettogether.app.jami.DataPathProvider
import com.gettogether.app.platform.AppLifecycleManager
import com.gettogether.app.platform.CallServiceBridge
import com.gettogether.app.platform.ExportPathProvider
import com.gettogether.app.platform.FileHelper
import com.gettogether.app.platform.ImageProcessor
import com.gettogether.app.platform.NotificationHelper
import com.gettogether.app.platform.PermissionManager
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    // App lifecycle manager (must be created early)
    single { AppLifecycleManager() }

    // iOS-specific dependencies
    single { CallServiceBridge() }
    single { NotificationHelper() }
    single { ImageProcessor() }
    single { FileHelper() }
    single { PermissionManager() }
    single { ExportPathProvider() }
    single<SettingsRepository> { createSettingsRepository() }

    // Contact persistence
    single<ContactPersistence> {
        IosContactPersistence().also { setContactPersistence(it) }
    }

    // Conversation persistence
    single<ConversationPersistence> {
        IosConversationPersistence()
    }

    // Jami daemon bridge and lifecycle
    // Note: JamiBridge is declared in sharedModule via createJamiBridge()
    single { DataPathProvider() }
    single { DaemonManager(get(), get()) }
}
