package com.ricardofrango.squadify.coach

import com.ricardofrango.squadify.model.LoginRequest
import com.ricardofrango.squadify.model.LoginResponse
import com.ricardofrango.squadify.model.RefreshTokenRequest
import com.ricardofrango.squadify.model.RegisterRequest
import com.ricardofrango.squadify.model.TokenResponse
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.uuid.Uuid

// The coach server boots on its own database (created locally if missing) and serves accounts with the
// shared sign-up, sign-in and refresh.
class CoachApplicationTest {
    @Test
    fun theServerIsUp() = testApplication {
        application { module() }
        val response = client.get("/")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Squadify for Coaches API is running", response.bodyAsText())
    }

    @Test
    fun signUpThenSignInThenRefresh() = testApplication {
        application { module() }
        val json = createClient { install(ContentNegotiation) { json() } }
        val email = "coach-${Uuid.random()}@test.com"

        val registered = json.post("/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(email = email, password = "password1", firstName = "Ana", lastName = "Coach"))
        }
        assertEquals(HttpStatusCode.Created, registered.status)

        // The same email again is refused.
        val again = json.post("/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(email = email, password = "password1", firstName = "Ana", lastName = "Coach"))
        }
        assertEquals(HttpStatusCode.Conflict, again.status)

        val login = json.post("/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(email, "password1"))
        }
        assertEquals(HttpStatusCode.OK, login.status)
        val session = login.body<LoginResponse>()
        assertEquals(email, session.user.email)

        val refreshed = json.post("/v1/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody(RefreshTokenRequest(session.refreshToken))
        }
        assertEquals(HttpStatusCode.OK, refreshed.status)
        assertNotEquals(session.refreshToken, refreshed.body<TokenResponse>().refreshToken)
    }

    @Test
    fun aWrongPasswordIsRefused() = testApplication {
        application { module() }
        val json = createClient { install(ContentNegotiation) { json() } }
        val response = json.post("/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest("nobody-${Uuid.random()}@test.com", "nope"))
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}
