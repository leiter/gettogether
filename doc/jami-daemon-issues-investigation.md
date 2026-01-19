# Jami Daemon Issues Investigation

**Date:** 2026-01-19

## Overview

Investigation of open issues in jami-daemon that may affect GetTogether app stability, focusing on messaging, contact handling, and file transfer.

Source: https://git.jami.net/savoirfairelinux/jami-daemon/-/issues

---

## Critical Issue: #1140

**Title:** Daemon doesn't correctly handle all possible topologies for a conversation's git repository

**Severity:** High | **Status:** In Progress | **Assignee:** François-Simon Fauteux-Chapleau

### Problem Description

Several key repository management functions make incorrect assumptions about git repository structure, causing messages to either fail to display or appear in wrong order until Jami restarts.

Affected functions:
- `ConversationRepository::Impl::log`
- `forEachCommit`
- `behind`
- `Conversation::Impl::addToHistory`

### Root Cause

1. The `behind` function assumes "all new commits are descendants of the merge base between the user's HEAD and the tip of the fetched branch." This breaks in complex topologies where commits exist outside the merge base lineage.

2. `addToHistory` incorrectly assumes fetched messages are always newer than existing ones, forcing incorrect chronological ordering in the UI.

### Impact

- Messages fail to appear in UI
- Messages display in incorrect order
- Message status indicators appear inconsistent
- Affects multi-device scenarios significantly

### Reproduction Scenario

Multi-device, multi-user scenario involving Alice (two devices) and Bob that reliably generates problematic topology - message #1 disappears while others misorder.

### Current Status

- Partial fix exists (PR #31406)
- Does not fully resolve message ordering
- Active development ongoing

### Workaround for GetTogether

- Ensure messages are sorted by timestamp in UI layer (defensive measure)
- Consider adding "refresh" mechanism to reload conversation history
- May need to persist and re-sort messages on load

---

## Messaging Issues

### #1107 - Multi-Device issue (1:1 chat)

**Severity:** Medium

**Problem:** Messages received on only one device of a contact, and chat history becomes mixed up with new messages listed above older messages.

**Impact:** Users with multiple devices will see inconsistent message history.

**Workaround:** Sort messages by timestamp in app layer.

---

### #1081 - MessageEngine sends potentially large number of useless messages

**Severity:** Medium

**Problem:** Multiple bugs cause unnecessary message transmission:
- Duplicate invite requests
- Repeated send attempts

**Impact:** Increased network usage, potential confusion with duplicate messages.

**Workaround:** Deduplicate messages on receive side using message ID.

---

## Contact Management Issues

### #1136 - Contact management needs to be redesigned

**Severity:** High | **Type:** Architecture

**Problem:** Multiple conversations can be created between a given pair of users without proper synchronization handling, causing data loss.

**Details:**
- Current logic for managing one-to-one conversations is flawed
- Can leave users waiting indefinitely for synchronization
- Multi-device scenarios reveal data loss when conversations between same contacts are created across different device pairs

**Impact:** Critical for multi-device users. May see duplicate conversations or lost messages.

**Workaround:**
- Deduplicate conversations by participant URIs (already implemented in GetTogether)
- Keep the conversation with most recent activity

---

### #1098 - User is banned for no reason

**Severity:** Medium

**Problem:** Users receive ban warnings preventing message sending despite successful transmission after restart.

**Impact:** Confusing UX, messages appear to fail but actually succeed.

**Workaround:**
- Add retry logic on ban errors
- Re-check contact status on app restart
- Don't permanently cache ban status

---

### #1071 - Group request fails with avatar

**Severity:** Medium | **Status:** In Progress

**Problem:** Creating a group conversation with an avatar and adding a connected contact results in request failure with a "PJ_ETOOLONG" error.

**Impact:** Group creation fails when avatar is too large.

**Workaround:**
- Compress/resize avatars before sending group invites
- Limit avatar size to prevent PJ_ETOOLONG errors
- Consider max ~100KB for avatars in group requests

---

### #1151 - Swarm invitation signature verification failure

**Severity:** High | **Type:** Non-reproducible

**Problem:** When account C accepts a swarm invitation after account B, C experiences a "signature verification" error and becomes stuck on the sync screen.

**Impact:** Third+ members may fail to join group conversations.

**Workaround:** No known workaround. Monitor for daemon fix.

---

## File Transfer

No open issues found related to file transfer functionality. Appears stable or underused in issue tracker.

---

## Summary: Actionable Items for GetTogether

### Low Effort (Can Implement Now)

| Issue | Action | File/Location |
|-------|--------|---------------|
| #1140, #1107 | Sort messages by timestamp in UI | `ConversationsViewModel.kt` |
| #1081 | Deduplicate messages by ID on receive | `ConversationRepositoryImpl.kt` |
| #1071 | Compress avatars before group creation | `SwigJamiBridge.kt` |
| #1098 | Don't persist ban status, re-check on restart | `ContactRepositoryImpl.kt` |
| #1136 | Deduplicate conversations by participants | Already implemented |

### Monitor for Daemon Fixes

| Issue | Description | PR/Status |
|-------|-------------|-----------|
| #1140 | Message ordering | PR #31406 (partial) |
| #1136 | Contact management redesign | Architecture work needed |
| #1151 | Swarm invitation verification | Non-reproducible |

### Already Mitigated in GetTogether

- **Conversation deduplication**: `ConversationRepositoryImpl.kt` already deduplicates by participant URIs
- **Message timestamp sorting**: Messages sorted by timestamp in UI layer

---

## References

- Jami Daemon Issues: https://git.jami.net/savoirfairelinux/jami-daemon/-/issues
- PR #31406 (partial fix for #1140): https://git.jami.net/savoirfairelinux/jami-daemon/-/merge_requests/31406
