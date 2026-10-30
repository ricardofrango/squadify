package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.PasswordResetCode
import com.ricardofrango.squadify.domain.repository.PasswordResetRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.service.EmailService
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.model.User
import com.ricardofrango.squadify.model.UserStatus
import org.mindrot.jbcrypt.BCrypt
import java.security.SecureRandom
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Forgot password, step 1: emails a 6-digit code to set a new password with (step 2 is
 * [ResetPasswordUseCase]). A code rather than a link, so it works the same in every client (Android,
 * iOS, web, desktop) without deep links.
 *
 * An email with no active account is a 404, so the user can fix a typo instead of waiting for an
 * email that never comes. Hiding it would protect nothing: Register already says "User with this email
 * already exists". A request inside the resend cooldown returns normally without sending anything
 * (the code already sent is still valid).
 */
class RequestPasswordResetUseCase(
    private val userRepository: UserRepository,
    private val passwordResetRepository: PasswordResetRepository,
    private val emailService: EmailService,
    private val transactionManager: TransactionManager,
    private val clock: Clock = Clock.System,
    private val generateCode: () -> String = ::randomResetCode
) {
    suspend operator fun invoke(email: String) {
        val trimmed = email.trim()
        if (trimmed.isEmpty()) throw NotFoundException(NO_ACCOUNT)

        val toSend: Pair<User, String>? = transactionManager.runInTransaction {
            val user = userRepository.findByEmail(trimmed)
            if (user == null || user.status != UserStatus.ACTIVE) throw NotFoundException(NO_ACCOUNT)

            val now = clock.now()
            val pending = passwordResetRepository.findByUser(user.id)
            // Stops a script (or an impatient double tap) from flooding someone's inbox.
            if (pending != null && now - pending.createdAt < RESEND_COOLDOWN) return@runInTransaction null

            val code = generateCode()
            passwordResetRepository.replace(
                PasswordResetCode(
                    userId = user.id,
                    codeHash = BCrypt.hashpw(code, BCrypt.gensalt()),
                    expiresAt = now + CODE_TTL,
                    attempts = 0,
                    createdAt = now
                )
            )
            user to code
        }

        // After the transaction commits, like every other email (EmailService never throws).
        toSend?.let { (user, code) ->
            emailService.sendPasswordResetCode(user.email, user.firstName, code, CODE_TTL.inWholeMinutes.toInt())
        }
    }

    companion object {
        val CODE_TTL = 15.minutes
        val RESEND_COOLDOWN = 60.seconds
        const val NO_ACCOUNT = "There's no account with this email"
    }
}

private val random = SecureRandom()

/** A 6-digit code, for password resets and email changes. */
fun randomResetCode(): String = random.nextInt(1_000_000).toString().padStart(6, '0')
