import SwiftUI
import Coach

@main
struct iOSApp: App {
    init() {
        // The coach server for each build configuration, as the Android app's flavors (TECHNICAL_DECISIONS
        // §72): Debug a local server, Staging coach-server on Railway staging, Release production.
        let info = Bundle.main.infoDictionary
        let version = "\(info?["CFBundleShortVersionString"] ?? "?") (\(info?["CFBundleVersion"] ?? "?"))"
        // The build number, sent with each request so an old build can be refused (TECHNICAL_DECISIONS §85).
        let buildNumber = Int32(info?["CFBundleVersion"] as? String ?? "") ?? 0
        #if DEBUG
        KoinHelperKt.startCoachKoinIOS(baseUrl: "http://localhost:8090/v1", isDebug: true, appVersion: version, buildNumber: buildNumber)
        #elseif STAGING
        KoinHelperKt.startCoachKoinIOS(baseUrl: "https://squadcall-for-coach-staging.up.railway.app/v1", isDebug: false, appVersion: version, buildNumber: buildNumber)
        #else
        // TODO placeholder: the coach server has no production service yet (it comes with the coach app's launch).
        KoinHelperKt.startCoachKoinIOS(baseUrl: "https://coach-api.squadcall.pt/v1", isDebug: false, appVersion: version, buildNumber: buildNumber)
        #endif
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
