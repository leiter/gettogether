package com.gettogether.app.di

import com.gettogether.app.data.repository.AccountRepository
import com.gettogether.app.jami.JamiBridge
import com.gettogether.app.jami.createJamiBridge
import com.gettogether.app.presentation.viewmodel.AddContactViewModel
import com.gettogether.app.presentation.viewmodel.CallViewModel
import com.gettogether.app.presentation.viewmodel.ConferenceViewModel
import com.gettogether.app.presentation.viewmodel.ContactDetailsViewModel
import com.gettogether.app.presentation.viewmodel.ChatViewModel
import com.gettogether.app.presentation.viewmodel.CreateAccountViewModel
import com.gettogether.app.presentation.viewmodel.ImportAccountViewModel
import com.gettogether.app.presentation.viewmodel.NewConversationViewModel
import com.gettogether.app.presentation.viewmodel.SettingsViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val sharedModule = module {
    // Jami Bridge
    single<JamiBridge> { createJamiBridge() }

    // Repositories
    single { AccountRepository(get()) }

    // ViewModels
    viewModel { CreateAccountViewModel(get(), get()) }
    viewModel { ImportAccountViewModel(get(), get()) }
    viewModel { ChatViewModel(get(), get()) }
    viewModel { NewConversationViewModel(get(), get()) }
    viewModel { AddContactViewModel(get(), get()) }
    viewModel { SettingsViewModel(get(), get()) }
    viewModel { ContactDetailsViewModel(get(), get()) }
    viewModel { CallViewModel(get(), get(), getOrNull()) }
    viewModel { ConferenceViewModel(get(), get()) }
}

expect val platformModule: Module
