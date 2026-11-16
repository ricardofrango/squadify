package com.ricardofrango.squadify.model

import kotlinx.serialization.Serializable

/**
 * The feature flags the apps read when they start (`GET /v1/feature-flags`, on both servers; TECHNICAL_DECISIONS §82).
 * Each flag is on or off from the server's configuration. More flags are added here, with their default off.
 */
@Serializable
data class FeatureFlags(
    /** Copying an age group between the clubs and coach apps (§81). On with FEATURE_AGE_GROUP_IMPORT=true. */
    val ageGroupImport: Boolean = false
)
