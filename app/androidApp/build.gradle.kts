import com.google.firebase.appdistribution.gradle.firebaseAppDistribution
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.googleServices)
    alias(libs.plugins.firebaseCrashlytics)
    alias(libs.plugins.firebaseAppDistribution)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(project(":app:shared"))

    implementation(libs.androidx.activity.compose)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
    
    // Koin
    implementation(libs.koin.android)

    // Firebase -- Crashlytics only for now (see ISSUES TO FIX.md); the BoM pins compatible versions
    // for every Firebase artifact so individual ones never need their own version.ref.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)
}

android {
    namespace = "com.ricardofrango.squadify"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.ricardofrango.squadify"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // Environment (not applicationId-suffixed -- google-services.json is registered for the bare
    // com.ricardofrango.squadify package only; a suffix would break Crashlytics/App Distribution for
    // every flavor but "dev"). See TECHNICAL_DECISIONS.md's "Environment-Based App Config" section.
    // dev = LAN address for physical-device testing against a local server. staging = what Firebase App
    // Distribution builds should target (appDistributionUploadStagingRelease). prod = the store release.
    flavorDimensions += "environment"
    productFlavors {
        create("dev") {
            dimension = "environment"
            buildConfigField("String", "BASE_URL", "\"http://192.168.1.84:8080/v1\"")
        }
        create("staging") {
            dimension = "environment"
            // Railway-generated domain for the staging server -- swap for stg-api.squadify.pt once that
            // custom domain is set up (see ISSUES TO FIX.md).
            buildConfigField("String", "BASE_URL", "\"https://squadify-staging.up.railway.app/v1\"")
        }
        create("prod") {
            dimension = "environment"
            // TODO placeholder -- swap once Railway production is rebuilt (see ISSUES TO FIX.md).
            buildConfigField("String", "BASE_URL", "\"https://api.squadify.pt/v1\"")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
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
    }
}