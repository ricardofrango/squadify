package com.ricardofrango.squadify.coach

import com.ricardofrango.squadify.model.FeatureFlags
import com.ricardofrango.squadify.model.ImportAgeGroupRequest
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.assertEquals
import org.junit.Test

class FeatureFlagsTest {

    // No sign-in needed, and every flag is off unless the server's configuration turns it on (the tests set none).
    @Test
    fun theFeatureFlagsArePublicAndOffByDefault() = testApplication {
        application { module() }
        val response = jsonClient().get("/v1/feature-flags")
        assertEquals(HttpStatusCode.OK, response.status)
        // No cache period configured: the apps don't cache. Plans are on in the tests (build.gradle.kts).
        assertEquals(FeatureFlags(ageGroupImport = false, cacheSeconds = 0, matchCacheSeconds = 0, plansEnabled = true), response.body<FeatureFlags>())
    }

    // Off: the import is refused on this server, whatever the app shows.
    @Test
    fun theImportIsRefusedWhileItsFlagIsOff() = testApplication {
        application { module() }
        val api = jsonClient()
        val coach = api.signUp()
        val response = api.post("/v1/import/age-groups") {
            json(coach, ImportAgeGroupRequest("space", "https://ana.example", "age-group", "grant"))
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }
}
