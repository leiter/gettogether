package com.gettogether.app.platform

actual class FileHelper {
    actual suspend fun copyUriToSendableFile(uri: String, conversationId: String): String {
        // TODO: Implement iOS file handling
        throw UnsupportedOperationException("iOS FileHelper not yet implemented")
    }

    actual fun getDownloadPath(conversationId: String, fileName: String): String {
        // TODO: Implement iOS file handling
        throw UnsupportedOperationException("iOS FileHelper not yet implemented")
    }

    actual fun fileExists(path: String): Boolean {
        // TODO: Implement iOS file handling
        return false
    }

    actual fun getMimeType(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "heic", "heif" -> "image/heic"
            else -> "application/octet-stream"
        }
    }
}
