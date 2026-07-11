import SwiftUI
import ComposeApp
import FirebaseCore
import FirebaseAnalytics
import FirebaseCrashlytics
import UIKit
import UserNotifications

/// Hosts the APNs callbacks SwiftUI's `App` can't receive directly. Kotlin (PushController) requests
/// authorization + registration; the device token lands here and is handed back over the same bridge
/// pattern as telemetry. Push registration itself is driven from Kotlin, so no prompt fires at cold start.
class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(_ application: UIApplication,
                     didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        return true
    }

    func application(_ application: UIApplication,
                     didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        let hex = deviceToken.map { String(format: "%02x", $0) }.joined()
        #if DEBUG
        let platform = "apns_sandbox" // development APNs → relay sends via api.sandbox.push.apple.com
        #else
        let platform = "apns"
        #endif
        MainViewControllerKt.setPushToken(platform: platform, token: hex)
    }

    func application(_ application: UIApplication,
                     didFailToRegisterForRemoteNotificationsWithError error: Error) {
        // transient — Kotlin re-attempts registration on the next foreground/connect
    }

    // surface the alert even when the app is in the foreground
    func userNotificationCenter(_ center: UNUserNotificationCenter,
                                willPresent notification: UNNotification,
                                withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        completionHandler([.banner, .sound])
    }

    // a tapped task-complete notification carries `wd`/`sid` custom keys → deep-link into that session
    func userNotificationCenter(_ center: UNUserNotificationCenter,
                                didReceive response: UNNotificationResponse,
                                withCompletionHandler completionHandler: @escaping () -> Void) {
        let info = response.notification.request.content.userInfo
        if let wd = info["wd"] as? String, let sid = info["sid"] as? String {
            MainViewControllerKt.handlePushOpen(workdir: wd, sessionId: sid)
        }
        completionHandler()
    }
}

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

    init() {
        // Firebase stays in Swift (the only place that imports it); the shared Kotlin Telemetry
        // calls back through the sink registered below — mirroring cc-dashboard's single seam.
        //
        // TrollStore / unsigned builds ship with the placeholder GoogleService-Info.plist
        // (API_KEY=AIzaPlaceholderKey…). FirebaseApp.configure() throws NSException on that.
        // Skip Firebase (no push / analytics / crashlytics) when the config is a placeholder
        // so the app stays usable.
        let firebaseAvailable = iOSApp.firebaseIsConfigured()
        if firebaseAvailable {
            FirebaseApp.configure()
            Analytics.setAnalyticsCollectionEnabled(true)
        } else {
            print("[cc-pocket] Firebase skipped — placeholder GoogleService-Info.plist detected")
        }
        MainViewControllerKt.setTelemetrySink(
            onEvent: { event, params in
                if firebaseAvailable { Analytics.logEvent(event, parameters: params) }
            },
            onError: { message, phase in
                if firebaseAvailable {
                    let info: [String: Any] = [NSLocalizedDescriptionKey: message, "phase": phase ?? ""]
                    Crashlytics.crashlytics().record(error: NSError(domain: "ccpocket", code: 0, userInfo: info))
                }
            }
        )
        // Push registration lives in Swift (UIKit symbols aren't uniform across Kotlin/Native targets).
        // Kotlin's PushController calls this when registration starts (after pairing), so the prompt
        // follows pairing rather than firing at cold launch.
        MainViewControllerKt.setPushRegistrar {
            UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { granted, _ in
                guard granted else { return }
                DispatchQueue.main.async { UIApplication.shared.registerForRemoteNotifications() }
            }
        }
    }

    /// Returns true when GoogleService-Info.plist contains a real API key (not the template placeholder).
    private static func firebaseIsConfigured() -> Bool {
        guard let path = Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist"),
              let plist = NSDictionary(contentsOfFile: path) else {
            return false
        }
        guard let apiKey = plist["API_KEY"] as? String,
              !apiKey.isEmpty,
              !apiKey.contains("Placeholder"),
              apiKey != "AIzaPlaceholderKeyForLocalDevBuildsOnly0000" else {
            return false
        }
        return true
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .ignoresSafeArea(.all)
                .onOpenURL { url in
                    MainViewControllerKt.handleDeepLink(url: url.absoluteString)
                }
        }
    }
}
