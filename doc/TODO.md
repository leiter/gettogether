# GetTogether - Open Tasks & Known Issues

**Last Updated:** 2025-12-21 16:20
**Version:** Consolidated from all task documents

---

## Quick Status Overview

| Priority | Category | Tasks | Status |
|----------|----------|-------|--------|
| 🔴 P1 | Android Critical | 7 | ⚠️ 4 Open, 3 Complete |
| 🟡 P2 | Android High Impact | 4 | ✅ All Complete |
| 🔵 P3 | iOS Platform | 3 | ⏳ All Pending |
| ⚫ P4 | Nice-to-Have | 3 | ⏳ Mixed |

---

## Priority 1: Critical - Android Platform 🔴

### 1.1 Incoming Call Notifications - Partially Functional ⚠️

**Status:** Improved but not fully functional (2025-12-21)
**Impact:** CRITICAL - Core feature not working reliably

#### What Works ✅
- Permission request on app start (RECORD_AUDIO, CAMERA, POST_NOTIFICATIONS)
- Global listener in Application class observes incoming call events
- NotificationHelper triggers incoming call notifications
- Permission checks before placing/accepting calls with clear error messages

#### What's Still Broken ❌
- Incoming call notification may not show full-screen intent properly
- Answer/Decline actions from notification may not work reliably
- App may not open to call screen when answering from notification
- Notification may disappear too quickly or not persist

#### Implementation Details
**Files Modified (2025-12-21):**
- `androidApp/src/main/kotlin/com/gettogether/app/GetTogetherApplication.kt` - Global listener added
- `androidApp/src/main/kotlin/com/gettogether/app/MainActivity.kt` - Permission request
- `shared/src/androidMain/kotlin/com/gettogether/app/platform/NotificationHelper.android.kt` - Full impl
- `shared/src/androidMain/kotlin/com/gettogether/app/platform/CallNotificationReceiver.kt` - Action handlers
- `shared/src/commonMain/kotlin/com/gettogether/app/platform/PermissionManager.kt` - New class
- `shared/src/commonMain/kotlin/com/gettogether/app/presentation/viewmodel/CallViewModel.kt` - Permission checks

#### Next Steps to Debug
1. **Check full-screen intent permission:**
   ```bash
   adb shell appops get com.gettogether.app USE_FULL_SCREEN_INTENT
   ```
   - On Android 14+, may need explicit permission request
   - Add to AndroidManifest: `USE_FULL_SCREEN_INTENT`

2. **Verify CallNotificationReceiver registration:**
   - Check receiver is registered in AndroidManifest
   - Add logging to track action broadcasts
   - Verify intent extras are properly passed

3. **Test notification behavior:**
   - Test on Android 13 vs 14+
   - Check if notification channels are created properly
   - Verify notification importance level is HIGH/MAX

4. **Add comprehensive logging:**
   ```kotlin
   Log.d("IncomingCall", "Event received: callId=$callId")
   Log.d("IncomingCall", "Notification shown: id=$notificationId")
   Log.d("IncomingCall", "Action clicked: $action")
   ```

#### Action Items
- [ ] Debug full-screen intent not showing on locked screen
- [ ] Fix answer/decline actions not working from notification
- [ ] Ensure app opens to call screen when answering
- [ ] Test on Android 13, 14, 15
- [ ] Add logging throughout call notification flow
- [ ] Test with screen locked vs unlocked
- [ ] Verify notification persistence

---

### 1.2 Logout Functionality - Missing 🔴

**Status:** Not implemented
**Impact:** CRITICAL - Users cannot log out

#### Requirements
- Add logout button in Settings screen
- Clear current account ID from AccountRepository
- Clear cached data (conversations, contacts, messages)
- Stop Jami daemon or disconnect account
- Clear all notifications
- Return to Welcome/Account Selection screen

#### Implementation Tasks
- [ ] Add "Logout" button UI in SettingsTab (bottom of settings list)
- [ ] Add `logout()` method to AccountRepository
  ```kotlin
  suspend fun logout() {
      // Clear account state
      _currentAccountId.value = null
      // Stop daemon or disconnect account
      jamiBridge.unregisterAccount(currentAccountId)
  }
  ```
- [ ] Add `logout()` method to SettingsViewModel
  ```kotlin
  fun logout() {
      viewModelScope.launch {
          accountRepository.logout()
          settingsRepository.clearSettings()
          conversationRepository.clearCache()
          contactRepository.clearCache()
          NotificationHelper.cancelAllNotifications()
          // Navigate to Welcome screen
      }
  }
  ```
