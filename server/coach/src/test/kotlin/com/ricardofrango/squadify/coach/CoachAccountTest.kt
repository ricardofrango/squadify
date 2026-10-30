package com.ricardofrango.squadify.coach

import com.ricardofrango.squadify.model.ChangePasswordRequest
import com.ricardofrango.squadify.model.EmailChangeConfirmRequest
import com.ricardofrango.squadify.model.EmailChangeRequest
import com.ricardofrango.squadify.model.LoginRequest
import com.ricardofrango.squadify.model.LoginResponse
import com.ricardofrango.squadify.model.PasswordResetConfirmRequest
import com.ricardofrango.squadify.model.PasswordResetRequest
import com.ricardofrango.squadify.model.RegisterRequest
import com.ricardofrango.squadify.model.UpdateProfileRequest
import com.ricardofrango.squadify.model.User
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

// The account on the coach server (the shared accountRoutes): the profile, the password, the email change
// and Forgot password, on the coach's own accounts. The emailed codes can't be read here (no Resend key
// in tests: the emails are skipped), so the steps that need one are checked up to a wrong code.
class CoachAccountTest {

    private suspend fun HttpClient.json(path: String, body: Any, token: String? = null) = post(path) {
        token?.let { bearerAuth(it) }
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    private suspend fun HttpClient.login(email: String, password: String) = json("/v1/auth/login", LoginRequest(email, password))

    @Test
    fun theProfileAndThePassword() = testApplication {
        application { module() }
        val api = jsonClient()
        val email = "coach-${Uuid.random()}@test.com"
        api.json("/v1/auth/register", RegisterRequest(email = email, password = "password1", firstName = "Ana", lastName = "Coach"))
        val token = api.login(email, "password1").body<LoginResponse>().accessToken

        assertEquals(email, api.get("/v1/users/me") { bearerAuth(token) }.body<User>().email)
        val updated = api.patch("/v1/users/me") {
            json(token, UpdateProfileRequest(firstName = "Ana", lastName = "Silva", phone = "912345678"))
        }.body<User>()
        assertEquals("Silva" to "912345678", updated.lastName to updated.phone)

        // A wrong current password is refused; the right one changes it.
        assertEquals(HttpStatusCode.BadRequest, api.json("/v1/users/me/password", ChangePasswordRequest("wrong-one", "password2"), token).status)
        assertEquals(HttpStatusCode.NoContent, api.json("/v1/users/me/password", ChangePasswordRequest("password1", "password2"), token).status)
        assertEquals(HttpStatusCode.Unauthorized, api.login(email, "password1").status)
        assertEquals(HttpStatusCode.OK, api.login(email, "password2").status)
    }

    @Test
    fun changingTheEmailAndForgotPasswordAskForTheEmailedCode() = testApplication {
        application { module() }
        val api = jsonClient()
        val email = "coach-${Uuid.random()}@test.com"
        api.json("/v1/auth/register", RegisterRequest(email = email, password = "password1", firstName = "Ana", lastName = "Coach"))
        val token = api.login(email, "password1").body<LoginResponse>().accessToken

        // Change email: a code to the new address, then a wrong one is refused.
        val newEmail = "new-${Uuid.random()}@test.com"
        assertEquals(HttpStatusCode.Accepted, api.json("/v1/users/me/email/request", EmailChangeRequest(newEmail, "password1"), token).status)
        assertEquals(HttpStatusCode.BadRequest, api.json("/v1/users/me/email/confirm", EmailChangeConfirmRequest("000000"), token).status)

        // Forgot password: an unknown email is a 404; a known one gets a code, and a wrong code is refused.
        assertEquals(HttpStatusCode.NotFound, api.json("/v1/auth/password-reset/request", PasswordResetRequest("nobody-${Uuid.random()}@test.com")).status)
        assertEquals(HttpStatusCode.Accepted, api.json("/v1/auth/password-reset/request", PasswordResetRequest(email)).status)
        assertEquals(
            HttpStatusCode.BadRequest,
            api.json("/v1/auth/password-reset/confirm", PasswordResetConfirmRequest(email, "000000", "password2")).status
        )
        assertEquals(HttpStatusCode.OK, api.login(email, "password1").status)
    }
}
