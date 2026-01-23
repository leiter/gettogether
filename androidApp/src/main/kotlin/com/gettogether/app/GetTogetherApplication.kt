package com.gettogether.app

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import com.gettogether.app.coil.VCardFetcher
import com.gettogether.app.coil.VCardMapper
import com.gettogether.app.data.repository.ContactRepositoryImpl
import com.gettogether.app.di.jamiBridgeModule
import com.gettogether.app.di.platformModule
import com.gettogether.app.di.sharedModule
import com.gettogether.app.jami.DaemonManager
import com.gettogether.app.jami.JamiBridge
import com.gettogether.app.jami.JamiCallEvent
import com.gettogether.app.network.NetworkMonitor
import com.gettogether.app.platform.NotificationHelper
import com.gettogether.app.service.CallNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class GetTogetherApplication : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        android.util.Log.d("GetTogetherApp", "=== Application onCreate() ===")

        android.util.Log.d("GetTogetherApp", "→ Starting Koin...")
        startKoin {
            androidLogger()
            androidContext(this@GetTogetherApplication)
            allowOverride(true)
            modules(sharedModule, platformModule, jamiBridgeModule)
        }
        android.util.Log.i("GetTogetherApp", "✓ Koin started successfully")

        // Set up Coil with VCard fetcher for loading avatars from daemon's vCard files
        android.util.Log.d("GetTogetherApp", "→ Setting up Coil ImageLoader...")
        SingletonImageLoader.setSafe {
            ImageLoader.Builder(this)
                .components {
                    add(VCardMapper())  // Maps .vcf paths to VCardData
                    add(VCardFetcher.Factory())  // Extracts avatars from vCard files
                }
                .build()
        }
        android.util.Log.i("GetTogetherApp", "✓ Coil ImageLoader configured with VCardFetcher")

        // Initialize notification channels
        android.util.Log.d("GetTogetherApp", "→ Initializing notification helper...")
        val notificationHelper: NotificationHelper by inject()
        notificationHelper.initialize()
        android.util.Log.i("GetTogetherApp", "✓ Notification helper initialized")

        // Create notification channels for calls (legacy)
        android.util.Log.d("GetTogetherApp", "→ Creating call notification channels...")
        CallNotificationManager(this).createNotificationChannels()
        android.util.Log.i("GetTogetherApp", "✓ Call notification channels created")

        // Start Jami daemon
        android.util.Log.d("GetTogetherApp", "→ Starting Jami daemon...")
        val daemonManager: DaemonManager by inject()
        daemonManager.start()
        android.util.Log.i("GetTogetherApp", "✓ Daemon start initiated (check DaemonManager logs for status)")

        // Start network monitor (after daemon)
        android.util.Log.d("GetTogetherApp", "→ Starting network monitor...")
        val networkMonitor: NetworkMonitor by inject()
        networkMonitor.start()
        android.util.Log.i("GetTogetherApp", "✓ Network monitor started")

        // Setup global incoming call listener
        android.util.Log.d("GetTogetherApp", "→ Setting up incoming call listener...")
        setupIncomingCallListener()
        android.util.Log.i("GetTogetherApp", "✓ Incoming call listener setup")

        // Setup app lifecycle shutdown hook for presence cleanup
        android.util.Log.d("GetTogetherApp", "→ Setting up app lifecycle shutdown hook...")
        setupShutdownHook()
        android.util.Log.i("GetTogetherApp", "✓ Shutdown hook configured")

        android.util.Log.d("GetTogetherApp", "=== Application onCreate() completed ===")
    }

    /**
     * Sets up a global listener for incoming calls.
     * This is the critical connection between JamiBridge events and notification display.
     */
    private fun setupIncomingCallListener() {
        scope.launch {
            val jamiBridge: JamiBridge by inject()
            jamiBridge.callEvents.collect { event ->
                when (event) {
                    is JamiCallEvent.IncomingCall -> {
                        handleIncomingCall(
                            callId = event.callId,
                            contactId = event.peerId,
                            contactName = event.peerDisplayName.ifEmpty { event.peerId.take(8) },
                            isVideo = event.hasVideo
                        )
                    }
                    else -> { /* Ignore other call events */ }
                }
            }
        }
    }

    /**
     * Handles incoming call by displaying a notification.
     */
    private fun handleIncomingCall(
        callId: String,
        contactId: String,
        contactName: String,
        isVideo: Boolean
    ) {
        android.util.Log.i(
            "GetTogetherApp",
            "Incoming call from $contactName (callId: $callId, isVideo: $isVideo)"
        )

        val notificationHelper: NotificationHelper by inject()
        notificationHelper.showIncomingCallNotification(
            callId = callId,
            contactId = contactId,
            contactName = contactName,
            isVideo = isVideo
        )
    }

    /**
     * Sets up lifecycle hooks for presence management.
     * Note: Offline publishing removed - it was corrupting account state via broken JamiService.publish() API.
     * Presence polling is managed by ContactRepository based on AppLifecycleManager.isInForeground.
     */
    private fun setupShutdownHook() {
        // Removed offline publishing - it corrupted account registration via buggy JamiService.publish()
        // Polling lifecycle is handled by ContactRepositoryImpl listening to AppLifecycleManager.isInForeground
    }

    override fun onTerminate() {
        android.util.Log.w("GetTogetherApp", "[APP-LIFECYCLE] onTerminate() called")
        performGracefulShutdown()
        super.onTerminate()
    }

    /**
     * Performs graceful shutdown of all services in the correct order.
     * This prevents crashes from callbacks (presence, network) firing during daemon shutdown.
     *
     * Order is critical:
     * 1. Stop presence polling and unsubscribe from contacts (prevents presence callbacks)
     * 2. Stop network monitor (prevents network state callbacks)
     * 3. Stop daemon (waits for completion)
     * 4. Cancel coroutine scope
     */
    private fun performGracefulShutdown() {
        android.util.Log.i("GetTogetherApp", "[APP-LIFECYCLE] === Starting graceful shutdown ===")

        runBlocking {
            // 1. Stop presence tracking FIRST (prevents new callbacks during shutdown)
            try {
                android.util.Log.d("GetTogetherApp", "[APP-LIFECYCLE] → Stopping presence tracking...")
                val contactRepo: ContactRepositoryImpl by inject()
                contactRepo.stopPresenceTracking()
                android.util.Log.i("GetTogetherApp", "[APP-LIFECYCLE] ✓ Presence tracking stopped")
            } catch (e: Exception) {
                android.util.Log.e("GetTogetherApp", "[APP-LIFECYCLE] ⚠️ Failed to stop presence tracking: ${e.message}")
            }

            // 2. Stop network monitor (prevents network state callbacks)
            try {
                android.util.Log.d("GetTogetherApp", "[APP-LIFECYCLE] → Stopping network monitor...")
                val networkMonitor: NetworkMonitor by inject()
                networkMonitor.stop()
                android.util.Log.i("GetTogetherApp", "[APP-LIFECYCLE] ✓ Network monitor stopped")
            } catch (e: Exception) {
                android.util.Log.e("GetTogetherApp", "[APP-LIFECYCLE] ⚠️ Failed to stop network monitor: ${e.message}")
            }

            // 3. Stop daemon (wait for completion)
            try {
                android.util.Log.d("GetTogetherApp", "[APP-LIFECYCLE] → Stopping daemon...")
                val daemonManager: DaemonManager by inject()
                daemonManager.stop()
                android.util.Log.i("GetTogetherApp", "[APP-LIFECYCLE] ✓ Daemon stopped")
            } catch (e: Exception) {
                android.util.Log.e("GetTogetherApp", "[APP-LIFECYCLE] ⚠️ Failed to stop daemon: ${e.message}")
            }
        }

        // 4. Cancel coroutine scope
        scope.cancel()
        android.util.Log.i("GetTogetherApp", "[APP-LIFECYCLE] === Graceful shutdown complete ===")
    }
}