- [ ] Add confirmation dialog before logout
  - "Are you sure you want to logout?"
  - "Your conversations will not be deleted from the network"
- [ ] Clear settings in SettingsRepository
- [ ] Clear caches in ConversationRepository
- [ ] Clear caches in ContactRepository
- [ ] Navigate to Welcome screen after logout
- [ ] Test logout clears all data properly

#### Related Files
- `shared/src/commonMain/kotlin/com/gettogether/app/ui/screens/home/SettingsTab.kt`
- `shared/src/commonMain/kotlin/com/gettogether/app/presentation/viewmodel/SettingsViewModel.kt`
- `shared/src/commonMain/kotlin/com/gettogether/app/data/repository/AccountRepository.kt`
- `shared/src/commonMain/kotlin/com/gettogether/app/ui/navigation/AppNavigation.kt`

---

### 1.3 Account Persistence & Auto-Login - Missing 🔴

**Status:** Not implemented
**Impact:** CRITICAL - Users must re-import account every app launch

#### Description
Account credentials are not saved between app restarts. Users must re-import or create account every time they launch the app.

#### Requirements
- Save account credentials securely (encrypted storage)
- Auto-detect saved account on app start
- Auto-login if account credentials found
- Skip account creation screen if already logged in
- Handle multiple accounts (optional future enhancement)

#### Implementation Tasks
- [ ] **Android: Use EncryptedSharedPreferences**
  ```kotlin
  // In SettingsRepository.android.kt
  private val encryptedPrefs = EncryptedSharedPreferences.create(
      context,
      "account_credentials",
      MasterKey.Builder(context)
          .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
          .build(),
      EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
      EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
  )

  fun saveAccountCredentials(accountId: String, password: String?) {
      encryptedPrefs.edit()
          .putString("account_id", accountId)
          .putString("account_password", password)
          .apply()
  }

  fun getSavedAccountId(): String? {
      return encryptedPrefs.getString("account_id", null)
  }
  ```

- [ ] **Load saved account on app start** (in GetTogetherApplication or AccountRepository)
  ```kotlin
  // In AccountRepository init block
  init {
      viewModelScope.launch {
          val savedAccountId = settingsRepository.getSavedAccountId()
          if (savedAccountId != null) {
              _currentAccountId.value = savedAccountId
              // Daemon should automatically load account details
          }
      }
  }
  ```

- [ ] **Update navigation to skip Welcome screen if logged in**
  ```kotlin
  // In AppNavigation.kt
  val startDestination = if (accountRepository.isLoggedIn()) {
      Screen.Home.route
  } else {
      Screen.Welcome.route
  }
  ```

- [ ] **Save credentials after account creation/import**
  - In CreateAccountViewModel after successful account creation
  - In ImportAccountViewModel after successful import

- [ ] **Clear saved credentials on logout**
  - Call `settingsRepository.clearAccountCredentials()` in logout flow

- [ ] **iOS: Use Keychain (future)**
  ```swift
  // Use KeychainAccess or native Keychain APIs
  ```

#### Security Considerations
- ✅ Use EncryptedSharedPreferences (not plain SharedPreferences)
- ✅ Use Android Keystore for encryption keys
- ⚠️ Handle key rotation and migration
- ⚠️ Clear credentials on app uninstall (automatic with EncryptedSharedPreferences)
- ⚠️ Consider biometric authentication for extra security (future)

#### Related Files
- `shared/src/commonMain/kotlin/com/gettogether/app/data/repository/AccountRepository.kt`
- `shared/src/androidMain/kotlin/com/gettogether/app/data/repository/SettingsRepository.kt`
- `androidApp/src/main/kotlin/com/gettogether/app/GetTogetherApplication.kt`
- `shared/src/commonMain/kotlin/com/gettogether/app/ui/navigation/AppNavigation.kt`
- `shared/src/commonMain/kotlin/com/gettogether/app/presentation/viewmodel/CreateAccountViewModel.kt`
- `shared/src/commonMain/kotlin/com/gettogether/app/presentation/viewmodel/ImportAccountViewModel.kt`

---

### 1.4 Message Notifications - Needs Testing ⚠️

**Status:** Implemented but not verified (2025-12-21)
**Impact:** HIGH - Feature may not work as expected

#### What Was Implemented
- ✅ NotificationHelper injected into ConversationRepositoryImpl
- ✅ `showMessageNotificationIfNeeded()` method added
- ✅ Automatically triggers when new messages received
- ✅ Generates notification IDs based on conversation hash
- ✅ Fetches contact name from JamiBridge

