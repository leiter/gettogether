package com.gettogether.app.di

import org.koin.core.module.Module
import org.koin.dsl.module

val sharedModule = module {
    // Domain layer
    // single { SomeUseCase(get()) }

    // Data layer
    // single { SomeRepository(get()) as SomeRepositoryInterface }

    // Jami Bridge
    // single { JamiBridge() }
}

expect val platformModule: Module
