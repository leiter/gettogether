//
//  SwiftJamiBridgeAdapter.swift
//  GetTogether
//
//  Swift adapter that bridges between Kotlin and JamiBridgeWrapper.
//  Implements the Kotlin NativeBridgeOperations protocol.
//

import Foundation
import Shared

/// Swift adapter that wraps JamiBridgeWrapper and implements NativeBridgeOperations.
/// This class bridges between the Kotlin shared module and the native libjami C++ wrapper.
class SwiftJamiBridgeAdapter: NSObject, NativeBridgeOperations, JamiBridgeDelegate {

    static let shared = SwiftJamiBridgeAdapter()

    private let wrapper = JamiBridgeWrapper.shared()

    /// The Kotlin callback for receiving events (retrieved from NativeBridgeProvider)
    private var callback: NativeBridgeCallback? {
        return NativeBridgeProvider.shared.callback
    }

    private override init() {
        super.init()
        wrapper.delegate = self
        NSLog("[SwiftJamiBridgeAdapter] Initialized")
    }

    /// Register this adapter with Kotlin's NativeBridgeProvider
    static func register() {
        NSLog("[SwiftJamiBridgeAdapter] Registering with NativeBridgeProvider")
        NativeBridgeProvider.shared.setOperations(ops: SwiftJamiBridgeAdapter.shared)
    }

    // MARK: - NativeBridgeOperations Protocol Implementation

    // MARK: Daemon Lifecycle

    func doInitDaemon(dataPath: String) {
        NSLog("[SwiftJamiBridgeAdapter] doInitDaemon: \(dataPath)")
        wrapper.initDaemon(withDataPath: dataPath)
    }

    func startDaemon() {
        NSLog("[SwiftJamiBridgeAdapter] startDaemon")
        wrapper.startDaemon()
    }

    func stopDaemon() {
        NSLog("[SwiftJamiBridgeAdapter] stopDaemon")
        wrapper.stopDaemon()
    }

    func isDaemonRunning() -> Bool {
        return wrapper.isDaemonRunning()
    }

    // MARK: Account Management

    func createAccount(displayName: String, password: String) -> String {
        return wrapper.createAccount(withDisplayName: displayName, password: password)
    }

    func importAccount(archivePath: String, password: String) -> String {
        return wrapper.importAccount(fromArchive: archivePath, password: password)
    }

    func exportAccount(accountId: String, destinationPath: String, password: String) -> Bool {
        return wrapper.exportAccount(accountId, toDestinationPath: destinationPath, withPassword: password)
    }

    func deleteAccount(accountId: String) {
        wrapper.deleteAccount(accountId)
    }

    func getAccountIds() -> [String] {
        return wrapper.getAccountIds() as? [String] ?? []
    }

    func getAccountDetails(accountId: String) -> [String: String] {
        return wrapper.getAccountDetails(accountId) as? [String: String] ?? [:]
    }

    func getVolatileAccountDetails(accountId: String) -> [String: String] {
        return wrapper.getVolatileAccountDetails(accountId) as? [String: String] ?? [:]
    }

    func setAccountDetails(accountId: String, details: [String: String]) {
        wrapper.setAccountDetails(accountId, details: details)
    }

    func setAccountActive(accountId: String, active: Bool) {
        wrapper.setAccountActive(accountId, active: active)
    }

    func updateProfile(accountId: String, displayName: String, avatarPath: String?) {
        wrapper.updateProfile(accountId, displayName: displayName, avatarPath: avatarPath)
    }

    func registerName(accountId: String, name: String, password: String) -> Bool {
        return wrapper.registerName(accountId, name: name, password: password)
    }

    func lookupName(accountId: String, name: String) {
        _ = wrapper.lookupName(accountId, name: name)
    }

    func lookupAddress(accountId: String, address: String) {
        _ = wrapper.lookupAddress(accountId, address: address)
    }

    // MARK: Contact Management

    func getContacts(accountId: String) -> [[String: Any]] {
        let contacts = wrapper.getContacts(accountId)
        return contacts.map { contact in
            [
                "uri": contact.uri as Any,
                "displayName": contact.displayName as Any,
                "avatarPath": contact.avatarPath as Any,
                "isConfirmed": contact.isConfirmed as Any,
                "isBanned": contact.isBanned as Any
            ]
        }
    }

