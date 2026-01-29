@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.gettogether.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSLog
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSURL
import platform.Foundation.timeIntervalSince1970
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTTypeGZIP
import platform.UniformTypeIdentifiers.UTTypeArchive
import platform.UniformTypeIdentifiers.UTTypeData
import platform.UniformTypeIdentifiers.UTTypeItem
import platform.darwin.NSObject

actual class FilePicker {
    companion object {
        private const val TAG = "FilePicker"
    }

    actual fun pickFile(onResult: (FilePickerResult) -> Unit) {
        val keyWindow = UIApplication.sharedApplication.keyWindow
        val rootViewController = keyWindow?.rootViewController

        if (rootViewController == null) {
            NSLog("$TAG: Cannot get root view controller")
            onResult(FilePickerResult.Error("Cannot present file picker"))
            return
        }

        // Find the topmost presented view controller
        var topController: UIViewController = rootViewController
        while (topController.presentedViewController != null) {
            topController = topController.presentedViewController!!
        }

        // Configure document picker for gzip/archive files and all file types
        val contentTypes = listOf(UTTypeGZIP, UTTypeArchive, UTTypeData, UTTypeItem)
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = contentTypes)

        // Create delegate
        val delegate = DocumentPickerDelegate(
            onFileSelected = { url ->
                picker.dismissViewControllerAnimated(true, null)
                val cachePath = copyFileToCacheDir(url)
                if (cachePath != null) {
                    onResult(FilePickerResult.Success(cachePath))
                } else {
                    onResult(FilePickerResult.Error("Failed to copy selected file"))
                }
            },
            onCancelled = {
                picker.dismissViewControllerAnimated(true, null)
                onResult(FilePickerResult.Cancelled)
            }
        )

        picker.delegate = delegate

        // Present the picker
        topController.presentViewController(picker, animated = true, completion = null)
    }

    private fun copyFileToCacheDir(url: NSURL): String? {
        return try {
            // Start accessing security-scoped resource
            val accessGranted = url.startAccessingSecurityScopedResource()

            val sourcePath = url.path ?: return null
            val fileName = url.lastPathComponent ?: "imported_file"

            val paths = NSSearchPathForDirectoriesInDomains(
                NSCachesDirectory,
                NSUserDomainMask,
                true
            )
            val cachesDir = paths.firstOrNull() as? String ?: return null

            val timestamp = (NSDate().timeIntervalSince1970 * 1000).toLong()
            val destPath = "$cachesDir/picked_file_${timestamp}_$fileName"

            val fileManager = NSFileManager.defaultManager
            val success = fileManager.copyItemAtPath(sourcePath, toPath = destPath, error = null)

            // Stop accessing security-scoped resource
            if (accessGranted) {
                url.stopAccessingSecurityScopedResource()
            }

            if (success) {
                NSLog("$TAG: Copied picked file to $destPath")
                destPath
            } else {
                NSLog("$TAG: Failed to copy picked file to cache")
                null
            }
        } catch (e: Exception) {
            NSLog("$TAG: Error copying file to cache: ${e.message}")
            null
        }
    }
}

private const val DELEGATE_TAG = "DocumentPickerDelegate"

private class DocumentPickerDelegate(
    private val onFileSelected: (NSURL) -> Unit,
    private val onCancelled: () -> Unit
) : NSObject(), UIDocumentPickerDelegateProtocol {

    override fun documentPicker(
        controller: UIDocumentPickerViewController,
        didPickDocumentsAtURLs: List<*>
    ) {
        val urls = didPickDocumentsAtURLs.filterIsInstance<NSURL>()
        if (urls.isEmpty()) {
            NSLog("$DELEGATE_TAG: No URLs in selection")
            onCancelled()
            return
        }
        val selectedUrl = urls.first()
        NSLog("$DELEGATE_TAG: File selected: ${selectedUrl.path}")
        onFileSelected(selectedUrl)
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        NSLog("$DELEGATE_TAG: Picker cancelled")
        onCancelled()
    }
}

@Composable
actual fun provideFilePicker(): FilePicker {
    return remember { FilePicker() }
}
