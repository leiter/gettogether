package com.gettogether.app.data.repository

import com.gettogether.app.domain.model.Contact
import com.gettogether.app.domain.repository.ContactRepository
import com.gettogether.app.jami.JamiBridge
import com.gettogether.app.jami.JamiContactEvent
import com.gettogether.app.jami.TrustRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Implementation of ContactRepository using JamiBridge.
 */
class ContactRepositoryImpl(
    private val jamiBridge: JamiBridge,
    private val accountRepository: AccountRepository,
    private val contactPersistence: com.gettogether.app.data.persistence.ContactPersistence
) : ContactRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Cache for contacts by account
    private val _contactsCache = MutableStateFlow<Map<String, List<Contact>>>(emptyMap())

    // Cache for online status by contact URI
    private val _onlineStatusCache = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    // Cache for trust requests by account
    private val _trustRequestsCache = MutableStateFlow<Map<String, List<TrustRequest>>>(emptyMap())

    init {
        // Listen for contact events
        scope.launch {
            jamiBridge.contactEvents.collect { event ->
                handleContactEvent(event)
            }
        }

        // Load contacts and trust requests when account changes
        scope.launch {
            accountRepository.currentAccountId.collect { accountId ->
                if (accountId != null) {
                    // Load persisted contacts first
                    loadPersistedContacts(accountId)
                    // Then refresh from Jami to get latest
                    refreshContacts(accountId)
                    refreshTrustRequests(accountId)
                }
            }
        }

        // Auto-save contacts when cache changes
        scope.launch {
            _contactsCache.collect { contactsMap ->
                contactsMap.forEach { (accountId, contacts) ->
                    contactPersistence.saveContacts(accountId, contacts)
                }
            }
        }
    }

    override fun getContacts(accountId: String): Flow<List<Contact>> {
        // Trigger refresh if not cached
        scope.launch {
            if (_contactsCache.value[accountId].isNullOrEmpty()) {
                refreshContacts(accountId)
            }
        }
        return _contactsCache.map { cache ->
            val contacts = cache[accountId] ?: emptyList()
            // Apply online status from cache
            contacts.map { contact ->
                contact.copy(isOnline = _onlineStatusCache.value[contact.uri] ?: false)
            }
        }
    }

    override fun getContactById(accountId: String, contactId: String): Flow<Contact?> {
        return getContacts(accountId).map { contacts ->
            contacts.find { it.id == contactId || it.uri == contactId }
        }
    }

    override suspend fun addContact(accountId: String, uri: String): Result<Contact> {
        return try {
            jamiBridge.addContact(accountId, uri)

            // Create a placeholder contact until we get the full details
            val contact = Contact(
                id = uri,
                uri = uri,
                displayName = uri.take(8),
                isOnline = false,
                isBanned = false
            )

            // Add to cache
            val currentContacts = _contactsCache.value[accountId] ?: emptyList()
            if (currentContacts.none { it.uri == uri }) {
                _contactsCache.value = _contactsCache.value + (accountId to (currentContacts + contact))
            }

            Result.success(contact)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun removeContact(accountId: String, contactId: String): Result<Unit> {
        return try {
            jamiBridge.removeContact(accountId, contactId, ban = false)

            // Remove from cache
            val currentContacts = _contactsCache.value[accountId] ?: emptyList()
            _contactsCache.value = _contactsCache.value +
                (accountId to currentContacts.filter { it.id != contactId && it.uri != contactId })

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun blockContact(accountId: String, contactId: String): Result<Unit> {
        return try {
            jamiBridge.removeContact(accountId, contactId, ban = true)

            // Update cache to mark as banned
            val currentContacts = _contactsCache.value[accountId] ?: emptyList()
            val updatedContacts = currentContacts.map { contact ->
                if (contact.id == contactId || contact.uri == contactId) {
                    contact.copy(isBanned = true)
                } else {
                    contact
                }
            }
            _contactsCache.value = _contactsCache.value + (accountId to updatedContacts)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun unblockContact(accountId: String, contactId: String): Result<Unit> {
        return try {
            // Re-add the contact to unblock
            jamiBridge.addContact(accountId, contactId)

            // Update cache to mark as not banned
            val currentContacts = _contactsCache.value[accountId] ?: emptyList()
            val updatedContacts = currentContacts.map { contact ->
                if (contact.id == contactId || contact.uri == contactId) {
                    contact.copy(isBanned = false)
                } else {
                    contact
                }
            }
            _contactsCache.value = _contactsCache.value + (accountId to updatedContacts)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Load persisted contacts from storage.
     */
    private suspend fun loadPersistedContacts(accountId: String) {
        try {
            val persistedContacts = contactPersistence.loadContacts(accountId)
            if (persistedContacts.isNotEmpty()) {
                _contactsCache.value = _contactsCache.value + (accountId to persistedContacts)
            }
        } catch (e: Exception) {
            // Keep existing cache on error
        }
    }

    /**
     * Refresh contacts from JamiBridge.
     */
    suspend fun refreshContacts(accountId: String) {
        try {
            val jamiContacts = jamiBridge.getContacts(accountId)
            val contacts = jamiContacts.map { jamiContact ->
                Contact(
                    id = jamiContact.uri,
                    uri = jamiContact.uri,
                    displayName = jamiContact.displayName.ifBlank { jamiContact.uri.take(8) },
                    avatarUri = jamiContact.avatarPath,
                    isOnline = _onlineStatusCache.value[jamiContact.uri] ?: false,
                    isBanned = jamiContact.isBanned
                )
            }
            _contactsCache.value = _contactsCache.value + (accountId to contacts)
        } catch (e: Exception) {
            // Keep existing cache on error
        }
    }

    private fun handleContactEvent(event: JamiContactEvent) {
        val accountId = accountRepository.currentAccountId.value ?: return

        when (event) {
            is JamiContactEvent.ContactAdded -> {
                if (event.accountId == accountId) {
                    // Fetch contact details and add to cache
                    scope.launch {
                        try {
                            val details = jamiBridge.getContactDetails(accountId, event.uri)
                            val displayName = details["displayName"] ?: event.uri.take(8)

                            val contact = Contact(
                                id = event.uri,
                                uri = event.uri,
                                displayName = displayName,
                                isOnline = _onlineStatusCache.value[event.uri] ?: false,
                                isBanned = false
                            )

                            val currentContacts = _contactsCache.value[accountId] ?: emptyList()
                            if (currentContacts.none { it.uri == event.uri }) {
                                _contactsCache.value = _contactsCache.value +
                                    (accountId to (currentContacts + contact))
                            }
                        } catch (e: Exception) {
                            // Handle error
                        }
                    }
                }
            }

            is JamiContactEvent.ContactRemoved -> {
                if (event.accountId == accountId) {
                    val currentContacts = _contactsCache.value[accountId] ?: emptyList()
                    _contactsCache.value = _contactsCache.value +
                        (accountId to currentContacts.filter { it.uri != event.uri })
                }
            }

            is JamiContactEvent.PresenceChanged -> {
                if (event.accountId == accountId) {
                    // Update online status cache
                    _onlineStatusCache.value = _onlineStatusCache.value + (event.uri to event.isOnline)

                    // Update contact in cache
                    val currentContacts = _contactsCache.value[accountId] ?: emptyList()
                    val updatedContacts = currentContacts.map { contact ->
                        if (contact.uri == event.uri) {
                            contact.copy(isOnline = event.isOnline)
                        } else {
                            contact
                        }
                    }
                    _contactsCache.value = _contactsCache.value + (accountId to updatedContacts)
                }
            }

            is JamiContactEvent.IncomingTrustRequest -> {
                if (event.accountId == accountId) {
                    // Add trust request to cache
                    val trustRequest = TrustRequest(
                        from = event.from,
                        conversationId = event.conversationId,
                        payload = event.payload,
                        received = event.received
                    )

                    val currentRequests = _trustRequestsCache.value[accountId] ?: emptyList()
                    // Only add if not already in the list
                    if (currentRequests.none { it.from == event.from }) {
                        _trustRequestsCache.value = _trustRequestsCache.value +
                            (accountId to (currentRequests + trustRequest))
                    }
                }
            }
        }
    }

    /**
     * Refresh trust requests from JamiBridge.
     */
    suspend fun refreshTrustRequests(accountId: String) {
        try {
            val requests = jamiBridge.getTrustRequests(accountId)
            _trustRequestsCache.value = _trustRequestsCache.value + (accountId to requests)
        } catch (e: Exception) {
            // Keep existing cache on error
        }
    }

    /**
     * Accept an incoming trust request.
     */
    suspend fun acceptTrustRequest(accountId: String, contactUri: String): Result<Unit> {
        return try {
            jamiBridge.acceptTrustRequest(accountId, contactUri)

            // Remove from trust requests cache
            val currentRequests = _trustRequestsCache.value[accountId] ?: emptyList()
            _trustRequestsCache.value = _trustRequestsCache.value +
                (accountId to currentRequests.filter { it.from != contactUri })

            // Refresh contacts to get the newly added contact
            refreshContacts(accountId)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Reject an incoming trust request.
     */
    suspend fun rejectTrustRequest(accountId: String, contactUri: String, block: Boolean = false): Result<Unit> {
        return try {
            if (block) {
                // Block the contact (adds to ban list)
                jamiBridge.removeContact(accountId, contactUri, ban = true)
            } else {
                // Just discard the request
                jamiBridge.discardTrustRequest(accountId, contactUri)
            }

            // Remove from trust requests cache
            val currentRequests = _trustRequestsCache.value[accountId] ?: emptyList()
            _trustRequestsCache.value = _trustRequestsCache.value +
                (accountId to currentRequests.filter { it.from != contactUri })

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get trust requests for the current account.
     */
    fun getTrustRequests(accountId: String): Flow<List<TrustRequest>> {
        // Trigger refresh if not cached
        scope.launch {
            if (_trustRequestsCache.value[accountId].isNullOrEmpty()) {
                refreshTrustRequests(accountId)
            }
        }
        return _trustRequestsCache.map { cache ->
            cache[accountId] ?: emptyList()
        }
    }
}