    func addContact(accountId: String, uri: String) {
        wrapper.addContact(accountId, uri: uri)
    }

    func removeContact(accountId: String, uri: String, ban: Bool) {
        wrapper.removeContact(accountId, uri: uri, ban: ban)
    }

    func getContactDetails(accountId: String, uri: String) -> [String: String] {
        return wrapper.getContactDetails(accountId, uri: uri) as? [String: String] ?? [:]
    }

    func acceptTrustRequest(accountId: String, uri: String) {
        wrapper.acceptTrustRequest(accountId, uri: uri)
    }

    func discardTrustRequest(accountId: String, uri: String) {
        wrapper.discardTrustRequest(accountId, uri: uri)
    }

    func getTrustRequests(accountId: String) -> [[String: Any]] {
        let requests = wrapper.getTrustRequests(accountId)
        return requests.map { request in
            [
                "from": request.from as Any,
                "conversationId": request.conversationId as Any,
                "received": request.received as Any
            ]
        }
    }

    // MARK: Conversation Management

    func getConversations(accountId: String) -> [String] {
        return wrapper.getConversations(accountId) as? [String] ?? []
    }

    func startConversation(accountId: String) -> String {
        return wrapper.startConversation(accountId)
    }

    func removeConversation(accountId: String, conversationId: String) {
        wrapper.removeConversation(accountId, conversationId: conversationId)
    }

    func getConversationInfo(accountId: String, conversationId: String) -> [String: String] {
        return wrapper.getConversationInfo(accountId, conversationId: conversationId) as? [String: String] ?? [:]
    }

    func updateConversationInfo(accountId: String, conversationId: String, info: [String: String]) {
        wrapper.updateConversationInfo(accountId, conversationId: conversationId, info: info)
    }

    func getConversationMembers(accountId: String, conversationId: String) -> [[String: Any]] {
        let members = wrapper.getConversationMembers(accountId, conversationId: conversationId)
        return members.map { member in
            [
                "uri": member.uri as Any,
                "role": member.role.rawValue as Any
            ]
        }
    }

    func addConversationMember(accountId: String, conversationId: String, contactUri: String) {
        wrapper.addConversationMember(accountId, conversationId: conversationId, contactUri: contactUri)
    }

    func removeConversationMember(accountId: String, conversationId: String, contactUri: String) {
        wrapper.removeConversationMember(accountId, conversationId: conversationId, contactUri: contactUri)
    }

    func acceptConversationRequest(accountId: String, conversationId: String) {
        wrapper.acceptConversationRequest(accountId, conversationId: conversationId)
    }

    func declineConversationRequest(accountId: String, conversationId: String) {
        wrapper.declineConversationRequest(accountId, conversationId: conversationId)
    }

    func getConversationRequests(accountId: String) -> [[String: Any]] {
        let requests = wrapper.getConversationRequests(accountId)
        return requests.map { request in
            [
                "conversationId": request.conversationId as Any,
                "from": request.from as Any,
                "metadata": request.metadata ?? [:] as Any,
                "received": request.received as Any
            ]
        }
    }

    // MARK: Messaging

    func sendMessage(accountId: String, conversationId: String, message: String, replyTo: String?) -> String {
        return wrapper.sendMessage(accountId, conversationId: conversationId, message: message, replyTo: replyTo)
    }

    func loadConversationMessages(accountId: String, conversationId: String, fromMessage: String, count: Int32) -> Int32 {
        return wrapper.loadConversationMessages(accountId, conversationId: conversationId, fromMessage: fromMessage, count: count)
    }

    func setIsComposing(accountId: String, conversationId: String, isComposing: Bool) {
        wrapper.setIsComposing(accountId, conversationId: conversationId, isComposing: isComposing)
    }

    func setMessageDisplayed(accountId: String, conversationId: String, messageId: String) {
        wrapper.setMessageDisplayed(accountId, conversationId: conversationId, messageId: messageId)
    }

    // MARK: Calls

