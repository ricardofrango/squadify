package com.sportssoftw.squadcall.data.remote

import com.sportssoftw.squadcall.data.config.DefaultAppConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutCapability
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

// Copying an age group is a long request (TECHNICAL_DECISIONS §81): the client's default 15 seconds cut it off on
// staging while the server went on and finished the copy, so the app showed an error for a copy that had worked.
class AgeGroupImportRepositoryImplTest {

    @Test
    fun theCopyHasItsOwnLongTimeoutAndTheOtherRequestsKeepTheDefault() = runTest {
        val seen = mutableMapOf<String, Long?>()
        val engine = MockEngine { request ->
            seen[request.url.encodedPath] = request.getCapabilityOrNull(HttpTimeoutCapability)?.requestTimeoutMillis
            respond(
                content = if (request.url.encodedPath.endsWith("/import/age-groups")) """{"ageGroupId":"ag","teams":1,"players":2,"opponents":1,"matches":3}""" else "[]",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = HttpClient(engine) {
            install(HttpTimeout) { requestTimeoutMillis = 15_000 }
            install(ContentNegotiation) { json() }
        }
        val repository = AgeGroupImportRepositoryImpl(client, DefaultAppConfig("https://example.test/v1", isDebug = false))

        repository.importAgeGroup("club", "https://other.test", "source-ag", "grant")
        repository.listSourceAgeGroups("club", "https://other.test", "grant")

        assertEquals(IMPORT_TIMEOUT_MILLIS, seen["/v1/import/age-groups"])
        // The list sets none of its own: the client's 15 seconds apply.
        assertEquals(15_000L, seen["/v1/import/source/age-groups"])
    }

    @Test
    fun aRequestWithNoAnswerInTimeIsNotAFailure() {
        assertEquals(true, HttpRequestTimeoutException("https://example.test/v1/import/age-groups", 180_000L).isNoAnswerInTime())
        assertEquals(false, ApiException(500, "SRV_000", "boom").isNoAnswerInTime())
        assertEquals(false, IllegalStateException("x").isNoAnswerInTime())
    }
}
