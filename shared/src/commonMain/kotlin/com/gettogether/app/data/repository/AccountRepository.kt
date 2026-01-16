package com.gettogether.app.data.repository

import com.gettogether.app.domain.model.ExistingAccount
import com.gettogether.app.jami.JamiBridge
import com.gettogether.app.jami.JamiAccountEvent
import com.gettogether.app.jami.RegistrationState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Repository for managing the current Jami account.
 * Tracks the active account ID and provides account-related operations.
 */
class AccountRepository(
    private val jamiBridge: JamiBridge
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
                println("[ACCOUNT-RESTORE] Setting current account to: $accountId")
                _currentAccountId.value = accountId

                // Load account details
                println("[ACCOUNT-RESTORE] Loading account details for: $accountId")
                val details = jamiBridge.getAccountDetails(accountId)
                println("[ACCOUNT-RESTORE] Account details loaded: displayName='${details["Account.displayName"]}', username='${details["Account.username"]}'")

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
            } else {
                println("[ACCOUNT-RESTORE] No accounts found - setting empty state")
                _accountState.value = AccountState(isLoaded = true)
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
     */
    suspend fun createAccount(displayName: String): String {
        println("[ACCOUNT-CREATE] === AccountRepository.createAccount() called ===")
        println("[ACCOUNT-CREATE] displayName='$displayName'")

        println("[ACCOUNT-CREATE] Calling jamiBridge.createAccount()...")
        val accountId = jamiBridge.createAccount(displayName)
        println("[ACCOUNT-CREATE] jamiBridge.createAccount() returned accountId='$accountId'")

        println("[ACCOUNT-CREATE] Setting current account ID to: $accountId")
        _currentAccountId.value = accountId

        println("[ACCOUNT-CREATE] Updating account state with TRYING registration state...")
        _accountState.value = AccountState(
            accountId = accountId,
            displayName = displayName,
            registrationState = RegistrationState.TRYING,
            isLoaded = true
        )

        println("[ACCOUNT-CREATE] ✓ Account creation initiated successfully")
        println("[ACCOUNT-CREATE]   accountId: $accountId")
        println("[ACCOUNT-CREATE]   displayName: $displayName")
        println("[ACCOUNT-CREATE]   registrationState: TRYING")
        println("[ACCOUNT-CREATE] Note: Listen for RegistrationStateChanged events for final state")

        return accountId
    }

    /**
     * Import an account from an archive file.
     */
    suspend fun importAccount(archivePath: String, password: String): String {
        val accountId = jamiBridge.importAccount(archivePath, password)
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
     */
    suspend fun updateDisplayName(displayName: String) {
        val accountId = _currentAccountId.value ?: return
        val currentDetails = jamiBridge.getAccountDetails(accountId).toMutableMap()
        currentDetails["Account.displayName"] = displayName
        jamiBridge.setAccountDetails(accountId, currentDetails)

        _accountState.value = _accountState.value.copy(displayName = displayName)
    }

    /**
     * Update the profile (display name and avatar) for the current account.
     */
    suspend fun updateProfile(displayName: String, avatarPath: String? = null) {
        val accountId = _currentAccountId.value ?: return
        jamiBridge.updateProfile(accountId, displayName, avatarPath)

        _accountState.value = _accountState.value.copy(displayName = displayName)
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
     */
    suspend fun reloginToAccount(accountId: String) {
        println("[ACCOUNT-RELOGIN] === reloginToAccount() called ===")
        println("[ACCOUNT-RELOGIN] Relogging to account: $accountId")

        // Activate the account
        println("[ACCOUNT-RELOGIN] Activating account...")
        jamiBridge.setAccountActive(accountId, true)

        // Set as current account
        _currentAccountId.value = accountId

        // Load account details
        println("[ACCOUNT-RELOGIN] Loading account details...")
        val details = jamiBridge.getAccountDetails(accountId)
        val volatileDetails = jamiBridge.getVolatileAccountDetails(accountId)
        val regStatus = volatileDetails["Account.registrationStatus"]
        val regState = parseRegistrationState(regStatus)

        _accountState.value = AccountState(
            accountId = accountId,
            displayName = details["Account.displayName"] ?: "",
            username = details["Account.username"] ?: "",
            jamiId = details["Account.username"] ?: accountId,
            registrationState = regState,
            isLoaded = true
        )

        println("[ACCOUNT-RELOGIN] ✓ Relogin complete")
        println("[ACCOUNT-RELOGIN]   accountId: $accountId")
        println("[ACCOUNT-RELOGIN]   displayName: ${_accountState.value.displayName}")
        println("[ACCOUNT-RELOGIN]   registrationState: $regState")
    }

    private fun handleAccountEvent(event: JamiAccountEvent) {
        println("[ACCOUNT-EVENT] Received event: ${event::class.simpleName}")

        when (event) {
            is JamiAccountEvent.RegistrationStateChanged -> {
                println("[ACCOUNT-EVENT] RegistrationStateChanged: accountId=${event.accountId}, state=${event.state}, code=${event.code}, detail=${event.detail}")
                println("[ACCOUNT-EVENT]   Current account ID: ${_currentAccountId.value}")
                println("[ACCOUNT-EVENT]   Is for current account: ${event.accountId == _currentAccountId.value}")

                if (event.accountId == _currentAccountId.value) {
                    val oldState = _accountState.value.registrationState
                    _accountState.value = _accountState.value.copy(
                        registrationState = event.state
                    )
                    println("[ACCOUNT-EVENT] ✓ Account state updated: $oldState -> ${event.state}")
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
                        username = newUsername
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
    val error: String? = null
)
