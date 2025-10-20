plugins {
    id("com.android.application")
    kotlin("android") version "1.9.24"
}

android {
    namespace = "com.soma.consumer"
    compileSdk = property("compile.sdk").toString().toInt()

    defaultConfig {
        applicationId = "com.soma.consumer"
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
        debug { isMinifyEnabled = false }
    }

    // ⛔️ مهم: قفل Java/Kotlin روی 17
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    // اختیاری اما مفید
    packaging { resources.excludes += setOf("META-INF/*") }

    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:${property("compose.bom")}")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3:1.2.1")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Material3 XML Theme
    implementation("com.google.android.material:material:1.12.0")

    // ZXing
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("com.google.zxing:core:3.5.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Crypto
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
}
