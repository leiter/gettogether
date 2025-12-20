# isBanned Bug Fix Report

**Date:** 2025-12-20
**Status:** ✅ **FIXED AND VERIFIED**

## Summary

Fixed a critical bug where all contacts were being marked as `isBanned: true` when they should have been `isBanned: false`.

## Root Cause

**File:** `ConversationRepositoryImpl.kt`
**Line:** 371
**Method:** `clearAllConversations()`

The method was calling `removeContact()` with `ban = true` instead of `ban = false`:

```kotlin
// BEFORE (incorrect)
jamiBridge.removeContact(accountId, contactUri, ban = true)

// AFTER (correct)
jamiBridge.removeContact(accountId, contactUri, ban = false)
```

## Investigation Process

1. **Initial observation:** All persisted contacts had `"isBanned": true` in the JSON
2. **Log analysis:** Found daemon logs showing `removeContact` with `banned: true`
3. **Grep search:** Located all `removeContact` calls with `ban = true`
4. **Context review:** Discovered `clearAllConversations` was incorrectly banning contacts
5. **Fix applied:** Changed `ban = true` to `ban = false` on line 371

## Evidence

### Before Fix
```json
{
  "id": "abc123def456789012345678901234567890abcd",
  "uri": "abc123def456789012345678901234567890abcd",
  "displayName": "abc123de",
  "isBanned": true  ← BUG: Should be false
}
```

### After Fix
```json
{
  "id": "14ef050905ecb47b511d3e29e968d10a5bec91f5",
  "uri": "14ef050905ecb47b511d3e29e968d10a5bec91f5",
  "displayName": "14ef0509"
  // isBanned field omitted = defaults to false ✅
}
```

## Log Evidence

**Before fix:**
```
ConversationRepository.clearAllConversations: Banning contact 14ef050905ecb47b511d3e29e968d10a5bec91f5
contact_list.cpp: removeContact: 14ef050905ecb47b511d3e29e968d10a5bec91f5 (banned: true)
```

**After fix:**
```
ConversationRepository.clearAllConversations: Removing contact 14ef050905ecb47b511d3e29e968d10a5bec91f5
contact_list.cpp: removeContact: 14ef050905ecb47b511d3e29e968d10a5bec91f5 (banned: false)
```

## Changes Made

**File:** `shared/src/commonMain/kotlin/com/gettogether/app/data/repository/ConversationRepositoryImpl.kt`

**Line 371:**
```kotlin
- jamiBridge.removeContact(accountId, contactUri, ban = true)
+ jamiBridge.removeContact(accountId, contactUri, ban = false)
```

**Line 370 (log message):**
```kotlin
- println("ConversationRepository.clearAllConversations: Banning contact $contactUri")
+ println("ConversationRepository.clearAllConversations: Removing contact $contactUri")
```

**Line 373 (error log):**
```kotlin
- println("ConversationRepository.clearAllConversations: Failed to ban contact $contactUri: ${e.message}")
+ println("ConversationRepository.clearAllConversations: Failed to remove contact $contactUri: ${e.message}")
```

## Testing

### Test Steps
1. ✅ Built app with fix
2. ✅ Cleared app data
3. ✅ Created new account
4. ✅ Added contact (Jami ID: `14ef050905ecb47b511d3e29e968d10a5bec91f5`)
5. ✅ Verified contact appears in Contacts tab
6. ✅ Checked persisted JSON - `isBanned` field absent (defaults to `false`)
7. ✅ Verified logs show `banned: false` instead of `banned: true`

### Test Result
**PASS** - Contacts are no longer being incorrectly banned.

## Impact

### Before Fix
- All contacts were marked as banned
- Contact persistence worked, but status was incorrect
- Caused UX confusion (contacts shown as "Offline" but internally banned)

### After Fix
- Contacts have correct ban status (`false` by default)
- Only explicitly banned/blocked contacts marked as `isBanned: true`
- Proper distinction between removed and banned contacts

## Related Systems

This fix also impacts:
- **Contact persistence** (tested in this session - working ✅)
- **Conversation management** - contacts no longer incorrectly banned during cleanup
- **UI display** - correct status shown for contacts

## Notes

- The `clearAllConversations` method is called during app initialization/cleanup
- The method's purpose is to remove contacts to prevent conversations from being recreated
- Banning was too aggressive - contacts should just be removed, not banned
- This was likely causing confusion between "remove" and "ban" operations

## Verification Checklist

- ✅ Code review completed
- ✅ Fix applied and tested
- ✅ Persisted data verified
- ✅ Logs confirmed correct behavior
- ✅ No regression in contact persistence
- ✅ Build successful
