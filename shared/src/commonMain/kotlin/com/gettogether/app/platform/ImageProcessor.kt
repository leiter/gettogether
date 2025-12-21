package com.gettogether.app.platform

/**
 * Result of image processing
 */
sealed class ImageProcessingResult {
    data class Success(val filePath: String, val sizeBytes: Long) : ImageProcessingResult()
    data class Error(val message: String) : ImageProcessingResult()
}

/**
 * Platform-specific image processing (resize, compress)
 */
expect class ImageProcessor {
    /**
     * Process an image: resize to maxSize x maxSize, compress to target quality
     * @param sourceUri Source image URI
     * @param maxSize Target size (256x256)
     * @param targetSizeKB Target file size in KB (~100KB)
     * @return Processed image file path in app storage
     */
    suspend fun processImage(
        sourceUri: String,
        maxSize: Int = 256,
        targetSizeKB: Int = 100
    ): ImageProcessingResult
}
