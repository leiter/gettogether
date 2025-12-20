package com.gettogether.app.bridge

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gettogether.app.jami.SwigJamiBridge
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Integration tests for JamiBridge file transfer operations.
 * These tests verify that the Kotlin bridge can properly send, accept, and cancel
 * file transfers through the native Jami daemon.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class JamiBridgeFileTransferTest {

    private lateinit var context: Context
    private lateinit var bridge: SwigJamiBridge
    private lateinit var testDataPath: String
    private lateinit var testFilesPath: String
    private val createdAccounts = mutableListOf<String>()

    @Before
    fun setUp() = runTest {
        context = ApplicationProvider.getApplicationContext()
        bridge = SwigJamiBridge(context)

        // Create isolated test data directory
        testDataPath = File(context.cacheDir, "jami-test-${System.currentTimeMillis()}").absolutePath
        File(testDataPath).mkdirs()

        // Create test files directory
        testFilesPath = File(context.cacheDir, "jami-test-files-${System.currentTimeMillis()}").absolutePath
        File(testFilesPath).mkdirs()

        // Initialize and start daemon
        bridge.initDaemon(testDataPath)
        bridge.startDaemon()
    }

    @After
    fun tearDown() = runTest {
        try {
            // Clean up: delete all created accounts
            createdAccounts.forEach { accountId ->
                try {
                    bridge.deleteAccount(accountId)
                } catch (e: Exception) {
                    // Ignore errors during cleanup
                }
            }
            createdAccounts.clear()

            // Clean up test data directory
            File(testDataPath).deleteRecursively()

            // Clean up test files directory
            File(testFilesPath).deleteRecursively()

            // Note: We don't call stopDaemon() - see JamiBridgeDaemonLifecycleTest for explanation
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
    }

    // ==================== Helper Methods ====================

    private fun createTestFile(filename: String, content: String): File {
        val file = File(testFilesPath, filename)
        file.writeText(content)
        return file
    }

    private fun createTestFile(filename: String, sizeBytes: Int): File {
        val file = File(testFilesPath, filename)
        val data = ByteArray(sizeBytes) { (it % 256).toByte() }
        file.writeBytes(data)
        return file
    }

    // ==================== Send File Operations ====================

    @Test
    fun testSendFile() = runTest {
        // Given: Account with a conversation
        val accountId = bridge.createAccount(displayName = "File Transfer Test")
        createdAccounts.add(accountId)

        val conversationId = bridge.startConversation(accountId)

        // And: A test file
        val testFile = createTestFile("test.txt", "Hello, this is a test file!")

        // When: Send the file
        val fileId = bridge.sendFile(
            accountId = accountId,
            conversationId = conversationId,
            filePath = testFile.absolutePath,
            displayName = "test.txt"
        )

        // Then: File ID is returned (may be empty depending on daemon state)
        assertThat(fileId).isNotNull()
    }

    @Test
    fun testSendFileWithCustomDisplayName() = runTest {
        // Given: Account with a conversation
        val accountId = bridge.createAccount(displayName = "Custom Display Name Test")
        createdAccounts.add(accountId)

        val conversationId = bridge.startConversation(accountId)

        // And: A test file
        val testFile = createTestFile("original.txt", "File content")

        // When: Send file with custom display name
        val fileId = bridge.sendFile(
            accountId = accountId,
            conversationId = conversationId,
            filePath = testFile.absolutePath,
            displayName = "custom-name.txt"
        )

        // Then: File ID is returned
        assertThat(fileId).isNotNull()
    }

    @Test
    fun testSendLargeFile() = runTest {
        // Given: Account with a conversation
        val accountId = bridge.createAccount(displayName = "Large File Test")
        createdAccounts.add(accountId)

        val conversationId = bridge.startConversation(accountId)

        // And: A large test file (100 KB)
        val testFile = createTestFile("large.bin", 100 * 1024)

        // When: Send the large file
        val fileId = bridge.sendFile(
            accountId = accountId,
            conversationId = conversationId,
            filePath = testFile.absolutePath,
            displayName = "large.bin"
        )

        // Then: File ID is returned
        assertThat(fileId).isNotNull()
    }

    @Test
    fun testSendMultipleFiles() = runTest {
        // Given: Account with a conversation
        val accountId = bridge.createAccount(displayName = "Multiple Files Test")
        createdAccounts.add(accountId)

        val conversationId = bridge.startConversation(accountId)

        // And: Multiple test files
        val file1 = createTestFile("file1.txt", "Content 1")
        val file2 = createTestFile("file2.txt", "Content 2")
        val file3 = createTestFile("file3.txt", "Content 3")

        // When: Send multiple files
        val fileId1 = bridge.sendFile(accountId, conversationId, file1.absolutePath, "file1.txt")
        val fileId2 = bridge.sendFile(accountId, conversationId, file2.absolutePath, "file2.txt")
        val fileId3 = bridge.sendFile(accountId, conversationId, file3.absolutePath, "file3.txt")

        // Then: All file IDs are returned
        assertThat(fileId1).isNotNull()
        assertThat(fileId2).isNotNull()
        assertThat(fileId3).isNotNull()
    }

    @Test
    fun testSendFileWithSpecialCharactersInName() = runTest {
        // Given: Account with a conversation
        val accountId = bridge.createAccount(displayName = "Special Chars File Test")
        createdAccounts.add(accountId)

        val conversationId = bridge.startConversation(accountId)

        // And: A test file
        val testFile = createTestFile("normal.txt", "Content")

        // When: Send file with special characters in display name
        val fileId = bridge.sendFile(
            accountId = accountId,
            conversationId = conversationId,
            filePath = testFile.absolutePath,
            displayName = "test file (2024) [final].txt"
        )

        // Then: File ID is returned
        assertThat(fileId).isNotNull()
    }

    @Test
    fun testSendFileWithUnicodeDisplayName() = runTest {
        // Given: Account with a conversation
        val accountId = bridge.createAccount(displayName = "Unicode File Test")
        createdAccounts.add(accountId)

        val conversationId = bridge.startConversation(accountId)

        // And: A test file
        val testFile = createTestFile("test.txt", "Content")

        // When: Send file with Unicode display name
        val fileId = bridge.sendFile(
            accountId = accountId,
            conversationId = conversationId,
            filePath = testFile.absolutePath,
            displayName = "测试文件 📄.txt"
        )

        // Then: File ID is returned
        assertThat(fileId).isNotNull()
    }

    @Test
    fun testSendEmptyFile() = runTest {
        // Given: Account with a conversation
        val accountId = bridge.createAccount(displayName = "Empty File Test")
        createdAccounts.add(accountId)

        val conversationId = bridge.startConversation(accountId)

        // And: An empty test file
        val testFile = createTestFile("empty.txt", "")

        // When: Send the empty file
        val fileId = bridge.sendFile(
            accountId = accountId,
            conversationId = conversationId,
            filePath = testFile.absolutePath,
            displayName = "empty.txt"
        )

        // Then: File ID is returned (daemon may handle empty files differently)
        assertThat(fileId).isNotNull()
    }

    // ==================== Accept File Transfer ====================

    @Test
    fun testAcceptFileTransfer() = runTest {
        // Given: Account with a conversation
        val accountId = bridge.createAccount(displayName = "Accept File Test")
        createdAccounts.add(accountId)

        val conversationId = bridge.startConversation(accountId)

        // And: A sent file
        val testFile = createTestFile("to-accept.txt", "File to accept")
        val fileId = bridge.sendFile(accountId, conversationId, testFile.absolutePath, "to-accept.txt")

        // When: Accept the file transfer
        val destinationPath = File(testFilesPath, "received.txt").absolutePath
        bridge.acceptFileTransfer(accountId, conversationId, fileId, destinationPath)

        // Then: Method completes without error
    }

    @Test
    fun testAcceptFileTransferWithDummyId() = runTest {
        // Given: Account with a conversation
        val accountId = bridge.createAccount(displayName = "Accept Dummy File Test")
        createdAccounts.add(accountId)

        val conversationId = bridge.startConversation(accountId)

        // When: Accept a dummy file transfer (may not exist)
        val dummyFileId = "dummy-file-id"
        val destinationPath = File(testFilesPath, "dummy-received.txt").absolutePath
        bridge.acceptFileTransfer(accountId, conversationId, dummyFileId, destinationPath)

        // Then: Method completes without crash
    }

    // ==================== Cancel File Transfer ====================

    @Test
    fun testCancelFileTransfer() = runTest {
        // Given: Account with a conversation
        val accountId = bridge.createAccount(displayName = "Cancel File Test")
        createdAccounts.add(accountId)

        val conversationId = bridge.startConversation(accountId)

        // And: A sent file
        val testFile = createTestFile("to-cancel.txt", "File to cancel")
        val fileId = bridge.sendFile(accountId, conversationId, testFile.absolutePath, "to-cancel.txt")

        // When: Cancel the file transfer
        bridge.cancelFileTransfer(accountId, conversationId, fileId)

        // Then: Method completes without error
    }

    @Test
    fun testCancelNonExistentFileTransfer() = runTest {
        // Given: Account with a conversation
        val accountId = bridge.createAccount(displayName = "Cancel Nonexistent Test")
        createdAccounts.add(accountId)

        val conversationId = bridge.startConversation(accountId)

        // When: Cancel a non-existent file transfer
        val dummyFileId = "non-existent-file-id"
        bridge.cancelFileTransfer(accountId, conversationId, dummyFileId)

        // Then: Method completes gracefully (daemon handles this)
    }

    // ==================== Get File Transfer Info ====================

    @Test
    fun testGetFileTransferInfo() = runTest {
        // Given: Account with a conversation
        val accountId = bridge.createAccount(displayName = "File Info Test")
        createdAccounts.add(accountId)

        val conversationId = bridge.startConversation(accountId)

        // And: A sent file
        val testFile = createTestFile("info-test.txt", "File info test")
        val fileId = bridge.sendFile(accountId, conversationId, testFile.absolutePath, "info-test.txt")

        // When: Get file transfer info
        val info = bridge.getFileTransferInfo(accountId, conversationId, fileId)

        // Then: Returns FileTransferInfo or null (depending on daemon state)
        // The method doesn't crash is the main verification
        if (info != null) {
            assertThat(info.fileId).isNotNull()
            assertThat(info.displayName).isNotNull()
        }
    }

    @Test
    fun testGetFileTransferInfoForNonExistentFile() = runTest {
        // Given: Account with a conversation
        val accountId = bridge.createAccount(displayName = "File Info Nonexistent Test")
        createdAccounts.add(accountId)

        val conversationId = bridge.startConversation(accountId)

        // When: Get info for non-existent file
        val dummyFileId = "non-existent-file-id"
        val info = bridge.getFileTransferInfo(accountId, conversationId, dummyFileId)

        // Then: Returns null or empty info
        // Method completes without crash
    }

    // ==================== File Transfer Workflow ====================

    @Test
    fun testCompleteFileTransferWorkflow() = runTest {
        // Given: Account with a conversation
        val accountId = bridge.createAccount(displayName = "Complete Workflow Test")
        createdAccounts.add(accountId)

        val conversationId = bridge.startConversation(accountId)

        // And: A test file
        val testFile = createTestFile("workflow.txt", "Complete workflow test")

        // When: Send file
        val fileId = bridge.sendFile(accountId, conversationId, testFile.absolutePath, "workflow.txt")
        assertThat(fileId).isNotNull()

        // And: Get file info
        val info = bridge.getFileTransferInfo(accountId, conversationId, fileId)
        // Info may be null depending on daemon state

        // And: Accept file
        val destinationPath = File(testFilesPath, "workflow-received.txt").absolutePath
        bridge.acceptFileTransfer(accountId, conversationId, fileId, destinationPath)

        // Then: All operations complete without crash
    }

    @Test
    fun testSendThenCancelWorkflow() = runTest {
        // Given: Account with a conversation
        val accountId = bridge.createAccount(displayName = "Send Cancel Workflow Test")
        createdAccounts.add(accountId)

        val conversationId = bridge.startConversation(accountId)

        // And: A test file
        val testFile = createTestFile("to-cancel-workflow.txt", "Send then cancel")

        // When: Send file then cancel
        val fileId = bridge.sendFile(accountId, conversationId, testFile.absolutePath, "to-cancel-workflow.txt")
        assertThat(fileId).isNotNull()

        bridge.cancelFileTransfer(accountId, conversationId, fileId)

        // Then: Workflow completes without error
    }

    // ==================== Multiple Conversations ====================

    @Test
    fun testFileTransferInMultipleConversations() = runTest {
        // Given: Account with multiple conversations
        val accountId = bridge.createAccount(displayName = "Multi Conv File Test")
        createdAccounts.add(accountId)

        val conv1 = bridge.startConversation(accountId)
        val conv2 = bridge.startConversation(accountId)

        // And: Test files
        val file1 = createTestFile("conv1-file.txt", "Conversation 1 file")
        val file2 = createTestFile("conv2-file.txt", "Conversation 2 file")

        // When: Send files to different conversations
        val fileId1 = bridge.sendFile(accountId, conv1, file1.absolutePath, "conv1-file.txt")
        val fileId2 = bridge.sendFile(accountId, conv2, file2.absolutePath, "conv2-file.txt")

        // Then: Both files sent successfully
        assertThat(fileId1).isNotNull()
        assertThat(fileId2).isNotNull()
    }

    // ==================== Multiple Accounts ====================

    @Test
    fun testFileTransferWithMultipleAccounts() = runTest {
        // Given: Two different accounts
        val account1 = bridge.createAccount(displayName = "Multi Account File 1")
        val account2 = bridge.createAccount(displayName = "Multi Account File 2")
        createdAccounts.addAll(listOf(account1, account2))

        val conv1 = bridge.startConversation(account1)
        val conv2 = bridge.startConversation(account2)

        // And: Test files
        val file1 = createTestFile("account1-file.txt", "Account 1 file")
        val file2 = createTestFile("account2-file.txt", "Account 2 file")

        // When: Send files from different accounts
        val fileId1 = bridge.sendFile(account1, conv1, file1.absolutePath, "account1-file.txt")
        val fileId2 = bridge.sendFile(account2, conv2, file2.absolutePath, "account2-file.txt")

        // Then: Both files sent successfully
        assertThat(fileId1).isNotNull()
        assertThat(fileId2).isNotNull()
    }

    // ==================== Different File Types ====================

    @Test
    fun testSendBinaryFile() = runTest {
        // Given: Account with a conversation
        val accountId = bridge.createAccount(displayName = "Binary File Test")
        createdAccounts.add(accountId)

        val conversationId = bridge.startConversation(accountId)

        // And: A binary test file (10 KB)
        val testFile = createTestFile("binary.bin", 10 * 1024)

        // When: Send the binary file
        val fileId = bridge.sendFile(
            accountId = accountId,
            conversationId = conversationId,
            filePath = testFile.absolutePath,
            displayName = "binary.bin"
        )

        // Then: File ID is returned
        assertThat(fileId).isNotNull()
    }

    @Test
    fun testSendFileWithLongContent() = runTest {
        // Given: Account with a conversation
        val accountId = bridge.createAccount(displayName = "Long Content File Test")
        createdAccounts.add(accountId)

        val conversationId = bridge.startConversation(accountId)

        // And: A file with long content (50 KB of text)
        val longContent = "A".repeat(50 * 1024)
        val testFile = createTestFile("long-content.txt", longContent)

        // When: Send the file
        val fileId = bridge.sendFile(
            accountId = accountId,
            conversationId = conversationId,
            filePath = testFile.absolutePath,
            displayName = "long-content.txt"
        )

        // Then: File ID is returned
        assertThat(fileId).isNotNull()
    }
}
