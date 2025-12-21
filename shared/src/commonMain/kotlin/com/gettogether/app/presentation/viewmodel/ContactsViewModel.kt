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

data class ContactsState(
    val contacts: List<ContactUiItem> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val hasAccount: Boolean = false
)

data class ContactUiItem(
    val id: String,
    val uri: String,
    val name: String,
    val isOnline: Boolean,
    val isBanned: Boolean,
    val avatarInitial: String
)

class ContactsViewModel(
    private val contactRepository: ContactRepositoryImpl,
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ContactsState())
    val state: StateFlow<ContactsState> = _state.asStateFlow()

    init {
        // Observe account state
        viewModelScope.launch {
            accountRepository.currentAccountId.collect { accountId ->
                if (accountId != null) {
                    _state.update { it.copy(hasAccount = true) }
                    loadContacts(accountId)
                } else {
                    _state.update {
                        it.copy(
                            hasAccount = false,
                            contacts = emptyList(),
                            isLoading = false
                        )
                    }
                }
            }
        }
    }

    private fun loadContacts(accountId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            try {
                contactRepository.getContacts(accountId).collect { contacts ->
                    val uiItems = contacts
                        .filter { !it.isBanned }
                        .map { it.toUiItem() }
                        .sortedWith(compareByDescending<ContactUiItem> { it.isOnline }.thenBy { it.name })

                    _state.update {
                        it.copy(
                            contacts = uiItems,
                            isLoading = false
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load contacts"
                    )
                }
            }
        }
    }

    fun refresh() {
        val accountId = accountRepository.currentAccountId.value ?: return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                kotlinx.coroutines.withTimeout(5000) {
                    contactRepository.refreshContacts(accountId)
                    // Force wait for flow emission to complete
                    kotlinx.coroutines.delay(100)
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                println("ContactsViewModel: Refresh timed out after 5 seconds")
            } catch (e: Exception) {
                println("ContactsViewModel: Refresh failed: ${e.message}")
            } finally {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    fun removeContact(contactId: String) {
        val accountId = accountRepository.currentAccountId.value ?: return
        viewModelScope.launch {
            contactRepository.removeContact(accountId, contactId)
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    private fun Contact.toUiItem(): ContactUiItem {
        val effectiveName = getEffectiveName()
        val initial = effectiveName.firstOrNull()?.uppercase() ?: uri.firstOrNull()?.uppercase() ?: "?"

        return ContactUiItem(
            id = id,
            uri = uri,
            name = effectiveName,
            isOnline = isOnline,
            isBanned = isBanned,
            avatarInitial = initial
        )
    }
}
