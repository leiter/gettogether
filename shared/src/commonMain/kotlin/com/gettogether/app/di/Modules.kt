package com.gettogether.app.di

import com.gettogether.app.data.repository.AccountRepository
import com.gettogether.app.data.repository.ContactRepositoryImpl
import com.gettogether.app.data.repository.ConversationRepositoryImpl
import com.gettogether.app.data.repository.SettingsRepository
import com.gettogether.app.jami.JamiBridge
import com.gettogether.app.jami.createJamiBridge
import com.gettogether.app.presentation.viewmodel.AddContactViewModel
import com.gettogether.app.presentation.viewmodel.CallViewModel
import com.gettogether.app.presentation.viewmodel.ConferenceViewModel
import com.gettogether.app.presentation.viewmodel.ContactDetailsViewModel
import com.gettogether.app.presentation.viewmodel.ContactsViewModel
import com.gettogether.app.presentation.viewmodel.ChatViewModel
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
    single<JamiBridge> { createJamiBridge() }

    // Repositories
    single { AccountRepository(get()) }
    single { ContactRepositoryImpl(get(), get(), get()) }
    single<com.gettogether.app.domain.repository.ContactRepository> { get<ContactRepositoryImpl>() }
    single { ConversationRepositoryImpl(get(), get(), get(), get()) }

    // ViewModels
    viewModel { CreateAccountViewModel(get(), get()) }
    viewModel { ImportAccountViewModel(get(), get()) }
    viewModel { ChatViewModel(get(), get(), get()) }
    viewModel { ConversationsViewModel(get(), get()) }
    viewModel { ContactsViewModel(get(), get()) }
    viewModel { TrustRequestsViewModel(get(), get()) }
    viewModel { NewConversationViewModel(get(), get()) }
    viewModel { AddContactViewModel(get(), get()) }
    viewModel { SettingsViewModel(get(), get(), get()) }
    viewModel { ContactDetailsViewModel(get(), get(), get()) }
    viewModel { CallViewModel(get(), get(), getOrNull()) }
    viewModel { ConferenceViewModel(get(), get()) }
}

expect val platformModule: Module
