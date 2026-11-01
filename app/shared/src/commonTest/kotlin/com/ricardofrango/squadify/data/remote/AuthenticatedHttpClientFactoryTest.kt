package com.ricardofrango.squadify.data.remote

import com.ricardofrango.squadify.domain.repository.SessionRepository
import com.ricardofrango.squadify.domain.service.SessionEventBus
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.auth.clearAuthTokens
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Exercises the real Auth/bearer wiring end to end against a MockEngine, rather than trusting the
 * plugin config by inspection alone -- this is auth/session code, and this codebase had zero networking
 * test infrastructure before this file (see TECHNICAL_DECISIONS.md).
 *
 * Each test's force-logout collector starts on an [UnconfinedTestDispatcher] so it has subscribed
 * before the request: the MockEngine answers on a real thread, and a `SharedFlow` emission with no
 * subscriber yet is lost (seen on CI).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthenticatedHttpClientFactoryTest {

    private class InMemorySessionRepository(
        token: String? = "old-access-token",
        refreshToken: String? = "valid-refresh-token"
    ) : SessionRepository {
        var storedToken = token
        var storedRefreshToken = refreshToken
        var cleared = false
        override fun saveToken(token: String) { storedToken = token }
        override fun getToken(): String? = storedToken
        override fun saveRefreshToken(token: String) { storedRefreshToken = token }
        override fun getRefreshToken(): String? = storedRefreshToken
        override fun clearSession() {
            cleared = true
            storedToken = null
            storedRefreshToken = null
        }
    }

    private val baseUrl = "https://example.test"

    // Mirrors the real "public" client's shape closely enough for this test: ContentNegotiation is
    // required for handleResponse<TokenResponse>() (called from inside refreshTokens) to deserialize
    // the mocked /auth/refresh response at all.
    private fun publicClient(engine: MockEngine) = HttpClient(engine) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    @Test
    fun successfulRequestPassesThroughUnaffected() = runTest {
        val session = InMemorySessionRepository()
        val bus = SessionEventBus()
        val forceLogouts = mutableListOf<Unit>()
        val collector = async(UnconfinedTestDispatcher(testScheduler)) { bus.forceLogout.collect { forceLogouts.add(it) } }

        var callCount = 0
        val engine = MockEngine { request ->
            callCount++
            assertEquals("Bearer old-access-token", request.headers[HttpHeaders.Authorization])
            respond("ok", HttpStatusCode.OK)
        }
        val client = createAuthenticatedHttpClient(publicClient(engine), baseUrl, session, bus)

        val response = client.get("$baseUrl/matches")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(1, callCount)
        assertFalse(session.cleared)
        assertTrue(forceLogouts.isEmpty())
        collector.cancel()
    }

    @Test
    fun a401ThenSuccessfulRefreshSilentlyRetriesAndSucceeds() = runTest {
        val session = InMemorySessionRepository()
        val bus = SessionEventBus()
        val forceLogouts = mutableListOf<Unit>()
        val collector = async(UnconfinedTestDispatcher(testScheduler)) { bus.forceLogout.collect { forceLogouts.add(it) } }

        var protectedCalls = 0
        val engine = MockEngine { request ->
            when {
                request.url.encodedPath == "/auth/refresh" ->
                    respond(
                        """{"accessToken":"new-access-token","refreshToken":"new-refresh-token"}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json")
                    )
                else -> {
                    protectedCalls++
                    if (protectedCalls == 1) {
                        assertEquals("Bearer old-access-token", request.headers[HttpHeaders.Authorization])
                        // Deliberately no WWW-Authenticate header on this 401 -- the server doesn't send
                        // one (see Application.kt), and Ktor's Auth plugin doesn't need it to trigger a
                        // refresh when there's only one configured provider, which is this app's case.
                        respond("denied", HttpStatusCode.Unauthorized)
                    } else {
                        assertEquals("Bearer new-access-token", request.headers[HttpHeaders.Authorization])
                        respond("ok", HttpStatusCode.OK)
                    }
                }
            }
        }
        val client = createAuthenticatedHttpClient(publicClient(engine), baseUrl, session, bus)

        val response = client.get("$baseUrl/matches")
        advanceUntilIdle()

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(2, protectedCalls) // original 401 + one retry, no more
        assertEquals("new-access-token", session.storedToken)
        assertEquals("new-refresh-token", session.storedRefreshToken)
        assertFalse(session.cleared)
        assertTrue(forceLogouts.isEmpty())
        collector.cancel()
    }

    @Test
    fun a401WhoseRefreshCallAlsoFailsForcesLogout() = runTest {
        val session = InMemorySessionRepository()
        val bus = SessionEventBus()
        val forceLogouts = mutableListOf<Unit>()
        val collector = async(UnconfinedTestDispatcher(testScheduler)) { bus.forceLogout.collect { forceLogouts.add(it) } }

        val engine = MockEngine { request ->
            if (request.url.encodedPath == "/auth/refresh") {
                respond("refresh token expired", HttpStatusCode.Unauthorized)
            } else {
                respond("denied", HttpStatusCode.Unauthorized)
            }
        }
        val client = createAuthenticatedHttpClient(publicClient(engine), baseUrl, session, bus)

        val response = client.get("$baseUrl/matches")
        advanceUntilIdle()

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertTrue(session.cleared)
        assertTrue(forceLogouts.isNotEmpty())
        collector.cancel()
    }

    @Test
    fun a401ThatSurvivesEvenAFreshTokenForcesLogout() = runTest {
        val session = InMemorySessionRepository()
        val bus = SessionEventBus()
        val forceLogouts = mutableListOf<Unit>()
        val collector = async(UnconfinedTestDispatcher(testScheduler)) { bus.forceLogout.collect { forceLogouts.add(it) } }

        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/auth/refresh" -> respond(
                    """{"accessToken":"new-access-token","refreshToken":"new-refresh-token"}""",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "application/json")
                )
                // Even with a freshly-refreshed token, the server still says no (e.g. the account was
                // suspended between the two calls) -- Auth doesn't retry a second time, so this is
                // exactly the "still 401 after retry" case.
                else -> respond("denied", HttpStatusCode.Unauthorized)
            }
        }
        val client = createAuthenticatedHttpClient(publicClient(engine), baseUrl, session, bus)

        val response = client.get("$baseUrl/matches")
        advanceUntilIdle()

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        // The retry did happen with a fresh token (the mock proves it: the second call succeeded at
        // the network level) -- but the validator's own clearSession() wipes it again right after,
        // since the final response the caller sees is still 401.
        assertTrue(session.cleared)
        assertTrue(forceLogouts.isNotEmpty())
        collector.cancel()
    }

    @Test
    fun noStoredSessionSendsNoAuthorizationHeaderAndAnyResultingUnauthorizedStillForcesLogout() = runTest {
        val session = InMemorySessionRepository(token = null, refreshToken = null)
        val bus = SessionEventBus()
        val forceLogouts = mutableListOf<Unit>()
        val collector = async(UnconfinedTestDispatcher(testScheduler)) { bus.forceLogout.collect { forceLogouts.add(it) } }

        val engine = MockEngine { request ->
            assertEquals(null, request.headers[HttpHeaders.Authorization])
            respond("denied", HttpStatusCode.Unauthorized)
        }
        val client = createAuthenticatedHttpClient(publicClient(engine), baseUrl, session, bus)

        client.get("$baseUrl/matches")
        advanceUntilIdle()

        assertTrue(forceLogouts.isNotEmpty())
        collector.cancel()
    }

    @Test
    fun clearAuthTokensPicksUpADifferentUsersSessionOnTheNextRequest() = runTest {
        // Reproduces the reported bug: logging out and a different user logging in, on the same
        // still-running app process, kept authenticating every request as the *first* user -- because
        // the bearer provider's loadTokens() result is cached in memory for the life of the client
        // instance (see LoginUseCase/LogoutUseCase's doc comments), and nothing told it to forget that
        // cache just because SessionRepository changed underneath it. clearAuthTokens() (called by both
        // use cases now) is the fix -- verified here directly against the real Auth plugin.
        val session = InMemorySessionRepository(token = "user-a-token", refreshToken = "user-a-refresh")
        val bus = SessionEventBus()
        val engine = MockEngine { request ->
            assertEquals("Bearer ${session.storedToken}", request.headers[HttpHeaders.Authorization])
            respond("ok", HttpStatusCode.OK)
        }
        val client = createAuthenticatedHttpClient(publicClient(engine), baseUrl, session, bus)

        client.get("$baseUrl/users/me") // caches user A's token in the Auth plugin

        // "Logout" then "login as user B" -- SessionRepository changes, but nothing has told the
        // already-constructed client about it yet.
        session.storedToken = "user-b-token"
        session.storedRefreshToken = "user-b-refresh"
        client.clearAuthTokens()

        val response = client.get("$baseUrl/users/me")

        assertEquals(HttpStatusCode.OK, response.status)
    }
}
