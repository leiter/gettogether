package com.gettogether.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.gettogether.app.ui.screens.auth.WelcomeScreen

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Welcome.route
    ) {
        composable(Screen.Welcome.route) {
            WelcomeScreen(
                onNavigateToCreateAccount = {
                    // TODO: Navigate to create account
                },
                onNavigateToImportAccount = {
                    // TODO: Navigate to import account
                }
            )
        }
    }
}

sealed class Screen(val route: String) {
    object Welcome : Screen("welcome")
    object CreateAccount : Screen("create_account")
    object ImportAccount : Screen("import_account")
    object Home : Screen("home")
    object Contacts : Screen("contacts")
    object Conversations : Screen("conversations")
    object Chat : Screen("chat/{conversationId}") {
        fun createRoute(conversationId: String) = "chat/$conversationId"
    }
    object Call : Screen("call/{callId}") {
        fun createRoute(callId: String) = "call/$callId"
    }
    object Settings : Screen("settings")
}
