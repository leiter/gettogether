import Foundation
import CallKit
import AVFoundation
import UIKit

@objc public class CallKitManager: NSObject {

    @objc public static let shared = CallKitManager()

    private let provider: CXProvider
    private let callController = CXCallController()

    // Track active calls: callId -> UUID
    private var activeCalls: [String: UUID] = [:]
    // Track call states
    private var callStates: [UUID: CallState] = [:]

    // Audio state
    private var isMuted: Bool = false
    private var isSpeakerOn: Bool = false

    private struct CallState {
        var callId: String
        var contactId: String
        var contactName: String
        var hasVideo: Bool
        var isOutgoing: Bool
        var isConnected: Bool = false
    }

    private override init() {
        let configuration = CXProviderConfiguration()
        configuration.supportsVideo = true
        configuration.maximumCallsPerCallGroup = 1
        configuration.maximumCallGroups = 1
        configuration.supportedHandleTypes = [.generic, .phoneNumber]
        configuration.includesCallsInRecents = true

        // Set icon (iconTemplateImageData should be a 40x40 monochrome image)
        if let iconImage = UIImage(named: "AppIcon") {
            configuration.iconTemplateImageData = iconImage.pngData()
        }

        provider = CXProvider(configuration: configuration)

        super.init()

        provider.setDelegate(self, queue: DispatchQueue.main)

        // Configure audio session
        configureAudioSession()
    }

    private func configureAudioSession() {
        do {
            let audioSession = AVAudioSession.sharedInstance()
            try audioSession.setCategory(.playAndRecord, mode: .voiceChat, options: [.allowBluetoothA2DP, .defaultToSpeaker])
        } catch {
            NSLog("CallKitManager: Failed to configure audio session: \(error)")
        }
    }

    // MARK: - Public API

    @objc public func reportIncomingCall(
        callId: String,
        contactId: String,
        contactName: String,
        hasVideo: Bool,
        completion: @escaping (Error?) -> Void
    ) {
        let uuid = UUID()
        activeCalls[callId] = uuid
        callStates[uuid] = CallState(
            callId: callId,
            contactId: contactId,
            contactName: contactName,
            hasVideo: hasVideo,
            isOutgoing: false
        )

        let update = CXCallUpdate()
        update.remoteHandle = CXHandle(type: .generic, value: contactId)
        update.localizedCallerName = contactName
        update.hasVideo = hasVideo
        update.supportsGrouping = false
        update.supportsUngrouping = false
        update.supportsHolding = true
        update.supportsDTMF = false

        NSLog("CallKitManager: Reporting incoming call from \(contactName) (callId: \(callId))")

        provider.reportNewIncomingCall(with: uuid, update: update) { error in
            if let error = error {
                NSLog("CallKitManager: Failed to report incoming call: \(error)")
                self.activeCalls.removeValue(forKey: callId)
                self.callStates.removeValue(forKey: uuid)
            }
            completion(error)
        }
    }

    @objc public func startOutgoingCall(
        contactId: String,
        contactName: String,
        hasVideo: Bool
    ) -> String {
        let uuid = UUID()
        let callId = uuid.uuidString

        activeCalls[callId] = uuid
        callStates[uuid] = CallState(
            callId: callId,
            contactId: contactId,
            contactName: contactName,
            hasVideo: hasVideo,
            isOutgoing: true
        )

        let handle = CXHandle(type: .generic, value: contactId)
        let startCallAction = CXStartCallAction(call: uuid, handle: handle)
        startCallAction.isVideo = hasVideo
        startCallAction.contactIdentifier = contactName

        let transaction = CXTransaction(action: startCallAction)

        NSLog("CallKitManager: Starting outgoing call to \(contactName)")

        callController.request(transaction) { error in
            if let error = error {
                NSLog("CallKitManager: Failed to start call: \(error)")
                self.activeCalls.removeValue(forKey: callId)
                self.callStates.removeValue(forKey: uuid)
            } else {
                // Update call display
                let update = CXCallUpdate()
                update.remoteHandle = handle
                update.localizedCallerName = contactName
                update.hasVideo = hasVideo
                self.provider.reportCall(with: uuid, updated: update)
            }
        }

        return callId
    }

