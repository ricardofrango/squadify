package com.sportssoftw.squadify.data.cache

import com.sportssoftw.squadify.data.remote.createAuthenticatedHttpClient
import com.sportssoftw.squadify.domain.repository.SessionRepository
import com.sportssoftw.squadify.domain.service.SessionEventBus
import com.sportssoftw.squadify.model.FeatureFlags
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The response cache (TECHNICAL_DECISIONS §83), exercised through the authenticated client against a MockEngine:
 * a fresh answer is served without a request, a stale one isn't, a pull-to-refresh goes to the server, a write
 * clears, and nothing crosses users, languages or signed-out reads.
 */
class ResponseCacheTest {

    @Serializable
    private data class Thing(val name: String)

    private class Session(var current: String?) : SessionRepository {
        override fun saveToken(token: String) { current = token }
        override fun getToken(): String? = current
        override fun saveRefreshToken(token: String) {}
        override fun getRefreshToken(): String? = "refresh"
        override fun clearSession() { current = null }
    }

    private val baseUrl = "https://example.test"
    private val general = "$baseUrl/clubs"
    private val match = "$baseUrl/clubs/c1/home"

    // A JWT whose payload carries [sub]: the cache's user id comes from it.
    private fun tokenFor(sub: String): String {
        val header = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode("""{"alg":"none"}""".encodeToByteArray())
        val payload = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode("""{"sub":"$sub"}""".encodeToByteArray())
        return "$header.$payload.sig"
    }

    private class TestClock { var millis = 1_000_000L }

    private class Harness(val client: HttpClient, val cache: ResponseCache, val requests: () -> Int, val clock: TestClock)

    private fun harness(
        session: Session = Session(tokenFor("user-1")),
        general: Int = 3600,
        match: Int = 60,
        answer: (String) -> HttpStatusCode = { HttpStatusCode.OK },
    ): Harness {
        var count = 0
        val engine = MockEngine { request ->
            count++
            val status = answer(request.url.encodedPath)
            respond(
                content = if (status.value < 300) """{"name":"n$count"}""" else """{"code":"X","message":"no"}""",
                status = status,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val publicClient = HttpClient(engine) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        val clock = TestClock()
        val cache = ResponseCache(session, { FeatureFlags(cacheSeconds = general, matchCacheSeconds = match) }, now = { clock.millis })
        val client = createAuthenticatedHttpClient(publicClient, baseUrl, session, SessionEventBus(), cache)
        return Harness(client, cache, { count }, clock)
    }

    @Test
    fun aFreshAnswerIsServedWithoutARequest() = runTest {
        val h = harness()
        assertEquals("n1", h.client.cachedGet<Thing>(general).name)
        assertEquals("n1", h.client.cachedGet<Thing>(general).name)
        assertEquals(1, h.requests())
    }

    @Test
    fun anAnswerOlderThanItsPeriodIsFetchedAgain() = runTest {
        val h = harness(general = 3600)
        h.client.cachedGet<Thing>(general)
        h.clock.millis += 3_600_000L + 1
        assertEquals("n2", h.client.cachedGet<Thing>(general).name)
        assertEquals(2, h.requests())
    }

    @Test
    fun theGamePeriodIsShorterThanTheGeneralOne() = runTest {
        val h = harness(general = 3600, match = 60)
        h.client.cachedGet<Thing>(general)
        h.client.cachedGet<Thing>(match, CacheKind.MATCH)
        h.clock.millis += 61_000L
        h.client.cachedGet<Thing>(general) // still fresh: 3600 s
        h.client.cachedGet<Thing>(match, CacheKind.MATCH) // stale: 60 s
        assertEquals(3, h.requests())
    }

    @Test
    fun aPullToRefreshGoesToTheServerAndKeepsTheNewAnswer() = runTest {
        val h = harness()
        h.client.cachedGet<Thing>(general)
        val refreshed = withContext(ForceRefresh) { h.client.cachedGet<Thing>(general) }
        assertEquals("n2", refreshed.name)
        assertEquals("n2", h.client.cachedGet<Thing>(general).name)
        assertEquals(2, h.requests())
    }

    @Test
    fun aZeroPeriodTurnsCachingOff() = runTest {
        val h = harness(general = 0, match = 0)
        h.client.cachedGet<Thing>(general)
        h.client.cachedGet<Thing>(general)
        assertEquals(2, h.requests())
    }

    @Test
    fun usersNeverShareAnAnswer() = runTest {
        val session = Session(tokenFor("user-1"))
        val h = harness(session = session)
        h.client.cachedGet<Thing>(general)
        session.current = tokenFor("user-2")
        h.client.cachedGet<Thing>(general)
        assertEquals(2, h.requests())
    }

    @Test
    fun nothingIsKeptWhenNobodyIsSignedIn() = runTest {
        val session = Session(null)
        val h = harness(session = session)
        h.client.cachedGet<Thing>(general)
        h.client.cachedGet<Thing>(general)
        assertEquals(2, h.requests())
    }

    @Test
    fun aFailedAnswerIsNotKept() = runTest {
        var fail = true
        val h = harness(answer = { if (fail) HttpStatusCode.InternalServerError else HttpStatusCode.OK })
        assertFailsWith<Exception> { h.client.cachedGet<Thing>(general) }
        fail = false
        assertEquals("n2", h.client.cachedGet<Thing>(general).name)
        assertEquals(2, h.requests())
    }

    @Test
    fun aWriteClearsTheAnswersKept() = runTest {
        val h = harness()
        h.client.cachedGet<Thing>(general)
        h.client.post("$baseUrl/clubs") { }
        h.client.cachedGet<Thing>(general)
        assertEquals(3, h.requests())
    }

    @Test
    fun logoutClearsTheAnswersKept() = runTest {
        val h = harness()
        h.client.cachedGet<Thing>(general)
        h.client.clearResponseCache()
        h.client.cachedGet<Thing>(general)
        assertEquals(2, h.requests())
    }
}
