package com.gettogether.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gettogether.app.data.repository.AccountRepository
import com.gettogether.app.data.repository.ContactRepositoryImpl
import com.gettogether.app.domain.model.Contact
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BlockedContactsState(
    val blockedContacts: List<BlockedContactUiItem> = emptyList(),
    val isLoading: Boolean = true,
    val contactToUnblock: BlockedContactUiItem? = null,
    val isUnblocking: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)

data class BlockedContactUiItem(
    val id: String,
    val uri: String,
    val name: String,
    val jamiId: String,
    val avatarUri: String?
)

class BlockedContactsViewModel(
    private val contactRepository: ContactRepositoryImpl,
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val _state = MutableStateFlow(BlockedContactsState())
    val state: StateFlow<BlockedContactsState> = _state.asStateFlow()

    init {
        loadBlockedContacts()
    }

    private fun loadBlockedContacts() {
        val accountId = accountRepository.currentAccountId.value ?: return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            try {
                contactRepository.getContacts(accountId).collect { contacts ->
                    val blockedContacts = contacts
                        .filter { it.isBanned }
                        .map { it.toBlockedUiItem() }
                        .sortedBy { it.name }

                    _state.update {
                        it.copy(
                            blockedContacts = blockedContacts,
                            isLoading = false
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load blocked contacts"
                    )
                }
            }
        }
    }

    fun showUnblockConfirmation(contact: BlockedContactUiItem) {
        _state.update { it.copy(contactToUnblock = contact) }
    }

    fun hideUnblockConfirmation() {
        _state.update { it.copy(contactToUnblock = null) }
    }

    fun confirmUnblock() {
        val contact = _state.value.contactToUnblock ?: return
        val accountId = accountRepository.currentAccountId.value ?: return

        viewModelScope.launch {
            _state.update { it.copy(isUnblocking = true, contactToUnblock = null) }

            try {
                contactRepository.unblockContact(accountId, contact.uri)
                _state.update {
                    it.copy(
                        isUnblocking = false,
                        successMessage = "${contact.name} has been unblocked"
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isUnblocking = false,
                        error = e.message ?: "Failed to unblock contact"
                    )
                }
            }
        }
    }

    fun clearMessages() {
        _state.update { it.copy(error = null, successMessage = null) }
    }

    private fun Contact.toBlockedUiItem(): BlockedContactUiItem {
        val effectiveName = getEffectiveName()
        return BlockedContactUiItem(
            id = id,
            uri = uri,
            name = effectiveName,
            jamiId = uri.take(16) + "...",
            avatarUri = avatarUri
        )
    }
}
