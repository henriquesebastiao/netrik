package com.netrik.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.netrik.R

// Fontes variáveis embarcadas (subconjunto latino, só o eixo wght), para funcionar offline
// e sem depender do Google Play Services.
private fun robotoFlex(weight: FontWeight) = Font(
    resId = R.font.roboto_flex,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

private fun jetBrainsMono(weight: FontWeight) = Font(
    resId = R.font.jetbrains_mono,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val RobotoFlex = FontFamily(
    robotoFlex(FontWeight.Light),
    robotoFlex(FontWeight.Normal),
    robotoFlex(FontWeight.Medium),
    robotoFlex(FontWeight.SemiBold),
    robotoFlex(FontWeight.Bold),
)

val JetBrainsMono = FontFamily(
    jetBrainsMono(FontWeight.Normal),
    jetBrainsMono(FontWeight.Medium),
)

/** Escala M3 com Roboto Flex; os tamanhos usados pelo design são os padrões do M3. */
internal val NetrikTypography: Typography = Typography().run {
    fun TextStyle.flex() = copy(fontFamily = RobotoFlex)
    Typography(
        displayLarge = displayLarge.flex(),
        displayMedium = displayMedium.flex(),
        displaySmall = displaySmall.flex(),
        headlineLarge = headlineLarge.flex(),
        headlineMedium = headlineMedium.flex(),
        headlineSmall = headlineSmall.flex(),
        titleLarge = titleLarge.flex(),
        titleMedium = titleMedium.flex(),
        titleSmall = titleSmall.flex(),
        bodyLarge = bodyLarge.flex(),
        bodyMedium = bodyMedium.flex(),
        bodySmall = bodySmall.flex(),
        labelLarge = labelLarge.flex(),
        labelMedium = labelMedium.flex(),
        labelSmall = labelSmall.flex(),
    )
}

/**
 * Estilos para dados técnicos (IPs, MACs, portas, dBm, RTT) em JetBrains Mono, com algarismos
 * tabulares para colunas alinharem.
 */
@Immutable
data class DataTypography(
    val dataLarge: TextStyle,
    val dataMedium: TextStyle,
    val dataSmall: TextStyle,
    val terminal: TextStyle,
)

private val MonoBase = TextStyle(fontFamily = JetBrainsMono, fontFeatureSettings = "tnum")

internal val NetrikDataTypography = DataTypography(
    dataLarge = MonoBase.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium),
    dataMedium = MonoBase.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    dataSmall = MonoBase.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal),
    terminal = MonoBase.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal),
)

internal val LocalDataTypography = staticCompositionLocalOf { NetrikDataTypography }
