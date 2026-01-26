package com.gettogether.app.di

import androidx.compose.runtime.Composable
import org.koin.core.definition.Definition
import org.koin.core.definition.KoinDefinition
import org.koin.core.module.Module

/**
 * Platform-specific ViewModel definition.
 * - On Android: Uses viewModel { } for proper lifecycle scoping
 * - On iOS: Uses factory { } since there's no ViewModel lifecycle
 */
expect inline fun <reified T : Any> Module.viewModelFactory(
    crossinline definition: Definition<T>
): KoinDefinition<T>

/**
 * Platform-specific ViewModel retrieval in Composables.
 * - On Android: Uses koinViewModel() for lifecycle-aware instance
 * - On iOS: Uses koinInject() for factory instance
 */
@Composable
expect inline fun <reified T : Any> getViewModel(): T
