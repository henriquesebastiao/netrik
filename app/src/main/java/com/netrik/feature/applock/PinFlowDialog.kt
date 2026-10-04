package com.netrik.feature.applock

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.netrik.R
import com.netrik.core.security.PinCheck
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** What the PIN flow is for. */
enum class PinFlow { Enable, Change, Disable }

/** Operations the flow needs; implemented by the Settings ViewModel on top of AppLockManager. */
interface PinFlowActions {
    suspend fun confirm(pin: String): PinCheck
    suspend fun enable(pin: String)
    suspend fun changePin(pin: String)
    suspend fun disable(pin: String): PinCheck
}

private enum class Step { Current, Create, Repeat }

/**
 * Full-screen PIN steps: create + repeat (turn on), current + new + repeat (change), current
 * (turn off). Wrong current PINs count towards the same attempt limit as the lock screen.
 */
@Composable
fun PinFlowDialog(flow: PinFlow, actions: PinFlowActions, onDone: () -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(if (flow == PinFlow.Enable) Step.Create else Step.Current) }
    var pin by remember { mutableStateOf("") }
    var firstPin by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var errorKey by remember { mutableIntStateOf(0) }
    var waitUntil by remember { mutableLongStateOf(0L) }
    var busy by remember { mutableStateOf(false) }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(waitUntil) {
        while (true) {
            now = System.currentTimeMillis()
            if (now >= waitUntil) break
            delay(250)
        }
    }
    val waiting = waitUntil > now

    val wrongText = stringResource(R.string.lock_wrong)
    val mismatchText = stringResource(R.string.pin_mismatch)
    val wrongLeft1 = pluralStringResource(R.plurals.lock_wrong_left, 1, 1)
    val wrongLeft2 = pluralStringResource(R.plurals.lock_wrong_left, 2, 2)

    fun fail(text: String) {
        message = text
        errorKey++
    }

    fun handleCheck(check: PinCheck, onCorrect: suspend () -> Unit) {
        scope.launch {
            when (check) {
                PinCheck.Correct -> {
                    message = null
                    onCorrect()
                }
                is PinCheck.Wrong -> fail(
                    when (check.attemptsBeforeWait) {
                        1 -> wrongLeft1
                        2 -> wrongLeft2
                        else -> wrongText
                    },
                )
                is PinCheck.Wait -> {
                    waitUntil = check.untilMillis
                    message = null
                    errorKey++
                }
            }
        }
    }

    fun submit(entered: String) {
        busy = true
        scope.launch {
            pin = ""
            when (step) {
                Step.Current -> {
                    val check = if (flow == PinFlow.Disable) actions.disable(entered) else actions.confirm(entered)
                    handleCheck(check) {
                        if (flow == PinFlow.Disable) onDone() else step = Step.Create
                    }
                }
                Step.Create -> {
                    firstPin = entered
                    message = null
                    step = Step.Repeat
                }
                Step.Repeat -> {
                    if (entered == firstPin) {
                        if (flow == PinFlow.Enable) actions.enable(entered) else actions.changePin(entered)
                        onDone()
                    } else {
                        firstPin = ""
                        step = Step.Create
                        fail(mismatchText)
                    }
                }
            }
            busy = false
        }
    }

    val title = stringResource(
        when (step) {
            Step.Current -> R.string.pin_current
            Step.Create -> if (flow == PinFlow.Enable) R.string.pin_create else R.string.pin_new
            Step.Repeat -> R.string.pin_confirm
        },
    )

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Box(modifier = Modifier.safeDrawingPadding()) {
                IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopStart).padding(4.dp)) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.action_cancel))
                }
                Box(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), contentAlignment = Alignment.Center) {
                    PinPad(
                        title = title,
                        pin = pin,
                        onPinChange = { pin = it },
                        onComplete = ::submit,
                        message = if (waiting) stringResource(R.string.lock_wait, formatWait(waitUntil - now)) else message,
                        isError = waiting || message != null,
                        errorKey = errorKey,
                        enabled = !waiting && !busy,
                    )
                }
            }
        }
    }
}
