import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Properties

// Squadify for Coaches on Android (TECHNICAL_DECISIONS §72): starts :app:coach against the coach server.
// Its own app id, so it installs next to the clubs app. Its own Firebase project, squadify-for-coach
// (google-services.json): Crashlytics, and pushes from the coach server (app/shared's push code).
plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.googleServices)
    alias(libs.plugins.firebaseCrashlytics)
}

// Local-only, gitignored, as in the clubs app: the release keystore, else the debug one.
val keystorePropertiesFile = rootProject.file("keystore/keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

// The dev flavor talks to the coach server on this machine (port 8090), at this machine's LAN address,
// resolved as in the clubs app: `squadify.devHost` in local.properties, else the first site-local IPv4
// address, else the emulator's host loopback.
val devHost: String = run {
    val localProps = Properties().apply {
        rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
    }
    localProps.getProperty("squadify.devHost")?.trim()?.takeIf { it.isNotEmpty() }
        ?: NetworkInterface.getNetworkInterfaces().asSequence()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual }
            .flatMap { it.inetAddresses.asSequence() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { it.isSiteLocalAddress }
            ?.hostAddress
        ?: "10.0.2.2"
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(project(":app:coach"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.compose.foundation)
    implementation(libs.koin.android)

    // Firebase: Crashlytics, and Cloud Messaging for the device's push token (the BoM pins versions).
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.messaging)
}

// The same version as the clubs app (root gradle.properties' VERSION_NAME) until the coach app has
// releases of its own; CI passes -PversionCode.
val versionNameProp = (project.findProperty("VERSION_NAME") as? String) ?: "1.1.0"
val ciBuildNumber = (project.findProperty("versionCode") as? String)?.toIntOrNull()

android {
    namespace = "com.ricardofrango.squadify.coach.android"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.ricardofrango.squadify.coach"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = ciBuildNumber ?: 1
        versionName = versionNameProp
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // As in the clubs app: dev and staging have a suffix, so all three install side by side.
    flavorDimensions += "environment"
    productFlavors {
        create("dev") {
            dimension = "environment"
            applicationIdSuffix = ".dev"
            buildConfigField("String", "BASE_URL", "\"http://$devHost:8090/v1\"")
            resValue("string", "app_name", "Squadify Coach Dev")
        }
        create("staging") {
            dimension = "environment"
            applicationIdSuffix = ".stg"
            // The coach-server service's Railway domain on staging.
            buildConfigField("String", "BASE_URL", "\"https://COACH_STAGING_DOMAIN/v1\"")
            resValue("string", "app_name", "Squadify Coach Stg")
            versionNameSuffix = "-stg.${ciBuildNumber ?: "local"}"
        }
        create("prod") {
            dimension = "environment"
            // TODO placeholder: the coach server has no production service yet (it comes with the
            // coach app's launch).
            buildConfigField("String", "BASE_URL", "\"https://coach-api.squadify.pt/v1\"")
            resValue("string", "app_name", "Squadify Coach")
        }
    }
    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file("keystore/squadify-keystore.jks")
                storePassword = keystoreProperties.getProperty("password")
                keyAlias = keystoreProperties.getProperty("alias")
                keyPassword = keystoreProperties.getProperty("alias-password")
            }
        }
    }
    buildTypes {
        release {
            signingConfig = if (keystorePropertiesFile.exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }
}
