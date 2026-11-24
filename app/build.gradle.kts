plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "de.paul.sleeptimer"
    compileSdk = 35
    buildToolsVersion = "35.0.0"   // dieselbe Version, die setup.ps1 installiert

    defaultConfig {
        applicationId = "de.paul.sleeptimer"
        minSdk = 33          // Android 13+, das S22 läuft auf 14/15/16
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

// Keine Bibliotheken nötig – alles kommt direkt aus dem Android-SDK.
