package com.gettogether.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.gettogether.app.data.repository.AccountRepository
import com.gettogether.app.ui.screens.auth.CreateAccountScreen
import com.gettogether.app.ui.screens.auth.ImportAccountScreen
import com.gettogether.app.ui.screens.auth.WelcomeScreen
import com.gettogether.app.ui.screens.call.CallScreen
import com.gettogether.app.ui.screens.chat.ChatScreen
import com.gettogether.app.ui.screens.conference.ConferenceScreen
import com.gettogether.app.ui.screens.contacts.AddContactScreen
import com.gettogether.app.ui.screens.contacts.ContactDetailsScreen
import com.gettogether.app.ui.screens.home.HomeScreen
import com.gettogether.app.ui.screens.newconversation.NewConversationScreen
import org.koin.compose.koinInject

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val accountRepository: AccountRepository = koinInject()
    val accountState by accountRepository.accountState.collectAsState()

    // Show loading screen while accounts are being loaded
    if (!accountState.isLoaded) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    // Determine start destination based on whether an account exists
    val startDestination = if (accountState.accountId != null) {
        Screen.Home.route
    } else {
        Screen.Welcome.route
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Welcome.route) {
            WelcomeScreen(
                onNavigateToCreateAccount = {
                    navController.navigate(Screen.CreateAccount.route)
                },
                onNavigateToImportAccount = {
                    navController.navigate(Screen.ImportAccount.route)
                }
            )
        }

        composable(Screen.ImportAccount.route) {
            ImportAccountScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onAccountImported = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Welcome.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.CreateAccount.route) {
            CreateAccountScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onAccountCreated = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Welcome.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToChat = { conversationId ->
                    navController.navigate(Screen.Chat.createRoute(conversationId))
                },
                onNavigateToContact = { contactId ->
                    navController.navigate(Screen.ContactDetails.createRoute(contactId))
                },
                onStartNewConversation = {
                    navController.navigate(Screen.NewConversation.route)
                },
                onAddContact = {
                    navController.navigate(Screen.AddContact.route)
                },
                onSignedOut = {
                    navController.navigate(Screen.Welcome.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.AddContact.route) {
            AddContactScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onContactAdded = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.ContactDetails.route) { backStackEntry ->
            val contactId = backStackEntry.arguments?.getString("contactId") ?: ""
            ContactDetailsScreen(
                contactId = contactId,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToChat = { conversationId ->
                    navController.navigate(Screen.Chat.createRoute(conversationId))
                },
                onNavigateToCall = { callContactId, isVideo ->
                    navController.navigate(Screen.Call.createRoute(callContactId, isVideo))
                },
                onContactRemoved = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.NewConversation.route) {
            NewConversationScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onConversationCreated = { conversationId ->
                    navController.navigate(Screen.Chat.createRoute(conversationId)) {
                        popUpTo(Screen.Home.route)
                    }
                },
                onStartGroupCall = { participantIds, withVideo ->
                    navController.navigate(Screen.Conference.createRoute(participantIds, withVideo)) {
                        popUpTo(Screen.Home.route)
                    }
                }
            )
        }

        composable(Screen.Chat.route) { backStackEntry ->
            val conversationId = backStackEntry.arguments?.getString("conversationId") ?: ""
            ChatScreen(
                conversationId = conversationId,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Call.route) { backStackEntry ->
            val contactId = backStackEntry.arguments?.getString("contactId") ?: ""
            val isVideo = backStackEntry.arguments?.getString("isVideo")?.toBoolean() ?: false
            CallScreen(
                contactId = contactId,
                isVideo = isVideo,
                onCallEnded = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Conference.route) { backStackEntry ->
            val participantIdsArg = backStackEntry.arguments?.getString("participantIds") ?: ""
            val participantIds = if (participantIdsArg.isNotEmpty()) {
                participantIdsArg.split(",")
            } else {
                emptyList()
            }
            val withVideo = backStackEntry.arguments?.getString("withVideo")?.toBoolean() ?: false
            val conferenceId = backStackEntry.arguments?.getString("conferenceId")?.takeIf { it != "null" }
            ConferenceScreen(
                participantIds = participantIds,
                withVideo = withVideo,
                conferenceId = conferenceId,
                onConferenceEnded = {
                    navController.popBackStack()
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
    object NewConversation : Screen("new_conversation")
    object Contacts : Screen("contacts")
    object AddContact : Screen("add_contact")
    object ContactDetails : Screen("contact/{contactId}") {
        fun createRoute(contactId: String) = "contact/$contactId"
    }
    object Conversations : Screen("conversations")
    object Chat : Screen("chat/{conversationId}") {
        fun createRoute(conversationId: String) = "chat/$conversationId"
    }
    object Call : Screen("call/{contactId}/{isVideo}") {
        fun createRoute(contactId: String, isVideo: Boolean) = "call/$contactId/$isVideo"
    }
    object Conference : Screen("conference/{participantIds}/{withVideo}/{conferenceId}") {
        fun createRoute(participantIds: List<String>, withVideo: Boolean, conferenceId: String? = null) =
            "conference/${participantIds.joinToString(",")}/$withVideo/${conferenceId ?: "null"}"
    }
    object Settings : Screen("settings")
}
