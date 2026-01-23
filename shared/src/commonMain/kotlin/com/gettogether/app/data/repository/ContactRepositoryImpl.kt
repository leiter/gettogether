package com.gettogether.app.data.repository

import com.gettogether.app.domain.model.Contact
import com.gettogether.app.domain.repository.ContactRepository
import com.gettogether.app.jami.DataPathProvider
import com.gettogether.app.jami.JamiBridge
import com.gettogether.app.jami.JamiContactEvent
import com.gettogether.app.jami.TrustRequest
import com.gettogether.app.platform.AppLifecycleManager
import com.gettogether.app.util.procrastinate
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlin.time.Clock

/**
 * Implementation of ContactRepository using JamiBridge.
 */
class ContactRepositoryImpl(
    private val jamiBridge: JamiBridge,
    private val accountRepository: AccountRepository,
    private val contactPersistence: com.gettogether.app.data.persistence.ContactPersistence,
    private val lifecycleManager: AppLifecycleManager,
    private val dataPathProvider: DataPathProvider? = null
) : ContactRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pollingJob: Job? = null

    // Cache for contacts by account
    internal val internalContactsCache = MutableStateFlow<Map<String, List<Contact>>>(emptyMap())

    // Cache for online status by contact URI
    private val _onlineStatusCache = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    // Cache for last presence update timestamp by contact URI
    private val _lastPresenceTimestamp = MutableStateFlow<Map<String, Long>>(emptyMap())

    // Cache for last subscribe timestamp by contact URI (to detect stale daemon cache responses)
    private val _lastSubscribeTimestamp = MutableStateFlow<Map<String, Long>>(emptyMap())

    // Track which contacts we've already subscribed to (to avoid re-setting timestamp on refresh)
    private val _subscribedContacts = mutableSetOf<String>()

    // Cache for last poll timestamp by contact URI (for tiered polling of offline contacts)
    private val _lastOfflinePollTimestamp = MutableStateFlow<Map<String, Long>>(emptyMap())

    // Cache for trust requests by account
    private val _trustRequestsCache = MutableStateFlow<Map<String, List<TrustRequest>>>(emptyMap())

    // Shared flows for optimized contact list transformations (one computation shared across subscribers)
    private val sharedContactFlows = mutableMapOf<String, SharedFlow<List<Contact>>>()

    companion object {
        private const val PRESENCE_TIMEOUT_MS = 90_000L // 90 seconds (increased to allow polling to refresh first)
        private const val PRESENCE_POLL_INTERVAL_MS = 60_000L // 60 seconds - base poll interval (for online contacts)
        private const val OFFLINE_POLL_INTERVAL_MS = 300_000L // 5 minutes - poll interval for offline contacts (less frequent)
        private const val SUBSCRIBE_IGNORE_WINDOW_MS = 2_000L // Ignore presence updates within 2 seconds of subscribe (likely stale cache)
    }

    /**
     * Compute the path to the daemon's vCard file for a contact.
     *
     * The daemon stores contact profiles at:
     * {filesDir}/{accountId}/profiles/{base64(contactUri)}.vcf
     *
     * Note: dataPathProvider.getDataPath() returns {filesDir}/jami (daemon config dir),
     * but profiles are stored in the parent directory ({filesDir}).
     *
     * These vCard files contain the contact's display name and base64-encoded avatar.
     * Coil's VCardFetcher extracts the avatar directly from these files.
     */
    @OptIn(ExperimentalEncodingApi::class)
    private fun getContactVCardPath(accountId: String, contactUri: String): String? {
        val daemonPath = dataPathProvider?.getDataPath() ?: return null
        // Daemon stores profiles in parent of daemon config path
        // daemonPath = {filesDir}/jami, profiles at {filesDir}/{accountId}/profiles/
        val filesDir = daemonPath.substringBeforeLast("/jami")
        val encodedUri = Base64.encode(contactUri.encodeToByteArray())
        return "$filesDir/$accountId/profiles/$encodedUri.vcf"
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
                    // Clear online status caches on account change to ensure fresh state
                    // This prevents stale online status from previous sessions
                    println("ContactRepository: Clearing online status caches for fresh start")
                    _onlineStatusCache.value = emptyMap()
                    _lastPresenceTimestamp.value = emptyMap()
                    _lastSubscribeTimestamp.value = emptyMap()
                    _lastOfflinePollTimestamp.value = emptyMap()
                    _subscribedContacts.clear()

                    // Load persisted contacts first (for displayName, avatar, customName)
                    loadPersistedContacts(accountId)
                    // Then refresh from Jami to get latest (will set isOnline = false initially)
                    refreshContacts(accountId)
                    refreshTrustRequests(accountId)
                } else {
                    // Account logged out - clean up shared flows to free resources
                    println("ContactRepository: Clearing shared flows (account logout)")
                    sharedContactFlows.clear()
                    _subscribedContacts.clear()
                    _lastOfflinePollTimestamp.value = emptyMap()
                }
            }
        }

        // Periodic presence timeout checker
        scope.launch {
            while (true) {
                delay(10_000) // Check every 10 seconds
                checkPresenceTimeouts()
            }
        }

        // Periodic contact presence polling (unsubscribe/resubscribe to force fresh DHT query)
        // Polls before timeout to maintain continuous online status
        // ONLY polls when app is in foreground to save battery
        scope.launch {
            lifecycleManager.isInForeground.collect { isInForeground ->
                if (isInForeground) {
                    println("[PRESENCE-POLL-LIFECYCLE] App is in FOREGROUND → Starting polling")
                    startPolling()
                } else {
                    println("[PRESENCE-POLL-LIFECYCLE] App is in BACKGROUND → Stopping polling")
                    stopPolling()
                }
            }
        }

        // Auto-save contacts when cache changes
        scope.launch {
            internalContactsCache.collect { contactsMap ->
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
            if (internalContactsCache.value[accountId].isNullOrEmpty()) {
                refreshContacts(accountId)

                // If still empty after refresh, wait for daemon to initialize  todo find out if that should be reenabled
//                if (internalContactsCache.value[accountId].isNullOrEmpty()) {
//                    procrastinate(
//                        delayMs = 500,
//                        condition = { jamiBridge.getContacts(accountId).isNotEmpty() },
//                        onRetrySuccess = { refreshContacts(accountId) }
//                    )
//                }
            }
        }

        // Return cached shared flow or create new one
        // This optimizes the flow so that multiple subscribers share the same transformation
        // instead of each creating their own map{} operation
        return sharedContactFlows.getOrPut(accountId) {
            internalContactsCache.map { cache ->
                val contacts = cache[accountId] ?: emptyList()
                // Apply online status from cache
                contacts.map { contact ->
                    contact.copy(isOnline = _onlineStatusCache.value[contact.uri] ?: false)
                }
            }.shareIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5000),
                replay = 1
            )
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
            val currentContacts = internalContactsCache.value[accountId] ?: emptyList()
            if (currentContacts.none { it.uri == uri }) {
                internalContactsCache.value += (accountId to (currentContacts + contact))
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
            val currentContacts = internalContactsCache.value[accountId] ?: emptyList()
            internalContactsCache.value += (accountId to currentContacts.filter { it.id != contactId && it.uri != contactId })

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun blockContact(accountId: String, contactId: String): Result<Unit> {
        return try {
            jamiBridge.removeContact(accountId, contactId, ban = true)

            // Update cache to mark as banned
            val currentContacts = internalContactsCache.value[accountId] ?: emptyList()
            val updatedContacts = currentContacts.map { contact ->
                if (contact.id == contactId || contact.uri == contactId) {
                    contact.copy(isBanned = true)
                } else {
                    contact
                }
            }
            internalContactsCache.value += (accountId to updatedContacts)

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
            val currentContacts = internalContactsCache.value[accountId] ?: emptyList()
            val updatedContacts = currentContacts.map { contact ->
                if (contact.id == contactId || contact.uri == contactId) {
                    contact.copy(isBanned = false)
                } else {
                    contact
                }
            }
            internalContactsCache.value += (accountId to updatedContacts)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateCustomName(accountId: String, contactId: String, customName: String): Result<Unit> {
        return try {
            // Update custom name in cache
            val currentContacts = internalContactsCache.value[accountId] ?: emptyList()
            val updatedContacts = currentContacts.map { contact ->
                if (contact.id == contactId || contact.uri == contactId) {
                    contact.copy(customName = customName.takeIf { it.isNotBlank() })
                } else {
                    contact
                }
            }
            internalContactsCache.value += (accountId to updatedContacts)

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
     *
     * Note: Persisted contacts have isBanned=false by default (volatile fields are not persisted).
     * We immediately query the daemon to get the current ban status for each contact.
     */
    private suspend fun loadPersistedContacts(accountId: String) {
        println("ContactRepository: loadPersistedContacts() for account: $accountId")
        try {
            val persistedContacts = contactPersistence.loadContacts(accountId)
            println("ContactRepository: ✓ Loaded ${persistedContacts.size} persisted contacts")
            if (persistedContacts.isNotEmpty()) {
                persistedContacts.forEach { contact ->
                    println("  - ${contact.displayName} (${contact.uri.take(16)}...) [isBanned=${contact.isBanned}]")
                }

                // Query daemon for current ban status to avoid stale persisted state
                // This is important because isBanned is not persisted and defaults to false
                val contactsWithBanStatus = try {
                    println("ContactRepository: → Querying daemon for current ban status...")
                    val daemonContacts = jamiBridge.getContacts(accountId)
                    val daemonContactsMap = daemonContacts.associateBy { it.uri }

                    persistedContacts.map { contact ->
                        val daemonContact = daemonContactsMap[contact.uri]
                        if (daemonContact != null) {
                            // Merge: use persisted data but daemon's ban status
                            contact.copy(isBanned = daemonContact.isBanned)
                        } else {
                            // Contact not in daemon (maybe removed), keep default
                            contact
                        }
                    }
                } catch (e: Exception) {
                    println("ContactRepository: ⚠️ Failed to query daemon for ban status: ${e.message}")
                    // Fall back to persisted contacts without ban status correction
                    persistedContacts
                }

                internalContactsCache.value += (accountId to contactsWithBanStatus)
                println("ContactRepository: ✓ Added persisted contacts to cache with daemon ban status")
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
            val existingContacts = internalContactsCache.value[accountId] ?: emptyList()
            val existingContactsMap = existingContacts.associateBy { it.uri }

            val contacts = jamiContacts.map { jamiContact ->
                println("  - Mapping contact: ${jamiContact.displayName} (${jamiContact.uri.take(16)}...)")

                // Preserve data from existing contact if available (from profile events or persistence)
                val existingContact = existingContactsMap[jamiContact.uri]
                val customName = existingContact?.customName

                // Preserve displayName from existing contact if daemon returns blank
                // (profile events update displayName with actual contact name)
                val displayName = jamiContact.displayName.ifBlank {
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
            internalContactsCache.value += (accountId to contacts)
            println("ContactRepository: ✓ Updated cache with ${contacts.size} contacts")

            // Persist contacts (preserves avatarUri from existing contacts)
            persistContacts(accountId)

            // Subscribe to presence for all contacts
            println("ContactRepository: → Subscribing to presence for all contacts...")
            contacts.forEach { contact ->
                try {
                    val isFirstSubscribe = !_subscribedContacts.contains(contact.uri)

                    // Only record subscribe timestamp on FIRST subscribe
                    // This prevents the stale filter from blocking real presence updates on refresh
                    if (isFirstSubscribe) {
                        val now = Clock.System.now().toEpochMilliseconds()
                        _lastSubscribeTimestamp.value = _lastSubscribeTimestamp.value + (contact.uri to now)
                        _subscribedContacts.add(contact.uri)
                        println("  → First subscribe to: ${contact.uri.take(16)}...")
                    } else {
                        println("  → Re-subscribing to: ${contact.uri.take(16)}... (no timestamp update)")
                    }

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

                            val currentContacts = internalContactsCache.value[accountId] ?: emptyList()
                            if (currentContacts.none { it.uri == event.uri }) {
                                internalContactsCache.value += (accountId to (currentContacts + contact))
                                println("[CONTACT-EVENT] ✓ Contact added to cache: ${event.uri}")

                                // Persist updated contacts
                                persistContacts(accountId)
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
                    scope.launch {
                        val currentContacts = internalContactsCache.value[accountId] ?: emptyList()

                        if (event.banned) {
                            // Contact was blocked - keep in cache but mark as banned
                            val updatedContacts = currentContacts.map { contact ->
                                if (contact.uri == event.uri) {
                                    contact.copy(isBanned = true)
                                } else {
                                    contact
                                }
                            }
                            // If contact wasn't in cache, add it as banned
                            val contactExists = currentContacts.any { it.uri == event.uri }
                            val finalContacts = if (!contactExists) {
                                updatedContacts + Contact(
                                    id = event.uri,
                                    uri = event.uri,
                                    displayName = event.uri.take(8),
                                    isBanned = true
                                )
                            } else {
                                updatedContacts
                            }
                            internalContactsCache.value += (accountId to finalContacts)
                            println("[CONTACT-EVENT] ✓ Contact marked as banned: ${event.uri}")
                        } else {
                            // Contact was removed (not blocked) - remove from cache
                            val previousCount = currentContacts.size
                            internalContactsCache.value += (accountId to currentContacts.filter { it.uri != event.uri })
                            val newCount = (internalContactsCache.value[accountId] ?: emptyList()).size
                            println("[CONTACT-EVENT] ✓ Contact removed from cache: $previousCount -> $newCount contacts")
                        }

                        // Persist updated contacts
                        persistContacts(accountId)
                    }
                }
            }

            is JamiContactEvent.PresenceChanged -> {
                if (event.accountId == accountId) {
                    val now = Clock.System.now().toEpochMilliseconds()

                    // Check if this event is likely from stale daemon cache (triggered by our polling)
                    // Events within SUBSCRIBE_IGNORE_WINDOW_MS of a subscribe are likely cached, not real
                    val lastSubscribeTime = _lastSubscribeTimestamp.value[event.uri] ?: 0L
                    val timeSinceSubscribe = now - lastSubscribeTime
                    val isLikelyFromPolling = timeSinceSubscribe < SUBSCRIBE_IGNORE_WINDOW_MS

                    println("[PRESENCE-UPDATE] PresenceChanged for ${event.uri.take(16)}... → ${if (event.isOnline) "ONLINE" else "OFFLINE"} (timeSinceSubscribe=${timeSinceSubscribe}ms, likelyPolling=$isLikelyFromPolling)")

                    // CRITICAL: Ignore ALL stale ONLINE events from polling/subscribe
                    // The daemon returns cached ONLINE even when contact is offline
                    // Only trust: OFFLINE events (reliable) or real network ONLINE events (>2s after subscribe)
                    if (event.isOnline && isLikelyFromPolling) {
                        println("[PRESENCE-UPDATE]   → Ignoring stale ONLINE from polling (daemon cache)")
                        return
                    }

                    // Update online status cache
                    _onlineStatusCache.value += (event.uri to event.isOnline)

                    // Update timestamp for real ONLINE events
                    if (event.isOnline) {
                        println("[PRESENCE-UPDATE]   → Real network ONLINE event, updating timestamp")
                        _lastPresenceTimestamp.value += (event.uri to now)

                        // Trigger profile sync when contact comes online
                        // This ensures they receive our updated profile even if they missed earlier updates
                        scope.launch {
                            triggerProfileSyncToContact(event.uri)
                        }
                    }

                    // Update contact in cache
                    val currentContacts = internalContactsCache.value[accountId] ?: emptyList()
                    val updatedContacts = currentContacts.map { contact ->
                        if (contact.uri == event.uri) {
                            contact.copy(isOnline = event.isOnline)
                        } else {
                            contact
                        }
                    }
                    internalContactsCache.value += (accountId to updatedContacts)
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
                        _trustRequestsCache.value += (accountId to (currentRequests + trustRequest))
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
                        // Get the vCard path where daemon stores the contact's profile
                        // The vCard contains the avatar - Coil's VCardFetcher extracts it
                        val vCardPath = getContactVCardPath(accountId, event.contactUri)
                        val hasAvatarInProfile = event.avatarBase64 != null

                        if (vCardPath != null) {
                            println("[CONTACT-EVENT]   vCard path: $vCardPath")
                        } else {
                            println("[CONTACT-EVENT]   Warning: Could not compute vCard path (dataPathProvider not available)")
                        }

                        // Update contact in cache with new profile info
                        val currentContacts = internalContactsCache.value[accountId] ?: emptyList()
                        val existingContact = currentContacts.find { it.uri == event.contactUri }

                        if (existingContact != null) {
                            // Update existing contact
                            // IMPORTANT: Only update avatarUri if profile actually contained avatar data
                            // If profile has no avatar, preserve existing avatar (might be from previous sync)
                            // This prevents clearing avatar when contact sends incomplete profile during startup
                            val newAvatarUri = if (hasAvatarInProfile && vCardPath != null) vCardPath else existingContact.avatarUri
                            val updatedContact = existingContact.copy(
                                displayName = event.displayName ?: existingContact.displayName,
                                avatarUri = newAvatarUri,
                                profileVersion = existingContact.profileVersion + 1  // Force StateFlow emission
                            )
                            val updatedContacts = currentContacts.map { contact ->
                                if (contact.uri == event.contactUri) updatedContact else contact
                            }
                            internalContactsCache.value += (accountId to updatedContacts)
                            println("[CONTACT-EVENT] ✓ Contact profile updated: ${event.contactUri} (version=${updatedContact.profileVersion})")
                            println("[CONTACT-EVENT]   Avatar: ${if (hasAvatarInProfile) "using vCard: $vCardPath" else "preserved existing: ${existingContact.avatarUri}"}")

                            // Persist updated contacts to storage
                            persistContacts(accountId)
                        } else {
                            // Contact not in cache yet, create new entry
                            val newContact = Contact(
                                id = event.contactUri,
                                uri = event.contactUri,
                                displayName = event.displayName ?: event.contactUri.take(8),
                                avatarUri = if (hasAvatarInProfile) vCardPath else null,
                                isOnline = _onlineStatusCache.value[event.contactUri] ?: false,
                                isBanned = false
                            )
                            internalContactsCache.value += (accountId to (currentContacts + newContact))
                            println("[CONTACT-EVENT] ✓ New contact created from profile: ${event.contactUri}")

                            // Persist updated contacts to storage
                            persistContacts(accountId)
                        }
                    }
                }
            }
        }
    }

    /**
     * Persist current contacts cache to storage.
     */
    private suspend fun persistContacts(accountId: String) {
        try {
            val contacts = internalContactsCache.value[accountId] ?: return
            contactPersistence.saveContacts(accountId, contacts)
            println("[CONTACT-PERSIST] ✓ Contacts persisted after profile update")
        } catch (e: Exception) {
            println("[CONTACT-PERSIST] ✗ Failed to persist contacts: ${e.message}")
        }
    }

    /**
     * Refresh trust requests from JamiBridge.
     */
    fun refreshTrustRequests(accountId: String) {
        try {
            val requests = jamiBridge.getTrustRequests(accountId)
            _trustRequestsCache.value += (accountId to requests)
        } catch (e: kotlinx.coroutines.CancellationException) {
            println("ContactRepository: ⚠️ refreshTrustRequests failed: ${e.message}")
            throw e  // Don't swallow coroutine cancellation
        } catch (e: Exception) {
            println("ContactRepository: ⚠️ refreshTrustRequests failed: ${e.message}")
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
            _trustRequestsCache.value += (accountId to currentRequests.filter { it.from != contactUri })

            // Give daemon time to commit to git before refreshing
            delay(500)

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
    suspend fun rejectTrustRequest(accountId: String, contactUri: String): Result<Unit> {
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
            _trustRequestsCache.value += (
                accountId to currentRequests.filter { it.from != contactUri }
                )

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
        val currentContacts = internalContactsCache.value[accountId] ?: return

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
            val newCache = internalContactsCache.value.toMutableMap()
            newCache[accountId] = updatedContacts
            internalContactsCache.value = newCache
            println("ContactRepository: Cache updated, flow should emit now")
        }
    }

    // Track last profile sync time to debounce rapid online/offline events
    private var lastProfileSyncTimestamp: Long = 0
    private val PROFILE_SYNC_DEBOUNCE_MS = 5_000L // Minimum 5 seconds between profile syncs

    /**
     * Trigger sending our profile to a contact that just came online.
     *
     * When a contact comes online, we want to ensure they have our latest profile,
     * especially if they were offline when we last updated our profile.
     *
     * The daemon's sendProfileToPeers() only sends to currently connected peers,
     * so contacts who were offline during a profile update won't receive it automatically.
     * This method triggers a profile push by calling updateProfile with current values.
     */
    private suspend fun triggerProfileSyncToContact(contactUri: String) {
        val now = Clock.System.now().toEpochMilliseconds()

        // Debounce to avoid excessive profile syncs when multiple contacts come online
        if (now - lastProfileSyncTimestamp < PROFILE_SYNC_DEBOUNCE_MS) {
            println("[PROFILE-SYNC] Debouncing profile sync for ${contactUri.take(16)}... (too soon since last sync)")
            return
        }

        try {
            val currentProfile = accountRepository.accountState.value
            val displayName = currentProfile.displayName
            val avatarPath = currentProfile.avatarPath

            if (displayName.isBlank()) {
                println("[PROFILE-SYNC] Skipping profile sync - no display name set")
                return
            }

            // Check if account is fully loaded
            if (!currentProfile.isLoaded) {
                println("[PROFILE-SYNC] Skipping profile sync - account not fully loaded yet")
                return
            }

            println("[PROFILE-SYNC] Contact ${contactUri.take(16)}... came online - triggering profile push")
            println("[PROFILE-SYNC]   Current displayName: '$displayName'")
            println("[PROFILE-SYNC]   Current avatarPath: ${avatarPath ?: "null (no avatar set)"}")

            // Call updateProfile with explicit avatar path to ensure it's included
            // Pass the avatar path explicitly rather than relying on null -> use stored path
            // This ensures we send the correct avatar even if there's a race condition
            accountRepository.updateProfile(displayName, avatarPath)

            lastProfileSyncTimestamp = now
            println("[PROFILE-SYNC] ✓ Profile push triggered successfully")
        } catch (e: Exception) {
            println("[PROFILE-SYNC] ✗ Failed to trigger profile sync: ${e.message}")
        }
    }

    /**
     * Poll contact presence by unsubscribing and resubscribing.
     * This forces the daemon to query DHT for fresh presence data instead of using cache.
     *
     * TIERED POLLING STRATEGY:
     * - Online contacts: Poll every cycle (60s) to quickly detect when they go offline
     * - Offline contacts: Poll less frequently (5min) to discover when they come online
     *
     * This reduces DHT query volume while maintaining responsive online detection.
     */
    private suspend fun pollContactPresence() {
        val accountId = accountRepository.currentAccountId.value ?: return
        val contacts = internalContactsCache.value[accountId] ?: return

        if (contacts.isEmpty()) {
            println("[PRESENCE-POLL] No contacts to poll")
            return
        }

        val now = Clock.System.now().toEpochMilliseconds()

        // Split contacts into online and offline
        val onlineContacts = contacts.filter { _onlineStatusCache.value[it.uri] == true }
        val offlineContacts = contacts.filter { _onlineStatusCache.value[it.uri] != true }

        // Determine which offline contacts are due for polling
        val offlineContactsDueToPoll = offlineContacts.filter { contact ->
            val lastPoll = _lastOfflinePollTimestamp.value[contact.uri] ?: 0L
            (now - lastPoll) >= OFFLINE_POLL_INTERVAL_MS
        }

        val contactsToPoll = onlineContacts + offlineContactsDueToPoll
        val skippedOffline = offlineContacts.size - offlineContactsDueToPoll.size

        println("[PRESENCE-POLL] === Tiered polling: ${contactsToPoll.size} contacts ===")
        println("[PRESENCE-POLL]   Online (always poll): ${onlineContacts.size}")
        println("[PRESENCE-POLL]   Offline (due for poll): ${offlineContactsDueToPoll.size}")
        println("[PRESENCE-POLL]   Offline (skipped, polled recently): $skippedOffline")

        if (contactsToPoll.isEmpty()) {
            println("[PRESENCE-POLL] === No contacts due for polling ===")
            return
        }

        contactsToPoll.forEach { contact ->
            try {
                val isOnline = _onlineStatusCache.value[contact.uri] == true
                val tier = if (isOnline) "ONLINE" else "OFFLINE"
                println("[PRESENCE-POLL] Polling ${contact.uri.take(16)}... ($tier)")

                // Unsubscribe
                jamiBridge.subscribeBuddy(accountId, contact.uri, false)

                // Small delay to let unsubscribe complete
                delay(100)

                // Update subscribe timestamp BEFORE resubscribing
                // This ensures the stale filter can catch immediate daemon responses
                _lastSubscribeTimestamp.value += (contact.uri to now)

                // Re-subscribe (should force fresh DHT query)
                jamiBridge.subscribeBuddy(accountId, contact.uri, true)

                // Track poll time for offline contacts (for tiered polling)
                if (!isOnline) {
                    _lastOfflinePollTimestamp.value += (contact.uri to now)
                }

                println("[PRESENCE-POLL]   ✓ Refreshed")

            } catch (e: Exception) {
                println("[PRESENCE-POLL]   ✗ Failed: ${e.message}")
            }
        }

        println("[PRESENCE-POLL] === Poll complete (refreshed ${contactsToPoll.size} contacts) ===")
    }

    /**
     * Start the background polling job.
     * Called when app comes to foreground.
     */
    private fun startPolling() {
        // Cancel existing job if any
        pollingJob?.cancel()

        // Start new polling job
        pollingJob = scope.launch {
            // IMPORTANT: Skip immediate poll on start
            // The initial subscribe (in refreshContacts) triggers daemon to return cached state
            // which is often stale. Wait for first poll interval before polling.
            // Real network events will update presence in the meantime.
            println("[PRESENCE-POLL-LIFECYCLE] Waiting ${PRESENCE_POLL_INTERVAL_MS}ms before first poll (avoiding stale cache)")

            // Wait before first poll
            delay(PRESENCE_POLL_INTERVAL_MS)

            // Then poll periodically
            while (true) {
                pollContactPresence()
                delay(PRESENCE_POLL_INTERVAL_MS)
            }
        }
        println("[PRESENCE-POLL-LIFECYCLE] ✓ Polling started (first poll in ${PRESENCE_POLL_INTERVAL_MS}ms)")
    }

    /**
     * Stop the background polling job.
     * Called when app goes to background.
     */
    private fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
        println("[PRESENCE-POLL-LIFECYCLE] ✓ Polling stopped")
    }
}
