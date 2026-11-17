// Termux terminal emulator (xterm escape sequences, screen buffer, key encoding) and its Canvas renderer,
// vendored from termux-app v0.118.3 (Apache 2.0) so the whole app builds from source without JitPack.
// Pure Java: the JNI part (TerminalSession, local shell) isn't included. See README.md.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.termux.terminal"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        // The emulator calls android.util.Base64/Log; the upstream tests rely on the default stubs.
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    testImplementation(libs.junit)
}
