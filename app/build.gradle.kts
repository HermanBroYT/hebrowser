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
        versionCode = 20
        versionName = "2.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
