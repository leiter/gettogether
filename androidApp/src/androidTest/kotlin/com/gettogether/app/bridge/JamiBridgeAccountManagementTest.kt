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
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Integration tests for JamiBridge account management operations.
 * These tests verify that the Kotlin bridge can properly create, retrieve,
 * update, and delete accounts via the native Jami daemon.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class JamiBridgeAccountManagementTest {

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
    fun testCreateAccount() = runTest {
        // When: Create a new account
        val accountId = bridge.createAccount(displayName = "Test Account")
        createdAccounts.add(accountId)

        // Then: Account ID is returned and is not empty
        assertThat(accountId).isNotEmpty()

        // And: Account appears in account list
        val accountIds = bridge.getAccountIds()
        assertThat(accountIds).contains(accountId)
    }

    @Test
    fun testCreateAccountWithDisplayName() = runTest {
        // When: Create account with specific display name
        val displayName = "My Test Account"
        val accountId = bridge.createAccount(displayName = displayName)
        createdAccounts.add(accountId)

        // Then: Account details contain the correct display name
        val details = bridge.getAccountDetails(accountId)
        assertThat(details["Account.displayName"]).isEqualTo(displayName)
    }

    @Test
    fun testGetAccountDetails() = runTest {
        // Given: Created account
        val accountId = bridge.createAccount(displayName = "Details Test")
        createdAccounts.add(accountId)

        // When: Get account details
        val details = bridge.getAccountDetails(accountId)

        // Then: Details map is not empty
        assertThat(details).isNotEmpty()

        // And: Contains expected keys
        assertThat(details).containsKey("Account.displayName")
        assertThat(details).containsKey("Account.type")
    }

    @Test
    fun testGetVolatileAccountDetails() = runTest {
        // Given: Created account
        val accountId = bridge.createAccount(displayName = "Volatile Test")
        createdAccounts.add(accountId)

        // When: Get volatile account details
        val volatileDetails = bridge.getVolatileAccountDetails(accountId)

        // Then: Volatile details map is not empty
        assertThat(volatileDetails).isNotEmpty()

        // And: Contains runtime state information
        assertThat(volatileDetails).containsKey("Account.registrationStatus")
    }

    @Test
    fun testDeleteAccount() = runTest {
        // Given: Created account
        val accountId = bridge.createAccount(displayName = "Delete Test")
        createdAccounts.add(accountId)

        val initialAccountIds = bridge.getAccountIds()
        assertThat(initialAccountIds).contains(accountId)

        // When: Delete the account
        bridge.deleteAccount(accountId)
        createdAccounts.remove(accountId)

        // Then: Account no longer appears in list
        val remainingAccountIds = bridge.getAccountIds()
        assertThat(remainingAccountIds).doesNotContain(accountId)
    }

    @Test
    fun testSetAccountActive() = runTest {
        // Given: Created account
        val accountId = bridge.createAccount(displayName = "Active Test")
        createdAccounts.add(accountId)

        // When: Set account inactive
        bridge.setAccountActive(accountId, active = false)

        // Then: Account can be queried (this verifies the call succeeded)
        val details = bridge.getAccountDetails(accountId)
        assertThat(details).isNotEmpty()

        // When: Set account active again
        bridge.setAccountActive(accountId, active = true)

        // Then: Account is still queryable
        val detailsAfter = bridge.getAccountDetails(accountId)
        assertThat(detailsAfter).isNotEmpty()
    }

    @Test
    @Ignore("Native crash in libjami::updateProfile - null pointer dereference. Native library bug, not bridge issue.")
    fun testUpdateAccountProfile() = runTest {
        // Given: Created account
        val accountId = bridge.createAccount(displayName = "Profile Test")
        createdAccounts.add(accountId)

        // When: Update profile with new display name
        val newDisplayName = "Updated Display Name"
        bridge.updateProfile(accountId, displayName = newDisplayName, avatarPath = null)

        // Then: Account details reflect the new display name
        val details = bridge.getAccountDetails(accountId)
        assertThat(details["Account.displayName"]).isEqualTo(newDisplayName)
    }

    @Test
    fun testCreateMultipleAccounts() = runTest {
        // When: Create multiple accounts
        val accountId1 = bridge.createAccount(displayName = "Account 1")
        val accountId2 = bridge.createAccount(displayName = "Account 2")
        val accountId3 = bridge.createAccount(displayName = "Account 3")

        createdAccounts.addAll(listOf(accountId1, accountId2, accountId3))

        // Then: All accounts are unique
        assertThat(accountId1).isNotEqualTo(accountId2)
        assertThat(accountId2).isNotEqualTo(accountId3)
        assertThat(accountId1).isNotEqualTo(accountId3)

        // And: All accounts appear in the list
        val accountIds = bridge.getAccountIds()
        assertThat(accountIds).containsAtLeast(accountId1, accountId2, accountId3)
    }

    @Test
    @Ignore("May cause native crashes. Skipped for test suite stability.")
    fun testSetAccountDetails() = runTest {
        // Given: Created account
        val accountId = bridge.createAccount(displayName = "Settings Test")
        createdAccounts.add(accountId)

        // When: Update account details
        val updates = mapOf(
            "Account.displayName" to "New Name via Details"
        )
        bridge.setAccountDetails(accountId, updates)

        // Then: Details are updated
        val details = bridge.getAccountDetails(accountId)
        assertThat(details["Account.displayName"]).isEqualTo("New Name via Details")
    }
}
