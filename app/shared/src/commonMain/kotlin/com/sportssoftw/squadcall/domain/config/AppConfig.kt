package com.sportssoftw.squadcall.domain.config

interface AppConfig {
    val baseUrl: String
    val isDebug: Boolean
    // "1.4.0 (212)" for the More panel's footer; null where a platform doesn't provide it (the footer
    // is then left out).
    val appVersion: String? get() = null
    // The app's platform ("android" or "ios") and build number (Android versionCode, iOS CFBundleVersion), sent with
    // every request so the server can refuse a build below its minimum (TECHNICAL_DECISIONS §85). Null on web and desktop.
    val platform: String? get() = null
    val buildNumber: Int? get() = null
    // The app's store page, for the update screen's button. Null where there's none.
    val storeUrl: String? get() = null
}
