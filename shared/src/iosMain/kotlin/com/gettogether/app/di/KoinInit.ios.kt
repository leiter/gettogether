package com.gettogether.app.di

import org.koin.core.context.startKoin

fun initKoin() {
    startKoin {
        modules(sharedModule, platformModule)
    }
}
