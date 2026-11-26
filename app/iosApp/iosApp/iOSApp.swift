import SwiftUI
import Shared

@main
struct iOSApp: App {
    init() {
        let buildNumber = Int32(Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "") ?? 0
        // dev/production only for now (Debug/Release, Xcode's own default configurations) -- see
        // TECHNICAL_DECISIONS.md's "Environment-Based App Config" section for why a third "staging"
        // tier isn't wired up here yet (needs a real Xcode Build Configuration + scheme, safest done
        // interactively in Xcode rather than by hand-editing project.pbxproj).
        #if DEBUG
        KoinHelperKt.startKoinIOS(baseUrl: "http://localhost:8080/v1", isDebug: true, buildNumber: buildNumber)
        #else
        // TODO placeholder -- see ISSUES TO FIX.md.
        KoinHelperKt.startKoinIOS(baseUrl: "https://api.squadcall.pt/v1", isDebug: false, buildNumber: buildNumber)
        #endif
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}