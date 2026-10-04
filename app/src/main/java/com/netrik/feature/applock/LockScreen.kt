package com.netrik.feature.applock

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.netrik.R
import com.netrik.core.security.PinCheck
import kotlinx.coroutines.delay

/** Full-screen lock: PIN keypad, optional fingerprint, wait countdown after too many mistakes. */
@Composable
fun LockScreen(viewModel: AppLockViewModel = hiltViewModel()) {
    val lock by viewModel.lock.collectAsStateWithLifecycle()
    val pin by viewModel.pin.collectAsStateWithLifecycle()
    val result by viewModel.result.collectAsStateWithLifecycle()
    val errorKey by viewModel.errorKey.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showForgot by rememberSaveable { mutableStateOf(false) }


    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val waiting = lock.lockedUntilMillis > now
    LaunchedEffect(lock.lockedUntilMillis) {
        while (true) {
            now = System.currentTimeMillis()
            if (now >= lock.lockedUntilMillis) break
            delay(250)
        }
    }

    val biometricAvailable = lock.biometric && Biometrics.available(context)
    val biometricTitle = stringResource(R.string.lock_biometric_title)
    val biometricNegative = stringResource(R.string.lock_biometric_negative)
    val askBiometric = { Biometrics.prompt(context, biometricTitle, biometricNegative, viewModel::onBiometricSuccess) }
    // Opens the fingerprint prompt by itself every time the lock screen shows up.
    LaunchedEffect(biometricAvailable, waiting) { if (biometricAvailable && !waiting) askBiometric() }

    val message = when {
        waiting -> stringResource(R.string.lock_wait, formatWait(lock.lockedUntilMillis - now))
        result is PinCheck.Wrong -> {
            val left = (result as PinCheck.Wrong).attemptsBeforeWait
            if (left in 1..2) pluralStringResource(R.plurals.lock_wrong_left, left, left) else stringResource(R.string.lock_wrong)
        }
        else -> null
    }

    // A dialog window of its own, created when the app locks: it stays above any dialog the app was
    // showing (SSH fingerprint, PIN flow...). Back leaves the app instead of dismissing the lock.
    Dialog(
        onDismissRequest = { (context as? Activity)?.moveTaskToBack(true) },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnClickOutside = false),
    ) {
    Surface(color = MaterialTheme.colorScheme.surface) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            // Swallows touches so nothing behind the lock can be reached.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .safeDrawingPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_lock),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.height(16.dp))
            PinPad(
                title = stringResource(R.string.lock_title),
                pin = pin,
                onPinChange = viewModel::onPinChange,
                onComplete = viewModel::submit,
                message = message,
                isError = message != null,
                errorKey = errorKey,
                enabled = !waiting,
                onBiometric = if (biometricAvailable) askBiometric else null,
                footer = { ForgotPinButton { showForgot = true } },
            )
        }
    }
    }
    }

    if (showForgot) {
        AlertDialog(
            onDismissRequest = { showForgot = false },
            icon = { Icon(painterResource(R.drawable.ic_help), contentDescription = null) },
            title = { Text(stringResource(R.string.lock_forgot_title)) },
            text = { Text(stringResource(R.string.lock_forgot_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showForgot = false
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                    )
                }) { Text(stringResource(R.string.lock_open_app_settings)) }
            },
            dismissButton = { TextButton(onClick = { showForgot = false }) { Text(stringResource(R.string.action_close)) } },
        )
    }
}
