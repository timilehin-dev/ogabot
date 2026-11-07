import java.lang.System

plugins {
    id("com.android.application")
    kotlin("android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val ciKeystorePath: String? = System.getenv("OGABOT_KEYSTORE_PATH")
val ciKeystorePass: String? = System.getenv("OGABOT_KEYSTORE_PASSWORD")
val ciKeyAlias: String = System.getenv("OGABOT_KEY_ALIAS") ?: "ogabot"

android {
    namespace = "io.github.timilehin_dev.ogabot"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.timilehin_dev.ogabot"
        minSdk = 26
        targetSdk = 34
        versionCode = 4
        versionName = "0.2.0"
    }

    signingConfigs {
        if (ciKeystorePath != null && ciKeystorePass != null) {
            create("ci") {
                storeFile = file(ciKeystorePath)
                storePassword = ciKeystorePass
                keyAlias = ciKeyAlias
                keyPassword = ciKeystorePass
            }
        }
    }

    buildTypes {
        getByName("debug") {
            if (ciKeystorePath != null && ciKeystorePass != null) {
                signingConfig = signingConfigs.getByName("ci")
            }
        }
        getByName("release") {
            if (ciKeystorePath != null && ciKeystorePass != null) {
                signingConfig = signingConfigs.getByName("ci")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    implementation(project(":core"))
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
