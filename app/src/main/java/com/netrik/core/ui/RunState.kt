package com.netrik.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.netrik.R

// Estado comum às ferramentas que executam algo sobre um alvo (Ping, Traceroute, Port Scanner).

enum class RunPhase { Idle, Resolving, Running, Done, Stopped, Failed }

sealed interface TargetError {
    data object Required : TargetError
    data class Invalid(val target: String) : TargetError
    data class Unresolved(val target: String) : TargetError
}

sealed interface RunFailure {
    data object HostNotFound : RunFailure
    /** A rede caiu durante a execução; [step] é o salto/etapa em que parou, quando houver. */
    data class ConnectionLost(val step: Int? = null) : RunFailure
    data class ToolFailed(val message: String?) : RunFailure
}

@Composable
fun targetErrorText(error: TargetError?): String? = when (error) {
    null -> null
    TargetError.Required -> stringResource(R.string.target_required)
    is TargetError.Invalid -> stringResource(R.string.target_invalid, error.target)
    is TargetError.Unresolved -> stringResource(R.string.target_unresolved, error.target)
}

@Composable
fun failureText(failure: RunFailure): Pair<String, String> = when (failure) {
    RunFailure.HostNotFound -> stringResource(R.string.error_host_not_found) to stringResource(R.string.error_host_not_found_body)
    is RunFailure.ConnectionLost -> (failure.step?.let { stringResource(R.string.trace_error_lost, it) } ?: stringResource(R.string.error_connection_lost)) to
        stringResource(R.string.error_connection_lost_body)
    is RunFailure.ToolFailed -> stringResource(R.string.error_ping_failed) to (failure.message ?: "")
}

/** Menu ⋮ com "Limpar histórico de alvos". */
@Composable
fun ClearHistoryMenu(onClear: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.action_more))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.history_clear_targets)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_history), contentDescription = null) },
                onClick = {
                    expanded = false
                    onClear()
                },
            )
        }
    }
}
