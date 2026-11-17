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

android {
    // The app language can be changed in Settings: every language must ship in the APK/bundle.
    bundle {
        language { enableSplit = false }
    }
    packaging {
        jniLibs {
            // Reproducible builds: AGP strips the prebuilt AndroidX .so files only when an NDK is installed
            // (the GitHub runner has one, F-Droid's build server doesn't), which made the APKs differ. Never
            // strip, so every environment packages the same bytes.
            keepDebugSymbols += "**/*.so"
        }
    }
    namespace = "com.netrik"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.henriquesebastiao.netrik"
        minSdk = 26
        targetSdk = 37
        // Bumped by hand before each release, as literals: F-Droid reads them from this file to detect
        // updates. versionCode = major * 1_000_000 + minor * 1_000 + patch (checked below).
        versionCode = 2001
        versionName = "0.2.1"
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

/**
 * The versionCode must follow the versionName, and a release build given the tag
 * (`-Pnetrik.version=v1.2.3`, CI) must match it: a forgotten bump fails the build instead of publishing
 * an APK that can't update the previous one.
 */
run {
    val name = checkNotNull(android.defaultConfig.versionName)
    val code = checkNotNull(android.defaultConfig.versionCode)
    val match = Regex("""^(\d+)\.(\d{1,3})\.(\d{1,3})$""").matchEntire(name)
        ?: throw GradleException("versionName must look like 1.2.3, got \"$name\".")
    val (major, minor, patch) = match.destructured.toList().map(String::toInt)
    val expected = major * 1_000_000 + minor * 1_000 + patch
    if (code != expected) {
        throw GradleException("versionCode $code doesn't match versionName $name (expected $expected).")
    }
    providers.gradleProperty("netrik.version").orNull?.let { tag ->
        if (tag.trim().removePrefix("v") != name) {
            throw GradleException(
                "Release tag \"$tag\" doesn't match versionName $name: bump versionName and versionCode " +
                    "in app/build.gradle.kts first.",
            )
        }
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
    // Terminal: Termux xterm emulation and renderer (Apache 2.0, built from source in :termux-terminal);
    // the View and the SSH session are ours
    implementation(project(":termux-terminal"))
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