    @objc public func answerCall(callId: String) {
        guard let uuid = activeCalls[callId] else {
            NSLog("CallKitManager: No active call with id: \(callId)")
            return
        }

        let answerAction = CXAnswerCallAction(call: uuid)
        let transaction = CXTransaction(action: answerAction)

        NSLog("CallKitManager: Answering call \(callId)")

        callController.request(transaction) { error in
            if let error = error {
                NSLog("CallKitManager: Failed to answer call: \(error)")
            }
        }
    }

    @objc public func endCall(callId: String) {
        guard let uuid = activeCalls[callId] else {
            NSLog("CallKitManager: No active call with id: \(callId)")
            return
        }

        let endAction = CXEndCallAction(call: uuid)
        let transaction = CXTransaction(action: endAction)

        NSLog("CallKitManager: Ending call \(callId)")

        callController.request(transaction) { error in
            if let error = error {
                NSLog("CallKitManager: Failed to end call: \(error)")
            }
        }
    }

    @objc public func endAllCalls() {
        NSLog("CallKitManager: Ending all calls")
        for (callId, _) in activeCalls {
            endCall(callId: callId)
        }
    }

    @objc public func setMuted(_ muted: Bool, callId: String) {
        guard let uuid = activeCalls[callId] else {
            NSLog("CallKitManager: No active call with id: \(callId)")
            return
        }

        let muteAction = CXSetMutedCallAction(call: uuid, muted: muted)
        let transaction = CXTransaction(action: muteAction)

        NSLog("CallKitManager: Setting mute to \(muted) for call \(callId)")

        callController.request(transaction) { error in
            if let error = error {
                NSLog("CallKitManager: Failed to set mute: \(error)")
            } else {
                self.isMuted = muted
            }
        }
    }

    @objc public func toggleMute(callId: String) {
        setMuted(!isMuted, callId: callId)
    }

    @objc public func setHeld(_ held: Bool, callId: String) {
        guard let uuid = activeCalls[callId] else {
            NSLog("CallKitManager: No active call with id: \(callId)")
            return
        }

        let holdAction = CXSetHeldCallAction(call: uuid, onHold: held)
        let transaction = CXTransaction(action: holdAction)

        NSLog("CallKitManager: Setting hold to \(held) for call \(callId)")

        callController.request(transaction) { error in
            if let error = error {
                NSLog("CallKitManager: Failed to set hold: \(error)")
            }
        }
    }

    @objc public func toggleSpeaker() {
        do {
            let audioSession = AVAudioSession.sharedInstance()
            isSpeakerOn = !isSpeakerOn

            if isSpeakerOn {
                try audioSession.overrideOutputAudioPort(.speaker)
            } else {
                try audioSession.overrideOutputAudioPort(.none)
            }

            NSLog("CallKitManager: Speaker \(isSpeakerOn ? "enabled" : "disabled")")
        } catch {
            NSLog("CallKitManager: Failed to toggle speaker: \(error)")
        }
    }

    @objc public func isSpeakerEnabled() -> Bool {
        return isSpeakerOn
    }

    @objc public func isMutedEnabled() -> Bool {
        return isMuted
    }

    @objc public func getCurrentCallId() -> String? {
        return activeCalls.keys.first
    }

    @objc public func hasActiveCall() -> Bool {
        return !activeCalls.isEmpty
    }

    // MARK: - Call State Reporting

    @objc public func reportCallConnected(callId: String) {
        guard let uuid = activeCalls[callId] else { return }

        NSLog("CallKitManager: Call \(callId) connected")
        provider.reportOutgoingCall(with: uuid, connectedAt: Date())

        if var state = callStates[uuid] {
            state.isConnected = true
            callStates[uuid] = state
        }
    }

    @objc public func reportCallStartedConnecting(callId: String) {
        guard let uuid = activeCalls[callId] else { return }

        NSLog("CallKitManager: Call \(callId) started connecting")
        provider.reportOutgoingCall(with: uuid, startedConnectingAt: Date())
    }

