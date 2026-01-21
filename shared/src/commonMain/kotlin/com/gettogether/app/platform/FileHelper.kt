package com.gettogether.app.platform

/**
 * Platform-specific file helper for handling URI to file path conversions
 * and managing file storage for chat attachments.
 */
expect class FileHelper {
    /**
     * Copy a content URI to a file in app storage suitable for sending.
     * On Android, this copies from a content:// URI to the app's files directory.
     * @param uri The content URI to copy from
     * @param conversationId The conversation ID for organizing files
     * @return Absolute file path of the copied file
     */
    suspend fun copyUriToSendableFile(uri: String, conversationId: String): String

    /**
     * Get the download directory path for received files.
     * @param conversationId The conversation ID for organizing files
     * @param fileName The file name
     * @return Absolute file path where the file should be saved
     */
    fun getDownloadPath(conversationId: String, fileName: String): String

    /**
     * Check if a file exists at the given path.
     */
    fun fileExists(path: String): Boolean

    /**
     * Get the MIME type of a file based on its extension.
     */
    fun getMimeType(fileName: String): String
}