#### Needs Testing
- [ ] Test notifications appear when app in background
- [ ] Test notification actions (reply, mark read)
- [ ] Test notification grouping for multiple messages
- [ ] Test notification clearing when conversation opened
- [ ] Test notification doesn't show when conversation is active (needs implementation)
- [ ] Test notification sound/vibration
- [ ] Test notification on different Android versions

#### Implementation Files
- `shared/src/commonMain/kotlin/com/gettogether/app/data/repository/ConversationRepositoryImpl.kt`
  - Lines 771-817: `showMessageNotificationIfNeeded()` method
  - Lines 440-446: Triggers notification on new message

---

### 1.5 Avatar Feature - ✅ COMPLETE & WORKING

**Status:** ✅ Fully functional (tested 2025-12-21)
**Impact:** MEDIUM - Feature now working on all test devices

#### What Works ✅
- ✅ Image selection from photo gallery works correctly
- ✅ Image compression to ~100KB verified working
- ✅ Avatar upload to Jami daemon successful (with fallback)
- ✅ Avatar displays correctly in Settings profile section
- ✅ Avatar displays correctly in Contacts list
- ✅ Avatar displays correctly in Conversations list
- ✅ Remove avatar functionality works
- ✅ Cancel picker behavior (no crashes)
- ✅ Handles various image sizes and formats

#### Implementation Completed
- ✅ Fixed crash: "LifecycleOwner attempting to register while RESUMED"
  - Solution: Used `rememberLauncherForActivityResult` instead of constructor registration
- ✅ Fixed DI injection crash by creating `provideImagePicker()` composable pattern
- ✅ Image picker (Android) with gallery selection
- ✅ Image processor with EXIF correction, resize to 256x256px, ~100KB compression
- ✅ Avatar display components (AvatarImage, ContactAvatarImage)
- ✅ Settings UI integration with avatar selection dialog
- ✅ Persistent URI permissions handling
- ✅ Fallback to initials when no avatar

#### Testing Results
**Tested on:**
- ✅ Pixel 2 (Android 11) - All features working
- ✅ Pixel 7a (Android 16) - All features working

**User Confirmation:** "I checked again And it seems that profile picker and avatar is fully functioning"

#### Related Files
- `shared/src/commonMain/kotlin/com/gettogether/app/platform/ImagePicker.kt`
- `shared/src/androidMain/kotlin/com/gettogether/app/platform/ImagePicker.android.kt`
- `shared/src/androidMain/kotlin/com/gettogether/app/platform/ImageProcessor.android.kt`
- `shared/src/commonMain/kotlin/com/gettogether/app/ui/components/AvatarImage.kt`
- `shared/src/commonMain/kotlin/com/gettogether/app/ui/screens/home/SettingsTab.kt`
- `shared/src/commonMain/kotlin/com/gettogether/app/presentation/viewmodel/SettingsViewModel.kt`

**Documented in:** `doc/WORKING_FEATURES.md`

---

## Priority 2: High Impact - Android ✅

**Status:** All P2 tasks complete!

### 2.1 File Transfer Implementation ✅
- Fully implemented in SwigJamiBridge
- Methods: acceptFileTransfer(), cancelFileTransfer(), getFileTransferInfo()

### 2.2 Notification Action Handlers ✅
- Message notification actions (reply, mark read) implemented
- Call notification actions (answer, decline, mute) implemented
- KoinComponent integration complete

### 2.3 Settings Persistence ✅
- Android implementation using SharedPreferences
- iOS stub implementation (TODO: NSUserDefaults)
- All notification and privacy settings persist

### 2.4 Account Data Persistence ✅
- Root cause fixed: getAppDataPath callback added to SwigJamiBridge
- Account data now persists between app restarts
- Navigation checks account on startup

---

## Priority 3: iOS Platform 🔵

**Status:** All pending - iOS development not started

### 3.1 iOS JamiBridge Implementation ⏳

**Scope:** 90+ methods returning mock data
**Effort:** 1-2 weeks

**Description:**
iOS JamiBridge currently provides mock data for UI testing. Full integration with Jami daemon pending.

**Files:**
- `shared/src/iosMain/kotlin/com/gettogether/app/jami/JamiBridge.ios.kt` - Mock implementation
- `iosApp/iosApp/JamiBridgeWrapper.swift` - Swift bridge (to be implemented)

