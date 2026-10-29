// The database code shared by the clubs server and the coach server (TECHNICAL_DECISIONS §72): the
// Exposed table definitions and every Exposed repository. Both databases have these tables (the coach
// one leaves out training, permissions, invites and parents' links), each created by its own server's
// Flyway migrations, and each server's schema test checks its migrations against these definitions.
// No Ktor and no server config: each server connects, migrates and binds the repositories itself.
plugins {
    alias(libs.plugins.kotlinJvm)
    // SchemaCheck, for each server's schema test.
    `java-test-fixtures`
}

group = "com.ricardofrango.squadify"

dependencies {
    api(project(":server:core"))
    api(libs.exposed.core)
    api(libs.exposed.jdbc)
    api(libs.exposed.kotlin.datetime)
    implementation(libs.exposed.dao)
    // A match's lineup and a training plan are stored as JSON.
    implementation(libs.kotlinx.serialization.json)

    testFixturesImplementation(libs.flyway.core)
    testFixturesImplementation(libs.flyway.database.postgresql)
    testFixturesImplementation(libs.postgresql)
}
