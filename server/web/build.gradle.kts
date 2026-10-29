// The Ktor setup both servers install (TECHNICAL_DECISIONS §72): JWT sign-in, JSON, WebSockets, the
// errors' status codes and bodies, CORS and the server-time header. Kept out of :server:core, which has
// no Ktor; each server calls installSquadifyApi with its own tokens and adds its own routes.
plugins {
    alias(libs.plugins.kotlinJvm)
}

group = "com.ricardofrango.squadify"

dependencies {
    api(project(":server:core"))
    api(libs.ktor.server.core)
    api(libs.ktor.server.auth)
    api(libs.ktor.server.auth.jwt)
    api(libs.ktor.server.websockets)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.serialization.kotlinx.json)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.cors)
}
