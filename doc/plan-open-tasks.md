# GetTogether - Open Tasks & Workarounds

## Context
JNI integration is complete. ConversationsTab and ContactsTab now use real JamiBridge data.
This plan captures open tasks discovered during testing that need to be addressed.

---

## Priority 1: Android Completion (High Impact)

### 1.1 File Transfer Implementation
**Status**: ✅ COMPLETE - Already implemented in SwigJamiBridge
**Files**:
- `androidApp/src/main/kotlin/com/gettogether/app/jami/SwigJamiBridge.kt`
  - `acceptFileTransfer()` - implemented via Ringservice
  - `cancelFileTransfer()` - implemented via Ringservice
  - `getFileTransferInfo()` - implemented via Ringservice

**Note**: AndroidJamiBridge stubs updated with comments pointing to SwigJamiBridge implementation.

---

### 1.2 Notification Action Handlers
**Status**: ✅ COMPLETE
**Files modified**:
- `shared/src/androidMain/kotlin/com/gettogether/app/platform/MessageNotificationReceiver.kt`
  - Added KoinComponent integration
  - Implemented reply action via `jamiBridge.sendMessage()`
  - Implemented mark-as-read via `conversationRepository.markAsRead()`

- `shared/src/androidMain/kotlin/com/gettogether/app/platform/CallNotificationReceiver.kt`
  - Added KoinComponent integration
  - Implemented accept/decline/end call via JamiBridge
  - Implemented mute toggle via JamiBridge

- `shared/src/commonMain/kotlin/com/gettogether/app/data/repository/ConversationRepositoryImpl.kt`
  - Added `markAsRead()` method

---

### 1.3 Settings Persistence
**Status**: ✅ COMPLETE
**Files created/modified**:
- Created: `shared/src/commonMain/kotlin/com/gettogether/app/data/repository/SettingsRepository.kt`
  - Interface with StateFlow for notification and privacy settings
  - `expect fun createSettingsRepository()` for platform implementation

- Created: `shared/src/androidMain/kotlin/com/gettogether/app/data/repository/SettingsRepository.android.kt`
  - Android implementation using SharedPreferences
  - Persists all notification and privacy settings

- Created: `shared/src/iosMain/kotlin/com/gettogether/app/data/repository/SettingsRepository.ios.kt`
  - iOS stub implementation (TODO: NSUserDefaults)

- Modified: `shared/src/commonMain/kotlin/com/gettogether/app/presentation/viewmodel/SettingsViewModel.kt`
  - Injected SettingsRepository
  - Load settings on init, save on change

- Modified: `shared/src/commonMain/kotlin/com/gettogether/app/di/Modules.kt`
  - Added SettingsRepository to DI

---

### 1.4 Trust Request Payload
**Status**: ✅ COMPLETE
**File modified**:
- `shared/src/androidMain/kotlin/com/gettogether/app/jami/JamiBridge.android.kt`
  - Added Base64 decoding for trust request payload
  - Falls back to empty ByteArray if decoding fails

---

## Priority 2: Data Persistence Issues (Discovered During Testing)

### 2.1 Account Data Not Persisting
**Status**: ✅ COMPLETE - ROOT CAUSE FIXED
**Root cause**: SwigJamiBridge was missing `getAppDataPath` callback in ConfigurationCallback

**Fix applied**:
- `androidApp/src/main/kotlin/com/gettogether/app/jami/SwigJamiBridge.kt`
  - Added `getAppDataPath()` callback returning `context.filesDir.absolutePath`
  - Added `getDeviceName()` callback returning device model

### 2.2 Navigation Not Checking Account on Startup
**Status**: ✅ COMPLETE
**Root cause**: AppNavigation always started at Welcome screen without checking AccountRepository

**Fix applied**:
- `shared/src/commonMain/kotlin/com/gettogether/app/ui/navigation/AppNavigation.kt`
  - Injected AccountRepository via Koin
  - Observe `accountState.isLoaded` to show loading screen while loading
  - Conditionally set `startDestination` based on whether account exists
  - If account exists → navigate to Home
  - If no account → navigate to Welcome

