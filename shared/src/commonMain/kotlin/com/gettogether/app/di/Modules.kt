package com.gettogether.app.di

import com.gettogether.app.jami.JamiBridge
import com.gettogether.app.jami.createJamiBridge
import com.gettogether.app.presentation.viewmodel.AddContactViewModel
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

    // ViewModels
    viewModel { CreateAccountViewModel(get()) }
    viewModel { ImportAccountViewModel(get()) }
    viewModel { ChatViewModel(get()) }
    viewModel { NewConversationViewModel(get()) }
    viewModel { AddContactViewModel(get()) }
    viewModel { SettingsViewModel(get()) }
}

expect val platformModule: Module
