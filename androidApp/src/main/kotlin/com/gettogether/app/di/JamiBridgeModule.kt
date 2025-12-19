package com.gettogether.app.di

import com.gettogether.app.jami.JamiBridge
import com.gettogether.app.jami.SwigJamiBridge
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * DI module that provides the SwigJamiBridge implementation using SWIG-generated bindings.
 * This module overrides the JamiBridge binding from platformModule.
 */
val jamiBridgeModule = module {
    single<JamiBridge> { SwigJamiBridge(androidContext()) }
}
