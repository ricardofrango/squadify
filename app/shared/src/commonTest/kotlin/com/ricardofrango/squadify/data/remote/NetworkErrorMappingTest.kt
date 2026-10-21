package com.ricardofrango.squadify.data.remote

import com.ricardofrango.squadify.domain.repository.SessionRepository
import com.ricardofrango.squadify.domain.service.SessionEventBus
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/**
 * On web, a request that can't reach the server fails with a `kotlin.Error` ("Fail to fetch"), not an
 * Exception -- the view models' `catch (e: Exception)` missed it and the app crashed submitting a report.
 */
class NetworkErrorMappingTest {

    private object NoSession : SessionRepository {
        override fun saveToken(token: String) {}
        override fun getToken(): String? = "token"
        override fun saveRefreshToken(token: String) {}
        override fun getRefreshToken(): String? = "refresh"
        override fun clearSession() {}
    }

    private fun publicClient(engine: MockEngine) = HttpClient(engine) { mapNetworkErrors() }

    private val failingEngine get() = MockEngine { throw Error("Fail to fetch") }

    @Test
    fun aFetchErrorBecomesANetworkException() = runTest {
        val e = assertFailsWith<NetworkException> { publicClient(failingEngine).get("https://example.test/x") }
        assertEquals("Fail to fetch", e.cause?.message)
    }

    @Test
    fun theAuthenticatedClientMapsItToo() = runTest {
        val client = createAuthenticatedHttpClient(publicClient(failingEngine), "https://example.test", NoSession, SessionEventBus())
        assertFailsWith<NetworkException> { client.get("https://example.test/x") }
    }

    @Test
    fun anExceptionIsLeftAsItIs() = runTest {
        val client = publicClient(MockEngine { throw IllegalStateException("boom") })
        assertIs<IllegalStateException>(assertFailsWith<IllegalStateException> { client.get("https://example.test/x") })
    }

    @Test
    fun aResponseIsUnaffected() = runTest {
        val client = publicClient(MockEngine { respond("ok", HttpStatusCode.OK) })
        assertEquals(HttpStatusCode.OK, client.get("https://example.test/x").status)
    }
}
