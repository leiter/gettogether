package com.gettogether.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gettogether.app.data.repository.AccountRepository
import com.gettogether.app.domain.repository.ContactRepository
import com.gettogether.app.jami.JamiBridge
import com.gettogether.app.presentation.state.ContactDetails
import com.gettogether.app.presentation.state.ContactDetailsState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ContactDetailsViewModel(
    private val jamiBridge: JamiBridge,
    private val accountRepository: AccountRepository,
    private val contactRepository: ContactRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ContactDetailsState())
    val state: StateFlow<ContactDetailsState> = _state.asStateFlow()

    fun loadContact(contactId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val accountId = accountRepository.currentAccountId.value

                if (accountId != null) {
                    // Subscribe to contact updates from repository (includes online status from presence cache)
                    viewModelScope.launch {
                        contactRepository.getContactById(accountId, contactId).collect { domainContact ->
                            if (domainContact != null) {
                                // Get additional details from Jami
                                val contactDetails = jamiBridge.getContactDetails(accountId, contactId)

                                val contact = ContactDetails(
                                    id = contactId,
                                    displayName = domainContact.displayName,
                                    customName = domainContact.customName,
                                    avatarUri = domainContact.avatarUri,
                                    username = contactDetails["username"] ?: "",
                                    jamiId = contactId,
                                    isOnline = domainContact.isOnline, // Use online status from repository cache
                                    isTrusted = contactDetails["confirmed"] == "true",
                                    isBlocked = domainContact.isBanned,
                                    addedDate = contactDetails["addedDate"] ?: "",
                                    lastSeen = if (domainContact.isOnline) null else contactDetails["lastSeen"]
                                )

                                _state.update {
                                    it.copy(
                                        isLoading = false,
                                        contact = contact
                                    )
                                }
                            } else {
                                // Contact not found in repository
                                _state.update {
                                    it.copy(
                                        isLoading = false,
                                        error = "Contact not found"
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Demo contact data fallback
                    val contact = getDemoContact(contactId)
                    _state.update {
                        it.copy(
                            isLoading = false,
                            contact = contact,
                            error = if (contact == null) "Contact not found" else null
                        )
                    }
                }
            } catch (e: Exception) {
                // Fallback to demo data on error
                val contact = getDemoContact(contactId)
                _state.update {
                    it.copy(
                        isLoading = false,
                        contact = contact,
                        error = if (contact == null) "Contact not found" else null
                    )
                }
            }
        }
    }

    fun startConversation() {
        val contact = _state.value.contact ?: return

        viewModelScope.launch {
            try {
                val accountId = accountRepository.currentAccountId.value

                if (accountId != null) {
                    // Start a conversation with this contact
                    val conversationId = jamiBridge.startConversation(accountId)
                    // Add the contact to the conversation
                    jamiBridge.addConversationMember(accountId, conversationId, contact.jamiId)
                    _state.update { it.copy(conversationStarted = conversationId) }
                } else {
                    // Demo mode
                    val conversationId = "conv_${contact.id}"
                    _state.update { it.copy(conversationStarted = conversationId) }
                }
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
                val accountId = accountRepository.currentAccountId.value

                if (accountId != null) {
                    jamiBridge.removeContact(accountId, contact.jamiId, ban = false)
                }

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
                val accountId = accountRepository.currentAccountId.value

                if (accountId != null) {
                    if (contact.isBlocked) {
                        // Unblock by adding contact back
                        jamiBridge.addContact(accountId, contact.jamiId)
                    } else {
                        // Block by removing with ban flag
                        jamiBridge.removeContact(accountId, contact.jamiId, ban = true)
                    }
                }

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

    fun showEditNameDialog() {
        _state.update { it.copy(showEditNameDialog = true) }
    }

    fun hideEditNameDialog() {
        _state.update { it.copy(showEditNameDialog = false) }
    }

    fun updateCustomName(customName: String) {
        val contact = _state.value.contact ?: return
        val accountId = accountRepository.currentAccountId.value ?: return

        viewModelScope.launch {
            try {
                // Update custom name in repository
                contactRepository.updateCustomName(accountId, contact.id, customName)

                // Update UI immediately
                _state.update {
                    it.copy(
                        contact = contact.copy(customName = customName.takeIf { it.isNotBlank() }),
                        showEditNameDialog = false
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        error = "Failed to update custom name: ${e.message}",
                        showEditNameDialog = false
                    )
                }
            }
        }
    }
}
