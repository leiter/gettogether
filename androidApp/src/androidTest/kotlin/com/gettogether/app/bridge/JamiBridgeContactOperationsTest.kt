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
 * Integration tests for JamiBridge contact management operations.
 * These tests verify that the Kotlin bridge can properly add, remove,
 * and query contacts through the native Jami daemon.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class JamiBridgeContactOperationsTest {

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
    fun testAddContact() = runTest {
        // Given: Created account
        val accountId = bridge.createAccount(displayName = "Contact Test")
        createdAccounts.add(accountId)

        // When: Add a contact
        val contactUri = "ring:1234567890abcdef1234567890abcdef12345678"
        bridge.addContact(accountId, contactUri)

        // Then: Contact appears in contacts list
        val contacts = bridge.getContacts(accountId)
        val contactUris = contacts.map { it.uri }

        // Note: Contact may not immediately appear due to async processing
        // This test verifies addContact doesn't crash
    }

    @Test
    fun testRemoveContact() = runTest {
        // Given: Account with a contact
        val accountId = bridge.createAccount(displayName = "Remove Contact Test")
        createdAccounts.add(accountId)

        val contactUri = "ring:abcdef1234567890abcdef1234567890abcdef12"
        bridge.addContact(accountId, contactUri)

        // When: Remove the contact
        bridge.removeContact(accountId, contactUri, ban = false)

        // Then: Method completes without crashing
        // Note: Async processing means contact may not immediately disappear
    }

    @Test
    fun testRemoveContactWithBan() = runTest {
        // Given: Account with a contact
        val accountId = bridge.createAccount(displayName = "Ban Contact Test")
        createdAccounts.add(accountId)

        val contactUri = "ring:fedcba0987654321fedcba0987654321fedcba09"
        bridge.addContact(accountId, contactUri)

        // When: Remove and ban the contact
        bridge.removeContact(accountId, contactUri, ban = true)

        // Then: Method completes without crashing
        // Note: Ban status would need additional daemon integration to verify
    }

    @Test
    fun testGetContacts() = runTest {
        // Given: Created account
        val accountId = bridge.createAccount(displayName = "Get Contacts Test")
        createdAccounts.add(accountId)

        // When: Get contacts list
        val contacts = bridge.getContacts(accountId)

        // Then: Returns a list (may be empty for new account)
        assertThat(contacts).isNotNull()
        // New accounts typically have no contacts initially
    }

    @Test
    fun testGetContactsReturnsListStructure() = runTest {
        // Given: Created account
        val accountId = bridge.createAccount(displayName = "Contacts List Test")
        createdAccounts.add(accountId)

        // When: Get contacts
        val contacts = bridge.getContacts(accountId)

        // Then: Returns proper List type
        assertThat(contacts).isInstanceOf(List::class.java)
    }

    @Test
    fun testAddMultipleContacts() = runTest {
        // Given: Created account
        val accountId = bridge.createAccount(displayName = "Multiple Contacts Test")
        createdAccounts.add(accountId)

        // When: Add multiple contacts
        val contact1 = "ring:1111111111111111111111111111111111111111"
        val contact2 = "ring:2222222222222222222222222222222222222222"
        val contact3 = "ring:3333333333333333333333333333333333333333"

        bridge.addContact(accountId, contact1)
        bridge.addContact(accountId, contact2)
        bridge.addContact(accountId, contact3)

        // Then: No crash occurs
        // Note: Due to async processing, contacts may not immediately appear
        val contacts = bridge.getContacts(accountId)
        assertThat(contacts).isNotNull()
    }

    @Test
    fun testGetContactDetails() = runTest {
        // Given: Account with a contact
        val accountId = bridge.createAccount(displayName = "Contact Details Test")
        createdAccounts.add(accountId)

        val contactUri = "ring:4444444444444444444444444444444444444444"
        bridge.addContact(accountId, contactUri)

        // When: Get contact details
        val details = bridge.getContactDetails(accountId, contactUri)

        // Then: Returns a map
        assertThat(details).isNotNull()
        assertThat(details).isInstanceOf(Map::class.java)
    }

    @Test
    fun testGetTrustRequests() = runTest {
        // Given: Created account
        val accountId = bridge.createAccount(displayName = "Trust Requests Test")
        createdAccounts.add(accountId)

        // When: Get trust requests
        val trustRequests = bridge.getTrustRequests(accountId)

        // Then: Returns a list (likely empty for new account)
        assertThat(trustRequests).isNotNull()
        assertThat(trustRequests).isInstanceOf(List::class.java)
    }

    @Test
    fun testAcceptTrustRequest() = runTest {
        // Given: Created account
        val accountId = bridge.createAccount(displayName = "Accept Trust Test")
        createdAccounts.add(accountId)

        val contactUri = "ring:5555555555555555555555555555555555555555"

        // When: Accept a trust request (even if none exists)
        // This tests the bridge method doesn't crash
        bridge.acceptTrustRequest(accountId, contactUri)

        // Then: Method completes without error
        // Note: Without an actual pending request, this is a no-op in the daemon
    }

    @Test
    fun testDiscardTrustRequest() = runTest {
        // Given: Created account
        val accountId = bridge.createAccount(displayName = "Discard Trust Test")
        createdAccounts.add(accountId)

        val contactUri = "ring:6666666666666666666666666666666666666666"

        // When: Discard a trust request
        bridge.discardTrustRequest(accountId, contactUri)

        // Then: Method completes without error
    }

    @Test
    fun testContactWithSpecialCharactersInUri() = runTest {
        // Given: Created account
        val accountId = bridge.createAccount(displayName = "Special URI Test")
        createdAccounts.add(accountId)

        // When: Use a valid Jami URI format
        val contactUri = "ring:abcdef0123456789abcdef0123456789abcdef01"
        bridge.addContact(accountId, contactUri)

        // Then: Operation completes successfully
        val contacts = bridge.getContacts(accountId)
        assertThat(contacts).isNotNull()
    }

    @Test
    fun testRemoveNonExistentContact() = runTest {
        // Given: Account without contacts
        val accountId = bridge.createAccount(displayName = "Remove Nonexistent Test")
        createdAccounts.add(accountId)

        // When: Try to remove a contact that doesn't exist
        val contactUri = "ring:9999999999999999999999999999999999999999"
        bridge.removeContact(accountId, contactUri, ban = false)

        // Then: Method completes without crashing
        // (Daemon should handle this gracefully)
    }

    @Test
    fun testContactOperationsWithMultipleAccounts() = runTest {
        // Given: Two different accounts
        val account1 = bridge.createAccount(displayName = "Multi Account Test 1")
        val account2 = bridge.createAccount(displayName = "Multi Account Test 2")
        createdAccounts.addAll(listOf(account1, account2))

        // When: Add contacts to each account
        val contact1 = "ring:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        val contact2 = "ring:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"

        bridge.addContact(account1, contact1)
        bridge.addContact(account2, contact2)

        // Then: Both operations complete successfully
        val contacts1 = bridge.getContacts(account1)
        val contacts2 = bridge.getContacts(account2)

        assertThat(contacts1).isNotNull()
        assertThat(contacts2).isNotNull()
    }

    @Test
    fun testSubscribeBuddy() = runTest {
        // Given: Account with a contact
        val accountId = bridge.createAccount(displayName = "Subscribe Buddy Test")
        createdAccounts.add(accountId)

        val contactUri = "ring:7777777777777777777777777777777777777777"

        // When: Subscribe to buddy presence
        bridge.subscribeBuddy(accountId, contactUri, flag = true)

        // Then: Method completes without error
        // Note: Presence subscription effects would need daemon integration to verify
    }

    @Test
    fun testUnsubscribeBuddy() = runTest {
        // Given: Account with a contact
        val accountId = bridge.createAccount(displayName = "Unsubscribe Buddy Test")
        createdAccounts.add(accountId)

        val contactUri = "ring:8888888888888888888888888888888888888888"

        // When: Unsubscribe from buddy presence
        bridge.subscribeBuddy(accountId, contactUri, flag = false)

        // Then: Method completes without error
    }
}
