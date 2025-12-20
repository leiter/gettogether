# Message Delivery Investigation - 2025-12-20

## Problem Statement

Messages sent from one device do not appear on the receiving device, despite:
- Contact addition working correctly
- Contacts persisting across app restarts
- Both devices showing "Online" status
- DHT connectivity established on both devices

## Investigation Summary

### Code Review Findings

**✅ All Required Components Are Implemented:**

1. **Send Message Implementation** - Found in multiple files:
   - `SwigJamiBridge.kt` (line 613-626)
   - `AndroidJamiBridge.kt` (line 596-609)
   - `JamiBridge.android.kt` (line 436-450) - **Actual expect/actual implementation**
   - All call native `JamiService.sendMessage()`

2. **Receive Message Callback** - Found in multiple files:
   - `SwigJamiBridge.kt` - `swarmMessageReceived` callback (line 223-235)
   - `AndroidJamiBridge.kt` - `swarmMessageReceived` callback (line 209-221)
   - `JamiBridge.android.kt` - `onSwarmMessageReceived` JNI callback (line 804-822)
   - All emit `JamiConversationEvent.MessageReceived` events

3. **Event Handling** - `ConversationRepositoryImpl.kt`:
   - Listens to `conversationEvents` flow (line 40)
   - Handles `MessageReceived` events (line 260-283)
   - Adds received messages to cache
   - Updates conversation last message

4. **Callback Registration** - `SwigJamiBridge.kt`:
   - `ConversationCallback` created (line 214)
   - Registered with `JamiService.init()` (line 369)

**Conclusion:** The entire message receiving chain is correctly implemented.

### Architecture Confusion

Multiple JamiBridge implementations exist:
1. `SwigJamiBridge.kt` - SWIG bindings implementation (androidApp module)
2. `AndroidJamiBridge.kt` - Alternative Android implementation (androidApp module)
3. `JamiBridge.android.kt` - Expect/actual implementation (shared/androidMain)

**Dependency Injection Config** (`Modules.android.kt` line 28):
```kotlin
single<JamiBridge> { AndroidJamiBridge(androidContext()) }
```

**Issue:** It's unclear which implementation is actually being used at runtime. Logging was added to all three, but NO logs appeared in logcat, suggesting:
- A fourth implementation exists (undiscovered)
- OR logs are not reaching logcat for unknown reasons
- OR the message send UI never actually calls `sendMessage()`

### Logging Added But Not Visible

Added comprehensive logging to:
1. `SwigJamiBridge.sendMessage` and `swarmMessageReceived`
2. `AndroidJamiBridge.sendMessage` and `swarmMessageReceived`
3. `JamiBridge.android.sendMessage` and `onSwarmMessageReceived`
4. `ConversationRepositoryImpl.sendMessage` and `handleConversationEvent`

**Result:** None of these logs appeared in logcat when test messages were sent.

### Test Results

**Test Setup:**
- Pixel 7a (sender) - Account: 2078d1ac09712bfb, Jami ID: 11f22a30...
- Pixel 2 (receiver) - Account: db605d1be1ae9e3e, Jami ID: 14ef0509...
- Both on WiFi 192.168.178.x
- Both showing DHT connected
- Contacts showing as "Online" (initially), then "Offline"

**Test Actions:**
1. Navigated to conversation on Pixel 7a
2. Typed test message ("Test123", "TestLogging", "FinalTest")
3. Tapped send button

**Expected:**
- `sendMessage` logs on Pixel 7a
- Message appears on Pixel 7a (sent)
- `swarmMessageReceived` callback on Pixel 2
- Message appears on Pixel 2 (received)

**Actual:**
- No logs appeared
- Message appeared on Pixel 7a from earlier tests (but cleared after fresh install)
- No message appeared on Pixel 2

---

## Root Cause Hypotheses

### Hypothesis 1: Conversation Not Initialized for Messaging

**Evidence:**
- Conversations are created when contacts are added
- But Jami might require explicit conversation initialization/acceptance before messages can be sent
- The `conversationReady` callback might not have fired

**Likelihood:** HIGH

