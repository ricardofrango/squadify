package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.PasswordResetRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.PasswordResetConfirmRequest
import com.ricardofrango.squadify.model.UserStatus
import org.mindrot.jbcrypt.BCrypt
import kotlin.time.Clock

/**
 * Forgot password, step 2: checks the emailed code and sets the new password. On success the code is
 * used up and every refresh token of the user is deleted, so any other signed-in device has to sign
 * in again with the new password.
 *
 * Every failure (unknown email, no code, wrong, expired or exhausted code) is the same 400. A wrong code counts an attempt; after [MAX_ATTEMPTS] the
 * code is dead and a new one has to be requested -- 5 guesses at a 6-digit code is a 1 in 200,000 shot.
 */
class ResetPasswordUseCase(
    private val userRepository: UserRepository,
    private val passwordResetRepository: PasswordResetRepository,
    private val transactionManager: TransactionManager,
    private val clock: Clock = Clock.System
) {
    suspend operator fun invoke(request: PasswordResetConfirmRequest) {
        if (request.newPassword.length < 8) {
            throw ValidationException("New password must be at least 8 characters")
        }
        val code = request.code.filter { !it.isWhitespace() }

        // Returns instead of throwing on a wrong code: a throw would roll back the attempt count.
        val accepted = transactionManager.runInTransaction {
            val user = userRepository.findByEmail(request.email.trim())
                ?.takeIf { it.status == UserStatus.ACTIVE }
                ?: return@runInTransaction false
            val pending = passwordResetRepository.findByUser(user.id) ?: return@runInTransaction false
            if (clock.now() >= pending.expiresAt || pending.attempts >= MAX_ATTEMPTS) return@runInTransaction false

            if (!BCrypt.checkpw(code, pending.codeHash)) {
                passwordResetRepository.incrementAttempts(user.id)
                return@runInTransaction false
            }

            userRepository.updatePasswordHash(user.id, BCrypt.hashpw(request.newPassword, BCrypt.gensalt()))
            passwordResetRepository.delete(user.id)
            userRepository.deleteRefreshTokensForUser(user.id)
            true
        }
        if (!accepted) throw ValidationException(INVALID_CODE)
    }

    companion object {
        const val MAX_ATTEMPTS = 5
        const val INVALID_CODE = "This code is invalid or has expired"
    }
}
