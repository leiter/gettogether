package com.gettogether.app.data.repository

import com.gettogether.app.domain.model.ExistingAccount
import com.gettogether.app.jami.JamiBridge
import com.gettogether.app.jami.JamiAccountEvent
import com.gettogether.app.jami.RegistrationState
import com.gettogether.app.util.procrastinate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Repository for managing the current Jami account.
 * Tracks the active account ID and provides account-related operations.
 */
class AccountRepository(
    private val jamiBridge: JamiBridge,
    private val presenceManager: PresenceManager,
    private val settingsRepository: SettingsRepository? = null
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _currentAccountId = MutableStateFlow<String?>(null)
    val currentAccountId: StateFlow<String?> = _currentAccountId.asStateFlow()

    private val _accountState = MutableStateFlow(AccountState())
    val accountState: StateFlow<AccountState> = _accountState.asStateFlow()

    init {
        println("[ACCOUNT-RESTORE] AccountRepository initializing...")

        // Listen to account events
        try {
            scope.launch {
                println("[ACCOUNT-RESTORE] Starting account events collection...")
                jamiBridge.accountEvents.collect { event ->
                    handleAccountEvent(event)
                }
            }
            println("[ACCOUNT-RESTORE] Account events collection started successfully")
        } catch (e: Exception) {
            println("[ACCOUNT-RESTORE] ERROR: Failed to start account events collection: ${e.message}")
        }

        // Observe avatar path from settings to keep AccountState in sync
        settingsRepository?.let { settings ->
            scope.launch {
                settings.avatarPath.collect { avatarPath ->
                    _accountState.value = _accountState.value.copy(avatarPath = avatarPath)
                    println("[ACCOUNT] Avatar path synced from settings: $avatarPath")
                }
            }
        }

        // Watch account changes and manage presence broadcasting
        scope.launch {
            var previousAccountId: String? = null
            currentAccountId.collect { accountId ->
                println("[ACCOUNT-LIFECYCLE] Account changed: $previousAccountId -> $accountId")

                // Only act if the account actually changed
                if (previousAccountId != accountId) {
                    // Stop broadcasting for previous account (if different)
                    val prevId = previousAccountId
                    if (prevId != null) {
                        println("[ACCOUNT-LIFECYCLE] Stopping presence for previous account: ${prevId.take(16)}...")
                        presenceManager.stopBroadcasting(prevId)
                    }

                    // Start broadcasting for new account
                    if (accountId != null) {
                        println("[ACCOUNT-LIFECYCLE] Starting presence for new account: ${accountId.take(16)}...")
                        presenceManager.startBroadcasting(accountId)
                    } else {
                        println("[ACCOUNT-LIFECYCLE] No account active (logged out)")
                    }

                    previousAccountId = accountId
                } else {
                    println("[ACCOUNT-LIFECYCLE] Account unchanged, skipping presence restart")
                }
            }
        }

        // Load existing accounts on startup
        try {
            scope.launch {
                println("[ACCOUNT-RESTORE] Starting account loading...")
                loadAccounts()
            }
            println("[ACCOUNT-RESTORE] Account loading initiated")
        } catch (e: Exception) {
            println("[ACCOUNT-RESTORE] ERROR: Failed to start account loading: ${e.message}")
        }
    }

    /**
     * Enable peer discovery and presence settings for an account.
     * These settings are required for proper continuous presence detection.
     *
     * Based on official jami-android-client analysis:
     * - Account.peerDiscovery: Enables DHT peer discovery
     * - Account.accountDiscovery: Enables account discovery
     * - Account.accountPublish: Enables account publishing for discovery
     * - Account.presenceEnabled: Enables presence information sharing
     */
    private suspend fun enablePeerDiscoverySettings(accountId: String) {
        println("[PRESENCE-CONFIG] === Enabling peer discovery settings for account: ${accountId.take(16)}... ===")

        try {
            // Get current account details
            val currentDetails = jamiBridge.getAccountDetails(accountId).toMutableMap()

            // Enable peer discovery settings
            currentDetails["Account.peerDiscovery"] = "true"
            currentDetails["Account.accountDiscovery"] = "true"
            currentDetails["Account.accountPublish"] = "true"
            currentDetails["Account.presenceEnabled"] = "true"

            println("[PRESENCE-CONFIG] Setting account details:")
            println("[PRESENCE-CONFIG]   Account.peerDiscovery = true")
            println("[PRESENCE-CONFIG]   Account.accountDiscovery = true")
            println("[PRESENCE-CONFIG]   Account.accountPublish = true")
            println("[PRESENCE-CONFIG]   Account.presenceEnabled = true")

            // Apply the settings
            jamiBridge.setAccountDetails(accountId, currentDetails)

            println("[PRESENCE-CONFIG] ✓ Peer discovery settings enabled successfully")
        } catch (e: Exception) {
            println("[PRESENCE-CONFIG] ✗ Failed to enable peer discovery settings: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Load existing accounts and set the first one as current.
     */
    suspend fun loadAccounts() {
        println("[ACCOUNT-RESTORE] === loadAccounts() called ===")
        try {
            println("[ACCOUNT-RESTORE] Calling jamiBridge.getAccountIds()...")
            val accountIds = jamiBridge.getAccountIds()
            println("[ACCOUNT-RESTORE] Found ${accountIds.size} accounts: $accountIds")

            if (accountIds.isNotEmpty()) {
                val accountId = accountIds.first()
                println("[ACCOUNT-RESTORE] Found account: $accountId")

                // Load account details BEFORE setting currentAccountId
                // This ensures accountState.jamiId is available when repositories react to the change
                println("[ACCOUNT-RESTORE] Loading account details for: $accountId")
                val details = jamiBridge.getAccountDetails(accountId)
                println("[ACCOUNT-RESTORE] Account details loaded: displayName='${details["Account.displayName"]}', username='${details["Account.username"]}'")

                // Ensure DHT proxy is enabled for NAT traversal (important for emulators and devices behind NAT)
                val proxyEnabled = details["Account.proxyEnabled"]
                if (proxyEnabled != "true") {
                    println("[ACCOUNT-RESTORE] Enabling DHT proxy for better connectivity...")
                    jamiBridge.setAccountDetails(accountId, mapOf("Account.proxyEnabled" to "true"))
                    println("[ACCOUNT-RESTORE] ✓ DHT proxy enabled")
                }

                // Disable UPnP - it fails in emulators and causes unnecessary connection attempts and instability
                val upnpEnabled = details["Account.upnpEnabled"]
                if (upnpEnabled == "true") {
                    println("[ACCOUNT-RESTORE] Disabling UPnP for better stability...")
                    jamiBridge.setAccountDetails(accountId, mapOf("Account.upnpEnabled" to "false"))
                    println("[ACCOUNT-RESTORE] ✓ UPnP disabled")
                }

                // Enable TURN - required for cross-network connectivity via relay
                // TURN relay is the fallback when direct/STUN connections fail across NAT
                val turnEnabled = details["TURN.enable"]
                if (turnEnabled != "true") {
                    println("[ACCOUNT-RESTORE] Enabling TURN for cross-network relay support...")
                    jamiBridge.setAccountDetails(accountId, mapOf(
                        "TURN.enable" to "true",
                        "TURN.server" to "turn.jami.net",
                        "TURN.username" to "ring",
                        "TURN.password" to "ring"
                    ))
                    println("[ACCOUNT-RESTORE] ✓ TURN enabled (turn.jami.net)")
                }

                // Enable peer discovery and presence settings (includes accountPublish, peerDiscovery, etc.)
                enablePeerDiscoverySettings(accountId)

                println("[ACCOUNT-RESTORE] Loading volatile details for: $accountId")
                val volatileDetails = jamiBridge.getVolatileAccountDetails(accountId)
                val regStatus = volatileDetails["Account.registrationStatus"]
                println("[ACCOUNT-RESTORE] Volatile details: registrationStatus='$regStatus'")

                val regState = parseRegistrationState(regStatus)
                println("[ACCOUNT-RESTORE] Parsed registration state: $regState")

                _accountState.value = AccountState(
                    accountId = accountId,
                    displayName = details["Account.displayName"] ?: "",
                    username = details["Account.username"] ?: "",
                    jamiId = details["Account.username"] ?: accountId,
                    registrationState = regState,
                    isLoaded = true
                )
                println("[ACCOUNT-RESTORE] ✓ Account state updated successfully")
                println("[ACCOUNT-RESTORE]   accountId: $accountId")
                println("[ACCOUNT-RESTORE]   displayName: ${_accountState.value.displayName}")
                println("[ACCOUNT-RESTORE]   username: ${_accountState.value.username}")
                println("[ACCOUNT-RESTORE]   registrationState: ${_accountState.value.registrationState}")
                println("═══════════════════════════════════════════════════════════════")
                println("[JAMI-ID] Account loaded - Jami ID (40-char): $accountId")
                println("[JAMI-ID] Share this ID with others to receive contact requests")
                println("═══════════════════════════════════════════════════════════════")

                // Set currentAccountId AFTER accountState is fully loaded
                // This ensures repositories have access to jamiId when they react
                println("[ACCOUNT-RESTORE] Setting current account ID: $accountId")
                _currentAccountId.value = accountId
            } else {
                // Brief delay to allow daemon initialization events to arrive
                // This prevents Welcome screen flicker when daemon hasn't fully initialized yet
                println("[ACCOUNT-RESTORE] No accounts found initially - waiting for daemon to initialize...")
                val found = procrastinate(
                    delayMs = 500,
                    condition = { jamiBridge.getAccountIds().isNotEmpty() },
                    onRetrySuccess = {
                        println("[ACCOUNT-RESTORE] Found accounts after delay - reloading")
                        loadAccounts()
                    }
                )
                if (!found) {
                    println("[ACCOUNT-RESTORE] No accounts found after delay - setting empty state")
                    _accountState.value = AccountState(isLoaded = true)
                }
            }
        } catch (e: Exception) {
            println("[ACCOUNT-RESTORE] ERROR: Exception during loadAccounts(): ${e.message}")
            e.printStackTrace()
            _accountState.value = AccountState(
                isLoaded = true,
                error = e.message
            )
        }
    }

    /**
     * Create a new account with the given display name.
     * Waits for the account to be fully registered on the Jami network before returning.
     */
    suspend fun createAccount(displayName: String): String {
        println("[ACCOUNT-CREATE] === AccountRepository.createAccount() called ===")
        println("[ACCOUNT-CREATE] displayName='$displayName'")

        println("[ACCOUNT-CREATE] Calling jamiBridge.createAccount()...")
        val accountId = jamiBridge.createAccount(displayName)
        println("[ACCOUNT-CREATE] jamiBridge.createAccount() returned accountId='$accountId'")

        // Enable peer discovery and presence settings for new account
        enablePeerDiscoverySettings(accountId)

        println("[ACCOUNT-CREATE] Setting current account ID to: $accountId")
        _currentAccountId.value = accountId

        println("[ACCOUNT-CREATE] Updating account state with TRYING registration state...")
        _accountState.value = AccountState(
            accountId = accountId,
            displayName = displayName,
            registrationState = RegistrationState.TRYING,
            isLoaded = true
        )
        println("[AccountRepo] createAccount: Set initial state (jamiId will be fetched when REGISTERED)")

        println("[ACCOUNT-CREATE] Waiting for account registration to complete...")
        // Use NonCancellable to ensure registration wait completes even if caller scope is cancelled
        withContext(NonCancellable) {
            try {
                // Wait for registration state to change to REGISTERED (with 60 second timeout)
                withTimeout(60_000L) {
                    jamiBridge.accountEvents.first { event ->
                        if (event is JamiAccountEvent.RegistrationStateChanged &&
                            event.accountId == accountId) {
                            println("[ACCOUNT-CREATE] Registration event received: state=${event.state}")
                            when (event.state) {
                                RegistrationState.REGISTERED -> {
                                    println("[ACCOUNT-CREATE] ✓ Account registered successfully on network!")
                                    true
                                }
                                RegistrationState.ERROR_GENERIC,
                                RegistrationState.ERROR_AUTH,
                                RegistrationState.ERROR_NETWORK,
                                RegistrationState.ERROR_HOST,
                                RegistrationState.ERROR_SERVICE_UNAVAILABLE -> {
                                    println("[ACCOUNT-CREATE] ✗ Registration failed: ${event.state}")
                                    _accountState.value = _accountState.value.copy(
                                        registrationState = event.state
                                    )
                                    // Don't wait anymore if there's an error
                                    true
                                }
                                else -> {
                                    // Keep waiting for final state (TRYING, INITIALIZING, etc.)
                                    false
                                }
                            }
                        } else {
                            false
                        }
                    }
                }

                // Fetch account details to get the username (Jami ID)
                println("[ACCOUNT-CREATE] Fetching account details...")
                val details = jamiBridge.getAccountDetails(accountId)
                val username = details["Account.username"] ?: ""
                println("[ACCOUNT-CREATE] Got username (Jami ID): $username")

                _accountState.value = _accountState.value.copy(
                    registrationState = RegistrationState.REGISTERED,
                    username = username,
                    jamiId = username.ifEmpty { accountId }
                )

                // Create vCard profile with the display name (inside NonCancellable to ensure completion)
                // This is essential for profile exchange with contacts
                try {
                    println("[ACCOUNT-CREATE] Creating vCard profile with displayName='$displayName'...")
                    jamiBridge.updateProfile(accountId, displayName, null)
                    println("[ACCOUNT-CREATE] ✓ vCard profile created successfully")
                } catch (e: Exception) {
                    println("[ACCOUNT-CREATE] ⚠ vCard creation failed (non-fatal): ${e.message}")
                    // Non-fatal - account was still created, profile can be updated later
                }
            } catch (e: Exception) {
                println("[ACCOUNT-CREATE] ⚠ Registration wait exception: ${e.message}")
                // Continue anyway - account was created, may just need time to register
            }
        }

        println("[ACCOUNT-CREATE] ✓ Account creation completed")
        println("[ACCOUNT-CREATE]   accountId: $accountId")
        println("[ACCOUNT-CREATE]   displayName: $displayName")
        println("[ACCOUNT-CREATE]   username: ${_accountState.value.username}")
        println("[ACCOUNT-CREATE]   registrationState: ${_accountState.value.registrationState}")
        println("═══════════════════════════════════════════════════════════════")
        println("[JAMI-ID] NEW ACCOUNT CREATED - Jami ID (40-char): $accountId")
        println("[JAMI-ID] Share this ID with others to receive contact requests")
        println("═══════════════════════════════════════════════════════════════")

        return accountId
    }

    /**
     * Import an account from an archive file.
     * Waits for the account to be fully registered on the Jami network before returning.
     */
    suspend fun importAccount(archivePath: String, password: String): String {
        println("[ACCOUNT-IMPORT] === importAccount() called ===")

        val accountId = jamiBridge.importAccount(archivePath, password)
        println("[ACCOUNT-IMPORT] Account imported with ID: $accountId")

        // Enable peer discovery and presence settings for imported account
        enablePeerDiscoverySettings(accountId)

        _currentAccountId.value = accountId

        // Load the imported account details
        val details = jamiBridge.getAccountDetails(accountId)
        _accountState.value = AccountState(
            accountId = accountId,
            displayName = details["Account.displayName"] ?: "",
            username = details["Account.username"] ?: "",
            jamiId = details["Account.username"] ?: accountId,
            registrationState = RegistrationState.TRYING,
            isLoaded = true
        )

        println("[ACCOUNT-IMPORT] Waiting for account registration to complete...")
        withContext(NonCancellable) {
            try {
                // Wait for registration state to change to REGISTERED (with 60 second timeout)
                withTimeout(60_000L) {
                    jamiBridge.accountEvents.first { event ->
                        if (event is JamiAccountEvent.RegistrationStateChanged &&
                            event.accountId == accountId) {
                            println("[ACCOUNT-IMPORT] Registration event received: state=${event.state}")
                            when (event.state) {
                                RegistrationState.REGISTERED -> {
                                    println("[ACCOUNT-IMPORT] ✓ Account registered successfully on network!")
                                    true
                                }
                                RegistrationState.ERROR_GENERIC,
                                RegistrationState.ERROR_AUTH,
                                RegistrationState.ERROR_NETWORK,
                                RegistrationState.ERROR_HOST,
                                RegistrationState.ERROR_SERVICE_UNAVAILABLE -> {
                                    println("[ACCOUNT-IMPORT] ✗ Registration failed: ${event.state}")
                                    _accountState.value = _accountState.value.copy(
                                        registrationState = event.state
                                    )
                                    true
                                }
                                else -> false
                            }
                        } else {
                            false
                        }
                    }
                }

                // Fetch updated account details
                val updatedDetails = jamiBridge.getAccountDetails(accountId)
                val username = updatedDetails["Account.username"] ?: ""
                _accountState.value = _accountState.value.copy(
                    registrationState = RegistrationState.REGISTERED,
                    username = username,
                    jamiId = username.ifEmpty { accountId }
                )
            } catch (e: Exception) {
                println("[ACCOUNT-IMPORT] ⚠ Registration wait exception: ${e.message}")
            }
        }

        println("[ACCOUNT-IMPORT] ✓ Account import completed, state: ${_accountState.value.registrationState}")
        return accountId
    }

    /**
     * Delete the current account and sign out.
     */
    suspend fun deleteCurrentAccount() {
        val accountId = _currentAccountId.value ?: return
        jamiBridge.deleteAccount(accountId)
        _currentAccountId.value = null
        _accountState.value = AccountState(isLoaded = true)
    }

    /**
     * Update the display name for the current account.
     * Also updates the vCard profile to ensure the name is sent with trust requests.
     */
    suspend fun updateDisplayName(displayName: String) {
        val accountId = _currentAccountId.value ?: return
        val currentDetails = jamiBridge.getAccountDetails(accountId).toMutableMap()
        currentDetails["Account.displayName"] = displayName
        jamiBridge.setAccountDetails(accountId, currentDetails)

        // Also update the profile vCard - this ensures the display name is included
        // in trust request payloads. The updateProfile call may fail silently but
        // the account details update above will still succeed.
        try {
            jamiBridge.updateProfile(accountId, displayName, null)
            println("[AccountRepo] updateDisplayName: Updated vCard profile with displayName='$displayName'")
        } catch (e: Exception) {
            println("[AccountRepo] updateDisplayName: vCard update failed (non-fatal): ${e.message}")
            // Non-fatal - account details were still updated
        }

        _accountState.value = _accountState.value.copy(displayName = displayName)
    }

    /**
     * Update the profile (display name and avatar) for the current account.
     *
     * @param displayName The new display name
     * @param avatarPath The avatar file path. If null, uses the currently stored avatar path.
     * @param clearAvatar If true, explicitly clears the avatar (sets to null even if avatarPath is null)
     */
    suspend fun updateProfile(displayName: String, avatarPath: String? = null, clearAvatar: Boolean = false) {
        val accountId = _currentAccountId.value ?: return

        // Determine the avatar path to use:
        // - If avatarPath is provided, use it
        // - If clearAvatar is true, use null (clear the avatar)
        // - Otherwise, use the currently stored avatar path
        val effectiveAvatarPath = when {
            avatarPath != null -> avatarPath
            clearAvatar -> null
            else -> _accountState.value.avatarPath
        }

        jamiBridge.updateProfile(accountId, displayName, effectiveAvatarPath)

        _accountState.value = _accountState.value.copy(
            displayName = displayName,
            avatarPath = effectiveAvatarPath
        )
    }

    /**
     * Refresh account details from the daemon.
     * Called after registration completes to populate jamiId and other fields.
     */
    private suspend fun refreshAccountDetails(accountId: String) {
        try {
            println("[AccountRepo] refreshAccountDetails: Fetching details for '$accountId'")
            val details = jamiBridge.getAccountDetails(accountId)
            val volatileDetails = jamiBridge.getVolatileAccountDetails(accountId)

            val jamiId = details["Account.username"] ?: ""
            println("[AccountRepo] refreshAccountDetails: Got jamiId='$jamiId'")

            _accountState.value = _accountState.value.copy(
                displayName = details["Account.displayName"] ?: _accountState.value.displayName,
                username = details["Account.username"] ?: _accountState.value.username,
                jamiId = jamiId.ifEmpty { accountId },
                registrationState = parseRegistrationState(volatileDetails["Account.registrationStatus"])
            )
        } catch (e: Exception) {
            println("[AccountRepo] refreshAccountDetails: Error - ${e.message}")
        }
    }

    /**
     * Check if an account exists.
     */
    fun hasAccount(): Boolean = _currentAccountId.value != null

    /**
     * Get the current account ID or throw if not logged in.
     */
    fun requireAccountId(): String {
        return _currentAccountId.value
            ?: throw IllegalStateException("No account is currently active")
    }

    /**
     * Export the current account to a backup file.
     * @param destinationPath Full path where the backup file will be saved
     * @param password Password to encrypt the backup (can be empty for unencrypted)
     * @return True if export succeeded
     */
    suspend fun exportAccount(destinationPath: String, password: String): Boolean {
        println("[ACCOUNT-EXPORT] === exportAccount() called ===")
        println("[ACCOUNT-EXPORT] destinationPath='$destinationPath', password.length=${password.length}")

        val accountId = _currentAccountId.value
        if (accountId == null) {
            println("[ACCOUNT-EXPORT] ERROR: No account active")
            throw IllegalStateException("No account is currently active")
        }

        println("[ACCOUNT-EXPORT] Exporting account: $accountId")
        val result = jamiBridge.exportAccount(accountId, destinationPath, password)
        println("[ACCOUNT-EXPORT] Export result: $result")

        return result
    }

    /**
     * Logout the current account without deleting it.
     * Deactivates the account but preserves local data for relogin.
     */
    suspend fun logoutCurrentAccount() {
        println("[ACCOUNT-LOGOUT] === logoutCurrentAccount() called ===")

        val accountId = _currentAccountId.value
        if (accountId == null) {
            println("[ACCOUNT-LOGOUT] No account to logout")
            return
        }

        // Announce offline status to contacts before deactivating
        println("[ACCOUNT-LOGOUT] Publishing offline presence...")
        try {
            jamiBridge.publishPresence(accountId, isOnline = false)
        } catch (e: Exception) {
            println("[ACCOUNT-LOGOUT] ⚠️ Failed to publish offline presence: ${e.message}")
        }

        println("[ACCOUNT-LOGOUT] Deactivating account: $accountId")
        jamiBridge.setAccountActive(accountId, false)

        println("[ACCOUNT-LOGOUT] Clearing current account state")
        _currentAccountId.value = null
        _accountState.value = AccountState(isLoaded = true)

        println("[ACCOUNT-LOGOUT] ✓ Logout complete. Account preserved for relogin.")
    }

    /**
     * Get list of all existing accounts on the device (active and deactivated).
     */
    fun getAllAccounts(): List<ExistingAccount> {
        println("[ACCOUNT-LIST] === getAllAccounts() called ===")

        val accountIds = jamiBridge.getAccountIds()
        println("[ACCOUNT-LIST] Found ${accountIds.size} account IDs: $accountIds")

        return accountIds.map { accountId ->
            val details = jamiBridge.getAccountDetails(accountId)
            val volatileDetails = jamiBridge.getVolatileAccountDetails(accountId)

            val isEnabled = details["Account.enable"] == "true"
            val regStatus = volatileDetails["Account.registrationStatus"] ?: ""
            val isActive = isEnabled && regStatus != "UNREGISTERED"

            println("[ACCOUNT-LIST]   Account $accountId: displayName='${details["Account.displayName"]}', isActive=$isActive")

            ExistingAccount(
                accountId = accountId,
                displayName = details["Account.displayName"] ?: "",
                username = details["Account.username"] ?: "",
                jamiId = details["Account.username"] ?: accountId,
                isActive = isActive
            )
        }
    }

    /**
     * Get list of deactivated accounts that can be relogged into.
     */
    fun getDeactivatedAccounts(): List<ExistingAccount> {
        println("[ACCOUNT-RELOGIN] === getDeactivatedAccounts() called ===")

        val all = getAllAccounts()
        val deactivated = all.filter { !it.isActive }

        println("[ACCOUNT-RELOGIN] Found ${deactivated.size} deactivated accounts")
        return deactivated
    }

    /**
     * Relogin to an existing deactivated account.
     * Waits for the account to be fully registered on the Jami network before returning.
     */
    suspend fun reloginToAccount(accountId: String) {
        println("[ACCOUNT-RELOGIN] === reloginToAccount() called ===")
        println("[ACCOUNT-RELOGIN] Relogging to account: $accountId")

        // Activate the account
        println("[ACCOUNT-RELOGIN] Activating account...")
        jamiBridge.setAccountActive(accountId, true)

        // Enable peer discovery and presence settings for reactivated account
        enablePeerDiscoverySettings(accountId)

        // Set as current account
        _currentAccountId.value = accountId

        // Load account details
        println("[ACCOUNT-RELOGIN] Loading account details...")
        val details = jamiBridge.getAccountDetails(accountId)
        _accountState.value = AccountState(
            accountId = accountId,
            displayName = details["Account.displayName"] ?: "",
            username = details["Account.username"] ?: "",
            jamiId = details["Account.username"] ?: accountId,
            registrationState = RegistrationState.TRYING,
            isLoaded = true
        )

        println("[ACCOUNT-RELOGIN] Waiting for account registration to complete...")
        withContext(NonCancellable) {
            try {
                // Wait for registration state to change to REGISTERED (with 60 second timeout)
                withTimeout(60_000L) {
                    jamiBridge.accountEvents.first { event ->
                        if (event is JamiAccountEvent.RegistrationStateChanged &&
                            event.accountId == accountId) {
                            println("[ACCOUNT-RELOGIN] Registration event received: state=${event.state}")
                            when (event.state) {
                                RegistrationState.REGISTERED -> {
                                    println("[ACCOUNT-RELOGIN] ✓ Account registered successfully on network!")
                                    true
                                }
                                RegistrationState.ERROR_GENERIC,
                                RegistrationState.ERROR_AUTH,
                                RegistrationState.ERROR_NETWORK,
                                RegistrationState.ERROR_HOST,
                                RegistrationState.ERROR_SERVICE_UNAVAILABLE -> {
                                    println("[ACCOUNT-RELOGIN] ✗ Registration failed: ${event.state}")
                                    _accountState.value = _accountState.value.copy(
                                        registrationState = event.state
                                    )
                                    true
                                }
                                else -> false
                            }
                        } else {
                            false
                        }
                    }
                }

                // Fetch updated account details
                val updatedDetails = jamiBridge.getAccountDetails(accountId)
                val username = updatedDetails["Account.username"] ?: ""
                _accountState.value = _accountState.value.copy(
                    registrationState = RegistrationState.REGISTERED,
                    username = username,
                    jamiId = username.ifEmpty { accountId }
                )
            } catch (e: Exception) {
                println("[ACCOUNT-RELOGIN] ⚠ Registration wait exception: ${e.message}")
            }
        }

        println("[ACCOUNT-RELOGIN] ✓ Relogin complete")
        println("[ACCOUNT-RELOGIN]   accountId: $accountId")
        println("[ACCOUNT-RELOGIN]   displayName: ${_accountState.value.displayName}")
        println("[ACCOUNT-RELOGIN]   registrationState: ${_accountState.value.registrationState}")
        println("═══════════════════════════════════════════════════════════════")
        println("[JAMI-ID] Account switched - Jami ID (40-char): $accountId")
        println("[JAMI-ID] Share this ID with others to receive contact requests")
        println("═══════════════════════════════════════════════════════════════")
    }

    private fun handleAccountEvent(event: JamiAccountEvent) {
        println("[ACCOUNT-EVENT] Received event: ${event::class.simpleName}")

        when (event) {
            is JamiAccountEvent.RegistrationStateChanged -> {
                println("[ACCOUNT-EVENT] RegistrationStateChanged: accountId=${event.accountId}, state=${event.state}, code=${event.code}, detail=${event.detail}")
                println("[ACCOUNT-EVENT]   Current account ID: ${_currentAccountId.value}")
                println("[ACCOUNT-EVENT]   Is for current account: ${event.accountId == _currentAccountId.value}")

                // If we don't have a current account but daemon reports one, reload accounts
                // This handles the case where daemon initializes after our initial account load
                if (_currentAccountId.value == null && event.accountId.isNotEmpty()) {
                    println("[ACCOUNT-EVENT] No current account but daemon reports account ${event.accountId.take(8)}... - reloading accounts")
                    scope.launch {
                        loadAccounts()
                    }
                } else if (event.accountId == _currentAccountId.value) {
                    val oldState = _accountState.value.registrationState
                    _accountState.value = _accountState.value.copy(
                        registrationState = event.state
                    )
                    println("[ACCOUNT-EVENT] ✓ Account state updated: $oldState -> ${event.state}")

                    // When account becomes REGISTERED, fetch full details to get jamiId
                    if (event.state == RegistrationState.REGISTERED) {
                        scope.launch {
                            refreshAccountDetails(event.accountId)
                        }
                    }
                }
            }
            is JamiAccountEvent.ProfileReceived -> {
                println("[ACCOUNT-EVENT] ProfileReceived: accountId=${event.accountId}, displayName=${event.displayName}")
                if (event.accountId == _currentAccountId.value) {
                    _accountState.value = _accountState.value.copy(
                        displayName = event.displayName
                    )
                    println("[ACCOUNT-EVENT] ✓ Profile updated: displayName=${event.displayName}")
                }
            }
            is JamiAccountEvent.AccountDetailsChanged -> {
                println("[ACCOUNT-EVENT] AccountDetailsChanged: accountId=${event.accountId}, details count=${event.details.size}")
                if (event.accountId == _currentAccountId.value) {
                    val newDisplayName = event.details["Account.displayName"] ?: _accountState.value.displayName
                    val newUsername = event.details["Account.username"] ?: _accountState.value.username
                    _accountState.value = _accountState.value.copy(
                        displayName = newDisplayName,
                        username = newUsername,
                        jamiId = newUsername.ifEmpty { _accountState.value.jamiId }
                    )
                    println("[ACCOUNT-EVENT] ✓ Account details updated: displayName=$newDisplayName, username=$newUsername")
                }
            }
            else -> {
                println("[ACCOUNT-EVENT] Unhandled event type: ${event::class.simpleName}")
            }
        }
    }

    private fun parseRegistrationState(state: String?): RegistrationState {
        return when (state) {
            "REGISTERED" -> RegistrationState.REGISTERED
            "UNREGISTERED" -> RegistrationState.UNREGISTERED
            "TRYING" -> RegistrationState.TRYING
            "INITIALIZING" -> RegistrationState.INITIALIZING
            "ERROR_GENERIC" -> RegistrationState.ERROR_GENERIC
            "ERROR_AUTH" -> RegistrationState.ERROR_AUTH
            "ERROR_NETWORK" -> RegistrationState.ERROR_NETWORK
            "ERROR_HOST" -> RegistrationState.ERROR_HOST
            "ERROR_SERVICE_UNAVAILABLE" -> RegistrationState.ERROR_SERVICE_UNAVAILABLE
            "ERROR_NEED_MIGRATION" -> RegistrationState.ERROR_NEED_MIGRATION
            else -> RegistrationState.UNREGISTERED
        }
    }
}

/**
 * Represents the current account state.
 */
data class AccountState(
    val accountId: String? = null,
    val displayName: String = "",
    val username: String = "",
    val jamiId: String = "",
    val registrationState: RegistrationState = RegistrationState.UNREGISTERED,
    val isLoaded: Boolean = false,
    val error: String? = null,
    val avatarPath: String? = null
)
