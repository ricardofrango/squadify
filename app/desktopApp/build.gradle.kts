import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":app:shared"))

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)
}

compose.desktop {
    application {
        mainClass = "com.ricardofrango.squadify.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.ricardofrango.squadify"
            packageVersion = "1.1.0"
        }
    }
}

// Desktop has no build-variant system like Android's product flavors, so the same dev/staging/production
// split is done via a Gradle property instead: `-PsquadifyEnv=staging` when packaging a real distribution.
// Defaults to "dev" (localhost -- the desktop app runs on the same machine as a local dev server, unlike
// a physical Android device, so no LAN IP is needed here). See TECHNICAL_DECISIONS.md's
// "Environment-Based App Config" section.
val squadifyEnv = (findProperty("squadifyEnv") as String?) ?: "dev"
val squadifyBaseUrl = when (squadifyEnv) {
    "staging" -> "https://squadify-staging.up.railway.app/v1" // Railway-generated domain -- swap for stg-api.squadify.pt once that custom domain is set up
    "production" -> "https://api.squadify.pt/v1" // TODO placeholder -- see ISSUES TO FIX.md
    else -> "http://localhost:8080/v1"
}

val generatedSquadifyConfigDir = layout.buildDirectory.dir("generated/squadifyConfig/kotlin")
val generateSquadifyConfig = tasks.register("generateSquadifyConfig") {
    val outputDir = generatedSquadifyConfigDir
    val baseUrl = squadifyBaseUrl
    val isDev = squadifyEnv == "dev"
    outputs.dir(outputDir)
    doLast {
        val file = outputDir.get().file("SquadifyBuildConfig.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            package com.ricardofrango.squadify

            internal object SquadifyBuildConfig {
                const val BASE_URL = "$baseUrl"
                const val IS_DEBUG = $isDev
            }
            """.trimIndent()
        )
    }
}

sourceSets.main {
    kotlin.srcDir(generateSquadifyConfig)
}