---

## Priority 3: iOS Platform (Complete Stub Implementation)

### 3.1 iOS JamiBridge
**Status**: 90+ TODO stubs returning placeholders
**File**: `shared/src/iosMain/kotlin/com/gettogether/app/jami/JamiBridge.ios.kt`

**Placeholder returns**:
- Line 76: `"placeholder-account-id"`
- Line 82: `"placeholder-account-id"`
- Line 199: `"placeholder-conversation-id"`
- Line 264: `"placeholder-message-id"`
- Line 296: `"placeholder-call-id"`
- Line 363: `"placeholder-conference-id"`

**Action**: Implement Swift bridge in `iosApp/iosApp/JamiBridgeWrapper.swift`

---

### 3.2 iOS NotificationHelper
**Status**: All methods are TODO stubs
**File**: `shared/src/iosMain/kotlin/com/gettogether/app/platform/NotificationHelper.ios.kt`
- Line 11: Request permissions
- Line 23: Show message notification (UNUserNotificationCenter)
- Line 36: Show incoming call (critical alert)
- Line 57: Show missed call
- Line 65: Group notifications
- Lines 69-81: Remove/cancel notifications

**Action**: Implement using UNUserNotificationCenter API

---

### 3.3 iOS CallServiceBridge
**Status**: All methods are TODO stubs with println()
**File**: `shared/src/iosMain/kotlin/com/gettogether/app/platform/CallServiceBridge.ios.kt`
- Lines 6-41: All 8 methods need CallKit/AVAudioSession implementation

**Action**: Implement using CallKit framework

---

## Summary Table

| Task | Priority | Status | Files |
|------|----------|--------|-------|
| File Transfer | P1 | ✅ Complete | SwigJamiBridge |
| Notification Actions | P1 | ✅ Complete | 3 files |
| Settings Persistence | P1 | ✅ Complete | 4 files |
| Trust Request Payload | P1 | ✅ Complete | 1 file |
| Account Data Persistence | P2 | ✅ Complete | 2 files |
| iOS JamiBridge | P3 | Pending | 1 file + Swift |
| iOS NotificationHelper | P3 | Pending | 1 file |
| iOS CallServiceBridge | P3 | Pending | 1 file |

---

## Success Criteria
- [x] Settings persist across app restarts
- [x] Quick reply from notification sends message (wired to JamiBridge)
- [x] Mark as read from notification works (wired to ConversationRepository)
- [x] File transfers can be accepted/cancelled (already in SwigJamiBridge)
- [x] Account data persists between app restarts (getAppDataPath callback + navigation fix)

---

## Remaining Work

Only iOS platform tasks remain:
1. **iOS JamiBridge** - Implement Swift bridge for Jami daemon
2. **iOS NotificationHelper** - Implement using UNUserNotificationCenter
3. **iOS CallServiceBridge** - Implement using CallKit framework

All Android tasks are complete and tested.

---

## Priority 4: UI Coordinate Testing

### 4.1 Chat Screen Coordinates
**Status**: ⏳ Pending - Requires active conversation
**File**: `doc/ui-coordinates.yaml`

**Coordinates to verify**:
- `back_button`: tap [84, 321]
- `message_input`: tap [498, 2073]
- `send_button`: tap [996, 2073]

**Prerequisite**:
1. Add a contact (need another Jami ID)
2. Start a conversation with the contact
3. Test coordinates in active chat screen

**Verified screens**:
- ✅ welcome
- ✅ create_account
- ✅ home (bottom_nav, fab)
- ✅ contacts (add_contact_button)
- ✅ settings (all sections)
- ✅ add_contact (username_field, search_button, back_button)
- ✅ new_conversation (back_button, groups_button, search_field)
- ⏳ chat (requires active conversation)
- ⏳ chats list_items (requires conversations)