**Investigation Needed:**
1. Check conversation status with `JamiService.conversationInfos(accountId, conversationId)`
2. Look for conversation "mode" or "status" fields
3. Check if conversation needs to be explicitly "started" or "accepted"

### Hypothesis 2: Missing Conversation Acceptance Step

**Evidence:**
- Trust requests were bypassed (auto-accepted)
- Conversation requests might also need acceptance
- `conversationRequestReceived` callback exists but might not be triggered

**Likelihood:** HIGH

**Investigation Needed:**
1. Monitor logs for `conversationRequestReceived` events
2. Check if calling `acceptConversationRequest()` is needed
3. Test with official Jami app to see if conversation acceptance is required

### Hypothesis 3: Swarm Conversation Bootstrap Failure

**Evidence:**
From Jami daemon logs:
```
[Conversation 03105cfa...] Bootstrap: Fallback with member: 11f22a30...
[Conversation 03105cfa...] Bootstrap: Fallback failed. Wait for remote connections.
```

This suggests the swarm conversation couldn't bootstrap properly.

**Likelihood:** HIGH

**Impact:** If swarm bootstrap fails, messages cannot be delivered over P2P network.

**Investigation Needed:**
1. Check why swarm bootstrap is failing
2. Verify both devices are members of the same swarm
3. Check if conversation needs manual sync/bootstrap trigger

### Hypothesis 4: Send Button Not Actually Calling sendMessage

**Evidence:**
- No `sendMessage` logs despite adding to ALL implementations
- Could indicate UI is not wired to ViewModel correctly

**Likelihood:** MEDIUM

**Investigation Needed:**
1. Add logs to `MessagesViewModel.sendMessage()`
2. Add logs to button click handler
3. Verify data flow from UI → ViewModel → Repository → Bridge

### Hypothesis 5: Conversation ID Mismatch