    func placeCall(accountId: String, uri: String, withVideo: Bool) -> String {
        return wrapper.placeCall(accountId, uri: uri, withVideo: withVideo)
    }

    func acceptCall(accountId: String, callId: String, withVideo: Bool) {
        wrapper.acceptCall(accountId, callId: callId, withVideo: withVideo)
    }

    func refuseCall(accountId: String, callId: String) {
        wrapper.refuseCall(accountId, callId: callId)
    }

    func hangUp(accountId: String, callId: String) {
        wrapper.hangUp(accountId, callId: callId)
    }

    func holdCall(accountId: String, callId: String) {
        wrapper.holdCall(accountId, callId: callId)
    }

    func unholdCall(accountId: String, callId: String) {
        wrapper.unholdCall(accountId, callId: callId)
    }

    func muteAudio(accountId: String, callId: String, muted: Bool) {
        wrapper.muteAudio(accountId, callId: callId, muted: muted)
    }

    func muteVideo(accountId: String, callId: String, muted: Bool) {
        wrapper.muteVideo(accountId, callId: callId, muted: muted)
    }

    func getCallDetails(accountId: String, callId: String) -> [String: String] {
        return wrapper.getCallDetails(accountId, callId: callId) as? [String: String] ?? [:]
    }

    func getActiveCalls(accountId: String) -> [String] {
        return wrapper.getActiveCalls(accountId) as? [String] ?? []
    }

    func switchCamera() {
        wrapper.switchCamera()
    }

    func switchAudioOutput(useSpeaker: Bool) {
        wrapper.switchAudioOutputUseSpeaker(useSpeaker)
    }

    // MARK: Video/Audio

    func getVideoDevices() -> [String] {
        return wrapper.getVideoDevices() as? [String] ?? []
    }

    func getCurrentVideoDevice() -> String {
        return wrapper.getCurrentVideoDevice()
    }

    func setVideoDevice(deviceId: String) {
        wrapper.setVideoDevice(deviceId)
    }

    func startVideo() {
        wrapper.startVideo()
    }

    func stopVideo() {
        wrapper.stopVideo()
    }

    func getAudioOutputDevices() -> [String] {
        return wrapper.getAudioOutputDevices() as? [String] ?? []
    }

    func setAudioOutputDevice(index: Int32) {
        wrapper.setAudioOutputDevice(index)
    }

    // MARK: - JamiBridgeDelegate Implementation
    // These methods are called by JamiBridgeWrapper when events occur from libjami.
    // We forward them to the Kotlin callback.

    func onRegistrationStateChanged(_ accountId: String, state: JBRegistrationState, code: Int32, detail: String) {
        callback?.onRegistrationStateChanged(accountId: accountId, state: Int32(state.rawValue), code: code, detail: detail)
    }

    func onAccountDetailsChanged(_ accountId: String, details: [String: String]) {
        callback?.onAccountDetailsChanged(accountId: accountId, details: details)
    }

    func onNameRegistrationEnded(_ accountId: String, state: Int32, name: String) {
        callback?.onNameRegistrationEnded(accountId: accountId, state: state, name: name)
    }

    func onRegisteredNameFound(_ accountId: String, state: JBLookupState, address: String, name: String) {
        callback?.onRegisteredNameFound(accountId: accountId, state: Int32(state.rawValue), address: address, name: name)
    }

    func onProfileReceived(_ accountId: String, from: String, displayName: String, avatarPath: String?) {
        callback?.onProfileReceived(accountId: accountId, from: from, displayName: displayName, avatarPath: avatarPath)
    }

    func onContactAdded(_ accountId: String, uri: String, confirmed: Bool) {
        callback?.onContactAdded(accountId: accountId, uri: uri, confirmed: confirmed)
    }

    func onContactRemoved(_ accountId: String, uri: String, banned: Bool) {
        callback?.onContactRemoved(accountId: accountId, uri: uri, banned: banned)
    }

    func onIncomingTrustRequest(_ accountId: String, conversationId: String, from: String, payload: Data, received: Int64) {
        callback?.onIncomingTrustRequest(accountId: accountId, conversationId: conversationId, from: from, received: received)
    }

    func onPresenceChanged(_ accountId: String, uri: String, isOnline: Bool) {
        callback?.onPresenceChanged(accountId: accountId, uri: uri, isOnline: isOnline)
    }

