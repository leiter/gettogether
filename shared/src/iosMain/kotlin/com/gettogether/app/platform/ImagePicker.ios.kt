@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.gettogether.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSLog
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.writeToFile
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTTypeImage
import platform.darwin.NSObject

actual class ImagePicker {
    companion object {
        private const val TAG = "ImagePicker"
    }

    // Strong reference to delegate — PHPickerViewController.delegate is weak,
    // so without this the delegate gets garbage collected and callbacks never fire.
    private var currentDelegate: ImagePickerDelegate? = null

    actual fun pickImage(onResult: (ImagePickerResult) -> Unit) {
        // Get the root view controller
        val keyWindow = UIApplication.sharedApplication.keyWindow
        val rootViewController = keyWindow?.rootViewController

        if (rootViewController == null) {
            NSLog("$TAG: Cannot get root view controller")
            onResult(ImagePickerResult.Error("Cannot present image picker"))
            return
        }

        // Find the topmost presented view controller
        var topController: UIViewController = rootViewController
        while (topController.presentedViewController != null) {
            topController = topController.presentedViewController!!
        }

        // Configure PHPicker (iOS 14+)
        val configuration = PHPickerConfiguration()
        configuration.filter = PHPickerFilter.imagesFilter
        configuration.selectionLimit = 1

        val picker = PHPickerViewController(configuration)

        // Create delegate and hold a strong reference (PHPickerViewController.delegate is weak)
        val delegate = ImagePickerDelegate(
            onImageSelected = { imagePath ->
                picker.dismissViewControllerAnimated(true, null)
                currentDelegate = null
                onResult(ImagePickerResult.Success(imagePath))
            },
            onCancelled = {
                picker.dismissViewControllerAnimated(true, null)
                currentDelegate = null
                onResult(ImagePickerResult.Cancelled)
            },
            onError = { message ->
                picker.dismissViewControllerAnimated(true, null)
                currentDelegate = null
                onResult(ImagePickerResult.Error(message))
            }
        )

        currentDelegate = delegate
        picker.delegate = delegate

        // Present the picker
        topController.presentViewController(picker, animated = true, completion = null)
    }
}

/**
 * PHPicker delegate to handle image selection.
 */
private const val DELEGATE_TAG = "ImagePickerDelegate"

private class ImagePickerDelegate(
    private val onImageSelected: (String) -> Unit,
    private val onCancelled: () -> Unit,
    private val onError: (String) -> Unit
) : NSObject(), PHPickerViewControllerDelegateProtocol {

    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        val results = didFinishPicking.filterIsInstance<PHPickerResult>()

        if (results.isEmpty()) {
            onCancelled()
            return
        }

        val result = results.first()
        val itemProvider = result.itemProvider

        // Check if it can load image data using UTTypeImage
        val imageTypeIdentifier = UTTypeImage.identifier
        if (!itemProvider.hasItemConformingToTypeIdentifier(imageTypeIdentifier)) {
            NSLog("$DELEGATE_TAG: Cannot load image from selected item")
            onError("Cannot load selected image")
            return
        }

        // Load the image data
        itemProvider.loadDataRepresentationForTypeIdentifier(imageTypeIdentifier) { data, error ->
            if (error != null) {
                NSLog("$DELEGATE_TAG: Error loading image: ${error.localizedDescription}")
                NSOperationQueue.mainQueue.addOperationWithBlock {
                    onError("Failed to load image: ${error.localizedDescription}")
                }
                return@loadDataRepresentationForTypeIdentifier
            }

            val nsData = data as? NSData
            if (nsData == null) {
                NSLog("$DELEGATE_TAG: Loaded data is null")
                NSOperationQueue.mainQueue.addOperationWithBlock {
                    onError("Invalid image data")
                }
                return@loadDataRepresentationForTypeIdentifier
            }

            // Create UIImage from data
            val uiImage = UIImage.imageWithData(nsData)
            if (uiImage == null) {
                NSLog("$DELEGATE_TAG: Cannot create UIImage from data")
                NSOperationQueue.mainQueue.addOperationWithBlock {
                    onError("Invalid image format")
                }
                return@loadDataRepresentationForTypeIdentifier
            }

            // Save image to temp file so it can be processed
            val tempPath = saveImageToTemp(uiImage)
            if (tempPath != null) {
                NSOperationQueue.mainQueue.addOperationWithBlock {
                    onImageSelected(tempPath)
                }
            } else {
                NSOperationQueue.mainQueue.addOperationWithBlock {
                    onError("Failed to save image")
                }
            }
        }
    }

    private fun saveImageToTemp(image: UIImage): String? {
        return try {
            val paths = NSSearchPathForDirectoriesInDomains(
                NSCachesDirectory,
                NSUserDomainMask,
                true
            )
            val cachesDir = paths.firstOrNull() as? String ?: return null

            val timestamp = (NSDate().timeIntervalSince1970 * 1000).toLong()
            val tempPath = "$cachesDir/picked_image_$timestamp.jpg"

            // Convert to JPEG data (high quality for later processing)
            val jpegData = UIImageJPEGRepresentation(image, 0.95) ?: return null

            val success = jpegData.writeToFile(tempPath, atomically = true)
            if (success) {
                NSLog("$DELEGATE_TAG: Saved picked image to $tempPath")
                tempPath
            } else {
                NSLog("$DELEGATE_TAG: Failed to write picked image to temp file")
                null
            }
        } catch (e: Exception) {
            NSLog("$DELEGATE_TAG: Error saving image to temp: ${e.message}")
            null
        }
    }
}

@Composable
actual fun provideImagePicker(): ImagePicker {
    return remember { ImagePicker() }
}
