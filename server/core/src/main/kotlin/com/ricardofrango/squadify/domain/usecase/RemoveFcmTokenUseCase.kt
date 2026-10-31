package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.FcmTokenRepository
import com.ricardofrango.squadify.domain.service.TransactionManager

/**
 * Removes the caller's device token, called by the client as part of its own logout flow (there is
 * no server-side logout concept otherwise -- see `LogoutUseCase` client-side, which just clears the
 * local session). Idempotent by design: an already-removed or unknown token is not an error, so the
 * client's logout flow never has to branch on this succeeding.
 */
class RemoveFcmTokenUseCase(
    private val fcmTokenRepository: FcmTokenRepository,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, token: String): Boolean = transactionManager.runInTransaction {
        fcmTokenRepository.deleteToken(userId, token)
        true
    }
}
