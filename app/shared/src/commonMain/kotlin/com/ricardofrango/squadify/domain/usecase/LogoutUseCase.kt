package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.SessionRepository
import com.ricardofrango.squadify.domain.service.PushTokenProvider

/**
 * Ends the session: best-effort removes this device's push token server-side (there's no other
 * server-side "logout" concept -- see TECHNICAL_DECISIONS.md's push-notifications section), then always
 * clears the local session regardless of whether that removal succeeded. Must be called before the
 * session is cleared -- removal is an authenticated call and needs the still-valid access token.
 */
class LogoutUseCase(
    private val sessionRepository: SessionRepository,
    private val removeFcmTokenUseCase: RemoveFcmTokenUseCase
) {
    suspend operator fun invoke() {
        removeFcmTokenUseCase()
        sessionRepository.clearSession()
    }
}
