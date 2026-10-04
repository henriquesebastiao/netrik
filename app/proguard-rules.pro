# Netrik-specific rules. The libraries used (Compose, Hilt, Navigation,
# kotlinx-serialization) already ship their own consumer rules.

# JSch instantiates algorithms by name (Class.forName) from its configuration.
-keep class com.jcraft.jsch.** { *; }
# Optional JSch integrations the app doesn't use.
-dontwarn org.apache.logging.log4j.**
-dontwarn org.slf4j.**
-dontwarn com.sun.jna.**
-dontwarn org.newsclub.net.unix.**
-dontwarn org.ietf.jgss.**
# BouncyCastle is referenced directly by com.jcraft.jsch.bc: R8 keeps only what is used.
-dontwarn org.bouncycastle.**

# Type-safe Navigation: enum arguments of the routes are found by class name (Class.forName).
-keepnames enum com.netrik.navigation.** { *; }
