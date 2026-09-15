// Plain JVM module holding the models/exceptions shared with :server. Deliberately NOT a Kotlin
// Multiplatform module: :core (below) re-sources these same files into its commonMain so client apps
// keep seeing identical types, but :server depends on :common directly so building it never needs to
// configure :core's Android/iOS/JS/Wasm targets (see server/Dockerfile).
plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.serialization)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
}
