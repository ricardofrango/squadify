package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.repository.SessionRepository
import com.sportssoftw.squadcall.domain.repository.UserRepository
import com.sportssoftw.squadcall.model.EmailChangeConfirmRequest
import com.sportssoftw.squadcall.model.EmailChangeRequest
import com.sportssoftw.squadcall.model.User

/** Change email, step 1: a 6-digit code is emailed to [newEmail], confirmed with [currentPassword]. */
class RequestEmailChangeUseCase(private val userRepository: UserRepository) {
    suspend operator fun invoke(newEmail: String, currentPassword: String) =
        userRepository.requestEmailChange(EmailChangeRequest(newEmail.trim(), currentPassword))
}

/**
 * Change email, step 2: confirms with the [code] and returns the updated user. Sends this device's
 * refresh token, so the server signs out every other session but this one.
 */
class ConfirmEmailChangeUseCase(
    private val userRepository: UserRepository,
    private val sessionRepository: SessionRepository
) {
    suspend operator fun invoke(code: String): User =
        userRepository.confirmEmailChange(EmailChangeConfirmRequest(code, sessionRepository.getRefreshToken()))
}
