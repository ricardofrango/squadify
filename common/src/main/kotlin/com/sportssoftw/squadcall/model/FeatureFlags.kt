package com.sportssoftw.squadcall.model

import kotlinx.serialization.Serializable

/**
 * The feature flags the apps read when they start (`GET /v1/feature-flags`, on both servers; TECHNICAL_DECISIONS §82).
 * Each flag is on or off from the server's configuration. More flags are added here, with their default off.
 */
@Serializable
data class FeatureFlags(
    /** Copying an age group between the clubs and coach apps (§81). On with FEATURE_AGE_GROUP_IMPORT=true. */
    val ageGroupImport: Boolean = false,
    /** How long the apps keep a general read (seconds); 0 turns client caching off. CLIENT_CACHE_SECONDS (§83). */
    val cacheSeconds: Int = 0,
    /** How long the apps keep a read of a game's status or results (seconds); 0 turns it off. CLIENT_MATCH_CACHE_SECONDS. */
    val matchCacheSeconds: Int = 0,
    /** Plans and ads (REQUIREMENTS §5c): off, every space is Pro and the app hides the plan UI. On with PLANS_ENABLED=true (coach server). */
    val plansEnabled: Boolean = false,
    /** Whether the asking app's build is below its platform's minimum (MIN_ANDROID_BUILD, MIN_IOS_BUILD; §85): it must update. */
    val updateRequired: Boolean = false
)
