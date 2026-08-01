plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.senin.vaultsync"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.senin.vaultsync"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1"
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

dependencies {
    implementation("androidx.activity:activity-ktx:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.appcompat:appcompat:1.7.0")

    // FTP (router'da Samba değil FTP servisi var)
    implementation("commons-net:commons-net:3.11.1")

    // Yerel senkron durumu takibi
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Arka plan senkron tetikleme
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Ayarları saklamak için (SMB adres/kullanıcı/şifre)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
