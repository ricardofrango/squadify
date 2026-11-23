package com.sportssoftw.squadify.domain.repository

import com.sportssoftw.squadify.model.FeatureFlags

/** The server's feature flags (`GET /v1/feature-flags`), read when the app starts. */
interface FeatureFlagsRepository {
    suspend fun load(): FeatureFlags
}
