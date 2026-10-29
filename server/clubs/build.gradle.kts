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

// Local runs only (not the deployed jar): Ktor's development mode, so the server reloads its code when
// `./gradlew -t :server:clubs:classes` recompiles it. A `-t :server:clubs:run` can't do that -- Gradle waits for
// the build to finish before watching, and `run` never finishes while the server is up.
tasks.named<JavaExec>("run") {
    jvmArgs("-Dio.ktor.development=true")
}

dependencies {
    api(project(":common"))
    // The game code shared with the coach server (TECHNICAL_DECISIONS §72).
    api(project(":server:core"))
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

    // Image storage: Railway Storage Buckets speak the S3 API. The JDK's HTTP client only -- no Apache
    // or Netty client pulled in next to Ktor's.
    implementation(libs.awssdk.s3) {
        exclude(group = "software.amazon.awssdk", module = "apache-client")
        exclude(group = "software.amazon.awssdk", module = "netty-nio-client")
    }
    implementation(libs.awssdk.url.connection.client)

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