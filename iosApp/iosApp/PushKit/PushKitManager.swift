import Foundation
import PushKit
import CallKit
import UIKit

@objc public class PushKitManager: NSObject {

    @objc public static let shared = PushKitManager()

    private var voipRegistry: PKPushRegistry?
    private var deviceToken: Data?

    private override init() {
        super.init()
    }

    // MARK: - Public API

    @objc public func registerForVoIPPushes() {
        NSLog("PushKitManager: Registering for VoIP pushes")

        voipRegistry = PKPushRegistry(queue: DispatchQueue.main)
        voipRegistry?.delegate = self
        voipRegistry?.desiredPushTypes = [.voIP]
    }

    @objc public func getDeviceToken() -> String? {
        guard let token = deviceToken else { return nil }
        return token.map { String(format: "%02x", $0) }.joined()
    }

    @objc public func getDeviceTokenData() -> Data? {
        return deviceToken
    }
}

// MARK: - PKPushRegistryDelegate

extension PushKitManager: PKPushRegistryDelegate {

    public func pushRegistry(_ registry: PKPushRegistry, didUpdate pushCredentials: PKPushCredentials, for type: PKPushType) {
        guard type == .voIP else { return }

        deviceToken = pushCredentials.token
        let tokenString = pushCredentials.token.map { String(format: "%02x", $0) }.joined()

        NSLog("PushKitManager: Received VoIP push token: \(tokenString)")

        // TODO: Send this token to your server for push notifications
        NotificationCenter.default.post(
            name: NSNotification.Name("VoIPPushTokenReceived"),
            object: nil,
            userInfo: ["token": tokenString]
        )
    }

    public func pushRegistry(_ registry: PKPushRegistry, didInvalidatePushTokenFor type: PKPushType) {
        guard type == .voIP else { return }

        NSLog("PushKitManager: VoIP push token invalidated")
        deviceToken = nil

        NotificationCenter.default.post(
            name: NSNotification.Name("VoIPPushTokenInvalidated"),
            object: nil
        )
    }

    public func pushRegistry(_ registry: PKPushRegistry, didReceiveIncomingPushWith payload: PKPushPayload, for type: PKPushType, completion: @escaping () -> Void) {
        guard type == .voIP else {
            completion()
            return
        }

        NSLog("PushKitManager: Received VoIP push: \(payload.dictionaryPayload)")

        // Extract call information from payload
        // Expected payload format:
        // {
        //   "callId": "unique-call-id",
        //   "contactId": "caller-jami-id",
        //   "contactName": "Caller Name",
        //   "hasVideo": true/false
        // }

        let callId = payload.dictionaryPayload["callId"] as? String ?? UUID().uuidString
        let contactId = payload.dictionaryPayload["contactId"] as? String ?? "unknown"
        let contactName = payload.dictionaryPayload["contactName"] as? String ?? "Unknown Caller"
        let hasVideo = payload.dictionaryPayload["hasVideo"] as? Bool ?? false

        // IMPORTANT: iOS requires that we MUST report a call to CallKit immediately
        // when receiving a VoIP push, or the app will be terminated.
        CallKitManager.shared.reportIncomingCall(
            callId: callId,
            contactId: contactId,
            contactName: contactName,
            hasVideo: hasVideo
        ) { error in
            if let error = error {
                NSLog("PushKitManager: Failed to report incoming call: \(error)")
            } else {
                NSLog("PushKitManager: Successfully reported incoming call")
            }
            completion()
        }

        // Notify the app about the incoming call
        NotificationCenter.default.post(
            name: NSNotification.Name("IncomingVoIPCall"),
            object: nil,
            userInfo: [
                "callId": callId,
                "contactId": contactId,
                "contactName": contactName,
                "hasVideo": hasVideo
            ]
        )
    }
}
