package com.gettogether.app.platform

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.io.File
import java.io.FileOutputStream

actual class FilePicker(
    private val context: Context,
    private val launchPicker: (onResult: (FilePickerResult) -> Unit) -> Unit
) {
    actual fun pickFile(onResult: (FilePickerResult) -> Unit) {
        launchPicker(onResult)
    }
}

@Composable
actual fun provideFilePicker(): FilePicker {
    val context = LocalContext.current

    // Create a mutable callback holder that survives recomposition
    val callbackHolder = remember { mutableListOf<((FilePickerResult) -> Unit)?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        val callback = callbackHolder.getOrNull(0)
        callbackHolder[0] = null

        when {
            uri != null -> {
                try {
                    // Copy file content to cache directory
                    // The Jami daemon needs direct file path access
                    val cacheFile = copyUriToCache(context, uri)
                    callback?.invoke(FilePickerResult.Success(cacheFile.absolutePath))
                } catch (e: Exception) {
                    callback?.invoke(FilePickerResult.Error("Failed to read file: ${e.message}"))
                }
            }
            else -> callback?.invoke(FilePickerResult.Cancelled)
        }
    }

    return remember(launcher) {
        FilePicker(context) { onResult ->
            callbackHolder[0] = onResult
            try {
                // Accept gzip files and all files (for flexibility)
                launcher.launch(arrayOf(
                    "application/gzip",
                    "application/x-gzip",
                    "application/octet-stream",
                    "*/*"
                ))
            } catch (e: Exception) {
                callbackHolder[0] = null
                onResult(FilePickerResult.Error("Failed to launch file picker: ${e.message}"))
            }
        }
    }
}

/**
 * Copy content from a URI to the app's cache directory.
 * This is necessary because the Jami daemon needs a direct file path,
 * but content URIs from the document picker are not directly accessible as file paths.
 */
private fun copyUriToCache(context: Context, uri: Uri): File {
    val fileName = getFileNameFromUri(context, uri) ?: "backup_import.gz"
    val cacheFile = File(context.cacheDir, fileName)

    context.contentResolver.openInputStream(uri)?.use { inputStream ->
        FileOutputStream(cacheFile).use { outputStream ->
            inputStream.copyTo(outputStream)
        }
    } ?: throw IllegalStateException("Could not open input stream for URI")

    return cacheFile
}

/**
 * Extract file name from content URI
 */
private fun getFileNameFromUri(context: Context, uri: Uri): String? {
    val cursor = context.contentResolver.query(uri, null, null, null, null)
    return cursor?.use {
        if (it.moveToFirst()) {
            val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0) {
                it.getString(nameIndex)
            } else null
        } else null
    }
}
