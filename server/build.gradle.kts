plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.serialization)
    application
}

group = "com.ricardofrango.clubmanager"
version = "1.0.0"
application {
    mainClass = "com.ricardofrango.clubmanager.ApplicationKt"
}

dependencies {
    api(project(":core"))
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

    // H2 database
    implementation(libs.h2)

    // postgresql database
    implementation(libs.postgresql)

    // database migrations
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)

    // Connection pool (recommended)
    implementation(libs.hikaricp)

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
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.exposed.kotlin.datetime)
    implementation(libs.kotlinx.datetime)
    implementation(libs.identity.jvm)

    // konf
    implementation(libs.konf)

    testImplementation(libs.mockk)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.testJunit)
}