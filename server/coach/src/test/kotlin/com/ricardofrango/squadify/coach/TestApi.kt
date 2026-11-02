package com.ricardofrango.squadify.coach

import com.ricardofrango.squadify.model.LoginRequest
import com.ricardofrango.squadify.model.LoginResponse
import com.ricardofrango.squadify.model.RegisterRequest
import com.ricardofrango.squadify.model.SetSpacePlanRequest
import com.ricardofrango.squadify.model.SpacePlan
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid

// Calling the coach API in tests: a JSON client, a new signed-in account, a JSON body with a token.

fun ApplicationTestBuilder.jsonClient() = createClient {
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
}

/** A new account (or, for an [email] already signed up, that one); returns its access token. */
suspend fun HttpClient.signUp(email: String = "coach-${Uuid.random()}@test.com"): String {
    post("/v1/auth/register") {
        contentType(ContentType.Application.Json)
        setBody(RegisterRequest(email = email, password = "password1", firstName = "Ana", lastName = "Coach"))
    }
    return post("/v1/auth/login") {
        contentType(ContentType.Application.Json)
        setBody(LoginRequest(email, "password1"))
    }.body<LoginResponse>().accessToken
}

inline fun <reified T> HttpRequestBuilder.json(token: String, body: T) {
    bearerAuth(token)
    contentType(ContentType.Application.Json)
    setBody(body)
}

/** The tests' platform admin (PLATFORM_ADMIN_EMAILS, build.gradle.kts) sets [spaceId]'s plan. */
suspend fun HttpClient.setPlan(spaceId: String, plan: SpacePlan) {
    val admin = signUp(PLATFORM_ADMIN)
    val response = put("/v1/admin/spaces/$spaceId/plan") { json(admin, SetSpacePlanRequest(plan)) }
    check(response.status == HttpStatusCode.NoContent) { "Setting the plan: ${response.status}" }
}

const val PLATFORM_ADMIN = "platform-admin@test.com"
