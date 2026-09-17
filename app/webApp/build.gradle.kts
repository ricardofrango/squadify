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
