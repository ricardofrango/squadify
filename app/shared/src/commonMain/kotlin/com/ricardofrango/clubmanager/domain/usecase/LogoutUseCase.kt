package com.ricardofrango.clubmanager.domain.usecase

import com.ricardofrango.clubmanager.domain.repository.SessionRepository

class LogoutUseCase(
    private val sessionRepository: SessionRepository
) {
    operator fun invoke() {
        sessionRepository.clearSession()
    }
}
