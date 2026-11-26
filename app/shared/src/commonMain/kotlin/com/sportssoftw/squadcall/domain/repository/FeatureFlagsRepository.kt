package com.sportssoftw.squadcall.domain.repository

import com.sportssoftw.squadcall.model.FeatureFlags

/** The server's feature flags (`GET /v1/feature-flags`), read when the app starts. */
interface FeatureFlagsRepository {
    suspend fun load(): FeatureFlags
}
