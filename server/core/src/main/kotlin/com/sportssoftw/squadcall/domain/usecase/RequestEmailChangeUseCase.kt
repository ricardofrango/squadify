package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.repository.EmailChangeCode
import com.sportssoftw.squadcall.domain.repository.EmailChangeRepository
import com.sportssoftw.squadcall.domain.repository.UserRepository
import com.sportssoftw.squadcall.domain.service.EmailService
import com.sportssoftw.squadcall.domain.service.CurrentPasswordGuard
import com.sportssoftw.squadcall.domain.service.TransactionManager
import com.sportssoftw.squadcall.exception.ConflictException
import com.sportssoftw.squadcall.exception.NotFoundException
import com.sportssoftw.squadcall.exception.ValidationException
import com.sportssoftw.squadcall.model.EmailChangeRequest
import com.sportssoftw.squadcall.model.User
import org.mindrot.jbcrypt.BCrypt
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Change email, step 1: checks the caller's current password and that the new address is free, then
 * emails a 6-digit code to the NEW address (step 2 is [ConfirmEmailChangeUseCase]). Same code rules
 * as Forgot password (RequestPasswordResetUseCase): 15 minutes, 5 tries, one per minute, hashed.
 *
 * The password is asked for so that a stolen access token alone can't move the account to someone
 * else's address (same reasoning as ChangePasswordUseCase); [CurrentPasswordGuard] checks it.
 */
class RequestEmailChangeUseCase(
    private val userRepository: UserRepository,
    private val emailChangeRepository: EmailChangeRepository,
    private val emailService: EmailService,
    private val transactionManager: TransactionManager,
    private val currentPasswordGuard: CurrentPasswordGuard,
    private val clock: Clock = Clock.System,
    private val generateCode: () -> String = ::randomResetCode
) {
    suspend operator fun invoke(userId: String, request: EmailChangeRequest, language: String? = null) {
        val newEmail = request.newEmail.trim()
        if (!EMAIL.matches(newEmail)) throw ValidationException(INVALID_EMAIL)
        currentPasswordGuard.verify(userId, request.currentPassword)

        val toSend: Pair<User, String>? = transactionManager.runInTransaction {
            val user = userRepository.findById(userId) ?: throw NotFoundException("User not found")
            if (newEmail == user.email) throw ValidationException(SAME_EMAIL)
            if (userRepository.findByEmail(newEmail) != null) throw ConflictException(EMAIL_TAKEN)

            val now = clock.now()
            val pending = emailChangeRepository.findByUser(userId)
            // A repeat for the same address within a minute: the code already sent is still valid.
            if (pending != null && pending.newEmail == newEmail && now - pending.createdAt < RESEND_COOLDOWN) {
                return@runInTransaction null
            }

            val code = generateCode()
            emailChangeRepository.replace(
                EmailChangeCode(
                    userId = userId,
                    newEmail = newEmail,
                    codeHash = BCrypt.hashpw(code, BCrypt.gensalt()),
                    expiresAt = now + CODE_TTL,
                    attempts = 0,
                    createdAt = now
                )
            )
            user to code
        }

        toSend?.let { (user, code) ->
            emailService.sendEmailChangeCode(newEmail, user.firstName, code, CODE_TTL.inWholeMinutes.toInt(), language)
        }
    }

    companion object {
        val CODE_TTL = 15.minutes
        val RESEND_COOLDOWN = 60.seconds
        private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
        const val INVALID_EMAIL = "Enter a valid email address"
        const val SAME_EMAIL = "This is already your email"
        const val EMAIL_TAKEN = "An account with this email already exists"
    }
}
