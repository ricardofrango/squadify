package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.FcmTokenRepository
import com.ricardofrango.squadify.domain.service.PushTokenProvider

class RemoveFcmTokenUseCase(
    private val fcmTokenRepository: FcmTokenRepository,
    private val pushTokenProvider: PushTokenProvider,
) {
    suspend operator fun invoke() {
        try {
            pushTokenProvider.getCurrentToken()?.let { fcmTokenRepository.removeToken(it) }
        } catch (e: Exception) {
            e.printStackTrace()
            // Best-effort -- a failed/offline token removal must never block logging out locally.
        }
    }
}
