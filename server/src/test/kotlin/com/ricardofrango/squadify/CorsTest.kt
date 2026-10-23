package com.ricardofrango.squadify

import io.ktor.client.request.header
import io.ktor.client.request.options
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The browser's preflight for each method the app's repositories send. A refused one shows up in the
 * web app only as "Failed to fetch" -- PUT was missing, which crashed saving a match report on web.
 */
class CorsTest {

    private val webApp = "https://app.example.test"

    private fun preflight(method: String, origin: String = webApp, allowAnyLocalhost: Boolean = false) = testApplication {
        application { configureCors(webApp, allowAnyLocalhost) }
        val response = client.options("/matches/m1/report") {
            header(HttpHeaders.Origin, origin)
            header(HttpHeaders.AccessControlRequestMethod, method)
            header(HttpHeaders.AccessControlRequestHeaders, "authorization,content-type")
        }
        assertEquals(HttpStatusCode.OK, response.status, "$method from $origin")
        assertEquals(origin, response.headers[HttpHeaders.AccessControlAllowOrigin], "$method from $origin")
    }

    @Test
    fun everyMethodTheAppUsesIsAllowed() {
        listOf("GET", "POST", "PUT", "PATCH", "DELETE").forEach { preflight(it) }
    }

    @Test
    fun anotherOriginIsRefused() = testApplication {
        application { configureCors(webApp) }
        val response = client.options("/matches/m1/report") {
            header(HttpHeaders.Origin, "https://elsewhere.test")
            header(HttpHeaders.AccessControlRequestMethod, "PUT")
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun devAcceptsLocalhostOnAnyPort() {
        listOf("http://localhost:8082", "http://localhost:3000", "http://127.0.0.1:8081").forEach {
            preflight("POST", origin = it, allowAnyLocalhost = true)
        }
    }

    @Test
    fun outsideDevAnotherLocalhostPortIsRefused() = testApplication {
        application { configureCors(webApp) }
        val response = client.options("/auth/login") {
            header(HttpHeaders.Origin, "http://localhost:8082")
            header(HttpHeaders.AccessControlRequestMethod, "POST")
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun devStillRefusesOtherHosts() = testApplication {
        application { configureCors(webApp, allowAnyLocalhost = true) }
        val response = client.options("/auth/login") {
            header(HttpHeaders.Origin, "http://localhost.evil.test:8082")
            header(HttpHeaders.AccessControlRequestMethod, "POST")
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }
}
