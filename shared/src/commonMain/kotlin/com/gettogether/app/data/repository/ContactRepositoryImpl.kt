package com.gettogether.app.data.repository

import com.gettogether.app.domain.model.Contact
import com.gettogether.app.domain.repository.ContactRepository
import com.gettogether.app.jami.JamiBridge
import com.gettogether.app.jami.JamiContactEvent
import com.gettogether.app.jami.TrustRequest
import com.gettogether.app.platform.ContactAvatarStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.time.Clock
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
    private val contactPersistence: com.gettogether.app.data.persistence.ContactPersistence,
    private val avatarStorage: ContactAvatarStorage? = null
) : ContactRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Cache for contacts by account
    internal val _contactsCache = MutableStateFlow<Map<String, List<Contact>>>(emptyMap())

    // Cache for online status by contact URI
    private val _onlineStatusCache = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    // Cache for last presence update timestamp by contact URI
    private val _lastPresenceTimestamp = MutableStateFlow<Map<String, Long>>(emptyMap())

    // Cache for trust requests by account
    private val _trustRequestsCache = MutableStateFlow<Map<String, List<TrustRequest>>>(emptyMap())

    companion object {
        private const val PRESENCE_TIMEOUT_MS = 60_000L // 60 seconds
    }

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

        // Periodic presence timeout checker
        scope.launch {
            while (true) {
                kotlinx.coroutines.delay(10_000) // Check every 10 seconds
                checkPresenceTimeouts()
            }
        }

        // Auto-save contacts when cache changes
        scope.launch {
            _contactsCache.collect { contactsMap ->
                println("ContactRepository: Auto-save triggered (${contactsMap.size} accounts)")
                contactsMap.forEach { (accountId, contacts) ->
                    println("  → Saving ${contacts.size} contacts for account $accountId")
                    try {
                        contactPersistence.saveContacts(accountId, contacts)
                        println("  ✓ Saved contacts for account $accountId")
                    } catch (e: Exception) {
                        println("  ✗ Failed to save contacts: ${e.message}")
                    }
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
        println("ContactRepository: addContact() called")
        println("  AccountId: $accountId")
        println("  URI: $uri")

        return try {
            println("ContactRepository: → Calling jamiBridge.addContact()...")
            jamiBridge.addContact(accountId, uri)
            println("ContactRepository: ✓ jamiBridge.addContact() completed")

            // Create a placeholder contact until we get the full details
            println("ContactRepository: → Creating placeholder contact...")
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

    override suspend fun updateCustomName(accountId: String, contactId: String, customName: String): Result<Unit> {
        return try {
            // Update custom name in cache
            val currentContacts = _contactsCache.value[accountId] ?: emptyList()
            val updatedContacts = currentContacts.map { contact ->
                if (contact.id == contactId || contact.uri == contactId) {
                    contact.copy(customName = customName.takeIf { it.isNotBlank() })
                } else {
                    contact
                }
            }
            _contactsCache.value = _contactsCache.value + (accountId to updatedContacts)

            // Custom name is automatically saved to persistence via the auto-save flow in init{}
            println("ContactRepository: ✓ Updated custom name for contact $contactId to: $customName")

            Result.success(Unit)
        } catch (e: Exception) {
            println("ContactRepository: ✗ Failed to update custom name: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Load persisted contacts from storage.
     */
    private suspend fun loadPersistedContacts(accountId: String) {
        println("ContactRepository: loadPersistedContacts() for account: $accountId")
        try {
            val persistedContacts = contactPersistence.loadContacts(accountId)
            println("ContactRepository: ✓ Loaded ${persistedContacts.size} persisted contacts")
            if (persistedContacts.isNotEmpty()) {
                persistedContacts.forEach { contact ->
                    println("  - ${contact.displayName} (${contact.uri.take(16)}...)")
                }
                _contactsCache.value = _contactsCache.value + (accountId to persistedContacts)
                println("ContactRepository: ✓ Added persisted contacts to cache")
            } else {
                println("ContactRepository: No persisted contacts found")
            }
        } catch (e: Exception) {
            println("ContactRepository: ✗ Failed to load persisted contacts: ${e.message}")
            e.printStackTrace()
            // Keep existing cache on error
        }
    }

    /**
     * Refresh contacts from JamiBridge.
     */
    suspend fun refreshContacts(accountId: String) {
        println("ContactRepository: refreshContacts() for account: $accountId")
        try {
            println("ContactRepository: → Calling jamiBridge.getContacts()...")
            val jamiContacts = jamiBridge.getContacts(accountId)
            println("ContactRepository: ✓ Received ${jamiContacts.size} contacts from Jami")

            // Get existing contacts to preserve custom names
            val existingContacts = _contactsCache.value[accountId] ?: emptyList()
            val existingContactsMap = existingContacts.associateBy { it.uri }

            val contacts = jamiContacts.map { jamiContact ->
                println("  - Mapping contact: ${jamiContact.displayName} (${jamiContact.uri.take(16)}...)")

                // Preserve data from existing contact if available (from profile events or persistence)
                val existingContact = existingContactsMap[jamiContact.uri]
                val customName = existingContact?.customName

                // Preserve displayName from existing contact if daemon returns blank
                // (profile events update displayName with actual contact name)
                val displayName = if (jamiContact.displayName.isNotBlank()) {
                    jamiContact.displayName
                } else {
                    existingContact?.displayName ?: jamiContact.uri.take(8)
                }

                // Preserve avatarUri from existing contact (profile events save avatar locally)
                val avatarUri = existingContact?.avatarUri ?: jamiContact.avatarPath

                Contact(
                    id = jamiContact.uri,
                    uri = jamiContact.uri,
                    displayName = displayName,
                    customName = customName,
                    avatarUri = avatarUri,
                    isOnline = _onlineStatusCache.value[jamiContact.uri] ?: false,
                    isBanned = jamiContact.isBanned
                )
            }
            _contactsCache.value = _contactsCache.value + (accountId to contacts)
            println("ContactRepository: ✓ Updated cache with ${contacts.size} contacts")

            // Subscribe to presence for all contacts
            println("ContactRepository: → Subscribing to presence for all contacts...")
            contacts.forEach { contact ->
                try {
                    println("  → Subscribing to: ${contact.uri.take(16)}...")
                    jamiBridge.subscribeBuddy(accountId, contact.uri, true)
                } catch (e: Exception) {
                    println("  ✗ Failed to subscribe: ${e.message}")
                }
            }
            println("ContactRepository: ✓ Presence subscriptions requested")
        } catch (e: Exception) {
            println("ContactRepository: ✗ Failed to refresh contacts: ${e.message}")
            e.printStackTrace()
            // Keep existing cache on error
        }
    }

    private fun handleContactEvent(event: JamiContactEvent) {
        val accountId = accountRepository.currentAccountId.value ?: return

        when (event) {
            is JamiContactEvent.ContactAdded -> {
                println("[CONTACT-EVENT] ContactAdded received")
                println("[CONTACT-EVENT]   Event Account ID: ${event.accountId}")
                println("[CONTACT-EVENT]   Current Account ID: $accountId")
                println("[CONTACT-EVENT]   Contact URI: ${event.uri}")
                println("[CONTACT-EVENT]   Is Confirmed: ${event.confirmed}")

                if (event.accountId == accountId) {
                    // Fetch contact details and add to cache
                    scope.launch {
                        try {
                            val details = jamiBridge.getContactDetails(accountId, event.uri)
                            val displayName = details["displayName"] ?: event.uri.take(8)
                            println("[CONTACT-EVENT]   Display Name: $displayName")

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
                                println("[CONTACT-EVENT] ✓ Contact added to cache: ${event.uri}")
                            } else {
                                println("[CONTACT-EVENT]   Contact already in cache, skipping")
                            }
                        } catch (e: Exception) {
                            println("[CONTACT-EVENT] ✗ Failed to fetch contact details: ${e.message}")
                        }
                    }
                }
            }

            is JamiContactEvent.ContactRemoved -> {
                println("[CONTACT-EVENT] ContactRemoved received")
                println("[CONTACT-EVENT]   Event Account ID: ${event.accountId}")
                println("[CONTACT-EVENT]   Current Account ID: $accountId")
                println("[CONTACT-EVENT]   Contact URI: ${event.uri}")
                println("[CONTACT-EVENT]   Is Banned: ${event.banned}")

                if (event.accountId == accountId) {
                    val currentContacts = _contactsCache.value[accountId] ?: emptyList()
                    val previousCount = currentContacts.size
                    _contactsCache.value = _contactsCache.value +
                        (accountId to currentContacts.filter { it.uri != event.uri })
                    val newCount = (_contactsCache.value[accountId] ?: emptyList()).size
                    println("[CONTACT-EVENT] ✓ Contact removed from cache: $previousCount -> $newCount contacts")
                }
            }

            is JamiContactEvent.PresenceChanged -> {
                if (event.accountId == accountId) {
                    // Update online status cache
                    _onlineStatusCache.value = _onlineStatusCache.value + (event.uri to event.isOnline)

                    // Update last presence timestamp
                    val now = Clock.System.now().toEpochMilliseconds()
                    _lastPresenceTimestamp.value = _lastPresenceTimestamp.value + (event.uri to now)

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
                val timestamp = Clock.System.now().toEpochMilliseconds()
                println("[CONTACT-EVENT] IncomingTrustRequest received")
                println("[CONTACT-EVENT]   Timestamp: $timestamp")
                println("[CONTACT-EVENT]   Event Account ID: ${event.accountId}")
                println("[CONTACT-EVENT]   Current Account ID: $accountId")
                println("[CONTACT-EVENT]   From URI: ${event.from}")
                println("[CONTACT-EVENT]   Conversation ID: ${event.conversationId}")
                println("[CONTACT-EVENT]   Payload size: ${event.payload.size} bytes")
                println("[CONTACT-EVENT]   Received timestamp: ${event.received}")

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
                        println("[CONTACT-EVENT] ✓ Trust request added to cache from: ${event.from}")
                        println("[CONTACT-EVENT]   Total pending requests: ${currentRequests.size + 1}")
                    } else {
                        println("[CONTACT-EVENT]   Trust request already in cache, skipping")
                    }
                }
            }

            is JamiContactEvent.ContactProfileReceived -> {
                println("[CONTACT-EVENT] ContactProfileReceived received")
                println("[CONTACT-EVENT]   Event Account ID: ${event.accountId}")
                println("[CONTACT-EVENT]   Contact URI: ${event.contactUri}")
                println("[CONTACT-EVENT]   Display Name: ${event.displayName}")
                println("[CONTACT-EVENT]   Has Avatar: ${event.avatarBase64 != null}")

                // Skip if this is our own profile (daemon sometimes sends own profile through this callback)
                val userJamiId = accountRepository.accountState.value.jamiId
                if (event.contactUri == userJamiId || event.contactUri == accountId) {
                    println("[CONTACT-EVENT]   Skipping own profile (not a contact)")
                    return
                }

                if (event.accountId == accountId) {
                    scope.launch {
                        // Save avatar if present
                        var avatarPath: String? = null
                        if (event.avatarBase64 != null && avatarStorage != null) {
                            avatarPath = avatarStorage.saveContactAvatar(event.contactUri, event.avatarBase64)
                            println("[CONTACT-EVENT]   Avatar saved to: $avatarPath")
                        }

                        // Update contact in cache with new profile info
                        val currentContacts = _contactsCache.value[accountId] ?: emptyList()
                        val existingContact = currentContacts.find { it.uri == event.contactUri }

                        if (existingContact != null) {
                            // Update existing contact
                            val updatedContact = existingContact.copy(
                                displayName = event.displayName ?: existingContact.displayName,
                                avatarUri = avatarPath ?: existingContact.avatarUri
                            )
                            val updatedContacts = currentContacts.map { contact ->
                                if (contact.uri == event.contactUri) updatedContact else contact
                            }
                            _contactsCache.value = _contactsCache.value + (accountId to updatedContacts)
                            println("[CONTACT-EVENT] ✓ Contact profile updated: ${event.contactUri}")
                        } else {
                            // Contact not in cache yet, create new entry
                            val newContact = Contact(
                                id = event.contactUri,
                                uri = event.contactUri,
                                displayName = event.displayName ?: event.contactUri.take(8),
                                avatarUri = avatarPath,
                                isOnline = _onlineStatusCache.value[event.contactUri] ?: false,
                                isBanned = false
                            )
                            _contactsCache.value = _contactsCache.value +
                                (accountId to (currentContacts + newContact))
                            println("[CONTACT-EVENT] ✓ New contact created from profile: ${event.contactUri}")
                        }
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
     *
     * Note: In Jami's design, the only reliable way to stop a trust request from
     * reappearing is to block the contact. The remote device will keep re-sending
     * until blocked. Therefore, reject always blocks the contact.
     */
    suspend fun rejectTrustRequest(accountId: String, contactUri: String, block: Boolean = false): Result<Unit> {
        return try {
            // Find the trust request to get its conversation ID
            val trustRequest = _trustRequestsCache.value[accountId]?.find { it.from == contactUri }
            val conversationId = trustRequest?.conversationId

            println("[TRUST-REJECT-REPO] Rejecting trust request from: $contactUri")
            println("[TRUST-REJECT-REPO]   Associated conversationId: $conversationId")

            // Always block the contact to prevent re-delivery
            // In Jami, discardTrustRequest only removes locally - the sender keeps re-sending
            // The only way to truly reject is to block (add to ban list)
            println("[TRUST-REJECT-REPO] → Blocking contact to prevent re-delivery")
            jamiBridge.removeContact(accountId, contactUri, ban = true)
            println("[TRUST-REJECT-REPO] ✓ Contact blocked")

            // Also remove the conversation if it exists
            if (!conversationId.isNullOrBlank()) {
                println("[TRUST-REJECT-REPO] → Removing conversation: $conversationId")
                try {
                    jamiBridge.declineConversationRequest(accountId, conversationId)
                    jamiBridge.removeConversation(accountId, conversationId)
                    println("[TRUST-REJECT-REPO] ✓ Conversation removed")
                } catch (e: Exception) {
                    println("[TRUST-REJECT-REPO] ⚠️ Failed to remove conversation: ${e.message}")
                }
            }

            // Remove from trust requests cache
            val currentRequests = _trustRequestsCache.value[accountId] ?: emptyList()
            _trustRequestsCache.value = _trustRequestsCache.value +
                (accountId to currentRequests.filter { it.from != contactUri })

            println("[TRUST-REJECT-REPO] ✓ Trust request rejected and removed from cache")
            Result.success(Unit)
        } catch (e: Exception) {
            println("[TRUST-REJECT-REPO] ✗ Failed to reject: ${e.message}")
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

    /**
     * Check for presence timeouts and mark contacts as offline if they haven't sent
     * a presence update within the timeout period.
     */
    private fun checkPresenceTimeouts() {
        val accountId = accountRepository.currentAccountId.value ?: return
        val now = Clock.System.now().toEpochMilliseconds()
        val currentTimestamps = _lastPresenceTimestamp.value
        val currentOnlineStatus = _onlineStatusCache.value
        val currentContacts = _contactsCache.value[accountId] ?: return

        var statusChanged = false
        val updatedOnlineStatus = currentOnlineStatus.toMutableMap()
        val updatedContacts = mutableListOf<Contact>()

        currentContacts.forEach { contact ->
            val lastPresence = currentTimestamps[contact.uri] ?: 0L
            val timeSinceLastPresence = now - lastPresence
            val isCurrentlyOnline = currentOnlineStatus[contact.uri] ?: false

            // If marked online but no recent presence update, mark as offline
            if (isCurrentlyOnline && timeSinceLastPresence > PRESENCE_TIMEOUT_MS) {
                println("ContactRepository: Presence timeout for ${contact.uri.take(16)}... (${timeSinceLastPresence}ms since last update)")
                updatedOnlineStatus[contact.uri] = false
                updatedContacts.add(contact.copy(isOnline = false))
                statusChanged = true
            } else {
                updatedContacts.add(contact)
            }
        }

        if (statusChanged) {
            println("ContactRepository: Updating caches after presence timeout")
            _onlineStatusCache.value = updatedOnlineStatus
            val newCache = _contactsCache.value.toMutableMap()
            newCache[accountId] = updatedContacts
            _contactsCache.value = newCache
            println("ContactRepository: Cache updated, flow should emit now")
        }
    }
}
