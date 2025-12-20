# Message UI Investigation - 2025-12-20

## Critical Finding: Message Delivery IS Working

### Evidence from Logcat

**Cross-device message delivery confirmed:**

**Pixel 2 → Pixel 7a:**
```
[Pixel 2] 18:20:42.681 sendMessage: message='mein'
[Pixel 7a] 18:20:43.818 swarmMessageReceived: author=14ef050905ecb47b511d3e29e968d10a5bec91f5, body=mein
[Pixel 7a] 18:20:43.821 Message added to cache, key=2078d1ac09712bfb:03105cfa433a09a674ef34a90ab0347bdf0bef02, total messages=1
```

**Pixel 7a → Pixel 7a (self):**
```
[Pixel 7a] 18:19:53.190 sendMessage: message='hi'
[Pixel 7a] 18:19:53.240 swarmMessageReceived: body=hi
[Pixel 7a] 18:19:53.240 Message added to cache, key=2078d1ac09712bfb:82ef36bb7480831587c701fb2018bbe6b5a5a76d, total messages=2
```

### What's Working

✅ **Message sending** - JamiService.sendMessage() called successfully
✅ **P2P delivery** - Messages delivered across devices
✅ **Callback reception** - swarmMessageReceived fires correctly
✅ **Event emission** - Events emitted successfully
✅ **Repository handling** - Messages added to cache
✅ **Same conversation ID** - Both devices use same conversation ID

### What's NOT Working

❌ **UI display** - Messages in cache not shown in UI

## Root Cause: UI Layer Issue

Since messages are confirmed in the repository cache but not visible in UI, the problem must be in:

1. **MessagesViewModel** - Not observing correct flow
2. **MessagesScreen** - Not rendering messages from state
3. **Flow observation** - StateFlow not triggering recomposition
4. **Conversation ID mismatch** - UI observing different conversation than repository cache key

## Next Steps

1. Check MessagesViewModel - verify it observes messages flow correctly
2. Check MessagesScreen - verify it renders messages from state
3. Add ViewModel logging to track message flow UI → Repository → Cache → UI
4. Verify conversation ID used by UI matches cache keys

## Log Examples

**Successful message send:**
```
I SwigJamiBridge: sendMessage: accountId=2078d1ac09712bfb, conversationId=82ef36bb7480831587c701fb2018bbe6b5a5a76d, message='hi'
I SwigJamiBridge: sendMessage: JamiService.sendMessage called successfully
```

**Successful message receive:**
```
I SwigJamiBridge: swarmMessageReceived: accountId=2078d1ac09712bfb, conversationId=82ef36bb7480831587c701fb2018bbe6b5a5a76d, message=3acc157eae97c08918ddf80314a1eb257834e6e1
I SwigJamiBridge: swarmMessageReceived: Converted message - id=3acc157eae97c08918ddf80314a1eb257834e6e1, author=11f22a30a9efbf032c9eee05f98939e31bf0a412, body={... body=hi ...}
I SwigJamiBridge: swarmMessageReceived: Event emitted=true
I System.out: ConversationRepository.handleConversationEvent: MessageReceived - accountId=2078d1ac09712bfb, conversationId=82ef36bb7480831587c701fb2018bbe6b5a5a76d, messageId=3acc157eae97c08918ddf80314a1eb257834e6e1, author=11f22a30a9efbf032c9eee05f98939e31bf0a412, content=hi
I System.out: ConversationRepository.handleConversationEvent: Message added to cache, key=2078d1ac09712bfb:82ef36bb7480831587c701fb2018bbe6b5a5a76d, total messages=2
```

---

**Investigation Date:** 2025-12-20 19:30
**Status:** Message delivery CONFIRMED working, UI display issue identified
**Devices:** Pixel 7a (37281JEHN03065) + Pixel 2 (FA7AJ1A06417)
