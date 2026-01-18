package com.gettogether.app.data.repository

import com.gettogether.app.jami.JamiBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Clock

/**
 * Manages presence broadcasting for active accounts.
 *
 * This component solves the presence announcement architecture issue:
 * - Publishes "online" when account becomes active
 * - Maintains periodic heartbeats every 30 seconds
 * - Publishes "offline" when account becomes inactive
 *
 * Without this, contacts only see stale cached presence from daemon,
 * leading to incorrect online/offline status.
 */
class PresenceManager(
    private val jamiBridge: JamiBridge
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Track active heartbeat jobs per account
    private val heartbeatJobs = mutableMapOf<String, Job>()

    companion object {
        private const val HEARTBEAT_INTERVAL_MS = 30_000L // 30 seconds
    }

    /**
     * Start broadcasting presence for an account.
     * Immediately publishes "online" and starts periodic heartbeats.
     */
    fun startBroadcasting(accountId: String) {
        println("[PRESENCE-MANAGER] Starting presence broadcasting for account: ${accountId.take(16)}...")

        // Cancel existing heartbeat job if any (without publishing OFFLINE)
        val existingJob = heartbeatJobs.remove(accountId)
        if (existingJob != null) {
            println("[PRESENCE-MANAGER] → Cancelling existing heartbeat")
            existingJob.cancel()
        }

        // Start heartbeat job
        val job = scope.launch {
            try {
                // Immediately announce we're online
                // RFC 3863 PIDF requires "open" or "closed" as the basic status
                println("[PRESENCE-MANAGER] → Publishing initial ONLINE presence")
                val timestamp = Clock.System.now().toEpochMilliseconds()
                jamiBridge.publishPresence(accountId, isOnline = true, note = "open")
                println("[PRESENCE-MANAGER] ✓ Initial presence published with timestamp: $timestamp")

                // Start periodic heartbeat
                println("[PRESENCE-MANAGER] → Starting heartbeat (every ${HEARTBEAT_INTERVAL_MS}ms)")
                while (isActive) {
                    delay(HEARTBEAT_INTERVAL_MS)

                    println("[PRESENCE-MANAGER] → Heartbeat: Publishing ONLINE presence")
                    try {
                        val heartbeatTimestamp = Clock.System.now().toEpochMilliseconds()
                        jamiBridge.publishPresence(accountId, isOnline = true, note = "open")
                        println("[PRESENCE-MANAGER] ✓ Heartbeat published with timestamp: $heartbeatTimestamp")
                    } catch (e: Exception) {
                        println("[PRESENCE-MANAGER] ✗ Heartbeat failed: ${e.message}")
                        // Continue heartbeat even if one fails
                    }
                }
            } catch (e: Exception) {
                println("[PRESENCE-MANAGER] ✗ Heartbeat coroutine failed: ${e.message}")
                e.printStackTrace()
            }
        }

        heartbeatJobs[accountId] = job
        println("[PRESENCE-MANAGER] ✓ Heartbeat started for account: ${accountId.take(16)}...")
    }

    /**
     * Stop broadcasting presence for an account.
     * Publishes "offline" and cancels heartbeat.
     */
    fun stopBroadcasting(accountId: String) {
        println("[PRESENCE-MANAGER] Stopping presence broadcasting for account: ${accountId.take(16)}...")

        // Cancel heartbeat job
        val job = heartbeatJobs.remove(accountId)
        if (job != null) {
            job.cancel()
            println("[PRESENCE-MANAGER] ✓ Heartbeat cancelled")
        }

        // Publish offline (best-effort)
        // RFC 3863 PIDF requires "open" or "closed" as the basic status
        scope.launch {
            try {
                val timestamp = Clock.System.now().toEpochMilliseconds()
                println("[PRESENCE-MANAGER] → Publishing OFFLINE presence with timestamp: $timestamp")
                jamiBridge.publishPresence(accountId, isOnline = false, note = "closed")
                println("[PRESENCE-MANAGER] ✓ Offline presence published")
            } catch (e: Exception) {
                println("[PRESENCE-MANAGER] ✗ Failed to publish offline: ${e.message}")
                // Not critical if this fails (timeout will clean it up)
            }
        }
    }

    /**
     * Stop all broadcasting (app shutdown).
     */
    fun stopAll() {
        println("[PRESENCE-MANAGER] Stopping all presence broadcasting...")
        val accounts = heartbeatJobs.keys.toList()
        accounts.forEach { accountId ->
            stopBroadcasting(accountId)
        }
        println("[PRESENCE-MANAGER] ✓ All broadcasting stopped")
    }
}
