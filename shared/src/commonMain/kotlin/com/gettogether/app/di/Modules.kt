package com.gettogether.app.di

import com.gettogether.app.jami.JamiBridge
import com.gettogether.app.jami.createJamiBridge
import com.gettogether.app.presentation.viewmodel.CreateAccountViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val sharedModule = module {
    // Jami Bridge
    single<JamiBridge> { createJamiBridge() }

    // ViewModels
    viewModel { CreateAccountViewModel(get()) }
}

expect val platformModule: Module
