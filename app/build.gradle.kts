plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.androidx.room)
}

android {
    // The app language can be changed in Settings: every language must ship in the APK/bundle.
    bundle {
        language { enableSplit = false }
    }
    packaging {
        jniLibs {
            // Termux JNI (TerminalSession opens a local shell): the app only uses the emulator and the
            // renderer, in pure Java. The lib also isn't 16 KB aligned (Android 15+).
            excludes += "**/libtermux.so"
        }
    }
    namespace = "com.netrik"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.netrik"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

room {
    // Versioned schemas for future migrations
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // SSH: JSch (maintained fork, BSD) + BouncyCastle for Ed25519/X25519 without replacing Android's provider
    implementation(libs.jsch)
    implementation(libs.bouncycastle.prov)
    // Terminal: Termux xterm emulation and renderer (Apache 2.0); the View and the SSH session are ours
    implementation(libs.termux.terminal.view)
    // Preferences (settings and terminal font size)
    implementation(libs.androidx.datastore.preferences)
    // App lock: fingerprint/face unlock through the system BiometricPrompt
    implementation(libs.androidx.biometric)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
