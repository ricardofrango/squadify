package com.sportssoftw.squadify.coach

import com.sportssoftw.squadify.model.FeatureFlags
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

// The minimum app builds (MIN_ANDROID_BUILD and MIN_IOS_BUILD are 50 in the tests; TECHNICAL_DECISIONS §85): an app build
// below its platform's minimum is refused with 426 on every call, and the flags tell it to update.
class AppVersionGateTest {
    private fun HttpRequestBuilder.app(platform: String, build: Int) {
        header("X-App-Platform", platform)
        header("X-App-Build", build.toString())
    }

    @Test
    fun anOutOfDateAndroidBuildIsRefused() = testApplication {
        application { module() }
        val response = client.get("/v1/spaces") { app("android", 49) }
        assertEquals(HttpStatusCode.UpgradeRequired, response.status)
    }

    @Test
    fun aCurrentBuildIsNotRefusedByTheGate() = testApplication {
        application { module() }
        val response = client.get("/v1/spaces") { app("android", 50) }
        assertNotEquals(HttpStatusCode.UpgradeRequired, response.status)
    }

    @Test
    fun eachPlatformHasItsOwnMinimum() = testApplication {
        application { module() }
        assertEquals(HttpStatusCode.UpgradeRequired, client.get("/v1/spaces") { app("ios", 49) }.status)
        assertNotEquals(HttpStatusCode.UpgradeRequired, client.get("/v1/spaces") { app("ios", 50) }.status)
    }

    @Test
    fun aRequestWithoutTheHeadersIsNeverRefused() = testApplication {
        application { module() }
        assertNotEquals(HttpStatusCode.UpgradeRequired, client.get("/v1/spaces").status)
    }

    @Test
    fun theFlagsSayWhetherTheAppMustUpdate() = testApplication {
        application { module() }
        val old: FeatureFlags = jsonClient().get("/v1/feature-flags") { app("android", 49) }.body()
        assertEquals(true, old.updateRequired)
        val current: FeatureFlags = jsonClient().get("/v1/feature-flags") { app("android", 50) }.body()
        assertEquals(false, current.updateRequired)
        val web: FeatureFlags = jsonClient().get("/v1/feature-flags").body()
        assertEquals(false, web.updateRequired)
    }
}
