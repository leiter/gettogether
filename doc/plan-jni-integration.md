# Get-Together - Jami KMP Application

## Overview
**Get-Together** is a Kotlin Multiplatform communication app built on Jami's decentralized P2P network, featuring shared Compose UI for iOS and Android.

## Key Decisions
- **Integration**: Full Jami Daemon (decentralized, no server required)
- **Accounts**: Jami accounts only (OpenDHT-based)
- **Scope**: Full group support (group chats + conference calls)
- **Platforms**: iOS and Android (Milestone 1)

## Target Features (Milestone 1)
- Audio/video calls (1:1 and group/conference)
- Video toggle during calls
- Text messaging (1:1 and group chats)
- File sharing
- Contact management
- Group creation and management
- Account creation and management

---

## Technical Approach

### Full Jami Daemon Integration
- Build jami-daemon as native library for each platform
- Use JNI wrapper (Android) and Swift/ObjC interop (iOS)
- Full Jami network compatibility via OpenDHT
- Truly decentralized - no server infrastructure required
- Architecture uses expect/actual pattern to abstract platform differences

### Key Jami APIs to Integrate
- **CallManager**: Audio/video calls, conference management
- **ConfigurationManager**: Account creation, settings
- **PresenceManager**: Contact presence/availability
- **VideoManager**: Camera control, video streams
- **ConversationManager**: Messaging, group chats

---

## Proposed Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Compose Multiplatform UI                  │
│              (commonMain - Shared across platforms)          │
├─────────────────────────────────────────────────────────────┤
│                      Presentation Layer                      │
│     androidx.lifecycle ViewModel + StateFlow (MVI)           │
│                    (commonMain)                              │
├─────────────────────────────────────────────────────────────┤
│                       Domain Layer                           │
│              Use Cases + Business Logic                      │
│                    (commonMain)                              │
├─────────────────────────────────────────────────────────────┤
│                        Data Layer                            │
│            Repositories + Data Sources                       │
│                    (commonMain)                              │
├─────────────────────────────────────────────────────────────┤
│                   Jami Bridge Layer                          │
│    expect interface JamiBridge {                             │
│        fun initDaemon()                                      │
│        fun createAccount()                                   │
│        fun makeCall(contactId: String)                       │
│        fun sendMessage(...)                                  │
│    }                                                         │
│                    (commonMain)                              │
├──────────────────────┬──────────────────────────────────────┤
│    androidMain       │           iosMain                     │
│  actual JamiBridge   │     actual JamiBridge                 │
│    (JNI calls)       │    (Swift/ObjC interop)               │
├──────────────────────┴──────────────────────────────────────┤
│                    Jami Daemon (C/C++)                       │
│              Compiled as native library                      │
└─────────────────────────────────────────────────────────────┘
```

---

## Technology Stack

### Core
- **Kotlin**: 2.0.x
- **Compose Multiplatform**: 1.7.x (stable for iOS)
- **Gradle**: 8.x with Version Catalogs

### Shared Libraries
- **Ktor**: 3.3.x - HTTP client, WebSocket
- **Room**: 2.7.x - Local database (KMP-ready)
- **androidx.navigation**: Navigation Compose (KMP-ready)
- **androidx.lifecycle**: ViewModel + StateFlow (KMP-ready)
- **Koin**: 3.5.x - Dependency injection
- **kotlinx.serialization**: JSON parsing
- **kotlinx-datetime**: Date/time handling
- **Kermit**: Logging

### Platform-Specific
- **Android**: JNI bindings to jami-daemon
- **iOS**: Swift/ObjC bindings to jami-daemon, CallKit integration
- **KMPNotifier**: Push notifications

---

## Project Structure

```
jami-kmp/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle/
│   └── libs.versions.toml
│
├── shared/
│   ├── build.gradle.kts
│   └── src/
│       ├── commonMain/
│       │   └── kotlin/
│       │       ├── di/                  # Koin modules
│       │       ├── domain/
│       │       │   ├── model/           # Domain models
│       │       │   ├── repository/      # Repository interfaces
│       │       │   └── usecase/         # Use cases
│       │       ├── data/
│       │       │   ├── repository/      # Repository implementations
│       │       │   ├── local/           # Room DAOs and Database
│       │       │   └── mapper/          # Data mappers
│       │       ├── presentation/
│       │       │   ├── viewmodel/       # androidx.lifecycle ViewModels
│       │       │   └── state/           # UI States (MVI)
│       │       ├── jami/
│       │       │   └── JamiBridge.kt    # expect declarations
│       │       └── ui/
│       │           ├── theme/           # Material3 theme
│       │           ├── navigation/      # Navigation graph
│       │           ├── components/      # Reusable components
│       │           └── screens/
│       │               ├── auth/           # Login, create account
│       │               ├── contacts/       # Contact list, add contact
│       │               ├── conversations/  # Chat list
│       │               ├── chat/           # 1:1 and group messaging
│       │               ├── call/           # Audio/video call UI
│       │               ├── conference/     # Group call UI
│       │               ├── groups/         # Group management
│       │               └── settings/       # App and account settings
│       │
│       ├── androidMain/
│       │   └── kotlin/
│       │       └── jami/
│       │           └── JamiBridge.android.kt  # JNI actual
│       │
│       └── iosMain/
│           └── kotlin/
│               └── jami/
│                   └── JamiBridge.ios.kt      # iOS actual
│
├── androidApp/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── kotlin/
│       │   └── MainActivity.kt
│       ├── jni/                         # JNI C++ bridge
│       └── AndroidManifest.xml
│
├── iosApp/
│   ├── iosApp/
│   │   ├── AppDelegate.swift
│   │   └── ContentView.swift
│   └── iosApp.xcodeproj/
│
├── jami-daemon/                         # Git submodule
│   └── (jami-daemon source)
│
└── doc/
    └── (documentation)
