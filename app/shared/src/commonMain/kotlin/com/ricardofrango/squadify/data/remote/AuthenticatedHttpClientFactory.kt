package com.ricardofrango.squadify.data.remote

import com.ricardofrango.squadify.domain.repository.SessionRepository
import com.ricardofrango.squadify.domain.service.SessionEventBus
import com.ricardofrango.squadify.model.RefreshTokenRequest
import com.ricardofrango.squadify.model.TokenResponse
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType

/**
 * Derives the "authenticated" client every repository but [AuthRepositoryImpl] uses from [publicClient]
 * (see `Koin.kt`'s doc comment on why the two are kept separate): attaches the access token to every
 * request and transparently refreshes-and-retries once on a 401, via Ktor's own Auth/bearer plugin --
 * the fix for a session dying mid-live-match with no automatic recovery (see TECHNICAL_DECISIONS.md).
 * If the refresh fails, or the retried request is *still* 401 after a fresh token, that final 401 is
 * what reaches [HttpResponseValidator] below, which is the one place that decides "this session is
 * truly dead" and fires [SessionEventBus.emitForceLogout].
 *
 * Pulled out of Koin.kt as a standalone function specifically so it can be exercised in a test against
 * a [io.ktor.client.engine.mock.MockEngine]-backed [publicClient], without needing a running Koin
 * container.
 */
fun createAuthenticatedHttpClient(
    publicClient: HttpClient,
    baseUrl: String,
    sessionRepository: SessionRepository,
    sessionEventBus: SessionEventBus
): HttpClient = publicClient.config {
    install(Auth) {
        bearer {
            loadTokens {
                val access = sessionRepository.getToken()
                val refresh = sessionRepository.getRefreshToken()
                if (access != null && refresh != null) BearerTokens(access, refresh) else null
            }
            // Reads the refresh token from SessionRepository itself, not `oldTokens()` --
            // CheckAndRefreshSessionUseCase (Splash) refreshes independently through the public client
            // and writes straight to SessionRepository, which would leave this plugin's own in-memory
            // `oldTokens` cache stale until the *next* 401 anyway.
            refreshTokens {
                val refreshToken = sessionRepository.getRefreshToken() ?: return@refreshTokens null
                try {
                    val response = client.post("$baseUrl/auth/refresh") {
                        markAsRefreshTokenRequest()
                        contentType(ContentType.Application.Json)
                        setBody(RefreshTokenRequest(refreshToken))
                    }.handleResponse<TokenResponse>()
                    sessionRepository.saveToken(response.accessToken)
                    sessionRepository.saveRefreshToken(response.refreshToken)
                    BearerTokens(response.accessToken, response.refreshToken)
                } catch (e: Exception) {
                    // A dead/revoked refresh token, or a network failure reaching /auth/refresh itself
                    // -- either way there's no new token to hand back. A genuine 401 response from this
                    // very call still reaches the validator below (it isn't exempt from
                    // HttpResponseValidator, only from Auth's own retry-on-401), so that's the only
                    // place this needs to force a logout, not here too.
                    null
                }
            }
            sendWithoutRequest { true }
        }
    }
    HttpResponseValidator {
        validateResponse { response ->
            if (response.status == HttpStatusCode.Unauthorized) {
                // By the time a response reaches here, Auth (above) has already tried its one
                // refresh-and-retry -- this 401 is final, so the session really is dead.
                sessionRepository.clearSession()
                sessionEventBus.emitForceLogout()
            }
        }
    }
}
