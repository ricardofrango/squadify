import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Generates the coach app's Baseline Profile (TECHNICAL_DECISIONS.md §11.7): it runs the app on a
// connected device, records the code its startup runs, and writes the profile into the app's
// src/main/generated/baselineProfiles, which the release build ships. Run it with
// `./gradlew :app:coachAndroidApp:generateBaselineProfile`, the phone unlocked (it signs the dev app out).
plugins {
    alias(libs.plugins.androidTest)
    alias(libs.plugins.baselineProfile)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

android {
    namespace = "com.ricardofrango.squadify.coach.baselineprofile"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        // Baseline Profiles are recorded from Android 9 (API 28) up.
        minSdk = 28
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    targetProjectPath = ":app:coachAndroidApp"

    // The app's flavors, so each variant has a matching one here.
    flavorDimensions += "environment"
    productFlavors {
        create("dev") {
            dimension = "environment"
            // The app the generator starts (the coach app's dev id).
            testInstrumentationRunnerArguments["targetAppId"] = "com.ricardofrango.squadify.coach.dev"
        }
        create("staging") { dimension = "environment" }
        create("prod") { dimension = "environment" }
    }
}

// The phone over USB, not a Gradle managed emulator.
baselineProfile {
    useConnectedDevices = true
}

// Recorded with the dev build only (the app merges the profile into main, for every flavor): the
// staging and production apps on the phone are store installs it would otherwise replace.
androidComponents {
    beforeVariants { variant ->
        variant.enable = variant.productFlavors.any { (_, flavor) -> flavor == "dev" }
    }
}

dependencies {
    implementation(libs.androidx.testExt.junit)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
