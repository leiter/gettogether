package com.gettogether.app.platform

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
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
        val file = File(path)
        if (file.exists()) {
            return true
        }
        // Also check for .tmp version (daemon writes .tmp while downloading)
        val tmpFile = File("$path.tmp")
        if (tmpFile.exists()) {
            // Rename .tmp to final name if it seems complete (has content)
            if (tmpFile.length() > 0) {
                try {
                    tmpFile.renameTo(file)
                    return file.exists()
                } catch (e: Exception) {
                    // If rename fails, still return true since data exists
                    return true
                }
            }
        }
        return false
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

    actual suspend fun saveToPublicStorage(sourcePath: String, fileName: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val sourceFile = File(sourcePath)
            if (!sourceFile.exists()) {
                return@withContext Result.failure(Exception("Source file not found"))
            }

            val mimeType = getMimeType(fileName)
            val savedPath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ - Use MediaStore API
                saveToMediaStore(sourceFile, fileName, mimeType)
            } else {
                // Android 9 and below - Direct file access
                saveToDownloadsDirectory(sourceFile, fileName)
            }

            Result.success(savedPath)
        } catch (e: Exception) {
            println("FileHelper.saveToPublicStorage: Error - ${e.message}")
            Result.failure(e)
        }
    }

    private fun saveToMediaStore(sourceFile: File, fileName: String, mimeType: String): String {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/gettogether")
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            ?: throw Exception("Failed to create MediaStore entry")

        resolver.openOutputStream(uri)?.use { outputStream ->
            FileInputStream(sourceFile).use { inputStream ->
                inputStream.copyTo(outputStream)
            }
        } ?: throw Exception("Failed to open output stream")

        // Return a user-friendly path description
        return "${Environment.DIRECTORY_DOWNLOADS}/gettogether/$fileName"
    }

    @Suppress("DEPRECATION")
    private fun saveToDownloadsDirectory(sourceFile: File, fileName: String): String {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val getTogetherDir = File(downloadsDir, "gettogether").also { it.mkdirs() }

        // Handle filename conflicts
        var destFile = File(getTogetherDir, fileName)
        var counter = 1
        val baseName = fileName.substringBeforeLast('.', fileName)
        val extension = fileName.substringAfterLast('.', "")

        while (destFile.exists()) {
            val newName = if (extension.isNotEmpty()) {
                "${baseName}_$counter.$extension"
            } else {
                "${baseName}_$counter"
            }
            destFile = File(getTogetherDir, newName)
            counter++
        }

        FileInputStream(sourceFile).use { inputStream ->
            FileOutputStream(destFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }

        return destFile.absolutePath
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
