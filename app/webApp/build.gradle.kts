import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

// No build-variant system for Kotlin/JS or Wasm, so the same dev/staging/production split is done via a
// Gradle property instead: server/Dockerfile equivalents pass `-PsquadifyEnv=staging|production` when
// building for deployment. Defaults to "dev" (localhost) for local `jsBrowserDevelopmentRun`. See
// TECHNICAL_DECISIONS.md's "Environment-Based App Config" section.
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

kotlin {
    js {
        browser()
        binaries.executable()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":app:shared"))

            implementation(libs.compose.ui)
        }
        commonMain {
            kotlin.srcDir(generateSquadifyConfig)
        }
    }
}
