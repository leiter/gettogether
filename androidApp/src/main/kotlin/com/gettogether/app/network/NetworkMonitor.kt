package com.gettogether.app.network

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.PowerManager
import android.util.Log
import com.gettogether.app.data.repository.AccountRepository
import com.gettogether.app.jami.JamiBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Monitors network connectivity changes and notifies the Jami daemon.
 *
 * When network changes occur (WiFi <-> Mobile, network lost/regained),
 * this class:
 * 1. Calls JamiService.connectivityChanged() to notify the daemon
 * 2. Reactivates all accounts to trigger re-registration
 *
 * Based on jami-client-android's DRingService network monitoring.
 */
class NetworkMonitor(
    private val context: Context,
    private val jamiBridge: JamiBridge,
    private val accountRepository: AccountRepository
) {
    companion object {
        private const val TAG = "NetworkMonitor"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _isConnected = MutableStateFlow(true)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private var isMonitoring = false
    private var isInitialized = false  // Skip notifications during startup

    /**
     * NetworkCallback for monitoring network state changes.
     * This is the modern Android API for network monitoring.
     */
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.i(TAG, "[NETWORK] onAvailable: $network")
            updateConnectivityState(true)
        }

        override fun onLost(network: Network) {
            Log.i(TAG, "[NETWORK] onLost: $network")
            // Check if we still have any network available
            val stillConnected = hasNetworkConnectivity()
            updateConnectivityState(stillConnected)
        }

        override fun onUnavailable() {
            Log.i(TAG, "[NETWORK] onUnavailable")
            updateConnectivityState(false)
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val hasValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            Log.d(TAG, "[NETWORK] onCapabilitiesChanged: network=$network, internet=$hasInternet, validated=$hasValidated")

            if (hasInternet && hasValidated) {
                updateConnectivityState(true)
            }
        }
    }

    /**
     * BroadcastReceiver for legacy connectivity events and device idle mode.
     */
    private val connectivityReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action ?: return
            Log.d(TAG, "[NETWORK] BroadcastReceiver: $action")

            when (action) {
                @Suppress("DEPRECATION")
                ConnectivityManager.CONNECTIVITY_ACTION -> {
                    // Legacy fallback - check actual connectivity
                    updateConnectivityState(hasNetworkConnectivity())
                }
                PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED -> {
                    Log.i(TAG, "[NETWORK] Device idle mode changed")
                    // Re-check connectivity after idle mode change
                    updateConnectivityState(hasNetworkConnectivity())
                }
            }
        }
    }

    /**
     * Start monitoring network changes.
     * Should be called after the Jami daemon is started.
     */
    fun start() {
        if (isMonitoring) {
            Log.w(TAG, "[NETWORK] Already monitoring, skipping start")
            return
        }

        Log.i(TAG, "[NETWORK] Starting network monitor...")

        // Register NetworkCallback for modern API
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
            .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
            .build()

        try {
            connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
            Log.i(TAG, "[NETWORK] NetworkCallback registered")
        } catch (e: Exception) {
            Log.e(TAG, "[NETWORK] Failed to register NetworkCallback", e)
        }

        // Register BroadcastReceiver for legacy events and idle mode
        val intentFilter = IntentFilter().apply {
            @Suppress("DEPRECATION")
            addAction(ConnectivityManager.CONNECTIVITY_ACTION)
            addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
        }
        context.registerReceiver(connectivityReceiver, intentFilter)
        Log.i(TAG, "[NETWORK] BroadcastReceiver registered")

        isMonitoring = true

        // Initial connectivity check (don't notify daemon yet)
        val connected = hasNetworkConnectivity()
        _isConnected.value = connected
        Log.i(TAG, "[NETWORK] Initial connectivity state: $connected")

        // Delay initialization to let daemon fully start before accepting notifications
        scope.launch {
            kotlinx.coroutines.delay(3000)  // Wait 3 seconds for daemon to initialize
            isInitialized = true
            Log.i(TAG, "[NETWORK] Network monitor fully initialized, will now notify daemon of changes")
        }
    }

    /**
     * Stop monitoring network changes.
     * Should be called when the app is shutting down.
     */
    fun stop() {
        if (!isMonitoring) {
            Log.w(TAG, "[NETWORK] Not monitoring, skipping stop")
            return
        }

        Log.i(TAG, "[NETWORK] Stopping network monitor...")

        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
            Log.i(TAG, "[NETWORK] NetworkCallback unregistered")
        } catch (e: Exception) {
            Log.w(TAG, "[NETWORK] Failed to unregister NetworkCallback", e)
        }

        try {
            context.unregisterReceiver(connectivityReceiver)
            Log.i(TAG, "[NETWORK] BroadcastReceiver unregistered")
        } catch (e: Exception) {
            Log.w(TAG, "[NETWORK] Failed to unregister BroadcastReceiver", e)
        }

        isMonitoring = false
        isInitialized = false
    }

    /**
     * Check if device currently has network connectivity.
     */
    private fun hasNetworkConnectivity(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false

        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /**
     * Update connectivity state and notify daemon.
     */
    private fun updateConnectivityState(isConnected: Boolean) {
        val wasConnected = _isConnected.value

        // Skip if state hasn't actually changed
        if (wasConnected == isConnected) {
            Log.d(TAG, "[NETWORK] Connectivity unchanged: $isConnected (skipping)")
            return
        }

        _isConnected.value = isConnected
        Log.i(TAG, "[NETWORK] Connectivity state changed: $wasConnected → $isConnected")

        // Skip notifications during startup to avoid crashing daemon during initialization
        if (!isInitialized) {
            Log.d(TAG, "[NETWORK] Skipping daemon notification (not yet initialized)")
            return
        }

        // Notify daemon of actual connectivity change
        scope.launch {
            try {
                // Notify daemon about network change
                Log.i(TAG, "[NETWORK] Calling connectivityChanged()...")
                jamiBridge.connectivityChanged()
                Log.i(TAG, "[NETWORK] connectivityChanged() completed")

                // Reactivate accounts when network becomes available
                if (isConnected) {
                    reactivateAccounts()
                }
            } catch (e: Exception) {
                Log.e(TAG, "[NETWORK] Failed to update connectivity state", e)
            }
        }
    }

    /**
     * Reactivate all accounts to trigger re-registration.
     */
    private suspend fun reactivateAccounts() {
        val accountId = accountRepository.currentAccountId.value
        if (accountId != null) {
            Log.i(TAG, "[NETWORK] Reactivating account: ${accountId.take(8)}...")
            try {
                // Deactivate then reactivate to force re-registration
                jamiBridge.setAccountActive(accountId, false)
                kotlinx.coroutines.delay(100)
                jamiBridge.setAccountActive(accountId, true)
                Log.i(TAG, "[NETWORK] Account reactivated")
            } catch (e: Exception) {
                Log.e(TAG, "[NETWORK] Failed to reactivate account", e)
            }
        } else {
            Log.d(TAG, "[NETWORK] No active account to reactivate")
        }
    }
}
