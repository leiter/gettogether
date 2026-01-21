package com.gettogether.app.di

import com.gettogether.app.data.persistence.AndroidContactPersistence
import com.gettogether.app.data.persistence.AndroidConversationPersistence
import com.gettogether.app.data.persistence.ContactPersistence
import com.gettogether.app.data.persistence.ConversationPersistence
import com.gettogether.app.data.persistence.setContactPersistence
import com.gettogether.app.data.repository.AndroidSettingsRepository
import com.gettogether.app.data.repository.SettingsRepository
import com.gettogether.app.data.repository.setSettingsRepository
import com.gettogether.app.jami.DaemonManager
import com.gettogether.app.jami.DataPathProvider
import android.app.Application
import com.gettogether.app.platform.AndroidContactAvatarStorage
import com.gettogether.app.platform.AppLifecycleManager
import com.gettogether.app.platform.CallServiceBridge
import com.gettogether.app.platform.ContactAvatarStorage
import com.gettogether.app.platform.ExportPathProvider
import com.gettogether.app.platform.FileHelper
import com.gettogether.app.platform.ImageProcessor
import com.gettogether.app.platform.NotificationHelper
import com.gettogether.app.platform.PermissionManager
import com.gettogether.app.platform.setContactAvatarStorage
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    // App lifecycle manager (must be created early)
    single {
        println("Koin: Creating AppLifecycleManager")
        AppLifecycleManager(androidContext() as Application).also {
            println("Koin: AppLifecycleManager created")
        }
    }

    // Android-specific dependencies
    single { CallServiceBridge(androidContext()) }
    single { NotificationHelper(androidContext()) }
    single { PermissionManager(androidContext()) }

    // Image handling
    single { ImageProcessor(androidContext()) }

    // File helper for chat attachments
    single { FileHelper(androidContext()) }

    // Export path provider
    single { ExportPathProvider(androidContext()) }

    // Settings persistence
    single<SettingsRepository> {
        AndroidSettingsRepository(androidContext()).also { setSettingsRepository(it) }
    }

    // Contact persistence
    single<ContactPersistence> {
        AndroidContactPersistence(androidContext()).also { setContactPersistence(it) }
    }

    // Contact avatar storage
    single<ContactAvatarStorage> {
        AndroidContactAvatarStorage(androidContext()).also { setContactAvatarStorage(it) }
    }

    // Conversation persistence
    single<ConversationPersistence> {
        AndroidConversationPersistence(androidContext())
    }

    // Jami daemon bridge and lifecycle
    single { DataPathProvider(androidContext()) }
    // JamiBridge is provided by jamiBridgeModule (SwigJamiBridge)
    single { DaemonManager(get(), get()) }
}
