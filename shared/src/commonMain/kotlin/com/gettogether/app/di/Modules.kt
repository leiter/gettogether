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
import org.koin.core.module.dsl.viewModel
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
        AccountRepository(get(), get(), getOrNull()).also { println("Koin: AccountRepository created") }
    }
    single { ContactRepositoryImpl(get(), get(), get(), get(), get()) }
    single<com.gettogether.app.domain.repository.ContactRepository> { get<ContactRepositoryImpl>() }
    single { ConversationRepositoryImpl(get(), get(), get(), get(), getOrNull(), getOrNull(), getOrNull()) }

    // ViewModels
    // Using factory for CreateAccountViewModel to work around iOS koinViewModel issue
    factory {
        println("Koin: Creating CreateAccountViewModel")
        CreateAccountViewModel(get(), get()).also { println("Koin: CreateAccountViewModel created") }
    }
    viewModel { ImportAccountViewModel(get(), get()) }
    viewModel { ChatViewModel(get(), get(), get(), get()) }
    viewModel { ConversationsViewModel(get(), get()) }
    viewModel { ConversationRequestsViewModel(get(), get()) }
    viewModel { ContactsViewModel(get(), get()) }
    viewModel { BlockedContactsViewModel(get(), get()) }
    viewModel { TrustRequestsViewModel(get(), get()) }
    viewModel { NewConversationViewModel(get(), get()) }
    viewModel { AddContactViewModel(get(), get()) }
    viewModel { SettingsViewModel(get(), get(), get(), get(), get()) }
    viewModel { ContactDetailsViewModel(get(), get(), get(), get<ConversationRepositoryImpl>()) }
    viewModel { CallViewModel(get(), get(), getOrNull(), get()) }
    viewModel { ConferenceViewModel(get(), get()) }
}

expect val platformModule: Module
