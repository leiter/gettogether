package com.gettogether.app.util

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.*

/**
 * File-based logger for iOS debugging.
 * Writes logs to Documents folder where they can be accessed via Files app.
 * Immediately flushes to disk to ensure logs survive crashes.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
object IosFileLogger {
    private var fileHandle: NSFileHandle? = null
    private var logFilePath: String? = null
    private val dateFormatter = NSDateFormatter().apply {
        dateFormat = "HH:mm:ss.SSS"
    }

    enum class Level(val tag: String) {
        DEBUG("D"),
        INFO("I"),
        WARN("W"),
        ERROR("E")
    }

    /**
     * Initialize the file logger. Creates a new log file with timestamp.
     * Call this early in app startup.
     */
    fun initialize() {
        if (fileHandle != null) return

        val fileManager = NSFileManager.defaultManager
        val documentsPath = NSSearchPathForDirectoriesInDomains(
            NSDocumentDirectory,
            NSUserDomainMask,
            true
        ).firstOrNull() as? String ?: return

        val timestamp = NSDateFormatter().apply {
            dateFormat = "yyyy-MM-dd_HH-mm-ss"
        }.stringFromDate(NSDate())

        val fileName = "gettogether_debug_$timestamp.log"
        logFilePath = "$documentsPath/$fileName"

        // Create empty file
        fileManager.createFileAtPath(logFilePath!!, null, null)

        // Open for writing
        fileHandle = NSFileHandle.fileHandleForWritingAtPath(logFilePath!!)

        // Write header
        val header = """
            |=====================================
            |GetTogether iOS Debug Log
            |Started: ${NSDate()}
            |=====================================
            |
        """.trimMargin()
        writeToFile(header)

        NSLog("IosFileLogger: Initialized at $logFilePath")
    }

    /**
     * Log a debug message
     */
    fun d(tag: String, message: String) = log(Level.DEBUG, tag, message)

    /**
     * Log an info message
     */
    fun i(tag: String, message: String) = log(Level.INFO, tag, message)

    /**
     * Log a warning message
     */
    fun w(tag: String, message: String) = log(Level.WARN, tag, message)

    /**
     * Log an error message
     */
    fun e(tag: String, message: String) = log(Level.ERROR, tag, message)

    /**
     * Log an error with exception
     */
    fun e(tag: String, message: String, throwable: Throwable) {
        log(Level.ERROR, tag, "$message: ${throwable.message}")
        throwable.stackTraceToString().lines().forEach { line ->
            log(Level.ERROR, tag, "  $line")
        }
    }

    /**
     * Core logging function
     */
    fun log(level: Level, tag: String, message: String) {
        // Always log to NSLog as well
        NSLog("${level.tag}/$tag: $message")

        // Write to file
        if (fileHandle == null) {
            initialize()
        }

        val timestamp = dateFormatter.stringFromDate(NSDate())
        val line = "[$timestamp] ${level.tag}/$tag: $message\n"
        writeToFile(line)
    }

    private fun writeToFile(text: String) {
        val handle = fileHandle ?: return

        val data = text.encodeToByteArray()
        data.usePinned { pinned ->
            val nsData = NSData.create(
                bytes = pinned.addressOf(0),
                length = data.size.toULong()
            )
            handle.writeData(nsData)
            // Immediately flush to ensure crash-safe logging
            handle.synchronizeFile()
        }
    }

    /**
     * Close the log file. Call on app termination if needed.
     */
    fun close() {
        fileHandle?.closeFile()
        fileHandle = null
        logFilePath = null
    }

    /**
     * Get the path to the current log file
     */
    fun getLogFilePath(): String? = logFilePath
}
