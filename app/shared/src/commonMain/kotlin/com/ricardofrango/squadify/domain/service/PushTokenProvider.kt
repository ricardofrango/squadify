package com.ricardofrango.squadify.domain.service

/**
 * The current platform push-notification registration token, if this build has push wired up at all.
 * Android-only for now (see TECHNICAL_DECISIONS.md's push-notifications section) -- every platform's
 * `initKoin` call defaults to [NoOpPushTokenProvider] and only Android's entry point
 * (`SquadifyApplication`) overrides it with a real, Firebase-backed implementation.
 */
interface PushTokenProvider {
    /** Null if there's no token yet, the platform has no push implementation, or the fetch failed. */
    suspend fun getCurrentToken(): String?
}

object NoOpPushTokenProvider : PushTokenProvider {
    override suspend fun getCurrentToken(): String? = null
}
