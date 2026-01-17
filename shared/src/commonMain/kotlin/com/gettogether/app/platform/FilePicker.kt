package com.gettogether.app.platform

import androidx.compose.runtime.Composable

/**
 * Result of file selection
 */
sealed class FilePickerResult {
    data class Success(val path: String) : FilePickerResult()
    data class Error(val message: String) : FilePickerResult()
    object Cancelled : FilePickerResult()
}

/**
 * Platform-specific file picker abstraction for selecting backup archive files
 */
expect class FilePicker {
    /**
     * Launch the file picker to select a backup archive file (.gz).
     * Result will be delivered via callback.
     */
    fun pickFile(onResult: (FilePickerResult) -> Unit)
}

/**
 * Provides platform-specific FilePicker instance.
 * Must be called from a Composable context to access Activity on Android.
 */
@Composable
expect fun provideFilePicker(): FilePicker
