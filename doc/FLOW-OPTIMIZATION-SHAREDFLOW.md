# SharedFlow Optimization for Contact State Management

**Date:** 2026-01-18
**Status:** ✅ Implemented
**Files Modified:** `ContactRepositoryImpl.kt`

---

## Overview

Implemented SharedFlow optimization to reduce CPU usage from multiple Flow transformations when multiple ViewModels subscribe to the same contact data.

## Problem Identified

### Before Optimization

When multiple screens subscribed to contact data:
- ContactsViewModel calls `getContacts()` → creates Flow #1
- ContactDetailsViewModel calls `getContactById()` → creates Flow #2 (via `getContacts()`)
- ChatViewModel calls `getContactById()` → creates Flow #3 (via `getContacts()`)

**Issue**: Each Flow created a separate `map{}` transformation:
1. Reads from the same `_contactsCache` StateFlow
2. Applies the same `_onlineStatusCache` lookup independently
3. Recomputes `contact.copy(isOnline = ...)` for EVERY contact on EVERY emission

**Impact**: With the timeout checker running every 10 seconds, this created unnecessary CPU churn:
- 3 screens open → 3 independent transformations × every 10 seconds
- Each PresenceChanged event → 3 transformations
- Same data, computed 3 times independently

### After Optimization

With `shareIn()`:
- Single `map{}` transformation is computed once
- Result is broadcast to all subscribers
- Automatic cleanup when no subscribers (WhileSubscribed with 5s timeout)
- New subscribers get current state immediately (replay = 1)

**Impact**:
- 3 screens open → 1 transformation, 3 broadcasts
- **66% reduction** in transformation work with 3 subscribers
- Memory overhead: Minimal (replay buffer of 1 per account)

---

## Implementation Details

### Changes to ContactRepositoryImpl.kt

#### 1. Added Imports
```kotlin
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.shareIn
```

#### 2. Added Shared Flow Cache
```kotlin
// Shared flows for optimized contact list transformations (one computation shared across subscribers)
private val sharedContactFlows = mutableMapOf<String, SharedFlow<List<Contact>>>()
```

#### 3. Modified getContacts() Method
**Location:** Lines 105-129

```kotlin
override fun getContacts(accountId: String): Flow<List<Contact>> {
    // Trigger refresh if not cached
    scope.launch {
        if (_contactsCache.value[accountId].isNullOrEmpty()) {
            refreshContacts(accountId)
        }
    }

    // Return cached shared flow or create new one
    // This optimizes the flow so that multiple subscribers share the same transformation
    // instead of each creating their own map{} operation
    return sharedContactFlows.getOrPut(accountId) {
        _contactsCache.map { cache ->
            val contacts = cache[accountId] ?: emptyList()
            // Apply online status from cache
            contacts.map { contact ->
                contact.copy(isOnline = _onlineStatusCache.value[contact.uri] ?: false)
            }
        }.shareIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5000),
            replay = 1
        )
    }
}
```

**Key Parameters:**
- `scope`: Repository's CoroutineScope (survives app lifecycle)
- `started = WhileSubscribed(5000)`: Keeps Flow active while subscribers exist, stops 5 seconds after last unsubscribes
- `replay = 1`: Caches last emission so new subscribers get current state immediately

#### 4. Added Cleanup on Account Logout
**Location:** Lines 65-77

```kotlin
// Load contacts and trust requests when account changes
scope.launch {
    accountRepository.currentAccountId.collect { accountId ->
        if (accountId != null) {
            // Load persisted contacts first
            loadPersistedContacts(accountId)
            // Then refresh from Jami to get latest
            refreshContacts(accountId)
            refreshTrustRequests(accountId)
        } else {
            // Account logged out - clean up shared flows to free resources
            println("ContactRepository: Clearing shared flows (account logout)")
            sharedContactFlows.clear()
        }
    }
}
```

---

## How It Works with Existing Architecture

### Presence Timeout Interaction

The timeout mechanism (`checkPresenceTimeouts()`) runs every 10 seconds and updates `_contactsCache`:

```
Every 10 seconds:
checkPresenceTimeouts()
    ↓
Updates _contactsCache if contacts timed out
    ↓
StateFlow emits to shared Flow
    ↓ (computed ONCE)
Shared Flow transforms contacts + applies online status
    ↓ (broadcast to ALL)
    ├─> ContactsViewModel
    ├─> ContactDetailsViewModel
    └─> ChatViewModel
```

**Before**: 3 transformations per emission
**After**: 1 transformation, 3 broadcasts

### PresenceChanged Event Interaction

When a contact's presence changes:
1. `handleContactEvent(PresenceChanged)` updates `_onlineStatusCache` and `_contactsCache`
2. StateFlow emits new value
3. Shared Flow's upstream `map{}` executes ONCE
4. Result broadcasts to ALL subscribers simultaneously

### Multi-Account Support

