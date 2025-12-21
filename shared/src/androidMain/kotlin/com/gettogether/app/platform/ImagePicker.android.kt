package com.gettogether.app.platform

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

actual class ImagePicker(
    private val context: Context,
    private val launchPicker: (onResult: (ImagePickerResult) -> Unit) -> Unit
) {
    actual fun pickImage(onResult: (ImagePickerResult) -> Unit) {
        launchPicker(onResult)
    }
}

@Composable
actual fun provideImagePicker(): ImagePicker {
    val context = LocalContext.current

    // Create a mutable callback holder that survives recomposition
    val callbackHolder = remember { mutableListOf<((ImagePickerResult) -> Unit)?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        val callback = callbackHolder.getOrNull(0)
        callbackHolder[0] = null

        when {
            uri != null -> {
                try {
                    // Grant persistent read permission
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: SecurityException) {
                    // Permission not available, continue anyway
                    // The URI should still be readable for this session
                }
                callback?.invoke(ImagePickerResult.Success(uri.toString()))
            }
            else -> callback?.invoke(ImagePickerResult.Cancelled)
        }
    }

    return remember(launcher) {
        ImagePicker(context) { onResult ->
            callbackHolder[0] = onResult
            try {
                launcher.launch("image/*")
            } catch (e: Exception) {
                callbackHolder[0] = null
                onResult(ImagePickerResult.Error("Failed to launch image picker: ${e.message}"))
            }
        }
    }
}
