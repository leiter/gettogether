import SwiftUI
import UIKit
import UserNotifications
import Shared

class AppDelegate: NSObject, UIApplicationDelegate {

    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        NSLog("AppDelegate: App launched")

        // Register the Swift bridge adapter with Kotlin's NativeBridgeProvider
        // This must be done early, before any Kotlin code tries to use the bridge
        SwiftJamiBridgeAdapter.register()

        // Register for VoIP push notifications
        PushKitManager.shared.registerForVoIPPushes()

        // Request notification permissions for local notifications
        requestNotificationPermissions()

        return true
    }

    private func requestNotificationPermissions() {
        let center = UNUserNotificationCenter.current()
        center.requestAuthorization(options: [.alert, .sound, .badge]) { granted, error in
            if let error = error {
                NSLog("AppDelegate: Notification permission error: \(error)")
            } else {
                NSLog("AppDelegate: Notification permission granted: \(granted)")
            }
        }
    }

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        let tokenString = deviceToken.map { String(format: "%02x", $0) }.joined()
        NSLog("AppDelegate: Registered for remote notifications with token: \(tokenString)")
    }

    func application(_ application: UIApplication, didFailToRegisterForRemoteNotificationsWithError error: Error) {
        NSLog("AppDelegate: Failed to register for remote notifications: \(error)")
    }
}

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
