@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.gettogether.app.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.*
import platform.Foundation.*
import platform.UIKit.*
import kotlin.math.max

actual class ImageProcessor {
    companion object {
        private const val TAG = "ImageProcessor"
    }

    actual suspend fun processImage(
        sourceUri: String,
        maxSize: Int,
        targetSizeKB: Int
    ): ImageProcessingResult {
        return try {
            // Load original image - UIImage auto-normalizes EXIF orientation
            val originalImage = UIImage.imageWithContentsOfFile(sourceUri)
                ?: return ImageProcessingResult.Error("Cannot load image from $sourceUri")

            // Resize and center crop
            val resizedImage = resizeAndCrop(originalImage, maxSize)
                ?: return ImageProcessingResult.Error("Failed to resize image")

            // Get output directory
            val paths = NSSearchPathForDirectoriesInDomains(
                NSDocumentDirectory,
                NSUserDomainMask,
                true
            )
            val documentsDir = paths.firstOrNull() as? String
                ?: return ImageProcessingResult.Error("Cannot get documents directory")

            val avatarsDir = "$documentsDir/avatars"
            val fileManager = NSFileManager.defaultManager
            if (!fileManager.fileExistsAtPath(avatarsDir)) {
                fileManager.createDirectoryAtPath(
                    avatarsDir,
                    withIntermediateDirectories = true,
                    attributes = null,
                    error = null
                )
            }

            val timestamp = (NSDate().timeIntervalSince1970 * 1000).toLong()
            val outputPath = "$avatarsDir/avatar_$timestamp.jpg"

            // Compress with quality loop (0.9 -> 0.2) to meet target size
            var quality = 0.9
            var jpegData: NSData?
            var fileSizeKB: Long

            do {
                jpegData = UIImageJPEGRepresentation(resizedImage, quality)
                fileSizeKB = (jpegData?.length ?: 0uL).toLong() / 1024
                quality -= 0.1
            } while (fileSizeKB > targetSizeKB && quality > 0.2)

            if (jpegData == null) {
                return ImageProcessingResult.Error("Failed to compress image to JPEG")
            }

            // Write to file
            val success = jpegData.writeToFile(outputPath, atomically = true)
            if (!success) {
                return ImageProcessingResult.Error("Failed to write image file")
            }

            NSLog("$TAG: Processed image saved to $outputPath (${jpegData.length} bytes)")
            ImageProcessingResult.Success(
                filePath = outputPath,
                sizeBytes = jpegData.length.toLong()
            )
        } catch (e: Exception) {
            NSLog("$TAG: Image processing failed: ${e.message}")
            ImageProcessingResult.Error("Image processing failed: ${e.message}")
        }
    }

    private fun resizeAndCrop(image: UIImage, size: Int): UIImage? {
        val width = image.size.useContents { width }
        val height = image.size.useContents { height }

        if (width <= 0.0 || height <= 0.0) return null

        // Calculate scale to fill the target size (for center crop)
        val scale = max(size.toDouble() / width, size.toDouble() / height)
        val scaledWidth = (width * scale)
        val scaledHeight = (height * scale)

        // First, scale the image
        val scaledSize = CGSizeMake(scaledWidth, scaledHeight)
        UIGraphicsBeginImageContextWithOptions(scaledSize, false, 1.0)
        image.drawInRect(CGRectMake(0.0, 0.0, scaledWidth, scaledHeight))
        val scaledImage = UIGraphicsGetImageFromCurrentImageContext()
        UIGraphicsEndImageContext()

        if (scaledImage == null) return null

        // Now center-crop to exact size
        val cropX = (scaledWidth - size) / 2.0
        val cropY = (scaledHeight - size) / 2.0
        val cropRect = CGRectMake(cropX, cropY, size.toDouble(), size.toDouble())

        val cgImage = scaledImage.CGImage ?: return null
        val croppedCGImage = CGImageCreateWithImageInRect(cgImage, cropRect) ?: return null

        return UIImage.imageWithCGImage(croppedCGImage)
    }
}
