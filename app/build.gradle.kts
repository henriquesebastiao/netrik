import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.androidx.room)
}

/**
 * Release signing: from the environment (CI) or from a local `keystore.properties` (git-ignored) with
 * storeFile, storePassword, keyAlias and keyPassword. Without them the release APK is left unsigned,
 * unless `-Pnetrik.requireSigning=true` (CI), which fails the build.
 */
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.isFile) file.inputStream().use(::load)
}

fun signingValue(env: String, key: String): String? =
    providers.environmentVariable(env).orNull?.takeIf { it.isNotBlank() } ?: keystoreProperties.getProperty(key)

val releaseStoreFile = signingValue("NETRIK_KEYSTORE_FILE", "storeFile")
val releaseSigningConfigured = releaseStoreFile != null &&
    listOf(
        signingValue("NETRIK_KEYSTORE_PASSWORD", "storePassword"),
        signingValue("NETRIK_KEY_ALIAS", "keyAlias"),
        signingValue("NETRIK_KEY_PASSWORD", "keyPassword"),
    ).all { it != null }

if (providers.gradleProperty("netrik.requireSigning").orNull == "true" && !releaseSigningConfigured) {
    throw GradleException(
        "Release signing required but not configured: set NETRIK_KEYSTORE_FILE, NETRIK_KEYSTORE_PASSWORD, " +
            "NETRIK_KEY_ALIAS and NETRIK_KEY_PASSWORD.",
    )
}

/**
 * Version from the release tag: `-Pnetrik.version=1.2.3` (or `v1.2.3`) gives versionName 1.2.3 and
 * versionCode 1002003 (major * 1_000_000 + minor * 1_000 + patch). Without it, the defaults below.
 */
val releaseVersion: Pair<String, Int>? = providers.gradleProperty("netrik.version").orNull?.let { raw ->
    val match = Regex("""^v?(\d+)\.(\d{1,3})\.(\d{1,3})$""").matchEntire(raw.trim())
        ?: throw GradleException("netrik.version must look like 1.2.3 or v1.2.3, got \"$raw\".")
    val (major, minor, patch) = match.destructured
    val code = major.toInt() * 1_000_000 + minor.toInt() * 1_000 + patch.toInt()
    if (code <= 0 || major.toInt() > 2_000) throw GradleException("netrik.version out of range: \"$raw\".")
    "$major.$minor.$patch" to code
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
        applicationId = "com.henriquesebastiao.netrik"
        minSdk = 26
        targetSdk = 37
        versionCode = releaseVersion?.second ?: 1
        versionName = releaseVersion?.first ?: "0.1.2"
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = signingValue("NETRIK_KEYSTORE_PASSWORD", "storePassword")
                keyAlias = signingValue("NETRIK_KEY_ALIAS", "keyAlias")
                keyPassword = signingValue("NETRIK_KEY_PASSWORD", "keyPassword")
                // minSdk 26: APK Signature Scheme v2 and v3 (v3 allows rotating the key later); v1 isn't needed.
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            // Debug and release side by side on the same device; the debug build is "Netrik Debug".
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release")
        }
    }

    // The dependency list AGP embeds in the APK signing block is encrypted with a Google key: stores outside
    // Google Play (F-Droid, IzzyOnDroid) can't read it and flag it as an unknown blob. The app isn't on Play.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
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
    // Port knocking export/import files (JSON)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
