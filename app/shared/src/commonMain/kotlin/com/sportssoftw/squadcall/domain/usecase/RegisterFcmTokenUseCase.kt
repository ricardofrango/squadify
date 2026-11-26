package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.repository.FcmTokenRepository

class RegisterFcmTokenUseCase(
    private val fcmTokenRepository: FcmTokenRepository
) {
    suspend operator fun invoke(token: String) = fcmTokenRepository.registerToken(token)
}
