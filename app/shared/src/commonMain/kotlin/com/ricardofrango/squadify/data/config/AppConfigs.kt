package com.ricardofrango.squadify.data.config

import com.ricardofrango.squadify.domain.config.AppConfig

/**
 * Every platform entry point constructs its own instance and passes it to `initKoin` -- there is no
 * per-platform `expect`/`actual` here, since the values (which environment: dev/staging/production) are
 * decided by each platform's own build mechanism (Android product flavors, a Gradle property for
 * Desktop/Web) and that mechanism lives where the platform's real build variants are defined, not inside
 * this shared module. See TECHNICAL_DECISIONS.md's "Environment-Based App Config" section.
 */
data class DefaultAppConfig(
    override val baseUrl: String,
    override val isDebug: Boolean,
    override val appVersion: String? = null
) : AppConfig