    func onConversationReady(_ accountId: String, conversationId: String) {
        callback?.onConversationReady(accountId: accountId, conversationId: conversationId)
    }

    func onConversationRemoved(_ accountId: String, conversationId: String) {
        callback?.onConversationRemoved(accountId: accountId, conversationId: conversationId)
    }

    func onConversationRequestReceived(_ accountId: String, conversationId: String, metadata: [String: String]) {
        callback?.onConversationRequestReceived(accountId: accountId, conversationId: conversationId, metadata: metadata)
    }

    func onMessageReceived(_ accountId: String, conversationId: String, message: JBSwarmMessage) {
        let messageData = swarmMessageToDict(message)
        callback?.onMessageReceived(accountId: accountId, conversationId: conversationId, messageData: messageData)
    }

    func onMessageUpdated(_ accountId: String, conversationId: String, message: JBSwarmMessage) {
        let messageData = swarmMessageToDict(message)
        callback?.onMessageUpdated(accountId: accountId, conversationId: conversationId, messageData: messageData)
    }

    func onMessagesLoaded(_ requestId: Int32, accountId: String, conversationId: String, messages: [JBSwarmMessage]) {
        let messagesData = messages.map { swarmMessageToDict($0) }
        callback?.onMessagesLoaded(requestId: requestId, accountId: accountId, conversationId: conversationId, messages: messagesData)
    }

    func onConversationMemberEvent(_ accountId: String, conversationId: String, memberUri: String, event: JBMemberEventType) {
        callback?.onConversationMemberEvent(accountId: accountId, conversationId: conversationId, memberUri: memberUri, event: Int32(event.rawValue))
    }

    func onComposingStatusChanged(_ accountId: String, conversationId: String, from: String, isComposing: Bool) {
        callback?.onComposingStatusChanged(accountId: accountId, conversationId: conversationId, from: from, isComposing: isComposing)
    }

    func onConversationProfileUpdated(_ accountId: String, conversationId: String, profile: [String: String]) {
        callback?.onConversationProfileUpdated(accountId: accountId, conversationId: conversationId, profile: profile)
    }

    func onIncomingCall(_ accountId: String, callId: String, peerId: String, peerDisplayName: String, hasVideo: Bool) {
        callback?.onIncomingCall(accountId: accountId, callId: callId, peerId: peerId, peerDisplayName: peerDisplayName, hasVideo: hasVideo)
    }

    func onCallStateChanged(_ accountId: String, callId: String, state: JBCallState, code: Int32) {
        callback?.onCallStateChanged(accountId: accountId, callId: callId, state: Int32(state.rawValue), code: code)
    }

    func onAudioMuted(_ callId: String, muted: Bool) {
        callback?.onAudioMuted(callId: callId, muted: muted)
    }

    func onVideoMuted(_ callId: String, muted: Bool) {
        callback?.onVideoMuted(callId: callId, muted: muted)
    }

    func onConferenceCreated(_ accountId: String, conversationId: String, conferenceId: String) {
        callback?.onConferenceCreated(accountId: accountId, conversationId: conversationId, conferenceId: conferenceId)
    }

    func onConferenceChanged(_ accountId: String, conferenceId: String, state: String) {
        callback?.onConferenceChanged(accountId: accountId, conferenceId: conferenceId, state: state)
    }

    func onConferenceRemoved(_ accountId: String, conferenceId: String) {
        callback?.onConferenceRemoved(accountId: accountId, conferenceId: conferenceId)
    }

    // MARK: - Helpers

    private func swarmMessageToDict(_ message: JBSwarmMessage) -> [String: Any] {
        var reactions: [[String: String]] = []
        if let messageReactions = message.reactions as? [[String: String]] {
            reactions = messageReactions
        }

        return [
            "id": message.messageId as Any,
            "type": message.type as Any,
            "author": message.author as Any,
            "body": message.body ?? [:] as Any,
            "reactions": reactions as Any,
            "timestamp": message.timestamp as Any,
            "replyTo": message.replyTo as Any,
            "status": message.status ?? [:] as Any
        ]
    }
}
