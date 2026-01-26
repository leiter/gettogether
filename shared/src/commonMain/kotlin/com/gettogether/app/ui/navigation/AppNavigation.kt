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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import kotlin.time.Clock
import org.koin.compose.koinInject

/**
 * Represents initial navigation destination from notification or deep link.
 * Each instance includes a unique timestamp to ensure repeated navigation requests
 * to the same destination are recognized as new requests.
 */
sealed class InitialNavigation(val timestamp: Long = Clock.System.now().toEpochMilliseconds()) {
    data class Chat(val conversationId: String, private val ts: Long = Clock.System.now().toEpochMilliseconds()) : InitialNavigation(ts)
    data class Call(
        val contactId: String,
        val isVideo: Boolean,
        val callId: String? = null,
        val isAlreadyAccepted: Boolean = false,
        private val ts: Long = Clock.System.now().toEpochMilliseconds()
    ) : InitialNavigation(ts)
    data class ContactDetails(val contactId: String, private val ts: Long = Clock.System.now().toEpochMilliseconds()) : InitialNavigation(ts)
}

@Composable
fun AppNavigation(
    modifier: Modifier = Modifier,
    initialNavigation: InitialNavigation? = null
) {
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

    // Track which navigation we've handled to avoid repeating the same one
    // but allow new navigations to be processed
    var handledNavigation by remember { mutableStateOf<InitialNavigation?>(null) }

    // Handle initial navigation from notification/deep link
    LaunchedEffect(initialNavigation, accountState.accountId) {
        if (initialNavigation != null && accountState.accountId != null && initialNavigation != handledNavigation) {
            handledNavigation = initialNavigation
            println("AppNavigation: Handling initial navigation: $initialNavigation")

            // Check current destination to avoid duplicate navigation
            val currentRoute = navController.currentDestination?.route

            when (initialNavigation) {
                is InitialNavigation.Chat -> {
                    println("AppNavigation: Navigating to chat: ${initialNavigation.conversationId}")
                    navController.navigate(Screen.Chat.createRoute(initialNavigation.conversationId)) {
                        launchSingleTop = true
                    }
                }
                is InitialNavigation.Call -> {
                    // Check if we're already on a call screen
                    val isOnCallScreen = currentRoute?.startsWith("call/") == true
                    println("AppNavigation: Navigating to call: contactId=${initialNavigation.contactId}, isVideo=${initialNavigation.isVideo}, callId=${initialNavigation.callId}, accepted=${initialNavigation.isAlreadyAccepted}, currentRoute=$currentRoute, isOnCallScreen=$isOnCallScreen")

                    if (isOnCallScreen) {
                        // Already on call screen - just pop back to it if needed
                        // This avoids creating a new ViewModel
                        println("AppNavigation: Already on call screen, skipping navigation")
                    } else {
                        navController.navigate(
                            Screen.Call.createRoute(
                                initialNavigation.contactId,
                                initialNavigation.isVideo,
                                initialNavigation.callId,
                                initialNavigation.isAlreadyAccepted
                            )
                        ) {
                            launchSingleTop = true
                        }
                    }
                }
                is InitialNavigation.ContactDetails -> {
                    println("AppNavigation: Navigating to contact: ${initialNavigation.contactId}")
                    navController.navigate(Screen.ContactDetails.createRoute(initialNavigation.contactId)) {
                        launchSingleTop = true
                    }
                }
            }
        }
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
                onNavigateToActiveCall = { callContactId, callId, isVideo ->
                    // Navigate to existing active call
                    navController.navigate(Screen.Call.createRoute(callContactId, isVideo, callId, isAlreadyAccepted = true))
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
                navArgument("isVideo") { type = NavType.StringType },
                navArgument("callId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("accepted") {
                    type = NavType.StringType
                    defaultValue = "false"
                }
            )
        ) { backStackEntry ->
            val contactId = backStackEntry.extractArg("contactId")
            val isVideo = backStackEntry.extractArg("isVideo").toBoolean()
            val callId = backStackEntry.extractArgOrNull("callId")
            val isAlreadyAccepted = backStackEntry.extractArg("accepted").toBoolean()
            val callViewModel: CallViewModel = getViewModel()
            CallScreen(
                contactId = contactId,
                isVideo = isVideo,
                callId = callId,
                isAlreadyAccepted = isAlreadyAccepted,
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
    // Access argument using SavedStateHandle-style access
    return try {
        @Suppress("UNCHECKED_CAST")
        (this.arguments as? Map<String, Any?>)?.get(key)?.toString()
            ?: this.savedStateHandle.get<String>(key)
            ?: ""
    } catch (e: Exception) {
        ""
    }
}

// Nullable version for optional arguments
private fun androidx.navigation.NavBackStackEntry.extractArgOrNull(key: String): String? {
    return try {
        @Suppress("UNCHECKED_CAST")
        val value = (this.arguments as? Map<String, Any?>)?.get(key)?.toString()
            ?: this.savedStateHandle.get<String>(key)
        value?.takeIf { it.isNotEmpty() && it != "null" }
    } catch (e: Exception) {
        null
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
    object BlockedContacts : Screen("blocked_contacts")
    object ContactDetails : Screen("contact/{contactId}") {
        fun createRoute(contactId: String) = "contact/$contactId"
    }
    object Conversations : Screen("conversations")
    object Chat : Screen("chat/{conversationId}") {
        fun createRoute(conversationId: String) = "chat/$conversationId"
    }
    object Call : Screen("call/{contactId}/{isVideo}?callId={callId}&accepted={accepted}") {
        fun createRoute(
            contactId: String,
            isVideo: Boolean,
            callId: String? = null,
            isAlreadyAccepted: Boolean = false
        ): String {
            val base = "call/$contactId/$isVideo"
            return if (callId != null) {
                "$base?callId=$callId&accepted=$isAlreadyAccepted"
            } else {
                base
            }
        }
    }
    object Conference : Screen("conference/{participantIds}/{withVideo}/{conferenceId}") {
        fun createRoute(participantIds: List<String>, withVideo: Boolean, conferenceId: String? = null) =
            "conference/${participantIds.joinToString(",")}/$withVideo/${conferenceId ?: "null"}"
    }
    object Settings : Screen("settings")
}
