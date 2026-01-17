package com.gettogether.app.platform

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

actual class ImageProcessor(private val context: Context) {

    actual suspend fun processImage(
        sourceUri: String,
        maxSize: Int,
        targetSizeKB: Int
    ): ImageProcessingResult = withContext(Dispatchers.IO) {
        try {
            // Read original image
            val uri = Uri.parse(sourceUri)
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext ImageProcessingResult.Error("Cannot open image")

            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            if (originalBitmap == null) {
                return@withContext ImageProcessingResult.Error("Cannot decode image")
            }

            // Fix orientation using EXIF data
            val rotatedBitmap = fixOrientation(uri, originalBitmap)

            // Resize to maxSize x maxSize (maintaining aspect ratio, center crop)
            val resizedBitmap = resizeAndCrop(rotatedBitmap, maxSize)
            if (rotatedBitmap != originalBitmap) rotatedBitmap.recycle()
            originalBitmap.recycle()

            // Save with compression to meet target size
            val avatarsDir = File(context.filesDir, "avatars")
            avatarsDir.mkdirs()
            val outputFile = File(avatarsDir, "avatar_${System.currentTimeMillis()}.jpg")

            var quality = 90
            var fileSizeKB: Long

            do {
                FileOutputStream(outputFile).use { out ->
                    resizedBitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                }
                fileSizeKB = outputFile.length() / 1024
                quality -= 10
            } while (fileSizeKB > targetSizeKB && quality > 20)

            resizedBitmap.recycle()

            ImageProcessingResult.Success(
                filePath = outputFile.absolutePath,
                sizeBytes = outputFile.length()
            )
        } catch (e: Exception) {
            ImageProcessingResult.Error("Image processing failed: ${e.message}")
        }
    }

    private fun fixOrientation(uri: Uri, bitmap: Bitmap): Bitmap {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val exif = inputStream?.let { ExifInterface(it) }
            inputStream?.close()

            val orientation = exif?.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            ) ?: ExifInterface.ORIENTATION_NORMAL

            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(bitmap, 90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(bitmap, 180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(bitmap, 270f)
                else -> bitmap
            }
        } catch (e: Exception) {
            bitmap
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun resizeAndCrop(bitmap: Bitmap, size: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        // Calculate scale to fill the target size (center crop)
        val scale = max(size.toFloat() / width, size.toFloat() / height)
        val scaledWidth = (width * scale).toInt()
        val scaledHeight = (height * scale).toInt()

        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)

        // Center crop to exact size
        val x = (scaledWidth - size) / 2
        val y = (scaledHeight - size) / 2

        val croppedBitmap = Bitmap.createBitmap(scaledBitmap, x, y, size, size)
        if (scaledBitmap != croppedBitmap) scaledBitmap.recycle()

        return croppedBitmap
    }
}
