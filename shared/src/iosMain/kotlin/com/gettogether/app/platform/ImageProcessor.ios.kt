package com.gettogether.app.platform

actual class ImageProcessor {
    actual suspend fun processImage(
        sourceUri: String,
        maxSize: Int,
        targetSizeKB: Int
    ): ImageProcessingResult {
        // TODO: Implement iOS image processing with UIImage
        return ImageProcessingResult.Error("iOS image processing not yet implemented")
    }
}
