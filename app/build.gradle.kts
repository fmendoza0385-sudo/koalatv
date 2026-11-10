plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.grm.tvcanales"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.grm.tvcanales"
        minSdk = 21
        targetSdk = 34
        versionCode = 2
        versionName = "2.0"

        // Reduce el tamaño de la APK: solo ARM (celulares y TV box)
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
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

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    // Reproductor basado en VLC: decodifica MPEG-2, H.264, HEVC, MP2, AC3, etc. por software
    implementation("org.videolan.android:libvlc-all:3.6.0")
}
