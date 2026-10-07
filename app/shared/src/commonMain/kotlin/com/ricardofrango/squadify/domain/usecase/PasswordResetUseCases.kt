package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.AuthRepository
import com.ricardofrango.squadify.model.PasswordResetConfirmRequest
import com.ricardofrango.squadify.model.PasswordResetRequest

/** Forgot password, step 1: asks the server to email a 6-digit code. Succeeds even for unknown emails. */
class RequestPasswordResetUseCase(private val authRepository: AuthRepository) {
    suspend operator fun invoke(email: String) = authRepository.requestPasswordReset(PasswordResetRequest(email.trim()))
}

/** Forgot password, step 2: sets [newPassword] with the emailed [code]. */
class ResetPasswordUseCase(private val authRepository: AuthRepository) {
    suspend operator fun invoke(email: String, code: String, newPassword: String) =
        authRepository.confirmPasswordReset(PasswordResetConfirmRequest(email.trim(), code, newPassword))
}
