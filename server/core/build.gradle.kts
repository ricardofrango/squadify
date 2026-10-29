// The game code shared by the clubs server (:server) and, from Phase 9, the coach server
// (TECHNICAL_DECISIONS §72): the game use cases, the repository and service interfaces they use, the
// stats calculator, the exceptions and the AccessPolicy they ask. No Ktor, no Exposed, no club roles:
// each server supplies the implementations (repositories, buses, push, its own AccessPolicy).
plugins {
    alias(libs.plugins.kotlinJvm)
}

group = "com.ricardofrango.squadify"

dependencies {
    api(project(":common"))
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.datetime)
    // CurrentPasswordGuard checks a password against its bcrypt hash.
    implementation(libs.jbcrypt)
    // NotifyMatchEventUseCase logs push failures (slf4j, through logback as on the server).
    implementation(libs.logback)
}
