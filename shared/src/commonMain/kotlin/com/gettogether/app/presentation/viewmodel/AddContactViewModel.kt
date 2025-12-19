package com.gettogether.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gettogether.app.jami.JamiBridge
import com.gettogether.app.presentation.state.AddContactState
import com.gettogether.app.presentation.state.ContactSearchResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AddContactViewModel(
    private val jamiBridge: JamiBridge
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
                // TODO: Actually search via JamiBridge
                // val result = jamiBridge.lookupUsername(currentState.contactId)

                // Simulate search delay
                kotlinx.coroutines.delay(800)

                // Demo: simulate finding a user
                val searchResult = simulateSearch(currentState.contactId)

                _state.update {
                    it.copy(
                        isSearching = false,
                        searchResult = searchResult,
                        error = if (searchResult == null) "User not found" else null
                    )
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
                // TODO: Actually add contact via JamiBridge
                // jamiBridge.addContact(searchResult.id, currentState.displayName)

                // Simulate adding delay
                kotlinx.coroutines.delay(500)

                _state.update { it.copy(isAdding = false, isContactAdded = true) }
            } catch (e: Exception) {
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
