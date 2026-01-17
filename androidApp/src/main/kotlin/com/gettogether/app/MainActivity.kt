package com.gettogether.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import com.gettogether.app.platform.PermissionManager
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val permissionManager: PermissionManager by inject()

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

        setContent {
            App()
        }
    }
}
