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

    /**
     * Get the path where daemon stores conversation files.
     * Path pattern: files/{accountId}/conversation_data/{conversationId}/{fileId}
     * @return Absolute file path if it exists, null otherwise
     */
    fun getConversationFilePath(accountId: String, conversationId: String, fileId: String): String?

    /**
     * Save a file to public storage (Downloads/gettogether folder).
     * On Android 10+: Uses MediaStore API (no permission needed).
     * On Android 9 and below: Uses direct file access (needs WRITE_EXTERNAL_STORAGE).
     * @param sourcePath The source file path to copy from
     * @param fileName The desired file name in Downloads
     * @return Result with the saved file path on success, or error message on failure
     */
    suspend fun saveToPublicStorage(sourcePath: String, fileName: String): Result<String>
}
