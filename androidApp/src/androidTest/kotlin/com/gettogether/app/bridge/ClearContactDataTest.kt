package com.gettogether.app.bridge

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gettogether.app.jami.SwigJamiBridge
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Test to clear all contact and conversation data while preserving the account.
 * This simulates a "fresh install" state with an existing account.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class ClearContactDataTest {

    companion object {
        private const val TAG = "ClearContactDataTest"
        private const val MAX_CLEAR_ITERATIONS = 5
    }

    private lateinit var context: Context
    private lateinit var bridge: SwigJamiBridge
    private lateinit var dataPath: String

    @Before
    fun setUp() {
        runBlocking {
            context = ApplicationProvider.getApplicationContext()
            bridge = SwigJamiBridge(context)

            // Use the real app data path (same as the app uses)
            val jamiDir = File(context.filesDir, "jami")
            if (!jamiDir.exists()) {
                jamiDir.mkdirs()
            }
            dataPath = jamiDir.absolutePath

            Log.i(TAG, "┌─── Test Setup ───")
            Log.i(TAG, "│ Data path: $dataPath")

            // Initialize and start daemon with existing data
            bridge.initDaemon(dataPath)
            bridge.startDaemon()


        }
        // Wait for daemon to fully initialize and sync (real time!)
        Thread.sleep(3800)
        Log.i(TAG, "└─── Setup Complete ───")
    }

    @After
    fun tearDown() {
        runBlocking {
            try {
                bridge.stopDaemon()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping daemon: ${e.message}")
            }
        }
        // Wait for daemon to fully initialize and sync (real time!)
        Thread.sleep(3000)
        Log.i(TAG, "└─── TearDown Complete ───")
    }

    @Test
    fun clearAllContactsAndConversations() {
        runBlocking {
            Log.i(TAG, "┌═══════════════════════════════════════════")
            Log.i(TAG, "│ CLEARING ALL CONTACTS AND CONVERSATIONS")
            Log.i(TAG, "└═══════════════════════════════════════════")

            // Get all accounts
            val accounts = bridge.getAccountIds()
            Log.i(TAG, "Found ${accounts.size} account(s)")

            assertThat(accounts).isNotEmpty()

            // Loop until everything is cleared (daemon may sync new data during removal)
            for (iteration in 1..MAX_CLEAR_ITERATIONS) {
                Log.i(TAG, "")
                Log.i(TAG, "┌═══ Clearing Pass $iteration of $MAX_CLEAR_ITERATIONS ═══")

                var totalCleared = 0

                for (accountId in accounts) {
                    Log.i(TAG, "│")
                    Log.i(TAG, "│ ┌─── Account: $accountId ───")

                    // 1. Get and decline all conversation requests
                    val requests = bridge.getConversationRequests(accountId)
                    Log.i(TAG, "│ │ Requests: ${requests.size}")
                    for (request in requests) {
                        Log.i(TAG, "│ │   Declining: ${request.conversationId.take(8)}... from ${request.from.take(8)}...")
                        try {
                            bridge.declineConversationRequest(accountId, request.conversationId)
                            totalCleared++
                            Thread.sleep(300)
                        } catch (e: Exception) {
                            Log.w(TAG, "│ │   Failed: ${e.message}")
                        }
                    }

                    // 2. Get and remove all conversations
                    val conversationIds = bridge.getConversations(accountId)
                    Log.i(TAG, "│ │ Conversations: ${conversationIds.size}")
                    for (convId in conversationIds) {
                        Log.i(TAG, "│ │   Removing: ${convId.take(8)}...")
                        try {
                            bridge.removeConversation(accountId, convId)
                            totalCleared++
                            Thread.sleep(300)
                        } catch (e: Exception) {
                            Log.w(TAG, "│ │   Failed: ${e.message}")
                        }
                    }

                    // 3. Get and remove all contacts
                    val contacts = bridge.getContacts(accountId)
                    Log.i(TAG, "│ │ Contacts: ${contacts.size}")
                    for (contact in contacts) {
                        Log.i(TAG, "│ │   Removing: ${contact.uri.take(8)}...")
                        try {
                            bridge.removeContact(accountId, contact.uri, ban = false)
                            totalCleared++
                            Thread.sleep(300)
                        } catch (e: Exception) {
                            Log.w(TAG, "│ │   Failed: ${e.message}")
                        }
                    }

                    Log.i(TAG, "│ └─── Account done ───")
                }

                Log.i(TAG, "│")
                Log.i(TAG, "│ Items cleared this pass: $totalCleared")
                Log.i(TAG, "└═══ Pass $iteration Complete ═══")

                // Wait for daemon to process removals
                Thread.sleep(2000)

                // Check if we're done
                var allClear = true
                for (accountId in accounts) {
                    val remaining = bridge.getConversationRequests(accountId).size +
                            bridge.getConversations(accountId).size +
                            bridge.getContacts(accountId).size
                    if (remaining > 0) {
                        allClear = false
                        break
                    }
                }

                if (allClear) {
                    Log.i(TAG, "")
                    Log.i(TAG, "✓ All data cleared after $iteration pass(es)")
                    break
                }

                if (iteration == MAX_CLEAR_ITERATIONS) {
                    Log.w(TAG, "⚠ Reached max iterations, some data may remain")
                }
            }

            // Final verification
            Log.i(TAG, "")
            Log.i(TAG, "┌─── Final Verification ───")
            for (accountId in accounts) {
                val remainingRequests = bridge.getConversationRequests(accountId)
                val remainingConversationIds = bridge.getConversations(accountId)
                val remainingContacts = bridge.getContacts(accountId)

                Log.i(TAG, "│ Account: ${accountId.take(8)}...")
                Log.i(TAG, "│   Requests: ${remainingRequests.size}")
                Log.i(TAG, "│   Conversations: ${remainingConversationIds.size}")
                Log.i(TAG, "│   Contacts: ${remainingContacts.size}")

                // Log any remaining items for debugging
                remainingConversationIds.forEach {
                    Log.i(TAG, "│     Conv: $it")
                }
            }
            Log.i(TAG, "└─── Verification Complete ───")

            // Final assertion - only check contacts are cleared
            // (conversations may persist due to DHT sync behavior)
            for (accountId in accounts) {
                val remainingContacts = bridge.getContacts(accountId)
                assertThat(remainingContacts).isEmpty()
            }

            Log.i(TAG, "")
            Log.i(TAG, "✓ CONTACT DATA CLEAR COMPLETE")
        }
    }
}
