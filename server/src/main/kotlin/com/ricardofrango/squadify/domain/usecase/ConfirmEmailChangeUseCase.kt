package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.EmailChangeRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.service.EmailService
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ConflictException
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.EmailChangeConfirmRequest
import com.ricardofrango.squadify.model.User
import kotlin.time.Clock
import org.mindrot.jbcrypt.BCrypt

/**
 * Change email, step 2: checks the code sent to the new address and switches the account to it.
 * Every other session is signed out (the caller's own refresh token is kept), and the OLD address
 * gets a heads-up email.
 *
 * Wrong, expired, used-up or missing codes are all the same 400. A wrong code counts an attempt
 * (committed before the error, as in ResetPasswordUseCase); after [MAX_ATTEMPTS] a new code is needed.
 * The address is checked again here, since someone could have registered with it in the meantime.
 */
class ConfirmEmailChangeUseCase(
    private val userRepository: UserRepository,
    private val emailChangeRepository: EmailChangeRepository,
    private val emailService: EmailService,
    private val transactionManager: TransactionManager,
    private val clock: Clock = Clock.System
) {
    private sealed interface Outcome {
        data object InvalidCode : Outcome
        data class Changed(val user: User, val oldEmail: String) : Outcome
    }

    suspend operator fun invoke(userId: String, request: EmailChangeConfirmRequest): User {
        val code = request.code.filter { !it.isWhitespace() }

        val outcome = transactionManager.runInTransaction {
            val user = userRepository.findById(userId) ?: throw NotFoundException("User not found")
            val pending = emailChangeRepository.findByUser(userId) ?: return@runInTransaction Outcome.InvalidCode
            if (clock.now() >= pending.expiresAt || pending.attempts >= MAX_ATTEMPTS) return@runInTransaction Outcome.InvalidCode

            if (!BCrypt.checkpw(code, pending.codeHash)) {
                emailChangeRepository.incrementAttempts(userId)
                return@runInTransaction Outcome.InvalidCode
            }
            if (userRepository.findByEmail(pending.newEmail) != null) {
                emailChangeRepository.delete(userId)
                throw ConflictException(RequestEmailChangeUseCase.EMAIL_TAKEN)
            }

            userRepository.updateEmail(userId, pending.newEmail)
            emailChangeRepository.delete(userId)
            userRepository.deleteRefreshTokensForUserExcept(userId, request.refreshToken)
            Outcome.Changed(user.copy(email = pending.newEmail), oldEmail = user.email)
        }

        return when (outcome) {
            Outcome.InvalidCode -> throw ValidationException(INVALID_CODE)
            is Outcome.Changed -> {
                emailService.sendEmailChanged(outcome.oldEmail, outcome.user.firstName, outcome.user.email)
                outcome.user
            }
        }
    }

    companion object {
        const val MAX_ATTEMPTS = 5
        const val INVALID_CODE = "This code is invalid or has expired"
    }
}
