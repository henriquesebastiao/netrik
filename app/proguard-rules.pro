# Regras específicas do Netrik. As bibliotecas usadas (Compose, Hilt, Navigation,
# kotlinx-serialization) já trazem suas próprias regras de consumidor.

# JSch instancia algoritmos por nome (Class.forName) a partir da configuração.
-keep class com.jcraft.jsch.** { *; }
# Integrações opcionais do JSch que o app não usa.
-dontwarn org.apache.logging.log4j.**
-dontwarn org.slf4j.**
-dontwarn com.sun.jna.**
-dontwarn org.newsclub.net.unix.**
-dontwarn org.ietf.jgss.**
# BouncyCastle é referenciado diretamente por com.jcraft.jsch.bc: o R8 mantém só o que é usado.
-dontwarn org.bouncycastle.**

# Navigation type-safe: argumentos enum das rotas são achados pelo nome da classe (Class.forName).
-keepnames enum com.netrik.navigation.** { *; }
