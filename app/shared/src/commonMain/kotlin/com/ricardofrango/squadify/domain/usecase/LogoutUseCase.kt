package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.SessionRepository

class LogoutUseCase(
    private val sessionRepository: SessionRepository
) {
    operator fun invoke() {
        sessionRepository.clearSession()
    }
}
