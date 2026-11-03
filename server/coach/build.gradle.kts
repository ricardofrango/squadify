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
    // Google Play's Developer API, for the subscriptions bought in the app: Ktor's client, signed in
    // with the service account through Google's auth library (in firebase-admin, as on :server:web).
    implementation(libs.ktor.client.cio)
    implementation(libs.firebase.admin)

    testImplementation(testFixtures(project(":server:data")))
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.ktor.client.websockets)
    testImplementation(libs.ktor.server.serialization.kotlinx.json)
    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.mockk)
}

// The tests start the whole server, which signs up accounts and sets up spaces: on their own database,
// created when missing, never the dev one (`squadify_coach_dev`), and dropped once they've run.
val testDatabase = "squadify_coach_test"

val dropTestDatabase by tasks.registering(JavaExec::class) {
    description = "Drops the coach tests' database ($testDatabase)."
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "com.ricardofrango.squadify.coach.DropTestDatabaseKt"
    environment("DATABASE_NAME", testDatabase)
}

tasks.test {
    environment("DATABASE_NAME", testDatabase)
    // Their images, on disk under build/ rather than the dev server's uploads/.
    environment("LOCAL_IMAGE_DIR", layout.buildDirectory.dir("test-uploads").get().asFile.path)
    // No emails from the tests, even with a key in the shell: an emailed invite then fails to send.
    environment("RESEND_API_KEY", "")
    // The tests' platform admin, who sets spaces' plans (TestApi.setPlan).
    environment("PLATFORM_ADMIN_EMAILS", "platform-admin@test.com")
    finalizedBy(dropTestDatabase)
}
