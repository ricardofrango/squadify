import com.google.firebase.appdistribution.gradle.firebaseAppDistribution
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.googleServices)
    alias(libs.plugins.firebaseCrashlytics)
    alias(libs.plugins.firebaseAppDistribution)
}

// Local-only, gitignored -- never committed (same pattern as firebase-service-account.json). Falls back
// to the debug keystore (see buildTypes.release below) when absent, so a fresh clone/CI run without
// these secrets still produces an installable (if not properly release-signed) APK.
val keystorePropertiesFile = rootProject.file("keystore/keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

// The dev flavor talks to a server on this machine, so it needs this machine's LAN address -- which
// DHCP changes from time to time (a hardcoded 192.168.1.84 silently stopped working when it did).
// Resolved at build time: `squadify.devHost` in local.properties wins (e.g. a different network
// interface, or a server on another machine); otherwise the first site-local IPv4 address of an up,
// non-loopback interface; otherwise the emulator's host loopback, 10.0.2.2.
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
    implementation(project(":app:clubs"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)

    // The root keyboard padding in MainActivity (WindowInsets, windowInsetsPadding).
    implementation(libs.compose.foundation)
    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
    
    // Koin
    implementation(libs.koin.android)

    // Firebase -- Crashlytics + Cloud Messaging (push notifications, see TECHNICAL_DECISIONS.md);
    // the BoM pins compatible versions for every Firebase artifact so individual ones never need
    // their own version.ref.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.messaging)
}

// Single source of truth for the human-facing semantic version lives in root gradle.properties
// (VERSION_NAME) -- shared with server/clubs/build.gradle.kts so the two can't drift by hand-editing one and
// forgetting the other. versionCode is computed separately: CI passes -PversionCode=<n>
// (deploy-staging.yml uses ${{ github.run_number }}, which auto-increments every workflow run) so every
// uploaded build has a unique, strictly-increasing code even when VERSION_NAME doesn't change between
// merges to develop -- see TECHNICAL_DECISIONS.md's versioning section for the original problem this
// solves (every staging redeploy looked identical). Falls back to a fixed placeholder for local builds,
// where uniqueness doesn't matter.
val versionNameProp = (project.findProperty("VERSION_NAME") as? String) ?: "1.1.0"
val ciBuildNumber = (project.findProperty("versionCode") as? String)?.toIntOrNull()

android {
    namespace = "com.ricardofrango.squadify"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.ricardofrango.squadify"
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

    // Environment. dev/staging carry an applicationIdSuffix (prod stays bare, for the real Play Store
    // listing) so each is a genuinely distinct app to Firebase -- otherwise a device that's ever run
    // more than one flavor could get handed the *same* FCM registration token across them (Firebase
    // scopes a token by package name + project + signing cert, not by build variant), letting a push
    // triggered on one environment's server land on a device someone only meant to test another
    // environment on. google-services.json is registered for all three resulting package names under
    // the one Firebase project (squadify-958d3) -- see TECHNICAL_DECISIONS.md's push-notifications
    // section and "Environment-Based App Config" section. resValue app_name distinguishes the three on
    // a device's launcher/recents now that all three can be installed side by side.
    // dev = LAN address for physical-device testing against a local server. staging = what Firebase App
    // Distribution builds should target (appDistributionUploadStagingRelease). prod = the store release.
    flavorDimensions += "environment"
    productFlavors {
        create("dev") {
            dimension = "environment"
            applicationIdSuffix = ".dev"
            buildConfigField("String", "BASE_URL", "\"http://$devHost:8080/v1\"")
            resValue("string", "app_name", "Squadify Dev")
        }
        create("staging") {
            dimension = "environment"
            applicationIdSuffix = ".stg"
            // Railway-generated domain for the staging server -- swap for stg-api.squadify.pt once that
            // custom domain is set up (see ISSUES TO FIX.md).
            buildConfigField("String", "BASE_URL", "\"https://squadify-staging.up.railway.app/v1\"")
            resValue("string", "app_name", "Squadify Stg")
            // This is the flavor redeployed on every merge to develop (deploy-staging.yml) -- without a
            // per-build suffix, every install shows the identical versionName no matter how many times
            // it's been redeployed, which was the whole original problem (see TECHNICAL_DECISIONS.md's
            // versioning section). "local" when built outside CI (versionCode wasn't passed in).
            versionNameSuffix = "-stg.${ciBuildNumber ?: "local"}"
        }
        create("prod") {
            dimension = "environment"
            // TODO placeholder -- swap once Railway production is rebuilt (see ISSUES TO FIX.md).
            buildConfigField("String", "BASE_URL", "\"https://api.squadify.pt/v1\"")
            resValue("string", "app_name", "Squadify")
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
            // Without an explicit signingConfig, AGP leaves release APKs unsigned (it only auto-signs
            // "debug"), and Android refuses to install an unsigned APK at all -- that's why App Tester
            // installs were failing before keystore/ existed. Falls back to the debug keystore when the
            // real one isn't present locally/in CI (see keystorePropertiesFile above).
            signingConfig = if (keystorePropertiesFile.exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            // R8: shrinks, optimises and obfuscates the code, and drops unused resources (§11.7).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            firebaseAppDistribution {
                // Local-only file, gitignored -- never committed. Lets `appDistributionUploadRelease`
                // authenticate non-interactively via a service account instead of `firebase login`.
                serviceCredentialsFile = "$projectDir/firebase-service-account.json"
                groups = "squadify-testers"
            }
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