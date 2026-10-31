import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.serialization)
}

kotlin {
    // The iOS framework is built by each app's module (:app:clubs today), not by this shared one.
    iosArm64()
    iosSimulatorArm64()
    
    jvm()
    
    js {
        browser()
    }
    
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }
    
    android {
       namespace = "com.ricardofrango.squadify.app.shared"
       compileSdk = libs.versions.android.compileSdk.get().toInt()
       minSdk = libs.versions.android.minSdk.get().toInt()
    
       compilerOptions {
           jvmTarget = JvmTarget.JVM_11
       }
       androidResources {
           enable = true
       }
       withHostTest {
           isIncludeAndroidResources = true
       }
       withDeviceTestBuilder {
           sourceSetTreeName = "test"
       }.configure {
           instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
       }
    }
    
    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.compose.uiTooling)
            // OkHttp, not CIO: CIO does TLS via a raw SSLEngine, which doesn't propagate the hostname
            // Android's per-domain NetworkSecurityTrustManager needs once network_security_config.xml has
            // ANY <domain-config> entry (ours does, for the dev LAN IP) -- every HTTPS connection then
            // fails with "Domain specific configurations require that hostname aware
            // checkServerTrusted(X509Certificate[], String, String) is used". OkHttp's SSLSocket-based
            // stack handles this correctly; this is Ktor's own documented workaround for Android.
            implementation(libs.ktor.client.okhttp)
            // Credential Manager: the system's "save password?" sheet and saved-account sign-in
            // (CredentialStore.android.kt). play-services-auth is what serves it on Android 13 and below.
            implementation(libs.androidx.credentials)
            implementation(libs.androidx.credentials.playServicesAuth)
            // Push notifications for both apps (com.ricardofrango.squadify.push): receiving them, the
            // device's token, and asking for the permission.
            implementation(project.dependencies.platform(libs.firebase.bom))
            implementation(libs.firebase.messaging)
            implementation(libs.androidx.activity.compose)
        }
        iosMain.dependencies {
            // Ktor's HttpClient() picks its engine at runtime from whatever engine artifacts are
            // actually on the classpath -- there's no link-time check, so the app links and even
            // launches fine without this, then crashes the moment Koin first resolves an HttpClient
            // (very early) with "No client engines found". Darwin (NSURLSession-backed) is Ktor's
            // engine for iOS/macOS/watchOS/tvOS.
            implementation(libs.ktor.client.darwin)
        }
        commonMain.dependencies {
            api(project(":core"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.material3.adaptive)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.datetime)
            
            // Navigation
            implementation(libs.navigation3.ui)
            implementation(libs.androidx.lifecycle.viewmodelNavigation3)
            implementation(libs.navigationevent.compose)

            // Koin -- koin-core is `api`, not `implementation`: initKoin()'s return type (KoinApplication)
            // is part of this module's public surface, so consumers need it on their own compile
            // classpath. Only ever surfaced when Desktop/Web actually started calling initKoin().
            api(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)

            // Ktor Client
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.client.websockets)
            implementation(libs.ktor.client.auth)

            // Settings
            implementation(libs.multiplatform.settings.no.arg)

            // Images: Coil loads the club logo through the app's authenticated Ktor client; FileKit
            // opens the platform's image picker for uploading one.
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor3)
            implementation(libs.filekit.dialogs.compose)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.test)
        }
        jsMain.dependencies {
            implementation(libs.wrappers.browser)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}

// The apps' modules (:app:clubs, later :app:coach) use these strings and images too, so the generated
// Res class is public; its package stays the one every import already uses.
compose.resources {
    publicResClass = true
    packageOfResClass = "squadify.app.shared.generated.resources"
}
