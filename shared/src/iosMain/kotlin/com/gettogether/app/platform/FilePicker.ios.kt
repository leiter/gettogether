package com.gettogether.app.platform

import androidx.compose.runtime.Composable

actual class FilePicker {
    actual fun pickFile(onResult: (FilePickerResult) -> Unit) {
        // TODO: Implement iOS file picker using UIDocumentPickerViewController
        onResult(FilePickerResult.Error("iOS file picker not yet implemented"))
    }
}

@Composable
actual fun provideFilePicker(): FilePicker {
    return FilePicker()
}
