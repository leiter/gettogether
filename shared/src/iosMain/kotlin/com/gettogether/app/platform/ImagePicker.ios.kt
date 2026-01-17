package com.gettogether.app.platform

import androidx.compose.runtime.Composable

actual class ImagePicker {
    actual fun pickImage(onResult: (ImagePickerResult) -> Unit) {
        // TODO: Implement iOS image picker using UIDocumentPickerViewController
        onResult(ImagePickerResult.Error("iOS image picker not yet implemented"))
    }
}

@Composable
actual fun provideImagePicker(): ImagePicker {
    return ImagePicker()
}
