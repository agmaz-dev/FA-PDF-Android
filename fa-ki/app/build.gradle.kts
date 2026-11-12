plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "de.faki"
    compileSdk = 35

    defaultConfig {
        applicationId = "de.faki"
        minSdk = 29
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.1"
        ndk { abiFilters += "arm64-v8a" }
        externalNativeBuild {
            cmake {
                // Auch im Test-Build optimiert übersetzen – sonst rechnet die KI um ein Vielfaches langsamer
                arguments += listOf(
                    "-DLLAMA_DIR=${rootProject.file("llama.cpp").absolutePath}",
                    "-DCMAKE_BUILD_TYPE=Release",
                )
                cFlags += "-O3"
                cppFlags += "-O3"
            }
        }
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("test-signing.keystore")
            storePassword = "android"
            keyAlias = "fapdftest"
            keyPassword = "android"
        }
    }

    externalNativeBuild {
        cmake { path = file("src/main/cpp/CMakeLists.txt") }
    }
    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    // Kleiner HTTP-Server für die lokale Adresse http://127.0.0.1:8080/v1
    implementation("org.nanohttpd:nanohttpd:2.3.1")
}