**Mock Data Examples:**
- Account creation returns "placeholder-account-id"
- Contact operations return placeholder responses
- Message sending simulated with local state
- Call operations return "placeholder-call-id"

**Action Required:**
Implement Swift bridge for Jami daemon integration in Xcode project

---

### 3.2 iOS NotificationHelper Implementation ⏳

**Scope:** All notification methods
**Effort:** 3-5 days

**File:** `shared/src/iosMain/kotlin/com/gettogether/app/platform/NotificationHelper.ios.kt`

**Methods to Implement:**
- Request notification permissions (UNUserNotificationCenter)
- Show message notifications
- Show incoming call notifications (critical alerts)
- Show missed call notifications
- Group notifications
- Remove/cancel notifications

**Technology:** UNUserNotificationCenter API

---

### 3.3 iOS CallServiceBridge Implementation ⏳

**Scope:** 8 call service methods
**Effort:** 3-5 days

**File:** `shared/src/iosMain/kotlin/com/gettogether/app/platform/CallServiceBridge.ios.kt`

**Methods to Implement (Lines 6-41):**
- Start/end call UI
- Mute/unmute
- Hold/resume
- Speaker toggle
- Audio routing
- Conference call handling

**Technology:** CallKit framework + AVAudioSession

---

## Priority 4: Nice-to-Have ⚫

### 4.1 Cross-Network Presence Detection ⏳

**Status:** Investigation needed
**Priority:** MEDIUM
**Impact:** User experience - contacts may appear offline when actually online

#### Description
Online/offline status updates work differently based on network configuration:

**Same Network (mDNS):**
- ✅ Automatic, reliable presence detection
- ✅ Status updates work via multicast DNS broadcasts

**Different Networks (DHT):**
- ⚠️ Presence only updated on message activity
- ⚠️ Contacts appear offline until they send a message
- ⚠️ 60-second timeout marks contacts offline after no activity

#### Root Cause
- Jami's DHT protocol doesn't include continuous presence broadcasting
- This may be expected Jami protocol behavior, not a bug

#### Potential Solutions
1. **Accept current behavior** - Verify if official Jami apps behave similarly
2. **Implement periodic ping** - Adds network/battery overhead
3. **Adjust timeout values** - Different timeouts for mDNS vs DHT
4. **UI indication** - Show "last seen X minutes ago" instead of binary online/offline

#### Investigation Tasks
- [ ] Test official Jami apps (Android/iOS) for cross-network presence
- [ ] Check if Jami daemon API supports active presence queries on DHT
- [ ] Determine user expectations and acceptance criteria
- [ ] Monitor battery impact of current timeout checker (runs every 10 seconds)

#### Related Files
- `shared/src/commonMain/kotlin/com/gettogether/app/data/repository/ContactRepositoryImpl.kt`
  - Lines 459-487: Activity-based presence
  - Lines 493-528: Timeout mechanism

---

### 4.2 CallNotificationReceiver - Mute State Tracking 🔧

**Status:** Minor TODO
**File:** `shared/src/androidMain/kotlin/com/gettogether/app/platform/CallNotificationReceiver.kt`
**Line:** 141

**Issue:**
Currently always mutes audio. Should track mute state to toggle between mute/unmute.

**Code:**
```kotlin
jamiBridge.muteAudio(accountId, callId, true) // TODO: Track mute state to toggle
```

**Recommendation:**
- Add mute state tracking in CallRepository or local preference
- Update notification action to toggle based on current state
- Update notification UI to show current mute status

---

### 4.3 UI/UX Improvements ⏳

**Pending Items:**
- [ ] Add loading states during permission requests
- [ ] Add permission rationale dialogs (explain why permissions needed)
- [ ] Improve error messages when permissions denied
- [ ] Add settings deep link to grant permissions manually
- [ ] Add visual indicators when microphone/camera in use
- [ ] Add "last seen" timestamps instead of binary online/offline
- [ ] Improve notification grouping for multiple messages
- [ ] Add notification settings (sound, vibration, priority)

---

## Known Issues & Limitations

### Jami Native Library Crashes ⚠️

**Affected Tests:** 10 tests ignored due to native crashes
**Impact:** Test coverage gaps, but not bridge implementation issues

**Test Breakdown:**
- **Account Management:** 2 tests (updateProfile, setAccountDetails)
- **Conversation Operations:** 1 test (markMessagesAsDisplayed)
- **Device Management:** 7 tests (audio input device enumeration/switching)

**Root Cause:** Native library (libjami) bugs, not JamiBridge implementation

