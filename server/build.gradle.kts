plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.serialization)
    application
}

group = "com.ricardofrango.squadify"
// Same source of truth as app/androidApp's versionName (root gradle.properties' VERSION_NAME) -- see
// TECHNICAL_DECISIONS.md's versioning section. Only used for the Gradle module/artifact version today
// (e.g. the fat-jar's filename); not yet exposed at runtime (no /version endpoint).
version = (project.findProperty("VERSION_NAME") as? String) ?: "1.1.0"
application {
    mainClass = "com.ricardofrango.squadify.ApplicationKt"
}

dependencies {
    api(project(":common"))
    implementation(libs.logback)

    // koin
    implementation(libs.koin.ktor)
    implementation(libs.koin.logger.slf4j)

    // Password encryption
    implementation(libs.jbcrypt)

    // Database
    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)

    // postgresql database
    implementation(libs.postgresql)

    // database migrations
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)

    // Connection pool (recommended)
    implementation(libs.hikaricp)

    // Push notifications (Firebase Admin SDK)
    implementation(libs.firebase.admin)

    // KTOR
    implementation(libs.ktor.server.serialization.kotlinx.json)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.logging)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.auth.jwt)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.exposed.kotlin.datetime)
    implementation(libs.kotlinx.datetime)
    implementation(libs.identity.jvm)

    testImplementation(libs.mockk)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.testJunit)
}