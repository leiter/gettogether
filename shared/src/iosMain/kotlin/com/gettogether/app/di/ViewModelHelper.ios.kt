package com.gettogether.app.di

import androidx.compose.runtime.Composable
import org.koin.compose.koinInject
import org.koin.core.definition.Definition
import org.koin.core.definition.KoinDefinition
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.bind

/**
 * iOS implementation: Uses factory { } since there's no ViewModel lifecycle on iOS.
 * Creates a new instance each time it's requested.
 */
actual inline fun <reified T : Any> Module.viewModelFactory(
    crossinline definition: Definition<T>
): KoinDefinition<T> = factory { definition(it) }

/**
 * iOS implementation: Uses koinInject() to get the factory instance.
 * Creates a new instance for each composable scope.
 */
@Composable
actual inline fun <reified T : Any> getViewModel(): T = koinInject()
