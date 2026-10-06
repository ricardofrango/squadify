package com.ricardofrango.squadify.domain.config

interface AppConfig {
    val baseUrl: String
    val isDebug: Boolean
    // "1.4.0 (212)" for the More panel's footer; null where a platform doesn't provide it (the footer
    // is then left out).
    val appVersion: String? get() = null
}
