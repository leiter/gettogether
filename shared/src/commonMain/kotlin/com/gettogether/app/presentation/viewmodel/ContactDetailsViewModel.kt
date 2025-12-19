package com.gettogether.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gettogether.app.jami.JamiBridge
import com.gettogether.app.presentation.state.ContactDetails
import com.gettogether.app.presentation.state.ContactDetailsState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ContactDetailsViewModel(
    private val jamiBridge: JamiBridge
) : ViewModel() {

    private val _state = MutableStateFlow(ContactDetailsState())
    val state: StateFlow<ContactDetailsState> = _state.asStateFlow()

    fun loadContact(contactId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                // TODO: Load actual contact from JamiBridge
                // val contacts = jamiBridge.getContacts(accountId)
                // val contact = contacts.find { it["id"] == contactId }

                // Simulate loading delay
                kotlinx.coroutines.delay(300)

                // Demo contact data
                val contact = getDemoContact(contactId)

                _state.update {
                    it.copy(
                        isLoading = false,
                        contact = contact,
                        error = if (contact == null) "Contact not found" else null
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load contact"
                    )
                }
            }
        }
    }

    fun startConversation() {
        val contact = _state.value.contact ?: return

        viewModelScope.launch {
            try {
                // TODO: Start actual conversation via JamiBridge
                // val conversationId = jamiBridge.startConversation(accountId)
                // jamiBridge.addContact(accountId, contact.jamiId)

                // Simulate with demo conversation ID
                kotlinx.coroutines.delay(200)
                val conversationId = "conv_${contact.id}"

                _state.update { it.copy(conversationStarted = conversationId) }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun clearConversationStarted() {
        _state.update { it.copy(conversationStarted = null) }
    }

    fun showRemoveDialog() {
        _state.update { it.copy(showRemoveDialog = true) }
    }

    fun hideRemoveDialog() {
        _state.update { it.copy(showRemoveDialog = false) }
    }

    fun showBlockDialog() {
        _state.update { it.copy(showBlockDialog = true) }
    }

    fun hideBlockDialog() {
        _state.update { it.copy(showBlockDialog = false) }
    }

    fun removeContact() {
        val contact = _state.value.contact ?: return

        viewModelScope.launch {
            _state.update { it.copy(isRemoving = true, showRemoveDialog = false) }
            try {
                // TODO: Remove contact via JamiBridge
                // jamiBridge.removeContact(accountId, contact.jamiId)

                // Simulate removal
                kotlinx.coroutines.delay(300)

                _state.update {
                    it.copy(
                        isRemoving = false,
                        contactRemoved = true
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isRemoving = false,
                        error = e.message ?: "Failed to remove contact"
                    )
                }
            }
        }
    }

    fun toggleBlock() {
        val contact = _state.value.contact ?: return

        viewModelScope.launch {
            _state.update { it.copy(isBlocking = true, showBlockDialog = false) }
            try {
                // TODO: Block/unblock via JamiBridge

                // Simulate block toggle
                kotlinx.coroutines.delay(300)

                _state.update {
                    it.copy(
                        isBlocking = false,
                        contact = contact.copy(isBlocked = !contact.isBlocked)
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isBlocking = false,
                        error = e.message ?: "Failed to update block status"
                    )
                }
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    private fun getDemoContact(contactId: String): ContactDetails? {
        val demoContacts = mapOf(
            "1" to ContactDetails(
                id = "1",
                displayName = "Alice Smith",
                username = "alice",
                jamiId = "ring:alice123456789abcdef",
                isOnline = true,
                isTrusted = true,
                isBlocked = false,
                addedDate = "December 15, 2024",
                lastSeen = null
            ),
            "2" to ContactDetails(
                id = "2",
                displayName = "Bob Johnson",
                username = "bob",
                jamiId = "ring:bob987654321fedcba",
                isOnline = false,
                isTrusted = true,
                isBlocked = false,
                addedDate = "December 10, 2024",
                lastSeen = "2 hours ago"
            ),
            "3" to ContactDetails(
                id = "3",
                displayName = "Carol Williams",
                username = "carol",
                jamiId = "ring:carol555666777888",
                isOnline = true,
                isTrusted = false,
                isBlocked = false,
                addedDate = "December 5, 2024",
                lastSeen = null
            ),
            "4" to ContactDetails(
                id = "4",
                displayName = "David Brown",
                username = "david",
                jamiId = "ring:david111222333444",
                isOnline = false,
                isTrusted = true,
                isBlocked = false,
                addedDate = "November 28, 2024",
                lastSeen = "Yesterday"
            )
        )
        return demoContacts[contactId]
    }
}
