package com.netrik.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Semantic status colors that Material 3 doesn't define (success and warning).
 * Error uses the standard `colorScheme.error`. Status is always color + icon + label.
 */
@Immutable
data class ExtendedColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
)

internal val LightExtendedColors = ExtendedColors(
    success = successLight,
    onSuccess = onSuccessLight,
    successContainer = successContainerLight,
    onSuccessContainer = onSuccessContainerLight,
    warning = warningLight,
    onWarning = onWarningLight,
    warningContainer = warningContainerLight,
    onWarningContainer = onWarningContainerLight,
)

internal val DarkExtendedColors = ExtendedColors(
    success = successDark,
    onSuccess = onSuccessDark,
    successContainer = successContainerDark,
    onSuccessContainer = onSuccessContainerDark,
    warning = warningDark,
    onWarning = onWarningDark,
    warningContainer = warningContainerDark,
    onWarningContainer = onWarningContainerDark,
)

internal val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }
