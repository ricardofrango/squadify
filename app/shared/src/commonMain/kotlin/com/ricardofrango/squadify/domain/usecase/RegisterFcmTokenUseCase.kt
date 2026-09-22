package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.FcmTokenRepository

class RegisterFcmTokenUseCase(
    private val fcmTokenRepository: FcmTokenRepository
) {
    suspend operator fun invoke(token: String) = fcmTokenRepository.registerToken(token)
}
