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
 * Integration tests for JamiBridge data marshalling across the JNI boundary.
 * These tests verify that strings, special characters, and data structures
 * are correctly converted between Kotlin and C++ through SWIG bindings.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class JamiBridgeDataMarshallingTest {

    private lateinit var context: Context
    private lateinit var bridge: SwigJamiBridge
    private lateinit var testDataPath: String
    private val createdAccounts = mutableListOf<String>()

    @Before
    fun setUp() = runTest {
        context = ApplicationProvider.getApplicationContext()
        bridge = SwigJamiBridge(context)

        // Create isolated test data directory
        testDataPath = File(context.cacheDir, "jami-test-${System.currentTimeMillis()}").absolutePath
        File(testDataPath).mkdirs()

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

            // Note: We don't call stopDaemon() - see JamiBridgeDaemonLifecycleTest for explanation
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
    }

    @Test
    fun testSimpleAsciiString() = runTest {
        // When: Create account with simple ASCII display name
        val displayName = "Simple ASCII Name"
        val accountId = bridge.createAccount(displayName = displayName)
        createdAccounts.add(accountId)

        // Then: Display name is correctly marshalled
        val details = bridge.getAccountDetails(accountId)
        assertThat(details["Account.displayName"]).isEqualTo(displayName)
    }

    @Test
    fun testUtf8ChineseCharacters() = runTest {
        // When: Create account with Chinese characters
        val displayName = "测试账户 Test Account"
        val accountId = bridge.createAccount(displayName = displayName)
        createdAccounts.add(accountId)

        // Then: Chinese characters are correctly marshalled
        val details = bridge.getAccountDetails(accountId)
        assertThat(details["Account.displayName"]).isEqualTo(displayName)
    }

    @Test
    fun testUtf8ArabicCharacters() = runTest {
        // When: Create account with Arabic characters
        val displayName = "حساب تجريبي"
        val accountId = bridge.createAccount(displayName = displayName)
        createdAccounts.add(accountId)

        // Then: Arabic characters are correctly marshalled
        val details = bridge.getAccountDetails(accountId)
        assertThat(details["Account.displayName"]).isEqualTo(displayName)
    }

    @Test
    fun testEmojis() = runTest {
        // When: Create account with emojis
        val displayName = "Test Account 🌍 🚀 💬"
        val accountId = bridge.createAccount(displayName = displayName)
        createdAccounts.add(accountId)

        // Then: Emojis are correctly marshalled
        val details = bridge.getAccountDetails(accountId)
        assertThat(details["Account.displayName"]).isEqualTo(displayName)
    }

    @Test
    fun testSpecialCharacters() = runTest {
        // When: Create account with special characters
        val displayName = "Test!@#$%^&*()_+-=[]{}|;':\",./<>?"
        val accountId = bridge.createAccount(displayName = displayName)
        createdAccounts.add(accountId)

        // Then: Special characters are correctly marshalled
        val details = bridge.getAccountDetails(accountId)
        assertThat(details["Account.displayName"]).isEqualTo(displayName)
    }

    @Test
    fun testEmptyString() = runTest {
        // When: Create account with empty display name
        val displayName = ""
        val accountId = bridge.createAccount(displayName = displayName)
        createdAccounts.add(accountId)

        // Then: Empty string is correctly marshalled
        val details = bridge.getAccountDetails(accountId)
        assertThat(details["Account.displayName"]).isEqualTo(displayName)
    }

    @Test
    fun testLongString() = runTest {
        // When: Create account with long display name (1000 characters)
        val displayName = "A".repeat(1000)
        val accountId = bridge.createAccount(displayName = displayName)
        createdAccounts.add(accountId)

        // Then: Long string is correctly marshalled
        val details = bridge.getAccountDetails(accountId)
        assertThat(details["Account.displayName"]).isEqualTo(displayName)
    }

    @Test
    fun testVeryLongString() = runTest {
        // When: Create account with very long display name (10000 characters)
        val displayName = "B".repeat(10000)
        val accountId = bridge.createAccount(displayName = displayName)
        createdAccounts.add(accountId)

        // Then: Very long string is correctly marshalled
        val details = bridge.getAccountDetails(accountId)
        assertThat(details["Account.displayName"]).isEqualTo(displayName)
    }

    @Test
    fun testNewlineCharacters() = runTest {
        // When: Create account with newline characters
        val displayName = "Line1\nLine2\nLine3"
        val accountId = bridge.createAccount(displayName = displayName)
        createdAccounts.add(accountId)

        // Then: Newlines are correctly marshalled
        val details = bridge.getAccountDetails(accountId)
        assertThat(details["Account.displayName"]).isEqualTo(displayName)
    }

    @Test
    fun testTabCharacters() = runTest {
        // When: Create account with tab characters
        val displayName = "Col1\tCol2\tCol3"
        val accountId = bridge.createAccount(displayName = displayName)
        createdAccounts.add(accountId)

        // Then: Tabs are correctly marshalled
        val details = bridge.getAccountDetails(accountId)
        assertThat(details["Account.displayName"]).isEqualTo(displayName)
    }

    @Test
    fun testMapDataStructure() = runTest {
        // Given: Created account
        val accountId = bridge.createAccount(displayName = "Map Test")
        createdAccounts.add(accountId)

        // When: Get account details (returns Map<String, String>)
        val details = bridge.getAccountDetails(accountId)

        // Then: Map is correctly marshalled from C++
        assertThat(details).isInstanceOf(Map::class.java)
        assertThat(details).isNotEmpty()
        assertThat(details.keys).contains("Account.displayName")
        assertThat(details.values).isNotEmpty()
    }

    @Test
    fun testListDataStructure() = runTest {
        // When: Get account IDs (returns List<String>)
        val accountId1 = bridge.createAccount(displayName = "List Test 1")
        val accountId2 = bridge.createAccount(displayName = "List Test 2")
        createdAccounts.addAll(listOf(accountId1, accountId2))

        val accountIds = bridge.getAccountIds()

        // Then: List is correctly marshalled from C++
        assertThat(accountIds).isInstanceOf(List::class.java)
        assertThat(accountIds).isNotEmpty()
        assertThat(accountIds).containsAtLeast(accountId1, accountId2)
    }

    @Test
    fun testMixedUnicodeAndAscii() = runTest {
        // When: Create account with mixed Unicode and ASCII
        val displayName = "Hello مرحبا 你好 Привет こんにちは"
        val accountId = bridge.createAccount(displayName = displayName)
        createdAccounts.add(accountId)

        // Then: Mixed characters are correctly marshalled
        val details = bridge.getAccountDetails(accountId)
        assertThat(details["Account.displayName"]).isEqualTo(displayName)
    }

    @Test
    fun testRepeatedMarshalling() = runTest {
        // Given: Account with specific display name
        val displayName = "Repeated Marshalling Test"
        val accountId = bridge.createAccount(displayName = displayName)
        createdAccounts.add(accountId)

        // When: Retrieve details multiple times
        val details1 = bridge.getAccountDetails(accountId)
        val details2 = bridge.getAccountDetails(accountId)
        val details3 = bridge.getAccountDetails(accountId)

        // Then: All retrievals return the same data
        assertThat(details1["Account.displayName"]).isEqualTo(displayName)
        assertThat(details2["Account.displayName"]).isEqualTo(displayName)
        assertThat(details3["Account.displayName"]).isEqualTo(displayName)
    }
}
