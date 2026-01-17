package com.gettogether.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gettogether.app.data.repository.AccountRepository
import com.gettogether.app.jami.JamiBridge
import com.gettogether.app.presentation.state.AddContactState
import com.gettogether.app.presentation.state.ContactSearchResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AddContactViewModel(
    private val jamiBridge: JamiBridge,
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AddContactState())
    val state: StateFlow<AddContactState> = _state.asStateFlow()

    fun onContactIdChanged(id: String) {
        _state.update {
            it.copy(
                contactId = id,
                error = null,
                searchResult = null
            )
        }
    }

    fun onDisplayNameChanged(name: String) {
        _state.update { it.copy(displayName = name) }
    }

    fun searchContact() {
        val currentState = _state.value
        if (!currentState.canSearch) return

        viewModelScope.launch {
            _state.update { it.copy(isSearching = true, error = null, searchResult = null) }
            try {
                val accountId = accountRepository.currentAccountId.value
                val query = currentState.contactId.trim()

                println("[CONTACT-SEARCH] === searchContact() called ===")
                println("[CONTACT-SEARCH] Query: '$query'")
                println("[CONTACT-SEARCH] Account ID: ${accountId ?: "null (demo mode)"}")

                if (accountId != null) {
                    // Check if query is a 40-character hexadecimal Jami ID (DHT account)
                    val isJamiId = query.length == 40 && query.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
                    println("[CONTACT-SEARCH] Query length: ${query.length}")
                    println("[CONTACT-SEARCH] Is valid 40-char Jami ID: $isJamiId")

                    if (isJamiId) {
                        // Direct DHT account ID - skip name server lookup
                        println("[CONTACT-SEARCH] → Using direct DHT lookup (no name server)")
                        val searchResult = ContactSearchResult(
                            id = query,
                            username = query.take(8), // Show first 8 chars as username
                            displayName = query.take(8),
                            isAlreadyContact = false
                        )
                        println("[CONTACT-SEARCH] ✓ Contact found: id=${searchResult.id}")
                        _state.update {
                            it.copy(
                                isSearching = false,
                                searchResult = searchResult,
                                error = null
                            )
                        }
                    } else {
                        // Username or short ID - perform name server lookup
                        println("[CONTACT-SEARCH] → Performing name server lookup for username: '$query'")
                        val result = jamiBridge.lookupName(accountId, query)
                        if (result != null) {
                            println("[CONTACT-SEARCH] ✓ Name server found: name='${result.name}', address='${result.address}'")
                            val searchResult = ContactSearchResult(
                                id = result.address,
                                username = result.name,
                                displayName = result.name,
                                isAlreadyContact = false
                            )
                            _state.update {
                                it.copy(
                                    isSearching = false,
                                    searchResult = searchResult,
                                    error = null
                                )
                            }
                        } else {
                            println("[CONTACT-SEARCH] ✗ User not found on name server")
                            _state.update {
                                it.copy(
                                    isSearching = false,
                                    error = "User not found"
                                )
                            }
                        }
                    }
                } else {
                    println("[CONTACT-SEARCH] → Demo mode: simulating search")
                    // Demo mode: simulate finding a user
                    kotlinx.coroutines.delay(800)
                    val searchResult = simulateSearch(currentState.contactId)
                    _state.update {
                        it.copy(
                            isSearching = false,
                            searchResult = searchResult,
                            error = if (searchResult == null) "User not found" else null
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isSearching = false,
                        error = e.message ?: "Search failed"
                    )
                }
            }
        }
    }

    fun addContact() {
        val currentState = _state.value
        val searchResult = currentState.searchResult ?: return

        if (searchResult.isAlreadyContact) {
            _state.update { it.copy(error = "This user is already in your contacts") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isAdding = true, error = null) }
            try {
                val accountId = accountRepository.currentAccountId.value
                val timestamp = kotlin.time.Clock.System.now().toEpochMilliseconds()

                println("[CONTACT-ADD] === addContact() called ===")
                println("[CONTACT-ADD] Timestamp: $timestamp")
                println("[CONTACT-ADD] My Account ID: ${accountId ?: "null"}")
                println("[CONTACT-ADD] Target Contact ID: ${searchResult.id}")
                println("[CONTACT-ADD] Target Username: ${searchResult.username}")
                println("[CONTACT-ADD] Target Display Name: ${searchResult.displayName}")

                if (accountId != null) {
                    println("[CONTACT-ADD] → Sending contact request via JamiBridge...")
                    jamiBridge.addContact(accountId, searchResult.id)
                    println("[CONTACT-ADD] ✓ Contact request sent successfully")
                    println("[CONTACT-ADD]   From: $accountId")
                    println("[CONTACT-ADD]   To: ${searchResult.id}")
                } else {
                    println("[CONTACT-ADD] → Demo mode: no actual request sent")
                }

                _state.update { it.copy(isAdding = false, isContactAdded = true) }
                println("[CONTACT-ADD] ✓ UI state updated: isContactAdded=true")
            } catch (e: Exception) {
                println("[CONTACT-ADD] ✗ Failed to add contact: ${e.message}")
                e.printStackTrace()
                _state.update {
                    it.copy(
                        isAdding = false,
                        error = e.message ?: "Failed to add contact"
                    )
                }
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun resetState() {
        _state.value = AddContactState()
    }

    private fun simulateSearch(query: String): ContactSearchResult? {
        // Demo contacts for simulation
        val demoUsers = mapOf(
            "alice" to ContactSearchResult("user_alice_123", "alice", "Alice Smith", false),
            "bob" to ContactSearchResult("user_bob_456", "bob", "Bob Johnson", true),
            "carol" to ContactSearchResult("user_carol_789", "carol", "Carol Williams", false),
            "david" to ContactSearchResult("user_david_012", "david", "David Brown", false),
            "emma" to ContactSearchResult("user_emma_345", "emma", "Emma Davis", false)
        )

        val lowerQuery = query.lowercase()
        return demoUsers.entries.find { (key, _) ->
            key.contains(lowerQuery) || lowerQuery.contains(key)
        }?.value
    }
}
