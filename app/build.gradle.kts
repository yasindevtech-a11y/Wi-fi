plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.senin.vaultsync"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.aybars.privatedrive2026"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.2"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
        }
    }

    buildFeatures {
        compose = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

// Kasitli olarak SADECE su bagimliliklar var:
// - appcompat/core-ktx: temel Android uyumlulugu
// - commons-net: FTP istemcisi
// Room, WorkManager, DataStore, Compose YOK - daha az bagimlilik,
// daha az uyumsuzluk riski, daha kucuk/daha az "supheli" APK.
dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("commons-net:commons-net:3.11.1")
}
