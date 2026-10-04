plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.androidx.room)
}

android {
    packaging {
        jniLibs {
            // JNI do Termux (TerminalSession abre um shell local): o app só usa o emulador e o
            // renderizador, em Java puro. A lib também não é alinhada a 16 KB (Android 15+).
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
    // Esquemas versionados para migrações futuras
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

    // SSH: JSch (fork mantido, BSD) + BouncyCastle para Ed25519/X25519 sem trocar o provedor do Android
    implementation(libs.jsch)
    implementation(libs.bouncycastle.prov)
    // Terminal: emulação xterm e renderizador do Termux (Apache 2.0); a View e a sessão SSH são nossas
    implementation(libs.termux.terminal.view)
    // Preferências (tamanho da fonte do terminal)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
