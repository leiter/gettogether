package com.gettogether.app.jami

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
        if (_state.value != DaemonState.Uninitialized && _state.value != DaemonState.Stopped) {
            return
        }

        scope.launch {
            try {
                _state.value = DaemonState.Initializing
                _error.value = null

                val dataPath = dataPathProvider.getDataPath()
                bridge.initDaemon(dataPath)

                _state.value = DaemonState.Starting
                bridge.startDaemon()

                _state.value = DaemonState.Running
            } catch (e: Exception) {
                _state.value = DaemonState.Error
                _error.value = DaemonError.StartupFailed(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Stop the Jami daemon.
     * This should be called when the app is being terminated.
     */
    fun stop() {
        if (_state.value != DaemonState.Running) {
            return
        }

        scope.launch {
            try {
                _state.value = DaemonState.Stopping
                bridge.stopDaemon()
                _state.value = DaemonState.Stopped
            } catch (e: Exception) {
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
