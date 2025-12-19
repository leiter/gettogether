# GetTogether - Project Description & Manual Testing Guide

## Table of Contents
1. [Project Overview](#project-overview)
2. [Architecture Summary](#architecture-summary)
3. [Feature Areas](#feature-areas)
4. [Screen-by-Screen Reference](#screen-by-screen-reference)
5. [Manual Testing Use Cases](#manual-testing-use-cases)
6. [Platform Status](#platform-status)

---

## Project Overview

**GetTogether** is a secure, decentralized communication application built on the Jami daemon. It provides end-to-end encrypted messaging, voice calls, video calls, and conference calls without relying on centralized servers.

### Key Characteristics
- **Decentralized**: Uses OpenDHT (Distributed Hash Table) for peer discovery
- **End-to-End Encrypted**: All communications are encrypted by default
- **No Server Required**: Peer-to-peer architecture, no central infrastructure
- **Cross-Platform**: Kotlin Multiplatform with shared UI via Compose Multiplatform

### Technology Stack
| Component | Technology |
|-----------|------------|
| Language | Kotlin 2.0.x |
| UI Framework | Compose Multiplatform 1.7.x |
| Architecture | MVI (Model-View-Intent) with StateFlow |
| DI | Koin 3.5.x |
| Navigation | androidx.navigation (KMP) |
| Native Bridge | JNI/SWIG (Android), Swift interop (iOS) |
| Backend | Jami Daemon (C/C++) |

### Target Features (Milestone 1)
- Account creation and management
- Contact management with trust requests
- 1:1 text messaging
- 1:1 audio/video calls
- Group/conference calls
- File sharing
- Push notifications with quick actions

---

## Architecture Summary

```
┌─────────────────────────────────────────────────────────────┐
│                    Compose Multiplatform UI                  │
│              (commonMain - Shared across platforms)          │
├─────────────────────────────────────────────────────────────┤
│                      Presentation Layer                      │
│          ViewModels + StateFlow (MVI Pattern)                │
├─────────────────────────────────────────────────────────────┤
│                       Domain Layer                           │
│              Models + Repository Interfaces                  │
├─────────────────────────────────────────────────────────────┤
│                        Data Layer                            │
│         Repository Implementations + Data Sources            │
├─────────────────────────────────────────────────────────────┤
│                   JamiBridge Interface                       │
│        expect interface with 70+ methods                     │
├──────────────────────┬──────────────────────────────────────┤
│    androidMain       │           iosMain                     │
│  SwigJamiBridge      │     iOS Bridge (stub)                 │
│    (JNI/SWIG)        │    (Swift interop)                    │
├──────────────────────┴──────────────────────────────────────┤
│                    Jami Daemon (C/C++)                       │
│              Compiled as native library                      │
└─────────────────────────────────────────────────────────────┘
```

### Data Flow
1. **User Action** → UI Component
2. **UI Component** → ViewModel function call
3. **ViewModel** → Repository/JamiBridge call
4. **JamiBridge** → Native daemon via JNI
5. **Daemon Response** → Event stream (SharedFlow)
6. **Event** → ViewModel state update
7. **State Change** → UI recomposition

---

## Feature Areas

### 1. Authentication & Account Management
| Feature | Status | Description |
|---------|--------|-------------|
| Create Account | ✅ Android | Creates local Jami account with display name |
| Import from Archive | ✅ Android | Restore account from backup file |
| Import from PIN | ✅ Android | Restore account using account PIN |
| Export Account | ✅ Android | Backup account to archive file |
| Delete Account | ✅ Android | Remove account and sign out |
| Profile Update | ✅ Android | Change display name |
| Account Persistence | ✅ Android | Account survives app restart |

### 2. Contact Management
| Feature | Status | Description |
|---------|--------|-------------|
| Contact List | ✅ Android | View all contacts with online status |
| Add Contact | ✅ Android | Search by username/Jami ID |
| Remove Contact | ✅ Android | Remove from contact list |
| Block Contact | ✅ Android | Block unwanted contacts |
| Trust Requests | ✅ Android | Accept/decline incoming requests |
| Online Presence | ✅ Android | See contact online/offline status |

### 3. Messaging
| Feature | Status | Description |
|---------|--------|-------------|
| Conversation List | ✅ Android | View all conversations |
| Send Message | ✅ Android | Send text messages |
| Receive Message | ✅ Android | Real-time message reception |
| Message Status | ✅ Android | Sending/Sent/Delivered/Read indicators |
| Typing Indicator | ✅ Android | Show when contact is typing |
| Mark as Read | ✅ Android | Mark conversation as read |

### 4. Calling
| Feature | Status | Description |
|---------|--------|-------------|
| Audio Call | ✅ Android | 1:1 voice calls |
| Video Call | ✅ Android | 1:1 video calls |
| Incoming Call UI | ✅ Android | Accept/decline interface |
| In-Call Controls | ✅ Android | Mute, speaker, video toggle |
| Camera Switch | ✅ Android | Front/back camera switching |
| Call Duration | ✅ Android | Timer during active call |

### 5. Conference Calls
| Feature | Status | Description |
|---------|--------|-------------|
| Create Conference | ✅ Android | Start group call with 2+ contacts |
| Join Conference | ✅ Android | Join existing conference |
| Participant Grid | ✅ Android | Grid/Speaker/Filmstrip layouts |
| Moderator Controls | ✅ Android | Mute/remove participants |
| Layout Switching | ✅ Android | Change conference layout |

### 6. Notifications
| Feature | Status | Description |
|---------|--------|-------------|
| Message Notification | ✅ Android | Notification for new messages |
| Call Notification | ✅ Android | Full-screen incoming call |
| Quick Reply | ✅ Android | Reply from notification |
| Notification Actions | ✅ Android | Accept/decline call from notification |

### 7. Settings
| Feature | Status | Description |
|---------|--------|-------------|
| Notification Toggles | ✅ Android | Enable/disable notifications |
| Privacy Settings | ✅ Android | Read receipts, typing indicators |
| Settings Persistence | ✅ Android | Settings survive app restart |
| Sign Out | ✅ Android | Delete account and return to welcome |

---

## Screen-by-Screen Reference

### Welcome Screen
**Route**: `welcome`
**Purpose**: Entry point for new users or after sign out

**UI Elements**:
- App logo/title "GetTogether"
- Tagline describing the app
- "Create Account" button (primary)
- "Import Existing Account" button (secondary)
- Platform indicator (iOS/Android)

**Navigation**:
- Create Account → `create_account`
- Import Account → `import_account`

---

### Create Account Screen
**Route**: `create_account`
**Purpose**: Create new Jami account

**UI Elements**:
- Back button
- Title "Create Account"
- Avatar preview (circle with first letter of display name)
- Display name text field
- Info text explaining local account creation
- "Create Account" button
- Loading indicator during creation

**State**: `CreateAccountState`
```kotlin
displayName: String = ""
isCreating: Boolean = false
isAccountCreated: Boolean = false
error: String? = null
isValid: Boolean = false  // true when displayName.length >= 2
```

**Validation Rules**:
- Display name must be at least 2 characters
- Button disabled when invalid or creating

---

### Import Account Screen
**Route**: `import_account`
**Purpose**: Restore existing Jami account

**UI Elements**:
- Back button
- Title "Import Account"
- Import method selector (Archive / PIN tabs)
- Dynamic input fields based on method:
  - Archive: File path + Password
  - PIN: Account PIN field
- "Import Account" button
- Loading indicator during import

**State**: `ImportAccountState`
```kotlin
importMethod: ImportMethod = ImportMethod.ARCHIVE
archivePath: String = ""
archivePassword: String = ""
accountPin: String = ""
isImporting: Boolean = false
isAccountImported: Boolean = false
error: String? = null
isValid: Boolean = false
```

**Validation Rules**:
- Archive: Path must not be empty
- PIN: Must be 8+ characters

---

### Home Screen
**Route**: `home`
**Purpose**: Main hub with tabbed navigation

**UI Elements**:
- Bottom navigation bar with 3 tabs:
  - Chats (conversations icon)
  - Contacts (person icon)
  - Settings (gear icon)
- Content area showing selected tab
- Floating Action Button (on Chats tab)

**Tabs**:
1. **ConversationsTab** - List of conversations
2. **ContactsTab** - List of contacts
3. **SettingsTab** - App settings

---

### Conversations Tab
**Parent**: Home Screen
**Purpose**: Display all conversations

**UI Elements**:
- "Chats" title in app bar
- Pull-to-refresh
- Conversation list:
  - Avatar (colored circle with initial)
  - Conversation name
  - Last message preview (truncated)
  - Timestamp (relative: "Just now", "5m ago", "Yesterday")
  - Unread count badge
- FAB "+" for new conversation
- Empty state: "No conversations yet"
- Loading spinner

**State**: `ConversationsState`
```kotlin
conversations: List<ConversationUiItem> = emptyList()
isLoading: Boolean = false
error: String? = null
hasAccount: Boolean = false
```

**Data Model**: `ConversationUiItem`
```kotlin
id: String
name: String
lastMessage: String?
time: String?
unreadCount: Int
avatarInitial: Char
```

---

### Contacts Tab
**Parent**: Home Screen
**Purpose**: Display all contacts

**UI Elements**:
- "Contacts" title with add button (+)
- Pull-to-refresh
- Contact list (sorted: online first, then alphabetical):
  - Avatar with online indicator (green dot)
  - Contact name
  - Status text (Online/Offline)
- Empty state: "No contacts yet"
- Loading spinner

**State**: `ContactsState`
```kotlin
contacts: List<ContactUiItem> = emptyList()
isLoading: Boolean = false
error: String? = null
hasAccount: Boolean = false
```

---

### Settings Tab
**Parent**: Home Screen
**Purpose**: App configuration and account management

**UI Elements**:
- Expandable sections:
  1. **Profile**: Avatar, display name, username, registration badge
  2. **Account**: Display name, username, Jami ID, Account ID
  3. **Notifications**: Master toggle + individual toggles
  4. **Privacy & Security**: Online status, read receipts, typing indicators, block unknown
  5. **About**: App version, description, Jami attribution
- Sign Out button (red, destructive)
- Confirmation dialog for sign out

**State**: `SettingsState`
```kotlin
userProfile: UserProfile?
notificationSettings: NotificationSettings
privacySettings: PrivacySettings
isLoading: Boolean
isSigningOut: Boolean
signOutComplete: Boolean
showSignOutDialog: Boolean
```

---

### Add Contact Screen
**Route**: `add_contact`
**Purpose**: Search and add new contacts

**UI Elements**:
- Back button
- Title "Add Contact"
- Instructions text
- Search input field (username/Jami ID)
- Search button
- Search result card (when found):
  - Avatar
  - Display name
  - Username
  - "Already a contact" badge (if applicable)
  - Custom display name input
  - "Add Contact" button
- Loading indicators
- Error snackbar

**State**: `AddContactState`
```kotlin
contactId: String = ""
displayName: String = ""
isSearching: Boolean = false
isAdding: Boolean = false
searchResult: ContactSearchResult? = null
canSearch: Boolean = false
isContactAdded: Boolean = false
error: String? = null
```

---

### Contact Details Screen
**Route**: `contact/{contactId}`
**Purpose**: View contact info and manage relationship

**UI Elements**:
- Back button
- Title "Contact Details"
- Large avatar (120dp) with online indicator
- Contact name (headline)
- Username with @ prefix
- Status badges (Online/Offline, Trusted, Blocked)
- Action buttons row:
  - Message (navigates to chat)
  - Call (starts audio call)
  - Video (starts video call)
- Contact info card:
  - Username, Jami ID, Added date, Last seen
- Action buttons:
  - Block/Unblock Contact
  - Remove Contact (red)
- Confirmation dialogs

**State**: `ContactDetailsState`
```kotlin
contact: Contact?
isLoading: Boolean
isRemoving: Boolean
isBlocking: Boolean
error: String?
contactRemoved: Boolean
conversationStarted: String?
showRemoveDialog: Boolean
showBlockDialog: Boolean
```

---

### New Conversation Screen
**Route**: `new_conversation`
**Purpose**: Start new conversation or group call

**UI Elements**:
- Back button
- Title: "New Conversation" or "Select Participants"
- Groups icon toggle (multi-select mode)
- Search bar
- Contact list:
  - Selection indicator (checkbox in multi-select)
  - Avatar with online status
  - Contact name
  - Highlighted background when selected
- Multi-select hint: "Select 2 or more contacts for a group call"
- FABs (in multi-select with 2+ selected):
  - Video Call FAB (with count)
  - Audio Call FAB (with count)

**State**: `NewConversationState`
```kotlin
filteredContacts: List<SelectableContact> = emptyList()
selectedContactIds: Set<String> = emptySet()
isMultiSelectMode: Boolean = false
isLoading: Boolean = false
searchQuery: String = ""
canStartGroupCall: Boolean = false
createdConversationId: String? = null
error: String? = null
```

---

### Chat Screen
**Route**: `chat/{conversationId}`
**Purpose**: 1:1 messaging interface

**UI Elements**:
- Top bar:
  - Back button
  - Contact avatar (40dp)
  - Contact name
  - Online status indicator
- Message list (LazyColumn):
  - Own messages: right-aligned, primary color
  - Other messages: left-aligned, surface color
  - Timestamp below each message
  - Status indicator for own messages
- Message input bar:
  - Expandable text field (1-4 lines)
  - Send button (enabled when text not empty)
- Empty state: "No messages yet"

**State**: `ChatState`
```kotlin
conversationId: String = ""
contactName: String = ""
messages: List<ChatMessage> = emptyList()
messageInput: String = ""
isLoading: Boolean = false
isSending: Boolean = false
canSend: Boolean = false
error: String? = null
```

**Message Status Display**:
- SENDING → "Sending..."
- SENT → "Sent"
- DELIVERED → "Delivered"
- READ → "Read"
- FAILED → "Failed" (red)

---

### Call Screen
**Route**: `call/{contactId}/{isVideo}`
**Purpose**: 1:1 audio/video calling

**UI Elements by State**:

**Incoming Call**:
- Pulsing avatar (animated)
- Contact name
- "Incoming call..." / "Incoming video call..."
- Decline button (red FAB, 72dp)
- Accept button (green FAB, 72dp)

**Active Call (Audio)**:
- Large avatar (160dp)
- Contact name
- Call status / duration (MM:SS)
- Control buttons:
  - Mute (Mic icon)
  - Speaker (Volume icon)
- End call button (red FAB)

**Active Call (Video)**:
- Remote video area (or placeholder)
- Local video preview (100x140dp, top-right)
- Same controls as audio + Video toggle + Camera switch

**Call Ended**:
- Avatar (120dp)
- Contact name
- "Call Ended" / "Call Failed"
- Duration (if > 0)

**State**: `CallState`
```kotlin
contactId: String = ""
contactName: String = ""
callId: String? = null
isVideo: Boolean = false
callStatus: CallStatus = CallStatus.IDLE
isMuted: Boolean = false
isSpeakerOn: Boolean = false
isLocalVideoEnabled: Boolean = false
callDuration: Int = 0
error: String? = null
```

---

### Conference Screen
**Route**: `conference/{participantIds}/{withVideo}/{conferenceId}`
**Purpose**: Group call interface

**UI Elements**:

**Connecting State**:
- Person icon in circle
- Conference name / "Group Call"
- Status: "Creating conference..." / "Connecting..."

**Active Conference**:
- Top bar (toggles on tap):
  - Conference name
  - Duration + participant count
  - Layout selector dropdown
- Participant display (by layout):
  - Grid: 2x2 or 3x3 tiles
  - Speaker: Large active speaker + bottom strip
  - Filmstrip: Left strip + large active speaker
- Participant tile:
  - Avatar or video
  - Name label
  - Muted/video-off indicators
  - Speaking indicator (border highlight)
- Control bar:
  - Mute, Speaker, Video, Camera switch
  - Leave button (red FAB)

**Conference Ended**:
- CallEnd icon
- "Conference Ended" / "Conference Failed"
- Duration + participant count

**State**: `ConferenceState`
```kotlin
conferenceId: String? = null
participants: List<ConferenceParticipant> = emptyList()
conferenceStatus: ConferenceStatus = ConferenceStatus.IDLE
layoutMode: LayoutMode = LayoutMode.GRID
isMuted: Boolean = false
isSpeakerOn: Boolean = false
isLocalVideoEnabled: Boolean = false
conferenceDuration: Int = 0
isHost: Boolean = false
activeSpeakerId: String? = null
error: String? = null
```

---

## Manual Testing Use Cases

### UC-001: First-Time Account Creation

**Preconditions**: Fresh app install, no existing account

**Steps**:
1. Launch the app
2. Observe Welcome screen displays
3. Tap "Create Account"
4. Observe Create Account screen
5. Enter display name "TestUser"
6. Observe avatar shows "T"
7. Tap "Create Account"
8. Wait for creation to complete

**Expected Results**:
| Step | Expected Behavior |
|------|-------------------|
| 1 | App launches without crash |
| 2 | Welcome screen shows app title, tagline, two buttons |
| 3 | Navigation to Create Account screen |
| 4 | Screen shows avatar, text field, create button (disabled) |
| 5 | Avatar updates to show "T", button becomes enabled |
| 6 | Avatar circle displays first letter |
| 7 | Loading indicator appears, button disabled |
| 8 | Navigation to Home screen (Chats tab), empty conversation list |

**Verification**:
- [ ] Account created successfully
- [ ] Home screen displays
- [ ] Settings tab shows account info
- [ ] No error messages

---

### UC-002: Account Persistence After Restart

**Preconditions**: Account "TestUser" created (UC-001 completed)

**Steps**:
1. Force stop the app (Settings → Apps → Force Stop)
2. Wait 5 seconds
3. Launch the app again
4. Observe initial screen

**Expected Results**:
| Step | Expected Behavior |
|------|-------------------|
| 1 | App closes completely |
| 2 | - |
| 3 | App launches |
| 4 | Brief loading spinner, then Home screen (NOT Welcome screen) |

**Verification**:
- [ ] App goes directly to Home screen
- [ ] Account info preserved in Settings
- [ ] Conversations (if any) still visible
- [ ] Contacts (if any) still visible

---

### UC-003: Account Import from Archive

**Preconditions**:
- Fresh app install OR signed out state
- Jami account archive file available on device

**Steps**:
1. Launch app to Welcome screen
2. Tap "Import Existing Account"
3. Select "Archive" method (should be default)
4. Enter archive file path
5. Enter archive password
6. Tap "Import Account"
7. Wait for import to complete

**Expected Results**:
| Step | Expected Behavior |
|------|-------------------|
| 1 | Welcome screen displays |
| 2 | Import Account screen displays |
| 3 | Archive input fields shown (path + password) |
| 4 | Text appears in path field |
| 5 | Password masked with dots |
| 6 | Loading indicator, button disabled |
| 7 | Navigation to Home screen, account restored |

**Verification**:
- [ ] Account imported successfully
- [ ] Display name matches original
- [ ] Contacts restored (if any in archive)
- [ ] Conversations restored (if any in archive)

---

### UC-004: Add New Contact

**Preconditions**:
- Account exists and logged in
- Know a valid Jami username/ID to add

**Steps**:
1. Navigate to Contacts tab
2. Tap "+" add button
3. Enter contact's username or Jami ID
4. Tap "Search"
5. Wait for search results
6. (Optional) Enter custom display name
7. Tap "Add Contact"
8. Wait for contact to be added

**Expected Results**:
| Step | Expected Behavior |
|------|-------------------|
| 1 | Contacts tab displays with existing contacts |
| 2 | Add Contact screen displays |
| 3 | Text appears in search field |
| 4 | Loading indicator appears |
| 5 | Search result card shows contact info |
| 6 | Display name field updates |
| 7 | Loading indicator, button disabled |
| 8 | Success snackbar, navigation back to Contacts |

**Verification**:
- [ ] Contact appears in Contacts list
- [ ] Contact shows correct name (custom if set)
- [ ] Online status indicator visible
- [ ] Can tap contact to view details

---

### UC-005: Send Text Message

**Preconditions**:
- Account exists and logged in
- At least one contact exists

**Steps**:
1. Navigate to Contacts tab
2. Tap on a contact
3. Tap "Message" button
4. Observe Chat screen loads
5. Type "Hello, this is a test message"
6. Tap send button
7. Observe message appears in list

**Expected Results**:
| Step | Expected Behavior |
|------|-------------------|
| 1 | Contacts list displays |
| 2 | Contact Details screen displays |
| 3 | Chat screen displays with contact name in header |
| 4 | Empty message list or existing messages load |
| 5 | Text appears in input field, send button enables |
| 6 | Message bubble appears right-aligned with "Sending..." |
| 7 | Status changes: Sending → Sent → (if delivered) Delivered |

**Verification**:
- [ ] Message displays correctly formatted
- [ ] Timestamp shows correct time
- [ ] Status indicator updates
- [ ] Message persists after leaving and returning to chat

---

### UC-006: Receive Text Message

**Preconditions**:
- Account exists and logged in
- Another device/user can send message to this account

**Steps**:
1. Open Chat screen with the contact
2. Have contact send a message
3. Observe message appears in real-time

**Expected Results**:
| Step | Expected Behavior |
|------|-------------------|
| 1 | Chat screen open, showing existing messages |
| 2 | - (external action) |
| 3 | New message appears left-aligned with sender's style |

**Verification**:
- [ ] Message appears without manual refresh
- [ ] Message content correct
- [ ] Timestamp accurate
- [ ] List auto-scrolls to new message

---

### UC-007: Start Audio Call

**Preconditions**:
- Account exists and logged in
- Contact exists who can receive calls

**Steps**:
1. Navigate to Contact Details
2. Tap "Call" button (phone icon)
3. Wait for call to connect
4. Observe active call UI
5. Test mute button
6. Test speaker button
7. Tap "End Call"

**Expected Results**:
| Step | Expected Behavior |
|------|-------------------|
| 1 | Contact Details screen displays |
| 2 | Call screen displays with "Calling..." status |
| 3 | Status changes: Calling → Ringing → Connecting → Connected |
| 4 | Large avatar, duration timer starts, control buttons visible |
| 5 | Mute icon changes to MicOff, audio muted |
| 6 | Speaker icon changes, audio output switches |
| 7 | "Call Ended" screen, then navigation back |

**Verification**:
- [ ] Call initiates successfully
- [ ] Audio works (can hear/speak)
- [ ] Mute toggles audio correctly
- [ ] Speaker toggles output correctly
- [ ] Duration timer accurate
- [ ] Call ends cleanly

---

### UC-008: Start Video Call

**Preconditions**:
- Account exists and logged in
- Contact exists who can receive video calls
- Camera permission granted

**Steps**:
1. Navigate to Contact Details
2. Tap "Video" button
3. Wait for call to connect
4. Observe video UI
5. Test video toggle
6. Test camera switch
7. Tap "End Call"

**Expected Results**:
| Step | Expected Behavior |
|------|-------------------|
| 1 | Contact Details screen displays |
| 2 | Call screen displays with video enabled |
| 3 | Status changes to Connected |
| 4 | Remote video area + local preview (top-right) |
| 5 | Local preview hides/shows, icon changes |
| 6 | Local preview switches between front/back camera |
| 7 | "Call Ended" screen, then navigation back |

**Verification**:
- [ ] Video call initiates
- [ ] Local video preview visible
- [ ] Remote video displays when connected
- [ ] Video toggle works
- [ ] Camera switch works
- [ ] Call ends cleanly

---

### UC-009: Receive Incoming Call

**Preconditions**:
- Account exists and logged in
- Another device/user can call this account

**Steps**:
1. App is open (any screen)
2. Receive incoming call from contact
3. Observe incoming call UI
4. Tap "Accept" button
5. Conduct call
6. Tap "End Call"

**Expected Results**:
| Step | Expected Behavior |
|------|-------------------|
| 1 | App running |
| 2 | Call screen overlays with pulsing avatar, Accept/Decline buttons |
| 3 | Contact name visible, "Incoming call..." text |
| 4 | Call connects, active call UI displays |
| 5 | Audio/video works, controls functional |
| 6 | Call ends, returns to previous screen |

**Verification**:
- [ ] Incoming call UI displays correctly
- [ ] Avatar pulses with animation
- [ ] Accept connects the call
- [ ] Decline rejects the call (test separately)

---

### UC-010: Start Group Call (Conference)

**Preconditions**:
- Account exists and logged in
- At least 2 contacts exist

**Steps**:
1. Navigate to Chats tab
2. Tap "+" FAB
3. Tap Groups icon to enable multi-select
4. Select 2+ contacts
5. Tap Video Call FAB (or Audio Call)
6. Wait for conference to create
7. Observe participant grid
8. Test layout switching
9. Tap "Leave"

**Expected Results**:
| Step | Expected Behavior |
|------|-------------------|
| 1 | Chats tab displays |
| 2 | New Conversation screen displays |
| 3 | Title changes to "Select Participants", checkboxes appear |
| 4 | Contacts show checkmarks, FABs appear with count |
| 5 | Conference screen displays with "Creating conference..." |
| 6 | Status changes to Connected |
| 7 | Participant tiles display in grid layout |
| 8 | Layout changes (Grid → Speaker → Filmstrip) |
| 9 | "Conference Ended" screen, navigation back |

**Verification**:
- [ ] Multi-select mode works
- [ ] FABs show correct count
- [ ] Conference creates successfully
- [ ] All participants visible
- [ ] Layout modes switch correctly
- [ ] Leave ends participation cleanly

---

### UC-011: Settings Persistence

**Preconditions**: Account exists and logged in

**Steps**:
1. Navigate to Settings tab
2. Expand Notifications section
3. Toggle "Message Notifications" OFF
4. Expand Privacy section
5. Toggle "Read Receipts" OFF
6. Force stop app
7. Launch app
8. Navigate to Settings tab
9. Check toggle states

**Expected Results**:
| Step | Expected Behavior |
|------|-------------------|
| 1-2 | Notifications section expands |
| 3 | Toggle switches to OFF state |
| 4-5 | Privacy section expands, toggle switches OFF |
| 6 | App closes |
| 7-8 | App launches, navigates to Settings |
| 9 | Both toggles remain in OFF state |

**Verification**:
- [ ] All notification toggles persist
- [ ] All privacy toggles persist
- [ ] Settings survive multiple restarts

---

### UC-012: Sign Out

**Preconditions**: Account exists and logged in

**Steps**:
1. Navigate to Settings tab
2. Scroll to bottom
3. Tap "Sign Out" button
4. Observe confirmation dialog
5. Tap "Sign Out" in dialog
6. Wait for sign out to complete

**Expected Results**:
| Step | Expected Behavior |
|------|-------------------|
| 1 | Settings tab displays |
| 2 | Sign Out button visible (red text) |
| 3 | Confirmation dialog appears |
| 4 | Dialog shows warning about data deletion |
| 5 | Loading indicator appears |
| 6 | Navigation to Welcome screen |

**Verification**:
- [ ] Confirmation dialog prevents accidental sign out
- [ ] Account deleted from device
- [ ] Returns to Welcome screen
- [ ] Cannot access old data after sign out

---

### UC-013: Block and Unblock Contact

**Preconditions**:
- Account exists and logged in
- At least one contact exists

**Steps**:
1. Navigate to Contact Details
2. Tap "Block Contact" button
3. Confirm in dialog
4. Observe blocked state
5. Tap "Unblock Contact" button
6. Confirm in dialog
7. Observe unblocked state

**Expected Results**:
| Step | Expected Behavior |
|------|-------------------|
| 1 | Contact Details screen displays |
| 2 | Confirmation dialog appears |
| 3 | Contact shows "Blocked" badge |
| 4 | Button text changes to "Unblock Contact" |
| 5 | Confirmation dialog appears |
| 6 | "Blocked" badge disappears |
| 7 | Button text returns to "Block Contact" |

**Verification**:
- [ ] Block prevents receiving messages/calls from contact
- [ ] Unblock restores communication ability
- [ ] Visual indicators accurate

---

### UC-014: Remove Contact

**Preconditions**:
- Account exists and logged in
- At least one contact exists

**Steps**:
1. Navigate to Contact Details
2. Tap "Remove Contact" button (red)
3. Read confirmation dialog
4. Tap "Remove" to confirm
5. Observe navigation

**Expected Results**:
| Step | Expected Behavior |
|------|-------------------|
| 1 | Contact Details screen displays |
| 2 | Confirmation dialog with warning text |
| 3 | Warning explains consequences |
| 4 | Loading indicator, contact removed |
| 5 | Navigation back to Contacts list |

**Verification**:
- [ ] Contact no longer in Contacts list
- [ ] Conversation may remain but no new messages
- [ ] Can re-add contact later

---

### UC-015: Message Notification Quick Reply

**Preconditions**:
- Account exists and logged in
- App in background or screen off
- Contact sends a message

**Steps**:
1. Receive message notification
2. Expand notification
3. Tap "Reply" action
4. Type reply text
5. Tap send

**Expected Results**:
| Step | Expected Behavior |
|------|-------------------|
| 1 | Notification appears with message preview |
| 2 | Quick reply input visible |
| 3 | Keyboard appears |
| 4 | Text entered in notification input |
| 5 | Reply sent, notification may dismiss |

**Verification**:
- [ ] Notification displays correct sender/message
- [ ] Quick reply sends message
- [ ] Message appears in conversation when app opened

---

### UC-016: Pull-to-Refresh

**Preconditions**: Account exists and logged in

**Steps**:
1. Navigate to Chats tab
2. Pull down on conversation list
3. Observe refresh indicator
4. Navigate to Contacts tab
5. Pull down on contact list
6. Observe refresh indicator

**Expected Results**:
| Step | Expected Behavior |
|------|-------------------|
| 1 | Chats tab displays |
| 2-3 | Refresh indicator appears, data reloads |
| 4 | Contacts tab displays |
| 5-6 | Refresh indicator appears, data reloads |

**Verification**:
- [ ] Refresh indicator appears during pull
- [ ] Data updates after refresh
- [ ] New conversations/contacts appear

---

## Platform Status

### Android
| Component | Status | Notes |
|-----------|--------|-------|
| JNI Bridge (SwigJamiBridge) | ✅ Complete | Full daemon integration |
| Account Management | ✅ Complete | Create, import, export, delete |
| Contacts | ✅ Complete | Add, remove, block, presence |
| Messaging | ✅ Complete | Send, receive, status |
| 1:1 Calls | ✅ Complete | Audio, video, controls |
| Conference Calls | ✅ Complete | Create, join, layouts |
| Notifications | ✅ Complete | Messages, calls, actions |
| Settings Persistence | ✅ Complete | SharedPreferences |
| File Transfer | ✅ Complete | Accept, cancel |

### iOS
| Component | Status | Notes |
|-----------|--------|-------|
| Swift Bridge | ❌ Stub | 90+ TODO placeholders |
| Account Management | ❌ Stub | Returns placeholder IDs |
| Contacts | ❌ Stub | Returns empty lists |
| Messaging | ❌ Stub | Returns placeholder IDs |
| Calls | ❌ Stub | Returns placeholder IDs |
| Conference | ❌ Stub | Returns placeholder IDs |
| Notifications | ❌ Stub | UNUserNotificationCenter needed |
| CallKit | ❌ Stub | CallKit integration needed |
| Settings Persistence | ❌ Stub | NSUserDefaults needed |

---

## Appendix: Error Scenarios

### Network Errors
- **No Internet**: Operations fail with network error message
- **Timeout**: Operations timeout after 30 seconds
- **DHT Unreachable**: Account operations may fail

### Account Errors
- **Invalid Display Name**: "Display name must be at least 2 characters"
- **Import Failed**: "Failed to import account. Check your credentials."
- **Creation Failed**: "Failed to create account. Please try again."

### Contact Errors
- **Not Found**: "Contact not found. Check the username or ID."
- **Already Added**: Shows "Already a contact" badge
- **Add Failed**: "Failed to add contact. Please try again."

### Call Errors
- **Call Failed**: "Call Failed" displayed on end screen
- **Connection Lost**: Call ends with failure state
- **Conference Failed**: "Conference Failed" displayed

### Message Errors
- **Send Failed**: Message shows "Failed" status in red
- **Load Failed**: Error snackbar with retry option

---

*Document Version: 1.0*
*Last Updated: December 2024*
*Platform: Android (fully functional), iOS (stubs only)*
