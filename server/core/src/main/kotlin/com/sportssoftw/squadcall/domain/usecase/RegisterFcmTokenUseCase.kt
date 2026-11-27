package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.repository.FcmTokenRepository
import com.sportssoftw.squadcall.domain.service.TransactionManager
import com.sportssoftw.squadcall.exception.ValidationException

/**
 * Registers the caller's device for push notifications. No permission check beyond authentication --
 * userId always comes from the caller's own JWT, never a client-supplied parameter, so there's no
 * cross-user access to guard against.
 */
class RegisterFcmTokenUseCase(
    private val fcmTokenRepository: FcmTokenRepository,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, token: String): Boolean = transactionManager.runInTransaction {
        if (token.isBlank()) throw ValidationException("token must not be blank")
        fcmTokenRepository.upsertToken(userId, token)
        true
    }
}