The `sharedContactFlows` map is keyed by accountId:
- Each account gets its own shared Flow
- Switching accounts doesn't interfere
- Old account's Flow stops after 5s (WhileSubscribed)
- Cleanup on logout removes all shared flows

---

## Benefits

### Performance
- **CPU reduction**: 66% fewer transformations with 3 subscribers
- **Consistent updates**: All subscribers get updates simultaneously
- **Memory efficient**: Replay buffer of 1 per account

### Code Quality
- **No breaking changes**: ViewModels continue using same API (`getContacts()`, `getContactById()`)
- **Transparent optimization**: Implementation detail, doesn't affect consumers
- **Automatic cleanup**: WhileSubscribed handles lifecycle

### Correctness
- **Identical behavior**: All subscribers get same updates at same time
- **Immediate state**: New subscribers get current state via replay buffer
- **Race-free**: Single transformation eliminates potential inconsistencies

---

## Testing Scenarios

### Scenario 1: Timeout Triggers with Multiple Screens
1. Open ContactsTab (subscribes to `getContacts`)
2. Open ContactDetails for contact A (subscribes to `getContactById` for A)
3. Open Chat with contact A (subscribes to `getContactById` for A)
4. Wait 60+ seconds without contact A sending presence update
5. **Expected**: Timeout checker marks contact A offline
6. **Verify**: ALL three screens update simultaneously showing offline status

**Monitor**: `adb logcat | grep -E "(checkPresenceTimeouts|Presence timeout)"`

### Scenario 2: PresenceChanged Event
1. Same setup: ContactsTab + ContactDetails + Chat all open
2. Contact A comes online (PresenceChanged event fires)
3. **Expected**: All three screens update simultaneously showing online
4. **Verify**: Single transformation triggers all UI updates

### Scenario 3: Flow Lifecycle
1. Open ContactsTab (starts shared Flow)
2. Navigate to ContactDetails (adds second subscriber, Flow stays active)
3. Navigate back, close app
4. Wait 5 seconds
5. **Expected**: Shared Flow stops computing (WhileSubscribed timeout)
6. Reopen app
7. **Expected**: Shared Flow restarts, gets current state immediately (replay = 1)

---

## Future Enhancements

### Per-Contact Flow Caching (Not Implemented)

Could further optimize by caching individual contact Flows:

```kotlin
private val sharedContactByIdFlows = mutableMapOf<String, SharedFlow<Contact?>>()

override fun getContactById(accountId: String, contactId: String): Flow<Contact?> {
    val key = "$accountId:$contactId"
    return sharedContactByIdFlows.getOrPut(key) {
        getContacts(accountId)
            .map { contacts -> contacts.find { it.id == contactId || it.uri == contactId } }
            .shareIn(scope, SharingStarted.WhileSubscribed(5000), replay = 1)
    }
}
```

**Benefits:**
- Multiple ViewModels viewing same contact share `find()` operation
- Further reduces CPU for common scenarios (ContactDetails + Chat for same contact)

**Tradeoffs:**
- More complex cache management
- Map grows with unique contact views
- Need cleanup strategy

**Recommendation**: Add only if profiling shows it's needed.

### Metrics and Monitoring

Could add counters to measure optimization impact:

```kotlin
private var transformationCount = 0

.map { cache ->
    transformationCount++
    println("ContactRepository: Transformation #$transformationCount")
    // ... rest of transformation
}
```

---

## Related Issues

### Presence Broadcasting (Separate Issue - NOT Fixed)

This optimization addresses **Flow efficiency**, NOT the underlying presence broadcast architecture issue.

**Current presence system problems** (not addressed by this change):
- Clients don't broadcast "I am online" when app starts
- No periodic heartbeats to maintain online status
- Daemon serves stale cache from previous sessions
- Timeout is PRIMARY mechanism (not fallback) for marking contacts offline

**Real fix** (future work - Phase 2):
1. Implement proper presence broadcasting when account starts
2. Add periodic heartbeats (every 30-40s)
3. Listen for real network presence updates
4. Use timeout as fallback safety net

See plan for Phase 2 details.

---

## Verification

### Build & Install
```bash
./gradlew :androidApp:assembleDebug
./gradlew :androidApp:installDebug
```

### Monitor Flow Activity
Add debug logs to observe shared flow lifecycle:

```kotlin
.shareIn(...)
.onStart { println("ContactRepository: Shared flow STARTED for $accountId") }
.onCompletion { println("ContactRepository: Shared flow STOPPED for $accountId") }
```

### Monitor Transformations
```bash
adb logcat | grep -E "(Presence timeout|Contact updated|ContactRepository)"
```

---

## Summary

✅ **Implemented**: SharedFlow optimization for contact state management
✅ **Tested**: Build successful, app installed on devices
✅ **Performance**: 66% reduction in transformation work with 3 subscribers
✅ **Compatibility**: No breaking changes, transparent to consumers
✅ **Cleanup**: Automatic via WhileSubscribed + manual on account logout

**Next Steps**: Test with real usage patterns, consider per-contact Flow caching if needed.
