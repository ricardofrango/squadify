import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":app:clubs"))

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)
}

compose.desktop {
    application {
        mainClass = "com.sportssoftw.squadcall.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.sportssoftw.squadcall"
            packageVersion = "1.1.0"
        }
    }
}

// Desktop has no build-variant system like Android's product flavors, so the same dev/staging/production
// split is done via a Gradle property instead: `-PsquadcallEnv=staging` when packaging a real distribution.
// Defaults to "dev" (localhost -- the desktop app runs on the same machine as a local dev server, unlike
// a physical Android device, so no LAN IP is needed here). See TECHNICAL_DECISIONS.md's
// "Environment-Based App Config" section.
val squadcallEnv = (findProperty("squadcallEnv") as String?) ?: "dev"
val squadcallBaseUrl = when (squadcallEnv) {
    "staging" -> "https://stg-club-api.squadcallapp.com/v1"
    "production" -> "https://club-api.squadcallapp.com/v1" 
    else -> "http://localhost:8080/v1"
}

val generatedSquadcallConfigDir = layout.buildDirectory.dir("generated/squadcallConfig/kotlin")
val generateSquadcallConfig = tasks.register("generateSquadcallConfig") {
    val outputDir = generatedSquadcallConfigDir
    val baseUrl = squadcallBaseUrl
    val isDev = squadcallEnv == "dev"
    outputs.dir(outputDir)
    doLast {
        val file = outputDir.get().file("SquadcallBuildConfig.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            package com.sportssoftw.squadcall

            internal object SquadcallBuildConfig {
                const val BASE_URL = "$baseUrl"
                const val IS_DEBUG = $isDev
            }
            """.trimIndent()
        )
    }
}

sourceSets.main {
    kotlin.srcDir(generateSquadcallConfig)
}