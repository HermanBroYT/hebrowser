plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "ru.herlinux"
    compileSdk = 34

    defaultConfig {
        applicationId = "ru.herlinux.os"
        minSdk = 26
        targetSdk = 34
        versionCode = 21
        versionName = "2.1"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core:1.13.1")
}
