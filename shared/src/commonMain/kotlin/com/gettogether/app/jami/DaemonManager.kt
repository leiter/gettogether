package com.gettogether.app.jami

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Manages the Jami daemon lifecycle.
 *
 * This class is responsible for:
 * - Initializing and starting the daemon
 * - Stopping the daemon when the app is terminated
 * - Providing daemon state to the rest of the app
 * - Handling daemon errors and restarts
 */
class DaemonManager(
    private val bridge: JamiBridge,
    private val dataPathProvider: DataPathProvider
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(DaemonState.Uninitialized)
    val state: StateFlow<DaemonState> = _state.asStateFlow()

    private val _error = MutableStateFlow<DaemonError?>(null)
    val error: StateFlow<DaemonError?> = _error.asStateFlow()

    /**
     * Initialize and start the Jami daemon.
     * This should be called once when the app starts.
     */
    fun start() {
        println("DaemonManager: start() called, current state=${_state.value}")

        if (_state.value != DaemonState.Uninitialized && _state.value != DaemonState.Stopped) {
            println("DaemonManager: Skipping start - daemon already initialized/running (state=${_state.value})")
            return
        }

        scope.launch {
            try {
                println("DaemonManager: → Changing state to Initializing")
                _state.value = DaemonState.Initializing
                _error.value = null

                val dataPath = dataPathProvider.getDataPath()
                println("DaemonManager: → Data path: $dataPath")
                println("DaemonManager: → Calling bridge.initDaemon()...")

                bridge.initDaemon(dataPath)
                println("DaemonManager: ✓ bridge.initDaemon() completed")

                println("DaemonManager: → Changing state to Starting")
                _state.value = DaemonState.Starting

                println("DaemonManager: → Calling bridge.startDaemon()...")
                bridge.startDaemon()
                println("DaemonManager: ✓ bridge.startDaemon() completed")

                println("DaemonManager: → Changing state to Running")
                _state.value = DaemonState.Running
                println("DaemonManager: ✓✓✓ Daemon is now RUNNING")
            } catch (e: Exception) {
                println("DaemonManager: ✗✗✗ Daemon startup FAILED!")
                println("DaemonManager:   Error: ${e.message}")
                e.printStackTrace()
                _state.value = DaemonState.Error
                _error.value = DaemonError.StartupFailed(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Stop the Jami daemon.
     * This should be called when the app is being terminated.
     *
     * This is a suspend function that waits for the daemon to fully stop,
     * ensuring proper cleanup before the process exits.
     */
    suspend fun stop() {
        if (_state.value != DaemonState.Running) {
            println("DaemonManager: [SHUTDOWN] Skipping stop - daemon not running (state=${_state.value})")
            return
        }

        println("DaemonManager: [SHUTDOWN] Stopping daemon...")
        withContext(Dispatchers.IO) {
            try {
                _state.value = DaemonState.Stopping
                bridge.stopDaemon()
                _state.value = DaemonState.Stopped
                println("DaemonManager: [SHUTDOWN] ✓ Daemon stopped successfully")
            } catch (e: Exception) {
                println("DaemonManager: [SHUTDOWN] ✗ Error stopping daemon: ${e.message}")
                _state.value = DaemonState.Error
                _error.value = DaemonError.ShutdownFailed(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Restart the daemon after an error.
     */
    fun restart() {
        scope.launch {
            if (_state.value == DaemonState.Running) {
                try {
                    bridge.stopDaemon()
                } catch (_: Exception) {
                    // Ignore errors during stop for restart
                }
            }
            _state.value = DaemonState.Uninitialized
            start()
        }
    }

    /**
     * Check if the daemon is currently running.
     */
    fun isRunning(): Boolean = bridge.isDaemonRunning()
}

/**
 * Represents the current state of the Jami daemon.
 */
enum class DaemonState {
    /** Daemon has not been initialized yet */
    Uninitialized,
    /** Daemon is being initialized */
    Initializing,
    /** Daemon is starting up */
    Starting,
    /** Daemon is running and ready */
    Running,
    /** Daemon is being stopped */
    Stopping,
    /** Daemon has been stopped */
    Stopped,
    /** Daemon encountered an error */
    Error
}

/**
 * Represents errors that can occur during daemon lifecycle.
 */
sealed class DaemonError {
    abstract val message: String

    data class StartupFailed(override val message: String) : DaemonError()
    data class ShutdownFailed(override val message: String) : DaemonError()
    data class RuntimeError(override val message: String) : DaemonError()
}

/**
 * Provides the path where Jami daemon should store its data.
 * Platform-specific implementations provide the correct path.
 */
expect class DataPathProvider {
    fun getDataPath(): String
}
