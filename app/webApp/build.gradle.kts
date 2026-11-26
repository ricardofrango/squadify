import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

// No build-variant system for Kotlin/JS or Wasm, so the same dev/staging/production split is done via a
// Gradle property instead: server/clubs/Dockerfile equivalents pass `-PsquadcallEnv=staging|production` when
// building for deployment. Defaults to "dev" (localhost) for local `jsBrowserDevelopmentRun`. See
// TECHNICAL_DECISIONS.md's "Environment-Based App Config" section.
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
            implementation(project(":app:clubs"))

            implementation(libs.compose.ui)
        }
        commonMain {
            kotlin.srcDir(generateSquadcallConfig)
        }
    }
}

// composeCompatibilityBrowserDistribution's webpack step publishes the main wasm binary under a
// content-hashed filename, but originWasmWebApp.js's own fetch call still requests it by its Kotlin
// module name ("<rootProject>-<module path>.wasm") -- verified by diffing SHA256 of both files, byte
// identical. nginx's SPA fallback (try_files ... /index.html) turns that 404 into a silent 200 HTML
// response, so the browser fails to instantiate WebAssembly and the app never renders (blank page).
// Duplicate the binary under the name the loader actually requests so it's actually there to serve.
val wasmMainModuleName = "${rootProject.name}-${project.path.removePrefix(":").replace(":", "-")}"
val fixWasmLoaderAsset = tasks.register<Copy>("fixWasmLoaderAsset") {
    from(layout.buildDirectory.file("compileSync/wasmJs/main/productionExecutable/optimized/$wasmMainModuleName.wasm"))
    into(layout.buildDirectory.dir("dist/composeWebCompatibility/productionExecutable"))
}

tasks.named("composeCompatibilityBrowserDistribution") {
    finalizedBy(fixWasmLoaderAsset)
}
