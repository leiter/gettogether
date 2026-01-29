package com.gettogether.app.di

import com.gettogether.app.data.repository.AccountRepository
import com.gettogether.app.data.repository.ContactRepositoryImpl
import com.gettogether.app.data.repository.ConversationRepositoryImpl
import com.gettogether.app.data.repository.PresenceManager
import com.gettogether.app.jami.JamiBridge
import com.gettogether.app.jami.createJamiBridge
import com.gettogether.app.presentation.viewmodel.AddContactViewModel
import com.gettogether.app.presentation.viewmodel.BlockedContactsViewModel
import com.gettogether.app.presentation.viewmodel.CallViewModel
import com.gettogether.app.presentation.viewmodel.ChatViewModel
import com.gettogether.app.presentation.viewmodel.ConferenceViewModel
import com.gettogether.app.presentation.viewmodel.ContactDetailsViewModel
import com.gettogether.app.presentation.viewmodel.ContactsViewModel
import com.gettogether.app.presentation.viewmodel.ConversationRequestsViewModel
import com.gettogether.app.presentation.viewmodel.ConversationsViewModel
import com.gettogether.app.presentation.viewmodel.CreateAccountViewModel
import com.gettogether.app.presentation.viewmodel.ImportAccountViewModel
import com.gettogether.app.presentation.viewmodel.NewConversationViewModel
import com.gettogether.app.presentation.viewmodel.SettingsViewModel
import com.gettogether.app.presentation.viewmodel.TrustRequestsViewModel
import org.koin.core.module.Module
import org.koin.dsl.module

val sharedModule = module {
    // Jami Bridge
    single<JamiBridge> {
        println("Koin: Creating JamiBridge")
        createJamiBridge().also { println("Koin: JamiBridge created") }
    }

    // Presence Manager (create before AccountRepository since it depends on it)
    single {
        println("Koin: Creating PresenceManager")
        PresenceManager(get()).also { println("Koin: PresenceManager created") }
    }

    // Repositories
    single {
        println("Koin: Creating AccountRepository")
        AccountRepository(get(), get(), getOrNull(), getOrNull()).also { println("Koin: AccountRepository created") }
    }
    single { ContactRepositoryImpl(get(), get(), get(), get(), get()) }
    single<com.gettogether.app.domain.repository.ContactRepository> { get<ContactRepositoryImpl>() }
    single { ConversationRepositoryImpl(get(), get(), get(), get(), getOrNull(), getOrNull(), getOrNull()) }

    // ViewModels
    // Using viewModelFactory for platform-specific scoping:
    // - Android: viewModel { } for lifecycle scoping (survives config changes)
    // - iOS: factory { } since there's no ViewModel lifecycle
    viewModelFactory {
        println("Koin: Creating CreateAccountViewModel")
        CreateAccountViewModel(get(), get()).also { println("Koin: CreateAccountViewModel created") }
    }
    viewModelFactory { ImportAccountViewModel(get(), get()) }
    viewModelFactory { ChatViewModel(get(), get(), get(), get(), get(), get()) }
    viewModelFactory { ConversationsViewModel(get(), get()) }
    viewModelFactory { ConversationRequestsViewModel(get(), get()) }
    viewModelFactory { ContactsViewModel(get(), get()) }
    viewModelFactory { BlockedContactsViewModel(get(), get()) }
    viewModelFactory { TrustRequestsViewModel(get(), get()) }
    viewModelFactory { NewConversationViewModel(get(), get()) }
    viewModelFactory { AddContactViewModel(get(), get()) }
    viewModelFactory { SettingsViewModel(get(), get(), get(), get(), get()) }
    viewModelFactory { ContactDetailsViewModel(get(), get(), get(), get<ConversationRepositoryImpl>()) }
    viewModelFactory { CallViewModel(get(), get(), get(), getOrNull(), get()) }
    viewModelFactory { ConferenceViewModel(get(), get()) }
}

expect val platformModule: Module
