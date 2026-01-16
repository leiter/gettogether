package com.gettogether.app.platform

/**
 * Platform-specific export path provider for account backups.
 */
expect class ExportPathProvider {
    /**
     * Get the directory where backup files should be saved.
     * On Android: app's files/exports directory (internal storage)
     * On iOS: Documents directory
     */
    fun getExportDirectory(): String
}

/**
 * Get current timestamp in milliseconds.
 */
expect fun currentTimeMillis(): Long

/**
 * Generate a unique filename for an account export.
 */
fun generateExportFilename(): String {
    val timestamp = currentTimeMillis()
    return "gettogether_backup_$timestamp.gz"
}
