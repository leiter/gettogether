package com.gettogether.app.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.tween
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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.gettogether.app.data.repository.AccountRepository
import com.gettogether.app.presentation.viewmodel.AddContactViewModel
import com.gettogether.app.presentation.viewmodel.BlockedContactsViewModel
import com.gettogether.app.presentation.viewmodel.CallViewModel
import com.gettogether.app.presentation.viewmodel.ChatViewModel
import com.gettogether.app.presentation.viewmodel.ConferenceViewModel
import com.gettogether.app.presentation.viewmodel.ContactDetailsViewModel
import com.gettogether.app.presentation.viewmodel.ContactsViewModel
import com.gettogether.app.presentation.viewmodel.ConversationRequestsViewModel
import com.gettogether.app.presentation.viewmodel.ConversationsViewModel
import com.gettogether.app.presentation.viewmodel.CreateAccountViewModel
import com.gettogether.app.presentation.viewmodel.ImportAccountViewModel
import com.gettogether.app.presentation.viewmodel.NewConversationViewModel
import com.gettogether.app.presentation.viewmodel.SettingsViewModel
import com.gettogether.app.presentation.viewmodel.TrustRequestsViewModel
import com.gettogether.app.ui.screens.auth.CreateAccountScreen
import com.gettogether.app.ui.screens.auth.ImportAccountScreen
import com.gettogether.app.ui.screens.auth.WelcomeScreen
import com.gettogether.app.ui.screens.call.CallScreen
import com.gettogether.app.ui.screens.chat.ChatScreen
import com.gettogether.app.ui.screens.conference.ConferenceScreen
import com.gettogether.app.ui.screens.contacts.AddContactScreen
import com.gettogether.app.ui.screens.contacts.BlockedContactsScreen
import com.gettogether.app.ui.screens.contacts.ContactDetailsScreen
import com.gettogether.app.ui.screens.home.HomeScreen
import com.gettogether.app.ui.screens.newconversation.NewConversationScreen
import com.gettogether.app.di.getViewModel
import org.koin.compose.koinInject

@Composable
fun AppNavigation(modifier: Modifier = Modifier) {
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

    val animationDuration = 400

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier.background(MaterialTheme.colorScheme.background),
        enterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(animationDuration, easing = EaseInOut)
            )
        },
        exitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(animationDuration, easing = EaseInOut)
            )
        },
        popEnterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(animationDuration, easing = EaseInOut)
            )
        },
        popExitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(animationDuration, easing = EaseInOut)
            )
        }
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
            val importAccountViewModel: ImportAccountViewModel = getViewModel()
            ImportAccountScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onAccountImported = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Welcome.route) { inclusive = true }
                    }
                },
                viewModel = importAccountViewModel
            )
        }

        composable(Screen.CreateAccount.route) {
            val createAccountViewModel: CreateAccountViewModel = getViewModel()
            CreateAccountScreen(
                viewModel = createAccountViewModel,
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
            val conversationsViewModel: ConversationsViewModel = getViewModel()
            val conversationRequestsViewModel: ConversationRequestsViewModel = getViewModel()
            val contactsViewModel: ContactsViewModel = getViewModel()
            val trustRequestsViewModel: TrustRequestsViewModel = getViewModel()
            val settingsViewModel: SettingsViewModel = getViewModel()
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
                onNavigateToBlockedContacts = {
                    navController.navigate(Screen.BlockedContacts.route)
                },
                onSignedOut = {
                    navController.navigate(Screen.Welcome.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                },
                conversationsViewModel = conversationsViewModel,
                conversationRequestsViewModel = conversationRequestsViewModel,
                contactsViewModel = contactsViewModel,
                trustRequestsViewModel = trustRequestsViewModel,
                settingsViewModel = settingsViewModel
            )
        }

        composable(Screen.BlockedContacts.route) {
            val blockedContactsViewModel: BlockedContactsViewModel = getViewModel()
            BlockedContactsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                viewModel = blockedContactsViewModel
            )
        }

        composable(Screen.AddContact.route) {
            val addContactViewModel: AddContactViewModel = getViewModel()
            AddContactScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onContactAdded = {
                    navController.popBackStack()
                },
                viewModel = addContactViewModel
            )
        }

        composable(
            Screen.ContactDetails.route,
            arguments = listOf(navArgument("contactId") { type = NavType.StringType })
        ) { backStackEntry ->
            val contactId = backStackEntry.extractArg("contactId")
            val contactDetailsViewModel: ContactDetailsViewModel = getViewModel()
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
                },
                viewModel = contactDetailsViewModel
            )
        }

        composable(Screen.NewConversation.route) {
            val newConversationViewModel: NewConversationViewModel = getViewModel()
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
                },
                viewModel = newConversationViewModel
            )
        }

        composable(
            Screen.Chat.route,
            arguments = listOf(navArgument("conversationId") { type = NavType.StringType })
        ) { backStackEntry ->
            val conversationId = backStackEntry.extractArg("conversationId")
            val chatViewModel: ChatViewModel = getViewModel()
            ChatScreen(
                conversationId = conversationId,
                onNavigateBack = {
                    navController.popBackStack()
                },
                viewModel = chatViewModel
            )
        }

        composable(
            Screen.Call.route,
            arguments = listOf(
                navArgument("contactId") { type = NavType.StringType },
                navArgument("isVideo") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val contactId = backStackEntry.extractArg("contactId")
            val isVideo = backStackEntry.extractArg("isVideo").toBoolean()
            val callViewModel: CallViewModel = getViewModel()
            CallScreen(
                contactId = contactId,
                isVideo = isVideo,
                onCallEnded = {
                    navController.popBackStack()
                },
                viewModel = callViewModel
            )
        }

        composable(
            Screen.Conference.route,
            arguments = listOf(
                navArgument("participantIds") { type = NavType.StringType },
                navArgument("withVideo") { type = NavType.StringType },
                navArgument("conferenceId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val participantIdsArg = backStackEntry.extractArg("participantIds")
            val participantIds = if (participantIdsArg.isNotEmpty()) {
                participantIdsArg.split(",")
            } else {
                emptyList()
            }
            val withVideo = backStackEntry.extractArg("withVideo").toBoolean()
            val conferenceId = backStackEntry.extractArg("conferenceId").takeIf { it != "null" }
            val conferenceViewModel: ConferenceViewModel = getViewModel()
            ConferenceScreen(
                participantIds = participantIds,
                withVideo = withVideo,
                conferenceId = conferenceId,
                onConferenceEnded = {
                    navController.popBackStack()
                },
                viewModel = conferenceViewModel
            )
        }
    }
}

// Extension function to extract arguments in a KMP-compatible way
private fun androidx.navigation.NavBackStackEntry.extractArg(key: String): String {
    return this.savedStateHandle.get<String>(key) ?: ""
}

sealed class Screen(val route: String) {
    object Welcome : Screen("welcome")
    object CreateAccount : Screen("create_account")
    object ImportAccount : Screen("import_account")
    object Home : Screen("home")
    object NewConversation : Screen("new_conversation")
    object Contacts : Screen("contacts")
    object AddContact : Screen("add_contact")
    object BlockedContacts : Screen("blocked_contacts")
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