```

---

## Implementation Phases

### Phase 1: Project Setup (Foundation)
- [ ] Initialize KMP project structure with Compose Multiplatform
- [ ] Configure Gradle with version catalogs (libs.versions.toml)
- [ ] Set up Koin dependency injection
- [ ] Set up Room for local storage (KMP)
- [ ] Set up androidx.navigation for Compose navigation
- [ ] Set up androidx.lifecycle ViewModel
- [ ] Create Material3 theme (Get-Together branding)

### Phase 2: Jami Daemon Integration
- [ ] Add jami-daemon as git submodule
- [ ] Configure CMake build for Android (NDK toolchain)
- [ ] Create JNI wrapper layer for Android
- [ ] Configure build for iOS (Xcode integration)
- [ ] Create Swift/ObjC bridge for iOS
- [ ] Define expect/actual JamiBridge interface
- [ ] Implement daemon lifecycle management

### Phase 3: Account Management
- [ ] Account creation flow UI
- [ ] Account import/export (backup/restore)
- [ ] Profile management (display name, avatar)
- [ ] Settings screen (notifications, privacy, about)
- [ ] Account deletion

### Phase 4: Contacts
- [ ] Contact list screen with search
- [ ] Add contact by Jami ID / username
- [ ] Contact details screen
- [ ] Remove/block contacts
- [ ] Contact presence indicators

### Phase 5: 1:1 Messaging
- [ ] Conversation list screen
- [ ] Chat screen with message input
- [ ] Send/receive text messages
- [ ] Message status (sending, sent, delivered, read)
- [ ] File attachment picker
- [ ] File transfer (send/receive with progress)
- [ ] Media preview (images, videos)

### Phase 6: Group Chats
- [ ] Create group conversation UI
- [ ] Group management (add/remove members, rename)
- [ ] Group chat screen
- [ ] Group message sending/receiving
- [ ] Member list and roles
- [ ] Leave group functionality

### Phase 7: 1:1 Calling
- [ ] Initiate audio call
- [ ] Incoming call handling
- [ ] In-call UI (mute, speaker, hang up)
- [ ] Video call support
- [ ] Camera controls (toggle, front/back switch)
- [ ] Android: Foreground service for calls
- [ ] iOS: CallKit integration

### Phase 8: Conference Calls
- [ ] Initiate group/conference call
- [ ] Join ongoing conference
- [ ] Conference UI (participant grid/list)
- [ ] Participant management (mute others if host)
- [ ] Screen layout modes (grid, speaker focus)
- [ ] Video toggle per participant

### Phase 9: Notifications & Polish
- [ ] Push notification setup (FCM for Android, APNs for iOS)
- [ ] Incoming call notifications (full-screen intent)
- [ ] Message notifications with quick reply
- [ ] App icon and splash screen
- [ ] Error handling and offline states
- [ ] Performance optimization
- [ ] Accessibility improvements

---

## Critical Files to Create/Modify

### Shared Module
- `shared/src/commonMain/kotlin/jami/JamiBridge.kt` - Core Jami interface
- `shared/src/commonMain/kotlin/domain/model/*.kt` - Domain models
- `shared/src/commonMain/kotlin/presentation/viewmodel/*.kt` - ViewModels
- `shared/src/commonMain/kotlin/ui/screens/**/*.kt` - UI screens

### Android-Specific
- `shared/src/androidMain/kotlin/jami/JamiBridge.android.kt` - JNI actual
- `androidApp/src/main/jni/*.cpp` - JNI bridge code
- `androidApp/CMakeLists.txt` - Native build config

### iOS-Specific
- `shared/src/iosMain/kotlin/jami/JamiBridge.ios.kt` - iOS actual
- `iosApp/iosApp/JamiBridge.swift` - Swift bridge

---

## Risks & Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Complex native build | High | Start with Android first, leverage existing build scripts from jami-client-android |
| iOS interop complexity | Medium | Study jami-client-ios Swift bridge implementation |
| Large binary size | Medium | Strip unused features, use ProGuard/R8, split APKs by architecture |
| Daemon API changes | Medium | Pin specific jami-daemon version tag |
| Conference call complexity | High | Implement 1:1 calls first, extend to conferences |

---

## Build Requirements

### Development Environment
- Android Studio Hedgehog or later (KMP plugin)
- Xcode 15+ (for iOS builds)
- CMake 3.16+
- Android NDK 25+
- SWIG 4.2+ (for JNI generation)
- Python 3.7+
- CocoaPods or Swift Package Manager (iOS dependencies)

### Daemon Dependencies
- GnuTLS, OpenSSL (cryptography)
- FFmpeg (media processing)
- pjsip (SIP stack)
- OpenDHT (distributed hash table)

---

## Next Steps

1. ✅ Architecture approach confirmed (Full Jami Daemon)
2. ✅ Scope confirmed (Full group support)
3. ✅ App name confirmed (Get-Together)
4. Begin Phase 1: Initialize project structure
5. Clone and study jami-daemon build process