    @objc public func reportCallEnded(callId: String, reason: Int) {
        guard let uuid = activeCalls[callId] else { return }

        let endReason: CXCallEndedReason
        switch reason {
        case 1: endReason = .failed
        case 2: endReason = .remoteEnded
        case 3: endReason = .unanswered
        case 4: endReason = .answeredElsewhere
        case 5: endReason = .declinedElsewhere
        default: endReason = .remoteEnded
        }

        NSLog("CallKitManager: Call \(callId) ended with reason \(reason)")
        provider.reportCall(with: uuid, endedAt: Date(), reason: endReason)

        activeCalls.removeValue(forKey: callId)
        callStates.removeValue(forKey: uuid)
    }
}

// MARK: - CXProviderDelegate

extension CallKitManager: CXProviderDelegate {

    public func providerDidReset(_ provider: CXProvider) {
        NSLog("CallKitManager: Provider did reset")

        // End all active calls
        activeCalls.removeAll()
        callStates.removeAll()
        isMuted = false
        isSpeakerOn = false

        // Stop audio
        do {
            try AVAudioSession.sharedInstance().setActive(false)
        } catch {
            NSLog("CallKitManager: Failed to deactivate audio session: \(error)")
        }
    }

    public func provider(_ provider: CXProvider, perform action: CXStartCallAction) {
        NSLog("CallKitManager: CXStartCallAction for \(action.callUUID)")

        // Configure audio session for outgoing call
        do {
            try AVAudioSession.sharedInstance().setActive(true)
        } catch {
            NSLog("CallKitManager: Failed to activate audio session: \(error)")
        }

        // Signal that call is connecting
        provider.reportOutgoingCall(with: action.callUUID, startedConnectingAt: Date())

        // TODO: Notify JamiBridge to initiate the actual call

        action.fulfill()
    }

    public func provider(_ provider: CXProvider, perform action: CXAnswerCallAction) {
        NSLog("CallKitManager: CXAnswerCallAction for \(action.callUUID)")

        // Configure audio session for incoming call
        do {
            try AVAudioSession.sharedInstance().setActive(true)
        } catch {
            NSLog("CallKitManager: Failed to activate audio session: \(error)")
        }

        // TODO: Notify JamiBridge to accept the call

        action.fulfill()
    }

    public func provider(_ provider: CXProvider, perform action: CXEndCallAction) {
        NSLog("CallKitManager: CXEndCallAction for \(action.callUUID)")

        // Find and remove the call
        if let callId = activeCalls.first(where: { $0.value == action.callUUID })?.key {
            activeCalls.removeValue(forKey: callId)
            callStates.removeValue(forKey: action.callUUID)
        }

        // Deactivate audio session if no more calls
        if activeCalls.isEmpty {
            do {
                try AVAudioSession.sharedInstance().setActive(false)
            } catch {
                NSLog("CallKitManager: Failed to deactivate audio session: \(error)")
            }
        }

        // TODO: Notify JamiBridge to end the call

        action.fulfill()
    }

    public func provider(_ provider: CXProvider, perform action: CXSetMutedCallAction) {
        NSLog("CallKitManager: CXSetMutedCallAction - muted: \(action.isMuted)")

        isMuted = action.isMuted

        // TODO: Notify JamiBridge to mute/unmute

        action.fulfill()
    }

    public func provider(_ provider: CXProvider, perform action: CXSetHeldCallAction) {
        NSLog("CallKitManager: CXSetHeldCallAction - onHold: \(action.isOnHold)")

        // TODO: Notify JamiBridge to hold/unhold

        action.fulfill()
    }

    public func provider(_ provider: CXProvider, didActivate audioSession: AVAudioSession) {
        NSLog("CallKitManager: Audio session activated")

        // Audio is ready for the call
        // TODO: Notify JamiBridge that audio is ready
    }

    public func provider(_ provider: CXProvider, didDeactivate audioSession: AVAudioSession) {
        NSLog("CallKitManager: Audio session deactivated")
    }

    public func provider(_ provider: CXProvider, timedOutPerforming action: CXAction) {
        NSLog("CallKitManager: Action timed out: \(action)")
        action.fail()
    }
}
