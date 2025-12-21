package com.gettogether.app.platform

import androidx.compose.runtime.Composable

/**
 * Result of image selection
 */
sealed class ImagePickerResult {
    data class Success(val uri: String) : ImagePickerResult()
    data class Error(val message: String) : ImagePickerResult()
    object Cancelled : ImagePickerResult()
}

/**
 * Platform-specific image picker abstraction
 */
expect class ImagePicker {
    /**
     * Launch the image picker to select from gallery.
     * Result will be delivered via callback.
     */
    fun pickImage(onResult: (ImagePickerResult) -> Unit)
}

/**
 * Provides platform-specific ImagePicker instance.
 * Must be called from a Composable context to access Activity on Android.
 */
@Composable
expect fun provideImagePicker(): ImagePicker
