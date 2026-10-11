package com.ricardofrango.squadify.domain.service

import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.exception.TooManyPasswordAttemptsException
import com.ricardofrango.squadify.exception.WrongPasswordException
import org.mindrot.jbcrypt.BCrypt

/**
 * Checks the current password that Change password and Change email ask for.
 *
 * A wrong password is a [WrongPasswordException] (400), never a 401: the app reads any 401 as an
 * expired session and signs the user out, which a typo shouldn't do. Wrong attempts in a row are
 * counted on the user (`users.password_check_failures`). At [MAX_FAILURES] every refresh token of the
 * user is deleted and [TooManyPasswordAttemptsException] (403) tells the app to sign out with an alert.
 * A correct password resets the count.
 *
 * Runs in its own transaction, before the caller's, so the count is committed even though an
 * exception follows.
 */
class CurrentPasswordGuard(
    private val userRepository: UserRepository,
    private val transactionManager: TransactionManager
) {
    private enum class Result { OK, WRONG, LOCKED }

    suspend fun verify(userId: String, password: String) {
        val result = transactionManager.runInTransaction {
            val user = userRepository.findById(userId) ?: throw NotFoundException("User not found")
            val hash = userRepository.findPasswordHashByEmail(user.email) ?: throw NotFoundException("User not found")
            if (BCrypt.checkpw(password, hash)) {
                userRepository.resetPasswordCheckFailures(userId)
                Result.OK
            } else if (userRepository.incrementPasswordCheckFailures(userId) >= MAX_FAILURES) {
                userRepository.resetPasswordCheckFailures(userId)
                userRepository.deleteRefreshTokensForUser(userId)
                Result.LOCKED
            } else {
                Result.WRONG
            }
        }
        when (result) {
            Result.OK -> Unit
            Result.WRONG -> throw WrongPasswordException()
            Result.LOCKED -> throw TooManyPasswordAttemptsException()
        }
    }

    companion object {
        const val MAX_FAILURES = 3
    }
}
