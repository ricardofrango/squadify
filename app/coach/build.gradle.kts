import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Squadify for Coaches (TECHNICAL_DECISIONS §72): the coach app on top of :app:shared. Its own top-level
// navigation (CoachNavigation), Koin assembly (initCoachKoin), strings, and the screens only the coach
// app has: the space's first setup and its home. Android and iOS only; the Android app is
// :app:coachAndroidApp.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.serialization)
}

kotlin {
    iosArm64()
    iosSimulatorArm64()

    android {
        namespace = "com.ricardofrango.squadify.app.coach"
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
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.compose.uiTooling)
        }
        commonMain.dependencies {
            api(project(":app:shared"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.datetime)

            implementation(libs.navigation3.ui)
            implementation(libs.androidx.lifecycle.viewmodelNavigation3)

            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)

            implementation(libs.ktor.client.core)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor3)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

// The coach app's own strings (values, values-pt), in their own Res class.
compose.resources {
    packageOfResClass = "squadify.app.coach.generated.resources"
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}
