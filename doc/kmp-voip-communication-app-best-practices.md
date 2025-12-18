# Kotlin Multiplatform (KMP) Communication/VoIP Application - Best Practices Guide (2025)

**Last Updated:** December 18, 2025

This comprehensive guide covers best practices for building a Kotlin Multiplatform (KMP) communication/VoIP application with shared Compose UI for iOS and Android.

---

## Table of Contents

1. [KMP Project Structure with Shared Compose UI](#1-kmp-project-structure-with-shared-compose-ui)
2. [Native SDK Integration Best Practices](#2-native-sdk-integration-best-practices)
3. [Architecture Patterns for Real-Time Communication](#3-architecture-patterns-for-real-time-communication)
4. [Platform-Specific Features](#4-platform-specific-features)
5. [State Management Approaches](#5-state-management-approaches)
6. [Recommended Libraries and Ecosystem](#6-recommended-libraries-and-ecosystem)
7. [Production Examples and References](#7-production-examples-and-references)

---

## 1. KMP Project Structure with Shared Compose UI

### Overview

Kotlin Multiplatform (KMP) is **officially supported by Google** for sharing business logic between Android and iOS and is **stable and production-ready** as of 2025. With JetBrains' Compose Multiplatform (CMP), developers can share both business logic and UI across platforms.

### Key Characteristics

- **Not a UI Replacement Framework**: Unlike Flutter or React Native, KMP focuses on sharing business logic (networking, caching, data models, algorithms, database layers) while allowing native UIs when needed
- **Official Backing**: Fully supported by Google and JetBrains with continued investment
- **Production Ready**: Used by major companies including Google, Duolingo, Forbes, Philips, McDonald's, Bolt, H&M, Baidu, and Netflix
- **2.5 million developers** worldwide now use Kotlin

### Project Structure

A typical KMP project consists of:

```
project/
├── shared/
│   ├── commonMain/          # Shared Kotlin code (business logic, models, utilities)
│   │   ├── kotlin/
│   │   ├── resources/       # Shared resources
│   │   └── composeResources/  # Compose Multiplatform resources
│   ├── androidMain/         # Android-specific implementations
│   ├── iosMain/            # iOS-specific implementations
│   └── build.gradle.kts
├── androidApp/             # Android application
└── iosApp/                # iOS application (Xcode project)
```

### Code Compilation

- **Android**: Compiles to JVM bytecode
- **iOS**: Compiles to native binaries using Kotlin/Native
- **Result**: Write logic once, reuse everywhere while delivering fully native apps

### Compose Multiplatform UI Integration

#### Shared UI Benefits

- Write UI once using Compose declarative syntax
- Deploy across Android, iOS (stable in 2025), Desktop, and Web
- Share ViewModels, navigation, and resources across platforms

#### iOS Integration Pattern

To integrate Compose Multiplatform into iOS applications:

1. **Create a Bridge Component** in `iosMain` source set:

```kotlin
// shared/iosMain/kotlin/MainViewController.kt
fun MainViewController(): UIViewController {
    return ComposeUIViewController {
        App()  // Your root composable
    }
}
```

2. **iOS Side Integration** (minimal Swift code needed):

```swift
// iosApp/iOSApp.swift
import SwiftUI
import shared

@main
struct iOSApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.all)
    }
}

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        return MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
```

### Platform-Specific Code: expect/actual Pattern

For features requiring native APIs (maps, camera, sensors, custom rendering):

```kotlin
// commonMain
expect class PlatformSpecificFeature() {
    fun doSomething(): String
}

// androidMain
actual class PlatformSpecificFeature {
    actual fun doSomething(): String {
        // Android-specific implementation
        return "Android implementation"
    }
}

// iosMain
actual class PlatformSpecificFeature {
    actual fun doSomething(): String {
        // iOS-specific implementation
        return "iOS implementation"
    }
}
```

### Getting Started

1. **Install Kotlin Multiplatform Plugin** for Android Studio (developed by JetBrains)
2. **Create New Project**: Use Android Studio Meerkat and AGP 8.8.0+ with the Kotlin Multiplatform Shared Module template
3. **Select Targets**: Choose Android, iOS, Desktop, and/or Web
4. **Enable Share UI Option**: Select this for iOS and web targets

### IDE Support & Tooling (2025)

- **Android Studio & IntelliJ IDEA**: Full KMP support with:
  - Common UI previews
  - Hot reload for Compose Multiplatform
  - Cross-language navigation (Kotlin ↔ Swift)
  - Refactoring tools
  - Unified debugging
- **Live Edit**: Works for Android devices, editing any code (not just androidMain)
- **Compose Previews**: Available from commonMain source set
- **Windows/Linux**: KMP plugin available with wizard, preflight checks, and Compose Hot Reload

---

## 2. Native SDK Integration Best Practices

### Core Principles

#### 1. Start with Isolated Features

- Begin with one isolated feature to minimize risk
- Track results closely
- Expand gradually based on success
- **Example**: Start with analytics or a simple API integration

#### 2. Build Thin Platform Adapters

**Key Rule**: Keep adapters as thin as possible

```kotlin
// ❌ BAD: Too much logic in adapter
// androidMain
actual class AnalyticsAdapter {
    actual fun trackEvent(name: String, properties: Map<String, Any>) {
        // Validation logic
        if (name.isEmpty()) return

        // Data transformation
        val transformed = properties.mapValues { /* ... */ }

        // Firebase call
        FirebaseAnalytics.getInstance().logEvent(name, transformed)
    }
}

// ✅ GOOD: Thin adapter, logic in shared code
// commonMain
class Analytics(private val adapter: AnalyticsAdapter) {
    fun trackEvent(name: String, properties: Map<String, Any>) {
        // All validation and transformation here
        if (name.isEmpty()) return
        val transformed = properties.mapValues { /* ... */ }
        adapter.sendEvent(name, transformed)
    }
}

// androidMain
actual class AnalyticsAdapter {
    actual fun sendEvent(name: String, properties: Map<String, Any>) {
        // ONLY platform-specific SDK call
        FirebaseAnalytics.getInstance().logEvent(name, properties)
    }
}
```

#### 3. Use expect/actual for Platform APIs

```kotlin
// commonMain
expect class WebRTCClient() {
    fun createOffer(): String
    fun setRemoteDescription(sdp: String)
    fun addIceCandidate(candidate: String)
}

// androidMain
actual class WebRTCClient {
    private val peerConnection: PeerConnection = // Android WebRTC setup

    actual fun createOffer(): String {
        // Android-specific WebRTC implementation
    }

    actual fun setRemoteDescription(sdp: String) {
        // Android implementation
    }

    actual fun addIceCandidate(candidate: String) {
        // Android implementation
    }
}

// iosMain
actual class WebRTCClient {
    // iOS WebRTC implementation using native APIs
    actual fun createOffer(): String {
        // iOS implementation
    }

    actual fun setRemoteDescription(sdp: String) {
        // iOS implementation
    }

    actual fun addIceCandidate(candidate: String) {
        // iOS implementation
    }
}
```

#### 4. Swift/iOS Integration (2025 Updates)

**Kotlin Coroutines in Swift**:

Two main approaches:
- **KMP-NativeCoroutines**: More mature, tried-and-tested
- **SKIE**: Easier setup, less verbose, maps Kotlin Flow to Swift AsyncSequence directly

**Kotlin 2.2.20+ Swift Export**: Native Swift interop for KMP (experimental in 2025, becoming stable)

#### 5. Leverage Jetpack Libraries with KMP Support

Google is adding KMP support to Jetpack libraries:

- **Stable**: Room, DataStore, Collection
- **Recent Additions**: ViewModel, SavedState, Paging

Example using Room in KMP:

```kotlin
// commonMain
@Database(entities = [Message::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
}

expect fun getDatabaseBuilder(): RoomDatabase.Builder<AppDatabase>

// androidMain
actual fun getDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> {
    val context = // get Android context
    return Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        "app_database"
    )
}

// iosMain
actual fun getDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> {
    val dbFile = // get iOS file path
    return Room.databaseBuilder<AppDatabase>(
        name = dbFile,
    )
}
```

#### 6. Automated Testing & Validation

**Best Practice from Production Teams**:

- Set up nightly automated tests
- Compare data between native SDKs and KMP implementations
- Alert if data drift exceeds threshold (e.g., 1%)
- Makes scaling safer and faster

### 2025 Tooling Improvements

**Android Studio Plugin Features**:
- New project wizard for multiplatform projects
- Preflight checks for environment configuration
- Run configurations for iOS and Android from one IDE
- Cross-platform debugging

**Build Performance** (JetBrains Roadmap 2025):
- Focus on improving Kotlin/Native build speeds
- Addressing key issues for faster builds
- Experimental Swift Export feature improvements

---

## 3. Architecture Patterns for Real-Time Communication

### Real-Time Communication Libraries

#### 1. WebRTC Kotlin Multiplatform

**Primary Library: webrtc-kmp**

```kotlin
// Add dependency
implementation("com.shepeliev:webrtc-kmp-android:0.125.11")
```

**Features**:
- Supports Android, iOS, and JS
- Comprehensive WebRTC functionality
- Active development and maintenance
- Demo available: https://github.com/shepeliev/webrtc-kmp-demo

**Example Usage**:

```kotlin
// commonMain
class WebRTCManager(private val signaling: SignalingClient) {
    private val peerConnection: PeerConnection = createPeerConnection()

    suspend fun initiateCall(remoteUserId: String) {
        val offer = peerConnection.createOffer()
        peerConnection.setLocalDescription(offer)
        signaling.sendOffer(remoteUserId, offer.sdp)
    }

    suspend fun handleIncomingOffer(offer: String) {
        peerConnection.setRemoteDescription(SessionDescription(SdpType.OFFER, offer))
        val answer = peerConnection.createAnswer()
        peerConnection.setLocalDescription(answer)
        signaling.sendAnswer(answer.sdp)
    }
}
```

#### 2. Ktor WebRTC Support (Official - 2025)

**JetBrains is developing official WebRTC support in Ktor 3.3.0+**

**Features**:
- Unified multiplatform API
- Works across browsers, Android, and more platforms (iOS, JVM, Native coming)
- Focus on peer-to-peer connections
- Manual signaling management (WebSocket, HTTP)
- Experimental Rust-based WebRTC client support planned

**Current Support** (Ktor 3.3.0):
- JS/Wasm platforms
- Android platform
- Preview available

**Example Architecture**:

```kotlin
// commonMain
class KtorWebRTCClient(private val httpClient: HttpClient) {
    private val webSocket = // WebSocket for signaling

    suspend fun connectToRoom(roomId: String) {
        // Ktor WebRTC setup
        webSocket.sendSerialized(JoinRoom(roomId))

        // Handle WebRTC peer connection
        // Signaling through WebSocket
    }
}
```

**Resources**:
- See KLIP documentation
- Full stack KMP Ktor Video Chat example available

### Production-Tested Pattern

**Real-world success**: One team created a library for in-app calls using WebRTC and WebSockets, running successfully in production for over 2 years.

**Architecture**:
```
┌─────────────────┐
│   UI Layer      │  (Compose Multiplatform)
│  (commonMain)   │
└────────┬────────┘
         │
┌────────┴────────┐
│  Business Logic │  (WebRTC Manager, Call State)
│   (commonMain)  │
└────────┬────────┘
         │
┌────────┴────────┐
│ Platform Layer  │  (expect/actual)
│ WebRTC Native   │
│   Android/iOS   │
└─────────────────┘
```

### MVI Architecture for Communication Apps

**Model-View-Intent (MVI)** is recommended for real-time communication apps:

```kotlin
// commonMain
sealed class CallState {
    object Idle : CallState()
    data class Ringing(val callerId: String) : CallState()
    data class InCall(val participantIds: List<String>, val duration: Long) : CallState()
    object Ended : CallState()
}

sealed class CallIntent {
    data class InitiateCall(val userId: String) : CallIntent()
    object AcceptCall : CallIntent()
    object RejectCall : CallIntent()
    object EndCall : CallIntent()
}

class CallViewModel : ViewModel() {
    private val _state = MutableStateFlow<CallState>(CallState.Idle)
    val state: StateFlow<CallState> = _state.asStateFlow()

    fun handleIntent(intent: CallIntent) {
        when (intent) {
            is CallIntent.InitiateCall -> initiateCall(intent.userId)
            is CallIntent.AcceptCall -> acceptCall()
            is CallIntent.RejectCall -> rejectCall()
            is CallIntent.EndCall -> endCall()
        }
    }

    private fun initiateCall(userId: String) {
        viewModelScope.launch {
            // WebRTC call initiation
            _state.value = CallState.Ringing(userId)
        }
    }
}
```

**MVI Flow Library**: Teams have created MVI Flow libraries based on Coroutines Flows for component communication.

### Recommended Architecture Layers

```
┌──────────────────────────────────────┐
│         Presentation Layer           │
│  (Compose UI + ViewModels)           │
│         commonMain                   │
└──────────────────┬───────────────────┘
                   │
┌──────────────────┴───────────────────┐
│         Domain Layer                 │
│  (Use Cases, Business Logic)         │
│         commonMain                   │
└──────────────────┬───────────────────┘
                   │
┌──────────────────┴───────────────────┐
│         Data Layer                   │
│  (Repositories, Data Sources)        │
│         commonMain                   │
└──────────────────┬───────────────────┘
                   │
┌──────────────────┴───────────────────┐
│      Platform-Specific Layer         │
│  (Native SDKs, expect/actual)        │
│    androidMain / iosMain             │
└──────────────────────────────────────┘
```

**Benefits**:
- **Shared brains**: Business logic in commonMain
- **Native looks**: Platform-specific UI when needed
- **Testability**: Easy to test shared logic
- **Consistency**: Same behavior across platforms

---

## 4. Platform-Specific Features

### Push Notifications

#### KMPNotifier Library (Recommended)

**Features**:
- Firebase Cloud Messaging for Android and iOS
- Local notifications for Android, iOS, Desktop, Web (JS/WASM)
- Kotlin 2.0 support
- Custom notification sounds
- Deep linking support
- Flexible permission handling

**Installation**:

```kotlin
// shared/build.gradle.kts
commonMain.dependencies {
    implementation("io.github.mirzemehdi:kmpnotifier:1.0.0")
}
```

**Usage Example**:

```kotlin
// commonMain
class NotificationManager {
    private val notifier = NotifierManager.getNotifier()

    fun initialize() {
        NotifierManager.initialize(
            configuration = NotificationPlatformConfiguration.Android(
                notificationIconResId = R.drawable.ic_notification,
                notificationIconColorResId = R.color.notification_color,
            )
        )
    }

    fun sendLocalNotification(title: String, body: String) {
        notifier.notify {
            this.title = title
            this.body = body
        }
    }

    suspend fun getPushToken(): String? {
        return notifier.getToken()
    }
}

// For push notifications
class PushNotificationHandler {
    fun handlePushToken(token: String) {
        // Send token to your backend
    }

    fun handleRemoteNotification(payload: NotificationPayload) {
        // Handle incoming call notification
        when (payload.type) {
            "incoming_call" -> showIncomingCallUI()
            "message" -> showMessageNotification()
        }
    }
}
```

**Platform Configuration**:

```kotlin
// Android-specific configuration (androidMain)
NotifierManager.initialize(
    configuration = NotificationPlatformConfiguration.Android(
        notificationIconResId = R.drawable.ic_notification,
        showPushNotification = true,
        askNotificationPermissionOnStart = false  // Manual control
    )
)

// iOS-specific configuration (iosMain)
NotifierManager.initialize(
    configuration = NotificationPlatformConfiguration.iOS(
        askNotificationPermissionOnStart = true,
        notificationSoundName = "custom_sound.wav"
    )
)

// Web configuration
NotifierManager.initialize(
    configuration = NotificationPlatformConfiguration.Web(
        askNotificationPermissionOnStart = false,
        notificationIconPath = "/images/icon.png"
    )
)
```

#### Alarmee Library (Alternative - June 2025)

**Features**:
- Schedule local and push notifications
- Works on Android and iOS
- One-time and repeating alarms
- Single shared API

```kotlin
implementation("com.github.vivienmahe:alarmee:1.0.0")
```

#### Platform-Specific Implementation Details

**Android**:
- Uses `NotificationCompat` and `AlarmManager`
- Requires notification channels for Android 8+
- Background service for call notifications

**iOS**:
- Uses `UNUserNotificationCenter` and `UIApplication`
- Minimal Swift code in AppDelegate for notification callbacks
- Everything else runs from Kotlin

**Example expect/actual for Notifications**:

```kotlin
// commonMain
expect class NotificationPermissionHandler {
    suspend fun requestPermission(): Boolean
    fun hasPermission(): Boolean
}

// androidMain
actual class NotificationPermissionHandler(private val context: Context) {
    actual suspend fun requestPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Request POST_NOTIFICATIONS permission
            return // permission result
        }
        return true
    }

    actual fun hasPermission(): Boolean {
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
}

// iosMain
actual class NotificationPermissionHandler {
    actual suspend fun requestPermission(): Boolean {
        return suspendCoroutine { continuation ->
            UNUserNotificationCenter.current().requestAuthorizationWithOptions(
                options = setOf(
                    UNAuthorizationOption.Alert,
                    UNAuthorizationOption.Sound,
                    UNAuthorizationOption.Badge
                )
            ) { granted, error ->
                continuation.resume(granted)
            }
        }
    }

    actual fun hasPermission(): Boolean {
        // Check iOS notification settings
        return // permission status
    }
}
```

### Background Services for Calls

#### Android Background Service

```kotlin
// androidMain
class CallService : Service() {
    private val webRTCClient: WebRTCClient by inject()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_CALL -> startForegroundCall()
            ACTION_END_CALL -> endCall()
        }
        return START_STICKY
    }

    private fun startForegroundCall() {
        val notification = createCallNotification()
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createCallNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Ongoing Call")
            .setSmallIcon(R.drawable.ic_call)
            .setOngoing(true)
            .addAction(R.drawable.ic_end_call, "End Call", endCallPendingIntent)
            .build()
    }
}
```

#### iOS CallKit Integration

```swift
// iOS AppDelegate (Swift)
import CallKit

class CallManager: NSObject, CXProviderDelegate {
    let provider: CXProvider
    let callController = CXCallController()

    override init() {
        let config = CXProviderConfiguration()
        config.maximumCallGroups = 1
        config.maximumCallsPerCallGroup = 1
        provider = CXProvider(configuration: config)
        super.init()
        provider.setDelegate(self, queue: nil)
    }

    func reportIncomingCall(uuid: UUID, handle: String) {
        let update = CXCallUpdate()
        update.remoteHandle = CXHandle(type: .generic, value: handle)

        provider.reportNewIncomingCall(with: uuid, update: update) { error in
            if let error = error {
                print("Error reporting call: \(error)")
            }
        }
    }

    func providerDidReset(_ provider: CXProvider) {
        // Handle provider reset
    }
}
```

**Bridge to Kotlin**:

```kotlin
// iosMain
actual class CallKitManager {
    private val callManager = // Swift CallManager instance

    actual fun showIncomingCall(callerId: String, onAccept: () -> Unit, onReject: () -> Unit) {
        val uuid = UUID.randomUUID()
        callManager.reportIncomingCall(uuid, callerId)
    }
}
```

### Platform-Specific Permissions

```kotlin
// commonMain
interface PermissionHandler {
    suspend fun requestCameraPermission(): Boolean
    suspend fun requestMicrophonePermission(): Boolean
    suspend fun requestNotificationPermission(): Boolean
}

// Implement in androidMain and iosMain with platform-specific logic
```

---

## 5. State Management Approaches

### Built-in Compose State APIs

#### Basic State Management

```kotlin
// commonMain
@Composable
fun CallScreen() {
    var callState by remember { mutableStateOf(CallState.Idle) }
    var isMuted by remember { mutableStateOf(false) }

    CallUI(
        callState = callState,
        isMuted = isMuted,
        onMuteToggle = { isMuted = !isMuted },
        onEndCall = { callState = CallState.Ended }
    )
}
```

#### State Hoisting

**Principle**: Stateless composables receive data as parameters and pass events up to parent

```kotlin
// ✅ GOOD: Stateless composable
@Composable
fun CallControls(
    isMuted: Boolean,
    onMuteToggle: () -> Unit,
    onEndCall: () -> Unit
) {
    Row {
        IconButton(onClick = onMuteToggle) {
            Icon(if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic)
        }
        IconButton(onClick = onEndCall) {
            Icon(Icons.Filled.CallEnd)
        }
    }
}
```

### ViewModel with StateFlow (Recommended)

**Official Kotlin Multiplatform ViewModel** (androidx.lifecycle 2.10.0+):

```kotlin
// commonMain
dependencies {
    implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
}
```

**Features**:
- Automatic lifecycle management
- Scoped to Activity, Fragment, NavBackStackEntry (Navigation 2/3)
- AutoCloseable pattern for resource management
- viewModelScope for coroutines
- Supports JVM, Android, iOS, Native, Web (JS/WasmJS)

**Example - Call State Management**:

```kotlin
// commonMain
class CallViewModel(
    private val webRTCManager: WebRTCManager,
    private val messageRepository: MessageRepository
) : ViewModel() {

    private val _callState = MutableStateFlow<CallState>(CallState.Idle)
    val callState: StateFlow<CallState> = _callState.asStateFlow()

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _connectionState = MutableStateFlow(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    init {
        observeWebRTCState()
        observeMessages()
    }

    private fun observeWebRTCState() {
        viewModelScope.launch {
            webRTCManager.connectionState.collect { state ->
                _connectionState.value = state
            }
        }
    }

    private fun observeMessages() {
        viewModelScope.launch {
            messageRepository.getMessages().collect { messages ->
                _messages.value = messages
            }
        }
    }

    fun initiateCall(userId: String) {
        viewModelScope.launch {
            _callState.value = CallState.Connecting
            try {
                webRTCManager.createCall(userId)
                _callState.value = CallState.Ringing(userId)
            } catch (e: Exception) {
                _callState.value = CallState.Failed(e.message ?: "Unknown error")
            }
        }
    }

    fun acceptCall() {
        viewModelScope.launch {
            webRTCManager.acceptCall()
            _callState.value = CallState.InCall(startTime = Clock.System.now())
        }
    }

    fun endCall() {
        viewModelScope.launch {
            webRTCManager.endCall()
            _callState.value = CallState.Ended
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Cleanup handled automatically via AutoCloseable
    }
}
```

**Usage in Compose**:

```kotlin
// commonMain
@Composable
fun CallScreenRoot() {
    val viewModel = viewModel { CallViewModel(get(), get()) }
    val callState by viewModel.callState.collectAsState()
    val messages by viewModel.messages.collectAsState()

    CallScreen(
        callState = callState,
        messages = messages,
        onInitiateCall = { userId -> viewModel.initiateCall(userId) },
        onAcceptCall = { viewModel.acceptCall() },
        onEndCall = { viewModel.endCall() }
    )
}
```

### Advanced State Management

#### derivedStateOf for Performance

```kotlin
// commonMain
@Composable
fun MessagesList(messages: List<Message>) {
    val unreadCount by remember {
        derivedStateOf {
            messages.count { !it.isRead }
        }
    }

    // Only recomposes when unreadCount changes, not on every messages change
    Text("Unread: $unreadCount")
}
```

#### Global State with Koin

**Problem**: Sharing state across multiple screens in KMP

**Solution**: Use Koin + mutableStateOf for global state

```kotlin
// commonMain - DI Module
val appModule = module {
    single { CallStateManager() }
    single { UserPresenceManager() }
}

class CallStateManager {
    val currentCall = mutableStateOf<Call?>(null)
    val incomingCalls = mutableStateOf<List<Call>>(emptyList())

    fun addIncomingCall(call: Call) {
        incomingCalls.value = incomingCalls.value + call
    }

    fun removeCall(callId: String) {
        incomingCalls.value = incomingCalls.value.filter { it.id != callId }
    }
}

// Usage in Composable
@Composable
fun IncomingCallBanner() {
    val callStateManager: CallStateManager = get()
    val incomingCalls by remember { callStateManager.incomingCalls }

    if (incomingCalls.isNotEmpty()) {
        IncomingCallUI(call = incomingCalls.first())
    }
}
```

### State Management Libraries

#### 1. PreCompose

**Features**:
- Navigation + ViewModel + Lifecycle
- Inspired by Jetpack Navigation
- Kotlin Multiplatform
- Write once in commonMain, run anywhere

```kotlin
dependencies {
    implementation("moe.tlaster:precompose:1.6.0")
}
```

**Example**:

```kotlin
@Composable
fun App() {
    PreComposeApp {
        NavHost(
            navigator = rememberNavigator(),
            initialRoute = "/home"
        ) {
            scene("/home") {
                val viewModel = viewModel(HomeViewModel::class) {
                    HomeViewModel()
                }
                HomeScreen(viewModel)
            }

            scene("/call/{userId}") { backStackEntry ->
                val userId = backStackEntry.path<String>("userId")
                val viewModel = viewModel(CallViewModel::class) {
                    CallViewModel(userId)
                }
                CallScreen(viewModel)
            }
        }
    }
}
```

#### 2. Redux Pattern

**Used in Official KMP Production Sample** (RSS Reader):

```kotlin
// commonMain
class Store<S, A>(
    initialState: S,
    private val reducer: (S, A) -> S
) {
    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<S> = _state.asStateFlow()

    fun dispatch(action: A) {
        _state.value = reducer(_state.value, action)
    }
}

// Application State
data class AppState(
    val callState: CallState = CallState.Idle,
    val messages: List<Message> = emptyList(),
    val userPresence: Map<String, PresenceStatus> = emptyMap()
)

sealed class AppAction {
    data class SetCallState(val state: CallState) : AppAction()
    data class AddMessage(val message: Message) : AppAction()
    data class UpdatePresence(val userId: String, val status: PresenceStatus) : AppAction()
}

fun appReducer(state: AppState, action: AppAction): AppState {
    return when (action) {
        is AppAction.SetCallState -> state.copy(callState = action.state)
        is AppAction.AddMessage -> state.copy(messages = state.messages + action.message)
        is AppAction.UpdatePresence -> {
            state.copy(userPresence = state.userPresence + (action.userId to action.status))
        }
    }
}
```

#### 3. MVI with Flow

```kotlin
// commonMain
interface MVIViewModel<STATE, INTENT> {
    val state: StateFlow<STATE>
    fun handleIntent(intent: INTENT)
}

class CallViewModel : ViewModel(), MVIViewModel<CallViewState, CallIntent> {
    private val _state = MutableStateFlow(CallViewState())
    override val state: StateFlow<CallViewState> = _state.asStateFlow()

    override fun handleIntent(intent: CallIntent) {
        when (intent) {
            is CallIntent.InitiateCall -> handleInitiateCall(intent.userId)
            is CallIntent.ToggleMute -> handleToggleMute()
            is CallIntent.ToggleCamera -> handleToggleCamera()
        }
    }
}
```

### Message Sync Strategy

**For real-time message synchronization**:

```kotlin
// commonMain
class MessageSyncManager(
    private val localDataSource: MessageDao,
    private val remoteDataSource: MessageApi,
    private val webSocketClient: WebSocketClient
) {
    private val _syncState = MutableStateFlow(SyncState.Synced)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    suspend fun startSync() {
        // 1. Fetch initial messages from server
        val remoteMessages = remoteDataSource.getMessages()
        localDataSource.insertAll(remoteMessages)

        // 2. Listen for real-time updates via WebSocket
        webSocketClient.messages.collect { message ->
            localDataSource.insert(message)
        }
    }

    suspend fun sendMessage(content: String) {
        val message = Message(
            id = UUID.randomUUID().toString(),
            content = content,
            timestamp = Clock.System.now(),
            syncStatus = SyncStatus.Pending
        )

        // Optimistic update
        localDataSource.insert(message)

        try {
            remoteDataSource.sendMessage(message)
            localDataSource.updateSyncStatus(message.id, SyncStatus.Synced)
        } catch (e: Exception) {
            localDataSource.updateSyncStatus(message.id, SyncStatus.Failed)
            // Retry logic
        }
    }
}

enum class SyncStatus {
    Pending, Synced, Failed
}
```

---

## 6. Recommended Libraries and Ecosystem

### Networking

#### Ktor (Official Kotlin Multiplatform HTTP Client)

```kotlin
// shared/build.gradle.kts
dependencies {
    implementation("io.ktor:ktor-client-core:3.3.0")
    implementation("io.ktor:ktor-client-content-negotiation:3.3.0")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.3.0")

    // Platform engines
    implementation("io.ktor:ktor-client-android:3.3.0") // androidMain
    implementation("io.ktor:ktor-client-darwin:3.3.0")  // iosMain
}
```

**Example API Client**:

```kotlin
// commonMain
class ApiClient {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
        install(Logging) {
            level = LogLevel.BODY
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 30000
        }
    }

    suspend fun getMessages(): List<Message> {
        return client.get("https://api.example.com/messages").body()
    }

    suspend fun sendMessage(message: Message): Message {
        return client.post("https://api.example.com/messages") {
            contentType(ContentType.Application.Json)
            setBody(message)
        }.body()
    }
}
```

**WebSocket Support**:

```kotlin
class WebSocketClient {
    private val client = HttpClient {
        install(WebSockets)
    }

    private val _messages = MutableSharedFlow<Message>()
    val messages: SharedFlow<Message> = _messages.asSharedFlow()

    suspend fun connect() {
        client.webSocket("wss://api.example.com/ws") {
            // Send messages
            send("Hello Server")

            // Receive messages
            for (frame in incoming) {
                if (frame is Frame.Text) {
                    val message = Json.decodeFromString<Message>(frame.readText())
                    _messages.emit(message)
                }
            }
        }
    }
}
```

### Database

#### SQLDelight (Type-safe SQL for KMP)

```kotlin
dependencies {
    implementation("app.cash.sqldelight:runtime:2.0.0")
    implementation("app.cash.sqldelight:coroutines-extensions:2.0.0")

    // Platform drivers
    implementation("app.cash.sqldelight:android-driver:2.0.0")
    implementation("app.cash.sqldelight:native-driver:2.0.0")
}
```

**Schema Definition** (`.sq` file):

```sql
-- shared/src/commonMain/sqldelight/com/example/Database.sq

CREATE TABLE Message (
    id TEXT PRIMARY KEY NOT NULL,
    content TEXT NOT NULL,
    senderId TEXT NOT NULL,
    timestamp INTEGER NOT NULL,
    isRead INTEGER AS Boolean DEFAULT 0,
    syncStatus TEXT NOT NULL
);

selectAll:
SELECT * FROM Message
ORDER BY timestamp DESC;

selectUnread:
SELECT * FROM Message
WHERE isRead = 0;

insert:
INSERT OR REPLACE INTO Message(id, content, senderId, timestamp, isRead, syncStatus)
VALUES (?, ?, ?, ?, ?, ?);

markAsRead:
UPDATE Message SET isRead = 1 WHERE id = ?;
```

**Usage**:

```kotlin
// commonMain
class MessageRepository(private val database: Database) {
    fun getMessages(): Flow<List<Message>> {
        return database.messageQueries
            .selectAll()
            .asFlow()
            .mapToList()
    }

    suspend fun insertMessage(message: Message) {
        database.messageQueries.insert(
            id = message.id,
            content = message.content,
            senderId = message.senderId,
            timestamp = message.timestamp.toEpochMilliseconds(),
            isRead = message.isRead,
            syncStatus = message.syncStatus.name
        )
    }
}
```

#### Room (Google's KMP Support - Stable 2025)

```kotlin
dependencies {
    implementation("androidx.room:room-runtime:2.7.0")
    ksp("androidx.room:room-compiler:2.7.0")
    implementation("androidx.room:room-ktx:2.7.0")
}
```

**Entities and DAO**:

```kotlin
// commonMain
@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val content: String,
    val senderId: String,
    val timestamp: Long,
    val isRead: Boolean = false
)

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages ORDER BY timestamp DESC")
    fun getAllMessages(): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity)

    @Query("UPDATE messages SET isRead = 1 WHERE id = :messageId")
    suspend fun markAsRead(messageId: String)
}

@Database(entities = [MessageEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
}
```

### Dependency Injection

#### Koin (Kotlin Multiplatform DI)

```kotlin
dependencies {
    implementation("io.insert-koin:koin-core:3.5.0")
    implementation("io.insert-koin:koin-compose:3.5.0")
}
```

**Module Definition**:

```kotlin
// commonMain
val networkModule = module {
    single { ApiClient() }
    single { WebSocketClient() }
    single { WebRTCManager(get()) }
}

val databaseModule = module {
    single { getDatabaseBuilder().build() }
    single { get<AppDatabase>().messageDao() }
}

val repositoryModule = module {
    single { MessageRepository(get(), get(), get()) }
    single { UserRepository(get()) }
}

val viewModelModule = module {
    factory { HomeViewModel(get(), get()) }
    factory { CallViewModel(get(), get()) }
    factory { MessagesViewModel(get()) }
}

val appModules = listOf(
    networkModule,
    databaseModule,
    repositoryModule,
    viewModelModule
)
```

**App Initialization**:

```kotlin
// commonMain
fun initKoin() {
    startKoin {
        modules(appModules)
    }
}

// androidMain - in Application class
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin()
    }
}

// iosMain - in AppDelegate
fun startKoinIOS() {
    initKoin()
}
```

**Usage in Compose**:

```kotlin
@Composable
fun MessagesScreen() {
    val viewModel: MessagesViewModel = get()
    val messages by viewModel.messages.collectAsState()

    // UI implementation
}
```

### Logging

#### Kermit (Multiplatform Logging)

```kotlin
dependencies {
    implementation("co.touchlab:kermit:2.0.0")
}
```

**Usage**:

```kotlin
// commonMain
class WebRTCManager {
    private val logger = Logger.withTag("WebRTCManager")

    suspend fun initiateCall(userId: String) {
        logger.d { "Initiating call to user: $userId" }
        try {
            // Call logic
            logger.i { "Call initiated successfully" }
        } catch (e: Exception) {
            logger.e(e) { "Failed to initiate call" }
        }
    }
}
```

### Serialization

#### kotlinx.serialization

```kotlin
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
}
```

**Example**:

```kotlin
// commonMain
@Serializable
data class Message(
    val id: String,
    val content: String,
    val senderId: String,
    @Serializable(with = InstantSerializer::class)
    val timestamp: Instant,
    val isRead: Boolean = false
)

object InstantSerializer : KSerializer<Instant> {
    override val descriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.LONG)

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeLong(value.toEpochMilliseconds())
    }

    override fun deserialize(decoder: Decoder): Instant {
        return Instant.fromEpochMilliseconds(decoder.decodeLong())
    }
}
```

### Navigation

#### PreCompose (Recommended for KMP)

See State Management section for examples.

#### Compose Navigation (Experimental KMP Support)

```kotlin
dependencies {
    implementation("org.jetbrains.androidx.navigation:navigation-compose:2.9.0")
}
```

**Example**:

```kotlin
// commonMain
@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(navController, startDestination = "home") {
        composable("home") { HomeScreen(navController) }
        composable("messages") { MessagesScreen(navController) }
        composable("call/{userId}") { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: return@composable
            CallScreen(userId, navController)
        }
    }
}
```

### Image Loading

#### Coil (KMP Support)

```kotlin
dependencies {
    implementation("io.coil-kt.coil3:coil-compose:3.0.0")
    implementation("io.coil-kt.coil3:coil-network-ktor:3.0.0")
}
```

**Usage**:

```kotlin
// commonMain
@Composable
fun UserAvatar(imageUrl: String) {
    AsyncImage(
        model = imageUrl,
        contentDescription = "User avatar",
        modifier = Modifier.size(48.dp).clip(CircleShape)
    )
}
```

### Date/Time

#### kotlinx-datetime

```kotlin
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")
}
```

**Usage**:

```kotlin
// commonMain
val now = Clock.System.now()
val timestamp = now.toEpochMilliseconds()
val formatted = now.toLocalDateTime(TimeZone.currentSystemDefault())
    .toString()
```

---

## 7. Production Examples and References

### Official Samples

#### 1. KMP Production Sample (RSS Reader)

**Repository**: https://github.com/Kotlin/kmp-production-sample

**Key Features**:
- Available on App Store and Google Play
- Redux pattern for state management
- Shared business logic in commonMain
- Native UI: SwiftUI (iOS), Jetpack Compose (Android), Compose Multiplatform (Desktop), React.js (Web)

**Architecture Highlights**:
- Simplified Redux in shared module
- Store class for action dispatching
- Async work handling
- State generation

#### 2. Android KMP Samples

**Repository**: https://github.com/android/kotlin-multiplatform-samples

**Projects**:
- **Fruitties**: KMP ViewModel, Room, DataStore, Ktor for data fetching and storage
- **DiceRoller**: DataStore library for preferences

#### 3. VideoSDK KMP Guide

**Full guide**: https://www.videosdk.live/developer-hub/social/kotlin-multiplatform

Comprehensive guide for building video/communication apps with KMP.

### Real-World Production Apps Using KMP

Companies successfully using KMP in production:
- **Google**: Internal apps and libraries
- **Netflix**: Mobile app components
- **McDonald's**: Mobile ordering app
- **Cash App**: Financial features
- **Duolingo**: Language learning platform
- **Forbes**: News app
- **Philips**: Healthcare applications
- **Bolt**: Ride-hailing platform
- **H&M**: E-commerce app
- **Baidu**: Search and media apps

### Community Examples

#### WebRTC Examples

1. **webrtc-kmp-demo**: https://github.com/shepeliev/webrtc-kmp-demo
   - Full WebRTC implementation
   - Android, iOS, JS support
   - Signaling server example

2. **Ktor Video Chat**: Full stack KMP example with Ktor WebRTC support
   - See Ktor roadmap blog: https://blog.jetbrains.com/kotlin/2025/09/ktor-roadmap-2025/

#### Messaging Apps

- **LetsChat**: Sample messaging app with modern Android architecture
  - Kotlin, Coroutines, Flow, Dagger-Hilt, MVVM, Room, DataStore, Firebase
  - Demonstrates messaging patterns

#### Architecture Samples

- **D-KMP Architecture**: Shared KMP ViewModel and Navigation for Compose and SwiftUI
- **Multi-module with Decompose**: Shows scalable architecture patterns

### Learning Resources

#### Official Documentation

1. **Kotlin Multiplatform Docs**: https://kotlinlang.org/docs/multiplatform/
2. **Compose Multiplatform Docs**: https://www.jetbrains.com/help/kotlin-multiplatform-dev/
3. **Android KMP Guide**: https://developer.android.com/kotlin/multiplatform

#### Roadmaps and Updates

1. **KMP Roadmap 2025**: https://blog.jetbrains.com/kotlin/2024/10/kotlin-multiplatform-development-roadmap-for-2025/
2. **Ktor Roadmap 2025**: https://blog.jetbrains.com/kotlin/2025/09/ktor-roadmap-2025/
3. **Google I/O KMP Updates**: https://android-developers.googleblog.com/2025/05/android-kotlin-multiplatform-google-io-kotlinconf-2025.html

#### Tutorials and Guides

1. **VideoSDK KMP Guide**: https://www.videosdk.live/developer-hub/social/kotlin-multiplatform
2. **How to Set Up KMP 2025**: https://www.kmpship.app/blog/how-to-set-up-kotlin-multiplatform-complete-guide-2025
3. **KMP Integration Guide**: https://www.aetherius-solutions.com/blog-posts/kotlin-multiplatform-integration-without-full-migration

#### Community Resources

1. **Kotlin Slack**: #multiplatform channel - https://slack-chats.kotlinlang.org/
2. **KotlinConf**: Annual conference with KMP sessions
3. **droidcon**: Regular KMP articles and talks

### GitHub Topics to Follow

- `kotlin-multiplatform`
- `kotlin-multiplatform-mobile`
- `compose-multiplatform`
- `kotlin-multiplatform-sample`
- `webrtc`

---

## Conclusion

Kotlin Multiplatform in 2025 is production-ready and backed by Google and JetBrains. For communication/VoIP applications:

### Key Takeaways

1. **Start Small**: Begin with isolated features, use thin platform adapters
2. **Leverage Official Support**: Use official libraries (Room, ViewModel, DataStore)
3. **WebRTC**: Use webrtc-kmp or wait for official Ktor WebRTC support (stable soon)
4. **Push Notifications**: KMPNotifier provides comprehensive cross-platform support
5. **State Management**: Official ViewModel + StateFlow is recommended
6. **Architecture**: MVI or Redux patterns work well for real-time apps
7. **Testing**: Set up automated tests to validate shared vs native behavior

### 2025 Ecosystem Highlights

- **Compose Multiplatform for iOS**: Stable
- **Kotlin-to-Swift Export**: Improving (still experimental)
- **Jetpack Libraries**: Room, DataStore, ViewModel, Paging with KMP support
- **Ktor WebRTC**: Preview available, stable coming soon
- **Build Performance**: Continuous improvements to Kotlin/Native

### Next Steps

1. Install Kotlin Multiplatform plugin in Android Studio
2. Create a new KMP project with Compose Multiplatform
3. Start with shared business logic (API client, data models)
4. Add WebRTC integration using webrtc-kmp
5. Implement push notifications with KMPNotifier
6. Use official ViewModel for state management
7. Test on both platforms continuously

---

## Sources

- [Kotlin Multiplatform | Android Developers](https://developer.android.com/kotlin/multiplatform)
- [Create your Compose Multiplatform app | Kotlin Multiplatform Documentation](https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-multiplatform-create-first-app.html)
- [Kotlin Multiplatform in 2025 - Medium](https://medium.com/design-bootcamp/kotlin-multiplatform-in-2025-how-android-developers-can-build-for-ios-too-0b63490d8b1c)
- [Kotlin Multiplatform: The Ultimate Guide (2025) - VideoSDK](https://www.videosdk.live/developer-hub/social/kotlin-multiplatform)
- [How to Set Up Kotlin Multiplatform: Complete Guide 2025](https://www.kmpship.app/blog/how-to-set-up-kotlin-multiplatform-complete-guide-2025)
- [Android Kotlin Multiplatform: Google I/O & KotlinConf 2025](https://android-developers.googleblog.com/2025/05/android-kotlin-multiplatform-google-io-kotlinconf-2025.html)
- [Kotlin Multiplatform Development Roadmap for 2025](https://blog.jetbrains.com/kotlin/2024/10/kotlin-multiplatform-development-roadmap-for-2025/)
- [WebRTC Kotlin Multiplatform SDK - GitHub](https://github.com/shepeliev/webrtc-kmp)
- [Ktor Roadmap and Previews 2025](https://blog.jetbrains.com/kotlin/2025/09/ktor-roadmap-2025/)
- [KMPNotifier - GitHub](https://github.com/mirzemehdi/KMPNotifier)
- [Alarmee: Schedule Notifications in KMP - Medium](https://vivienmahe.medium.com/alarmee-schedule-local-and-push-notifications-in-kmp-44ea47972ae7)
- [Cross-Platform Notifications with KMP](https://www.kmpbits.com/posts/notifications-kmp)
- [Mastering State Management in KMP](https://academy.droidcon.com/course/mastering-state-management-in-kotlin-multiplatform-and-compose-multiplatform)
- [PreCompose - GitHub](https://github.com/Tlaster/PreCompose)
- [Global State Management in Compose Multiplatform with Koin](https://medium.com/@amanshaikh205276/global-state-management-in-compose-multiplatform-with-koin-and-mutablestateof-android-ios-83646c113e68)
- [Common ViewModel | Kotlin Multiplatform Documentation](https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-viewmodel.html)
- [Set up ViewModel for KMP | Android Developers](https://developer.android.com/kotlin/multiplatform/viewmodel)
- [kmp-viewmodel - GitHub](https://github.com/hoc081098/kmp-viewmodel)
- [KMP Production Sample - GitHub](https://github.com/Kotlin/kmp-production-sample)
- [Android Kotlin Multiplatform Samples - GitHub](https://github.com/android/kotlin-multiplatform-samples)

---

**Document Version**: 1.0
**Created**: December 18, 2025
**Last Updated**: December 18, 2025