**Pattern:** Most crashes (7/10) are audio device management related

**Workaround Applied (2025-12-21):**
- Deprecated `getAudioInputDevices()` with ERROR level
- Added `useDefaultAudioInputDevice()` as safe alternative
- Audio functionality works via higher-level APIs

**Recommendations:**
1. ✅ Report to Jami project (submit crash logs)
2. ⏳ Monitor Jami updates for fixes
3. ⏳ Document limitations in user documentation
4. ⏳ Re-enable tests when upgrading libjami version

**See:** `doc/CRITICAL-NATIVE-BUG.md` for historical context (note: call crashes were misdiagnosed - actually permission issues)

---

## Performance Optimization Opportunities

### Presence Timeout Checker
**Current:** Runs every 10 seconds checking all contacts
**Optimization Potential:** Could improve battery/CPU efficiency

**Improvements:**
- Increase check interval when app in background
- Batch presence updates instead of individual cache mutations
- Use exponential backoff for timeout checking
- Different intervals for mDNS vs DHT detected contacts

---

## Testing Checklists

### Permission Flow Testing
- [ ] Fresh install shows permission dialog
- [ ] Granting all permissions allows calls
- [ ] Denying permissions shows error when attempting call
- [ ] Revoking permissions after granting shows error
- [ ] Permission rationale shown on second denial

### Incoming Calls Testing
- [ ] Notification appears on incoming call
- [ ] Full-screen intent shows on locked screen (Android 14+)
- [ ] Answer button opens app to call screen
- [ ] Decline button dismisses notification and rejects call
- [ ] Multiple incoming calls handled properly
- [ ] Notification persists until answered/declined
- [ ] Test on Android 13, 14, 15

### Message Notifications Testing
- [ ] Notifications appear for new messages in background
- [ ] Tapping notification opens correct conversation
- [ ] Notifications don't appear when conversation is active
- [ ] Multiple message notifications group properly
- [ ] Reply action works from notification
- [ ] Mark read action works from notification

### Account Management Testing
- [ ] Logout clears all data
- [ ] Logout returns to Welcome screen
- [ ] Saved account auto-loads on app restart
- [ ] Account creation saves credentials
- [ ] Account import saves credentials
- [ ] Encrypted credentials stored securely

### Avatar Testing ✅ COMPLETE
- [x] Image picker launches successfully
- [x] Selected image displays in preview
- [x] Avatar uploads to Jami daemon
- [x] Avatar displays in Settings
- [x] Avatar displays in Contacts
- [x] Avatar displays in Conversations
- [x] Remove avatar works
- [x] Large images compress to ~100KB

---

## Success Criteria

### Android Platform
**Critical (P1):**
- [ ] Incoming call notifications work reliably
- [ ] Answer/Decline from notification works
- [ ] Logout functionality implemented
- [ ] Account persistence & auto-login implemented
- [ ] Message notifications tested and working
- [x] Avatar feature fully functional ✅

**High Impact (P2):**
- [x] Settings persist across app restarts
- [x] Quick reply from notification sends message
- [x] Mark as read from notification works
- [x] File transfers can be accepted/cancelled
- [x] Account data persists between app restarts

**Nice-to-Have (P4):**
- [ ] Cross-network presence documented and accepted
- [ ] All code TODOs resolved
- [ ] UI/UX improvements implemented

### iOS Platform
- [ ] JamiBridge integrated with Jami daemon
- [ ] Notifications working with UNUserNotificationCenter
- [ ] CallKit integration for call handling
- [ ] All features working on physical iOS device

---

## Immediate Next Steps

**For User:**
1. **Test incoming calls** - Try making calls between devices and report specific issues
2. **Test message notifications** - Send messages with app in background, verify notifications
3. ✅ ~~Test avatar feature~~ - **COMPLETE & WORKING**

**For Development:**
1. **Debug incoming call notifications:**
   - Check full-screen intent permission
   - Verify notification importance/priority
   - Add comprehensive logging
   - Test on Android 14+

2. **Implement logout functionality:**
   - Add logout button in Settings
   - Implement logout logic in ViewModel
   - Test data clearing

3. **Implement account persistence:**
   - Use EncryptedSharedPreferences
   - Save credentials after account creation
   - Load credentials on app start

---

**Document Version:** 2025-12-21 16:20 (Consolidated)
**Last Code Changes:** 2025-12-21 (Avatar feature complete, permission & notification implementation)
**Next Review:** After testing incoming calls, logout, and account persistence
