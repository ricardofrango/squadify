import SwiftUI
import Shared

@main
struct iOSApp: App {
    init() {
        KoinHelperKt.initKoinIOS()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}