package com.gettogether.app.data.repository

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
        // Listen to account events
        scope.launch {
            jamiBridge.accountEvents.collect { event ->
                handleAccountEvent(event)
            }
        }

        // Load existing accounts on startup
        scope.launch {
            loadAccounts()
        }
    }

    /**
     * Load existing accounts and set the first one as current.
     */
    suspend fun loadAccounts() {
        try {
            val accountIds = jamiBridge.getAccountIds()
            if (accountIds.isNotEmpty()) {
                val accountId = accountIds.first()
                _currentAccountId.value = accountId

                // Load account details
                val details = jamiBridge.getAccountDetails(accountId)
                val volatileDetails = jamiBridge.getVolatileAccountDetails(accountId)

                _accountState.value = AccountState(
                    accountId = accountId,
                    displayName = details["Account.displayName"] ?: "",
                    username = details["Account.username"] ?: "",
                    jamiId = details["Account.username"] ?: accountId,
                    registrationState = parseRegistrationState(volatileDetails["Account.registrationStatus"]),
                    isLoaded = true
                )
            } else {
                _accountState.value = AccountState(isLoaded = true)
            }
        } catch (e: Exception) {
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
        println("[AccountRepo] createAccount: Starting with displayName='$displayName'")
        val accountId = jamiBridge.createAccount(displayName)
        println("[AccountRepo] createAccount: Created account with id='$accountId'")
        _currentAccountId.value = accountId

        _accountState.value = AccountState(
            accountId = accountId,
            displayName = displayName,
            registrationState = RegistrationState.TRYING,
            isLoaded = true
        )
        println("[AccountRepo] createAccount: Set initial state (jamiId will be fetched when REGISTERED)")

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

    private fun handleAccountEvent(event: JamiAccountEvent) {
        when (event) {
            is JamiAccountEvent.RegistrationStateChanged -> {
                println("[AccountRepo] RegistrationStateChanged: accountId=${event.accountId}, state=${event.state}")
                if (event.accountId == _currentAccountId.value) {
                    _accountState.value = _accountState.value.copy(
                        registrationState = event.state
                    )

                    // When account becomes REGISTERED, fetch full details to get jamiId
                    if (event.state == RegistrationState.REGISTERED) {
                        scope.launch {
                            refreshAccountDetails(event.accountId)
                        }
                    }
                }
            }
            is JamiAccountEvent.ProfileReceived -> {
                if (event.accountId == _currentAccountId.value) {
                    _accountState.value = _accountState.value.copy(
                        displayName = event.displayName
                    )
                }
            }
            is JamiAccountEvent.AccountDetailsChanged -> {
                println("[AccountRepo] AccountDetailsChanged: accountId=${event.accountId}, details=${event.details}")
                if (event.accountId == _currentAccountId.value) {
                    val username = event.details["Account.username"] ?: _accountState.value.username
                    _accountState.value = _accountState.value.copy(
                        displayName = event.details["Account.displayName"] ?: _accountState.value.displayName,
                        username = username,
                        jamiId = username.ifEmpty { _accountState.value.jamiId }
                    )
                }
            }
            else -> { /* Handle other events as needed */ }
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
