package com.gettogether.app.platform

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

actual class FileHelper(private val context: Context) {

    private val sentFilesDir: File
        get() = File(context.filesDir, "sent").also { it.mkdirs() }

    private val receivedFilesDir: File
        get() = File(context.filesDir, "received").also { it.mkdirs() }

    actual suspend fun copyUriToSendableFile(uri: String, conversationId: String): String = withContext(Dispatchers.IO) {
        val contentUri = Uri.parse(uri)
        val originalFileName = getFileNameFromUri(contentUri) ?: "image_${UUID.randomUUID()}"

        // Create conversation-specific directory
        val convDir = File(sentFilesDir, conversationId).also { it.mkdirs() }

        // Generate unique file name to avoid conflicts
        val extension = originalFileName.substringAfterLast('.', "")
        val baseName = originalFileName.substringBeforeLast('.', originalFileName)
        val uniqueFileName = "${baseName}_${System.currentTimeMillis()}.$extension"

        val destFile = File(convDir, uniqueFileName)

        context.contentResolver.openInputStream(contentUri)?.use { inputStream ->
            FileOutputStream(destFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        } ?: throw IllegalStateException("Could not open input stream for URI: $uri")

        destFile.absolutePath
    }

    actual fun getDownloadPath(conversationId: String, fileName: String): String {
        val convDir = File(receivedFilesDir, conversationId).also { it.mkdirs() }

        // Generate unique file name if file already exists
        var destFile = File(convDir, fileName)
        var counter = 1
        val baseName = fileName.substringBeforeLast('.', fileName)
        val extension = fileName.substringAfterLast('.', "")

        while (destFile.exists()) {
            val newName = if (extension.isNotEmpty()) {
                "${baseName}_$counter.$extension"
            } else {
                "${baseName}_$counter"
            }
            destFile = File(convDir, newName)
            counter++
        }

        return destFile.absolutePath
    }

    actual fun fileExists(path: String): Boolean {
        return File(path).exists()
    }

    actual fun getMimeType(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: when (extension) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "gif" -> "image/gif"
                "webp" -> "image/webp"
                "heic", "heif" -> "image/heic"
                else -> "application/octet-stream"
            }
    }

    actual fun getConversationFilePath(accountId: String, conversationId: String, fileId: String): String? {
        // Daemon stores files at: files/{accountId}/conversation_data/{conversationId}/{fileId}
        val file = File(context.filesDir, "$accountId/conversation_data/$conversationId/$fileId")
        return if (file.exists()) file.absolutePath else null
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        return cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    it.getString(nameIndex)
                } else null
            } else null
        }
    }
}