**Evidence:**
- Pixel 7a Chats list shows wrong conversation IDs (showing own ID instead of contact's)
- Multiple duplicate conversations with same ID
- Might be sending to wrong conversation

**Likelihood:** MEDIUM

**Investigation Needed:**
1. Log the actual conversation ID being used for send
2. Compare with conversation ID from `addContact` operation
3. Verify conversation ID matches on both devices

---

## Recommended Next Steps

### Immediate Actions

1. **Add ViewModel Logging**
   ```kotlin
   // In MessagesViewModel
   fun sendMessage(content: String) {
       println("MessagesViewModel.sendMessage: content='$content'")
       // ... rest of implementation
   }
   ```

2. **Check Conversation Status**
   ```kotlin
   val accountId = accountRepository.currentAccountId.value
   val conversationId = state.conversationId
   val info = jamiBridge.conversationInfos(accountId, conversationId)
   Log.i(TAG, "Conversation info: $info")
   ```
   Look for:
   - `status`: active/pending/etc
   - `mode`: one-to-one/group
   - `members`: list of participant IDs
   - `syncing`: boolean

3. **Test With Official Jami App**
   - Install official Jami Android app
   - Create accounts on both devices
   - Add each other as contacts
   - Send messages
   - Observe the exact flow:
     - Is there a conversation request?
     - Does it need acceptance?
     - What's the exact UI flow?
   - Compare with our implementation

4. **Monitor Conversation Events**
   ```kotlin
   init {
       viewModelScope.launch {
           jamiBridge.conversationEvents.collect { event ->
               Log.i(TAG, "Conversation event: $event")
               when (event) {
                   is JamiConversationEvent.ConversationReady -> {
                       Log.i(TAG, "Conversation ready: ${event.conversationId}")
                   }
                   is JamiConversationEvent.ConversationRequestReceived -> {
                       Log.i(TAG, "Conversation request: ${event.conversationId}")
                   }
                   // ... handle other events
               }
           }
       }
   }
   ```

5. **Check Swarm Members**
   ```kotlin
   val members = jamiBridge.getConversationMembers(accountId, conversationId)
   Log.i(TAG, "Conversation members: $members")
   ```
   Verify both devices' Jami IDs are in the members list.

### Code Changes to Try

**Option 1: Explicit Conversation Start**
```kotlin
// After adding contact, explicitly start conversation
suspend fun startConversation(accountId: String, contactUri: String): Result<String> {
    return try {
        val conversationId = jamiBridge.startConversation(accountId)
        jamiBridge.addConversationMember(accountId, conversationId, contactUri)
        Result.success(conversationId)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
```

**Option 2: Load Conversation Before Sending**
```kotlin
// Before sending first message, load conversation
suspend fun ensureConversationLoaded(accountId: String, conversationId: String) {
    jamiBridge.loadConversationMessages(accountId, conversationId, "", 1)
}
```

**Option 3: Accept Conversation Automatically**
```kotlin
// Listen for conversation requests and auto-accept
is JamiConversationEvent.ConversationRequestReceived -> {
    Log.i(TAG, "Auto-accepting conversation request: ${event.conversationId}")
    jamiBridge.acceptConversationRequest(event.accountId, event.conversationId)
}
```

### Testing Recommendations

1. **Simplify Test**
   - Use only Pixel 7a (sender)
   - Send message to self
   - Check if `swarmMessageReceived` fires for own messages
   - This isolates P2P networking from the test

2. **Add Conversation List Logging**
   ```kotlin
   val conversations = jamiBridge.getConversations(accountId)
   conversations.forEach { conv ->
       Log.i(TAG, "Conversation: id=${conv.id}, members=${conv.members}")
   }
   ```

3. **Monitor All Jami Events**
   ```kotlin
   jamiBridge.events.collect { event ->
       Log.i(TAG, "Jami event: $event")
   }
   ```

---

## Known Issues

1. **Conversation ID Display Bug**
   - Chats list shows own Jami ID instead of contact's ID
   - Multiple duplicate conversations created
   - Needs investigation in conversation creation logic

2. **Pixel 2 App Stability**
   - App frequently renders blank white screen
   - Triggered by tapping text input field
   - Requires force-stop and restart
   - Needs investigation (possibly Android version specific)

3. **Cached Message Previews**
   - Chats list shows "heute" message in preview
   - But conversation view shows "No messages yet"
   - Cache not being invalidated properly

---

## Success Criteria

For messaging to be considered working:

1. ✅ Message typed in text field
2. ✅ Send button tapped
3. ⚠️ `sendMessage()` called (not verified - no logs)
4. ⚠️ Message sent to Jami daemon (not verified - no logs)
5. ❌ Message delivered to receiving device (NOT working)
6. ❌ `swarmMessageReceived` callback fired (NOT working)
7. ❌ Message appears in receiving device's conversation (NOT working)

**Current Status: 2/7 criteria met**

---

## Technical Debt

1. **Multiple Bridge Implementations** - Confusing which one is used
   - Recommendation: Consolidate to single implementation
   - Or clearly document which is used when

2. **Missing Logging Strategy** - println vs Log.i vs android.util.Log
   - Recommendation: Use consistent logging with TAG

3. **No Message Delivery Confirmation** - Can't tell if message was sent successfully
   - Recommendation: Implement message status tracking (Sending → Sent → Delivered)

4. **No Error Handling** - Silent failures make debugging impossible
   - Recommendation: Add try-catch with error logging to all Jami calls

---

## Conclusion

The messaging implementation appears complete from a code perspective. All required components exist:
- ✅ Send message methods
- ✅ Receive message callbacks
- ✅ Event emission and handling
- ✅ UI components

However, messages are NOT being delivered. The most likely root causes are:

1. **Conversation initialization issue** - Swarm conversation not properly bootstrapped
2. **Missing acceptance step** - Conversation or trust request needs explicit acceptance
3. **Network/P2P failure** - Devices can't establish connection despite same WiFi

**Recommended Priority:**
1. Test with official Jami app to understand expected flow
2. Add conversation status logging
3. Check swarm bootstrap status
4. Verify conversation membership on both devices
5. Add explicit conversation initialization if needed

---

**Investigation Date:** 2025-12-20 18:25
**Devices:** Pixel 7a + Pixel 2 (physical devices)
**Build:** DEBUG with comprehensive logging added
**Result:** Messages not delivered, logs not visible

