package com.netrik.feature.applock

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.netrik.R
import com.netrik.core.security.AppLockPolicy

/**
 * PIN entry: title, 4 dots, an optional message and a numeric keypad. [onComplete] is called as
 * soon as the 4th digit is typed. [errorKey] changes on every error, to shake the dots.
 */
@Composable
fun PinPad(
    title: String,
    pin: String,
    onPinChange: (String) -> Unit,
    onComplete: (String) -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    isError: Boolean = false,
    errorKey: Int = 0,
    enabled: Boolean = true,
    onBiometric: (() -> Unit)? = null,
    footer: @Composable () -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val shake = remember { Animatable(0f) }
    LaunchedEffect(errorKey) {
        if (errorKey == 0) return@LaunchedEffect
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        for (x in listOf(18f, -16f, 12f, -8f, 4f, 0f)) shake.animateTo(x, spring(stiffness = 4000f))
    }

    fun type(digit: Char) {
        if (!enabled || pin.length >= AppLockPolicy.PIN_LENGTH) return
        val next = pin + digit
        onPinChange(next)
        if (next.length == AppLockPolicy.PIN_LENGTH) onComplete(next)
    }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier
                .offset { IntOffset(shake.value.dp.roundToPx(), 0) }
                .semantics { contentDescription = "${pin.length}/${AppLockPolicy.PIN_LENGTH}" },
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            repeat(AppLockPolicy.PIN_LENGTH) { i ->
                val filled = i < pin.length
                val tint = if (isError) colors.error else colors.primary
                Box(
                    Modifier
                        .size(16.dp)
                        .then(if (filled) Modifier.background(tint, CircleShape) else Modifier.border(2.dp, if (isError) colors.error else colors.outline, CircleShape)),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            message.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) colors.error else colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            minLines = 2,
        )
        Spacer(Modifier.height(16.dp))
        val rows = listOf("123", "456", "789")
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    row.forEach { d -> DigitKey(d, enabled) { type(d) } }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(KEY_SIZE), contentAlignment = Alignment.Center) {
                    if (onBiometric != null) {
                        IconButton(onClick = onBiometric, enabled = enabled, modifier = Modifier.size(KEY_SIZE)) {
                            Icon(
                                painterResource(R.drawable.ic_fingerprint),
                                contentDescription = stringResource(R.string.lock_use_fingerprint),
                                modifier = Modifier.size(32.dp),
                            )
                        }
                    }
                }
                DigitKey('0', enabled) { type('0') }
                Box(Modifier.size(KEY_SIZE), contentAlignment = Alignment.Center) {
                    IconButton(onClick = { if (pin.isNotEmpty()) onPinChange(pin.dropLast(1)) }, enabled = enabled && pin.isNotEmpty(), modifier = Modifier.size(KEY_SIZE)) {
                        Icon(painterResource(R.drawable.ic_backspace), contentDescription = stringResource(R.string.lock_delete))
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        footer()
    }
}

@Composable
private fun DigitKey(digit: Char, enabled: Boolean, onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(KEY_SIZE)) {
        Text(digit.toString(), fontSize = 28.sp, style = MaterialTheme.typography.headlineMedium)
    }
}

private val KEY_SIZE = 72.dp

/** "0:30", "14:59". */
internal fun formatWait(millis: Long): String {
    val seconds = ((millis + 999) / 1000).coerceAtLeast(0)
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

@Composable
internal fun ForgotPinButton(onClick: () -> Unit) {
    TextButton(onClick = onClick) { Text(stringResource(R.string.lock_forgot)) }
}
