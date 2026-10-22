plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "de.fapdf"
    compileSdk = 35

    defaultConfig {
        applicationId = "de.fapdf"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "1.3.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Fester Test-Schlüssel: Nur so lassen sich neue Test-APKs als Update über die alte installieren.
    // Nur für Testversionen! Für eine Veröffentlichung einen eigenen, geheimen Schlüssel verwenden.
    signingConfigs {
        getByName("debug") {
            storeFile = file("test-signing.keystore")
            storePassword = "android"
            keyAlias = "fapdftest"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            // Minify ist bewusst aus: PDFBox nutzt Reflection; erst nach Tests mit
            // passenden ProGuard-Regeln aktivieren.
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*")
        }
    }
    testOptions {
        // android.util.Log usw. liefern in JVM-Tests Standardwerte statt Ausnahmen
        unitTests.isReturnDefaultValues = true
    }
    sourceSets["test"].resources.srcDir(layout.buildDirectory.dir("pdfboxTestResources"))
    androidResources {
        // Schriften nicht komprimieren, damit sie direkt gelesen werden können
        noCompress += "ttf"
    }
}

// PDFBox-Android liest CMaps, Glyphenlisten und AFM-Dateien aus seinen Assets. Für JVM-Tests
// werden diese aus dem AAR entpackt und als Test-Ressourcen (Klassenpfad) bereitgestellt.
val pdfboxAssets: Configuration by configurations.creating
val extractPdfboxAssets by tasks.registering(Sync::class) {
    from({ zipTree(pdfboxAssets.singleFile) }) {
        include("assets/**")
        eachFile { path = path.removePrefix("assets/") }
        includeEmptyDirs = false
    }
    into(layout.buildDirectory.dir("pdfboxTestResources"))
}
tasks.matching { it.name.contains("UnitTest") && (it.name.startsWith("process") || it.name.startsWith("compile")) }
    .configureEach { dependsOn(extractPdfboxAssets) }

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // Persistenz
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Sicherheit
    implementation("androidx.biometric:biometric:1.1.0")

    // PDF-Engine (Apache License 2.0) – Bearbeiten, Formulare, Verschlüsselung, Seitenoperationen
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    // Google ML Kit – kostenlos, läuft auf dem Gerät
    implementation("com.google.mlkit:text-recognition:16.0.1")          // OCR (lateinische Schrift: DE/EN/TR)
    implementation("com.google.mlkit:translate:17.0.3")                 // Offline-Übersetzung
    implementation("com.google.mlkit:language-id:17.0.6")               // Spracherkennung (auf dem Gerät)
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0-beta1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    testImplementation("junit:junit:4.13.2")
    pdfboxAssets("com.tom-roush:pdfbox-android:2.0.27.0@aar")
}
