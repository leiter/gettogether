# Contact Flow Issues - Analysis Report

## Test Date
2025-12-20 16:14

## Test Environment
- **Hardware Device**: Pixel 7a (37281JEHN03065)
  - User: TestUser_Pixel7a
  - Jami ID: 11f22a30a9efbf032c9eee05f98939e31bf0a412

- **Emulator**: Pixel 9 (emulator-5554)
  - User: Bob
  - Jami ID: 6d8df010b1b1fc62abadbb067ca8ddf36ad0063c

## Issues Identified

### 1. Name Server Lookup Failures
**Severity**: High
**Location**: `AddContactViewModel.kt:69`, `JamiBridge.lookupName()`

**Description**:
- Name lookups to https://ns.jami.net are failing with code=0
- Both username lookup ("Bob") and direct ID lookup fail
- This prevents users from finding contacts by registered usernames

**Logs**:
```
E namedirectory.cpp: Name lookup for 6d8df010b1b1fc62abadbb067ca8ddf36ad0063c failed with code=0
E namedirectory.cpp: Name lookup for Bob failed with code=0
```

**Root Cause**:
- Usernames may not be actually registered on the name server
- Possible network connectivity issues
- Name server integration may not be fully working

**Impact**: Users cannot search for contacts by username or validate Jami IDs via name server.

---

### 2. No Trust Request Handling
**Severity**: Critical
**Location**: `ContactRepositoryImpl.kt:234-236`

**Description**:
- `IncomingTrustRequest` events are received but not handled
- Only has a comment: "Could be handled to show pending contact requests"
- No UI exists to display incoming trust requests
- No way for users to accept or reject contact requests

**Code**:
```kotlin
is JamiContactEvent.IncomingTrustRequest -> {
    // Could be handled to show pending contact requests
}
```

**Impact**: When User A adds User B as a contact, User B never sees the request and cannot accept it.

---

### 3. Trust Requests Not Fetched on Startup
**Severity**: High
**Location**: `ContactRepositoryImpl.kt`, initialization

**Description**:
- When the app starts, existing pending trust requests are not loaded
- `jamiBridge.getTrustRequests(accountId)` is never called
- Users won't see requests that arrived while the app was closed

**Impact**: Trust requests that came in while offline are never shown to the user.

---

### 4. No Trust Request UI
**Severity**: Critical
**Location**: UI layer (missing)

**Description**:
- No UI component exists to show pending trust requests
- No "Accept"/"Reject" buttons for incoming requests
- No notification or indicator that a request is pending

**Required Components**:
- Trust request list screen or section
- Accept/Reject action buttons
- Optional: Display requesting user's name/ID
- Optional: Show trust request payload (VCard data)

**Impact**: Even if trust requests are received, users have no way to act on them.

---

### 5. Unconfirmed Contacts Not Shown
**Severity**: Medium
**Location**: `ContactRepositoryImpl.kt:159-170`, `ContactsViewModel.kt:64-66`

**Description**:
- When `addContact()` is called, it creates a placeholder contact in cache
- However, the contact might not be confirmed yet (pending trust request)
- The `JamiContact.isConfirmed` field indicates confirmation status
- Currently, all contacts are shown regardless of confirmation status

**Potential Issue**: Contacts may appear in the list before being actually confirmed by the other party.

---

### 6. No Error Handling for Failed Contact Addition
**Severity**: Medium
**Location**: `AddContactViewModel.kt:125-143`

**Description**:
- When `addContact()` fails, error is shown in state
- However, user feedback might not be clear
- No retry mechanism
- No guidance on what to do if addition fails

---

## Test Flow Attempted

1. ✅ Navigated to Add Contact screen on hardware device
2. ✅ Entered Bob's Jami ID: `6d8df010b1b1fc62abadbb067ca8ddf36ad0063c`
3. ❌ Search button clicked - no results returned (name server lookup failed)
4. ✅ Cleared and tried username "Bob"
5. ❌ Search failed again (name server lookup failed)

**Expected**: Search should find Bob either by username or by direct Jami ID match
**Actual**: Name server lookups failed, no results shown

## Acceptance Criteria (from user)

1. Setup fresh user on each phone
2. One user can search and add the other
3. The other receives a connection request and accepts it
4. After app restart, both have the new contact available in contact list

**Current Status**: ❌ **FAILING**
- Step 2: Partially works (can enter ID but search fails)
- Step 3: **COMPLETELY BROKEN** - no way to receive or accept requests
- Step 4: Unknown - cannot test until steps 2-3 work

## Recommended Fixes

### Priority 1: Implement Trust Request Handling
1. Handle `IncomingTrustRequest` events in `ContactRepositoryImpl`
2. Store trust requests in a StateFlow
3. Create a `TrustRequestsViewModel` to manage trust request state
4. Implement `acceptTrustRequest()` and `discardTrustRequest()` in repository

### Priority 2: Add Trust Request UI
1. Create a trust requests section in Contacts screen or separate screen
2. Add Accept/Reject buttons for each pending request
3. Show requesting user's Jami ID and display name (if available)
4. Add notification badges/counts

### Priority 3: Load Trust Requests on Startup
1. Call `getTrustRequests()` when account is loaded
2. Populate trust request cache
3. Subscribe to `IncomingTrustRequest` events for real-time updates

### Priority 4: Fix Name Server Issues
1. Add better error handling for failed lookups
2. Implement fallback to direct ID matching for 40-char hex IDs
3. Add user feedback when name server is unavailable
4. Consider caching successful lookups

### Priority 5: Improve Contact Search
1. Make 40-char hex ID search work without name server (already partially implemented)
2. Add validation feedback
3. Show loading states
4. Add better error messages

## Next Steps

1. Implement trust request handling in `ContactRepositoryImpl`
2. Create UI components for trust requests
3. Test full contact addition flow
4. Verify contacts persist after app restart
5. Test with fresh users
