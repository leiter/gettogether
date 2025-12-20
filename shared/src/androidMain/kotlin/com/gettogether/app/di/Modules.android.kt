package com.gettogether.app.di

import com.gettogether.app.data.persistence.AndroidContactPersistence
import com.gettogether.app.data.persistence.AndroidConversationPersistence
import com.gettogether.app.data.persistence.ContactPersistence
import com.gettogether.app.data.persistence.ConversationPersistence
import com.gettogether.app.data.persistence.setContactPersistence
import com.gettogether.app.data.repository.AndroidSettingsRepository
import com.gettogether.app.data.repository.SettingsRepository
import com.gettogether.app.data.repository.setSettingsRepository
import com.gettogether.app.jami.AndroidJamiBridge
import com.gettogether.app.jami.DaemonManager
import com.gettogether.app.jami.DataPathProvider
import com.gettogether.app.jami.JamiBridge
import com.gettogether.app.platform.CallServiceBridge
import com.gettogether.app.platform.NotificationHelper
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    // Android-specific dependencies
    single { CallServiceBridge(androidContext()) }
    single { NotificationHelper(androidContext()) }

    // Settings persistence
    single<SettingsRepository> {
        AndroidSettingsRepository(androidContext()).also { setSettingsRepository(it) }
    }

    // Contact persistence
    single<ContactPersistence> {
        AndroidContactPersistence(androidContext()).also { setContactPersistence(it) }
    }

    // Conversation persistence
    single<ConversationPersistence> {
        AndroidConversationPersistence(androidContext())
    }

    // Jami daemon bridge and lifecycle
    single { DataPathProvider(androidContext()) }
    single<JamiBridge> { AndroidJamiBridge(androidContext()) }
    single { DaemonManager(get(), get()) }
}
