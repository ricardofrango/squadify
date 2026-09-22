package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.service.PushTokenProvider

/**
 * Fetches this device's current push token (if any -- null on every platform but Android for now, see
 * [PushTokenProvider]) and registers it. Called right after establishing a session -- [LoginViewModel]
 * on a fresh login, [SplashViewModel] on an existing session confirmed still valid -- both need the
 * exact same "get the token, register it, don't let a failure block what the user is actually doing"
 * behavior, so it's one shared use case rather than duplicated in each. Best-effort: a missing token or
 * a failed registration call is swallowed, never surfaced to the caller, same reasoning as
 * `EmailService` (TECHNICAL_DECISIONS.md) -- push delivery is a nice-to-have, not something that should
 * ever block login or session restore.
 */
class RegisterCurrentFcmTokenUseCase(
    private val pushTokenProvider: PushTokenProvider,
    private val registerFcmTokenUseCase: RegisterFcmTokenUseCase
) {
    suspend operator fun invoke() {
        try {
            pushTokenProvider.getCurrentToken()?.let { registerFcmTokenUseCase(it) }
        } catch (e: Exception) {
            // Best-effort -- see class doc.
        }
    }
}
