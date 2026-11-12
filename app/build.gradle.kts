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
        versionCode = 6
        versionName = "1.0.5"
    }

    buildTypes {
        getByName("release") {
            // CI debug signing is for sideload testing only; production release needs a private persistent keystore.
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
