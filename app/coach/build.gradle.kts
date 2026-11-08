import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Squadify for Coaches (TECHNICAL_DECISIONS §72): the coach app on top of :app:shared. Its own top-level
// navigation (CoachNavigation), Koin assembly (initCoachKoin), strings, and the screens only the coach
// app has: the space's first setup and its home. Android and iOS only; the Android app is
// :app:coachAndroidApp, the iOS app app/coachIosApp.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.serialization)
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        // The framework the coach iOS app (app/coachIosApp) imports: `import Coach`.
        iosTarget.binaries.framework {
            baseName = "Coach"
            isStatic = true
        }
    }

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
            // Subscriptions on Google Play (TECHNICAL_DECISIONS §72, payments).
            implementation(libs.android.billing)
            // Ads on the Free plan, after the consent form (REQUIREMENTS §5c).
            implementation(libs.google.ads)
            implementation(libs.google.ump)
            // The ads library brings WorkManager 2.7.0 with Room 2.2.5, whose R8 rule keeps WorkManager's
            // database class but not its constructor: AGP 9's full-mode R8 drops it and the release app
            // crashed on start. A newer WorkManager brings a Room whose rule keeps it.
            implementation(libs.androidx.work.runtime)
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
            implementation(libs.navigationevent.compose)

            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)

            implementation(libs.ktor.client.core)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor3)
            // The image picker, for My club or team's logo.
            implementation(libs.filekit.dialogs.compose)
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
