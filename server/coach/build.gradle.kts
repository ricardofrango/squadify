// The Squadify for Coaches server (TECHNICAL_DECISIONS §72): its own Ktor service and Postgres database,
// on the code shared with the clubs server (:server:core's use cases, :server:data's tables and
// repositories, :server:web's Ktor setup). Deployed as its own Railway service from server/coach/Dockerfile.
plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.serialization)
    application
}

group = "com.ricardofrango.squadify"
version = (project.findProperty("VERSION_NAME") as? String) ?: "1.1.0"
application {
    mainClass = "com.ricardofrango.squadify.coach.ApplicationKt"
}

dependencies {
    implementation(project(":server:core"))
    implementation(project(":server:data"))
    implementation(project(":server:web"))
    implementation(libs.logback)

    implementation(libs.koin.ktor)
    implementation(libs.koin.logger.slf4j)

    implementation(libs.postgresql)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.hikaricp)

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)

    testImplementation(testFixtures(project(":server:data")))
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.ktor.server.serialization.kotlinx.json)
    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.mockk)
}
