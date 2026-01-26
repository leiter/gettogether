package com.gettogether.app.di

import androidx.compose.runtime.Composable
import org.koin.compose.koinInject
import org.koin.core.definition.Definition
import org.koin.core.definition.KoinDefinition
import org.koin.core.module.Module

/**
 * Android implementation: Uses factory for ViewModel definitions.
 *
 * Note: We use factory instead of viewModel DSL for cross-platform compatibility.
 * The expect declaration uses T : Any, but Android's viewModel DSL requires T : ViewModel.
 * Using factory works reliably across both platforms.
 */
actual inline fun <reified T : Any> Module.viewModelFactory(
    crossinline definition: Definition<T>
): KoinDefinition<T> = factory { definition(it) }

/**
 * Android implementation: Uses koinInject() to retrieve ViewModel instances.
 *
 * Note: This uses the same approach as iOS for cross-platform consistency.
 * ViewModels are created fresh on each composition, similar to iOS behavior.
 * For state persistence across configuration changes, use rememberSaveable
 * or SavedStateHandle in individual ViewModels.
 */
@Composable
actual inline fun <reified T : Any> getViewModel(): T = koinInject()
