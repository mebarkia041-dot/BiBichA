plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.bibicha.assistant"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.emilia.assistant"
        minSdk = 26
        targetSdk = 35
        versionCode = 11
        versionName = "1.1.0"
    }

    buildTypes {
        getByName("release") {
            // Sideload test build only. A production Play Store release must use a private,
            // persistent signing key that is never committed to this repository.
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}
