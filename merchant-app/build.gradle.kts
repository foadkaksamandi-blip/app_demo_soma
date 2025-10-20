plugins {
    id("com.android.application")
    kotlin("android") version "1.9.24"
}

android {
    namespace = "com.soma.merchant"
    compileSdk = property("compile.sdk").toString().toInt()

    defaultConfig {
        applicationId = "com.soma.merchant"
        minSdk = property("min.sdk").toString().toInt()
        targetSdk = property("target.sdk").toString().toInt()
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    // ✅ سازگاری کامل با Kotlin 1.9.24 و Compose 1.5.14
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    // ✅ فعال‌سازی Compose + ViewBinding
    buildFeatures {
        compose = true
        viewBinding = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = property("compose.compiler").toString()
    }

    packaging { resources.excludes += setOf("META-INF/**") }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:${property("compose.bom")}")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Core
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")

    // Compose UI
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Material3
    implementation("androidx.compose.material3:material3:1.2.1")

    // Material XML Theme
    implementation("com.google.android.material:material:1.12.0")

    // ZXing (QR)
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("com.google.zxing:core:3.5.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Crypto
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
}

kotlin { jvmToolchain(17) }
