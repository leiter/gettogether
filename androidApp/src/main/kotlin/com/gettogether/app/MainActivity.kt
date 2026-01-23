package com.gettogether.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import com.gettogether.app.data.repository.ContactRepositoryImpl
import com.gettogether.app.platform.NotificationConstants
import com.gettogether.app.platform.PermissionManager
import com.gettogether.app.ui.navigation.InitialNavigation
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val permissionManager: PermissionManager by inject()

    private var initialNavigation by mutableStateOf<InitialNavigation?>(null)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (!allGranted) {
            // Log which permissions were denied
            val deniedPermissions = permissions.filterValues { !it }.keys
            println("Permissions denied: $deniedPermissions")
            // App will still work but calls won't function without permissions
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Configure window to handle IME (keyboard) properly
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Request permissions if not granted
        if (!permissionManager.hasRequiredPermissions()) {
            permissionLauncher.launch(permissionManager.getRequiredPermissions().toTypedArray())
        }

        // Process intent for navigation
        initialNavigation = parseNavigationIntent(intent)

        setContent {
            App(initialNavigation = initialNavigation)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Handle new intents when activity is already running
        initialNavigation = parseNavigationIntent(intent)
    }

    /**
     * Parse the incoming intent to determine navigation destination.
     */
    private fun parseNavigationIntent(intent: Intent?): InitialNavigation? {
        if (intent == null) return null

        android.util.Log.d("MainActivity", "Parsing intent action: ${intent.action}")

        return when (intent.action) {
            "OPEN_CONVERSATION" -> {
                val conversationId = intent.getStringExtra(NotificationConstants.EXTRA_CONVERSATION_ID)
                android.util.Log.d("MainActivity", "Navigate to chat: $conversationId")
                conversationId?.let { InitialNavigation.Chat(it) }
            }
            "ANSWER_CALL" -> {
                // Call was already accepted by CallNotificationReceiver
                val contactId = intent.getStringExtra(NotificationConstants.EXTRA_CONTACT_ID)
                val callId = intent.getStringExtra(NotificationConstants.EXTRA_CALL_ID)
                val isVideo = intent.getBooleanExtra(NotificationConstants.EXTRA_IS_VIDEO, false)
                android.util.Log.d("MainActivity", "Navigate to answered call: contactId=$contactId, callId=$callId, isVideo=$isVideo")
                contactId?.let {
                    InitialNavigation.Call(
                        contactId = it,
                        isVideo = isVideo,
                        callId = callId,
                        isAlreadyAccepted = true
                    )
                }
            }
            "INCOMING_CALL" -> {
                // Call is incoming, not yet accepted
                val contactId = intent.getStringExtra(NotificationConstants.EXTRA_CONTACT_ID)
                val callId = intent.getStringExtra(NotificationConstants.EXTRA_CALL_ID)
                val isVideo = intent.getBooleanExtra(NotificationConstants.EXTRA_IS_VIDEO, false)
                android.util.Log.d("MainActivity", "Navigate to incoming call: contactId=$contactId, callId=$callId, isVideo=$isVideo")
                contactId?.let {
                    InitialNavigation.Call(
                        contactId = it,
                        isVideo = isVideo,
                        callId = callId,
                        isAlreadyAccepted = false
                    )
                }
            }
            "START_CALL" -> {
                // Initiating a new outgoing call
                val contactId = intent.getStringExtra(NotificationConstants.EXTRA_CONTACT_ID)
                val isVideo = intent.getBooleanExtra(NotificationConstants.EXTRA_IS_VIDEO, false)
                android.util.Log.d("MainActivity", "Navigate to start call: contactId=$contactId, isVideo=$isVideo")
                contactId?.let { InitialNavigation.Call(it, isVideo) }
            }
            "ONGOING_CALL" -> {
                val callId = intent.getStringExtra(NotificationConstants.EXTRA_CALL_ID)
                val contactId = intent.getStringExtra(NotificationConstants.EXTRA_CONTACT_ID)
                android.util.Log.d("MainActivity", "Navigate to ongoing call: callId=$callId")
                // For ongoing call, we need contact ID - but it may not be in the intent
                // Just navigate to home if contact ID is not available
                contactId?.let { InitialNavigation.Call(it, false) }
            }
            "CONTACT_DETAILS" -> {
                val contactId = intent.getStringExtra(NotificationConstants.EXTRA_CONTACT_ID)
                android.util.Log.d("MainActivity", "Navigate to contact: $contactId")
                contactId?.let { InitialNavigation.ContactDetails(it) }
            }
            else -> null
        }
    }

    /**
     * Clean up presence tracking when activity is destroyed.
     *
     * Note: onTerminate() is only called on emulator, not on real devices.
     * We need to handle cleanup here for real device scenarios.
     *
     * isFinishing check ensures we only clean up when the app is actually closing,
     * not during configuration changes (like screen rotation).
     *
     * Uses runBlocking to ensure cleanup completes before the activity is destroyed,
     * since lifecycleScope would be cancelled at this point.
     */
    override fun onDestroy() {
        if (isFinishing) {
            android.util.Log.i("MainActivity", "[APP-LIFECYCLE] onDestroy(isFinishing=true) - cleaning up presence tracking")
            runBlocking {
                try {
                    val contactRepo: ContactRepositoryImpl by inject()
                    contactRepo.stopPresenceTracking()
                    android.util.Log.i("MainActivity", "[APP-LIFECYCLE] ✓ Presence tracking stopped")
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "[APP-LIFECYCLE] ⚠️ Failed to stop presence tracking: ${e.message}")
                }
            }
        }
        super.onDestroy()
    }
}
