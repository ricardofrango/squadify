package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.SessionRepository
import com.ricardofrango.squadify.domain.service.PushTokenProvider
import io.ktor.client.HttpClient
import io.ktor.client.plugins.auth.clearAuthTokens

/**
 * Ends the session: best-effort removes this device's push token server-side (there's no other
 * server-side "logout" concept -- see TECHNICAL_DECISIONS.md's push-notifications section), then always
 * clears the local session regardless of whether that removal succeeded. Must be called before the
 * session is cleared -- removal is an authenticated call and needs the still-valid access token.
 *
 * [httpClient] is the authenticated client's `Auth`/bearer provider caches whatever `loadTokens()`
 * returns in memory for the life of the client instance (see `AuthenticatedHttpClientFactory.kt`) --
 * clearing `SessionRepository` alone doesn't touch that cache. Without this, a user who logs out and a
 * different user who logs in on the same still-running app process would keep authenticating as the
 * *first* user on every request until the process restarts (the bug this fixes: switching users on
 * staging silently kept showing the previous account until app data was cleared).
 */
class LogoutUseCase(
    private val sessionRepository: SessionRepository,
    private val removeFcmTokenUseCase: RemoveFcmTokenUseCase,
    private val httpClient: HttpClient
) {
    suspend operator fun invoke() {
        removeFcmTokenUseCase()
        sessionRepository.clearSession()
        httpClient.clearAuthTokens()
    }
}
