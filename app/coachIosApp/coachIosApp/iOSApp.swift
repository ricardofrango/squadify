import SwiftUI
import Coach

@main
struct iOSApp: App {
    init() {
        // The coach server for each build configuration, as the Android app's flavors (TECHNICAL_DECISIONS
        // §72): Debug a local server, Staging coach-server on Railway staging, Release production.
        let info = Bundle.main.infoDictionary
        let version = "\(info?["CFBundleShortVersionString"] ?? "?") (\(info?["CFBundleVersion"] ?? "?"))"
        #if DEBUG
        KoinHelperKt.startCoachKoinIOS(baseUrl: "http://localhost:8090/v1", isDebug: true, appVersion: version)
        #elseif STAGING
        KoinHelperKt.startCoachKoinIOS(baseUrl: "https://squadify-for-coach-staging.up.railway.app/v1", isDebug: false, appVersion: version)
        #else
        // TODO placeholder: the coach server has no production service yet (it comes with the coach app's launch).
        KoinHelperKt.startCoachKoinIOS(baseUrl: "https://coach-api.squadify.pt/v1", isDebug: false, appVersion: version)
        #endif
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
