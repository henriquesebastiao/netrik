package com.netrik.feature.knock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.netrik.R
import com.netrik.core.designsystem.component.ErrorCard
import com.netrik.core.designsystem.component.StatusChip
import com.netrik.core.designsystem.component.StatusTone
import com.netrik.core.designsystem.theme.NetrikTheme
import com.netrik.core.knock.KnockError
import com.netrik.core.knock.KnockProtocol
import com.netrik.core.knock.KnockStep
import com.netrik.core.knock.PortCheck
import com.netrik.core.ui.rememberLocalNetworkPermissionRequest

/** Progress of a knock: each step, the optional port test and the errors. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnockRunSheet(run: KnockRunState, viewModel: KnockViewModel) {
    val requestLocalNetwork = rememberLocalNetworkPermissionRequest(onGranted = viewModel::retryKnock)
    ModalBottomSheet(onDismissRequest = viewModel::closeRun) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(run.profile.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    run.address?.takeIf { it != run.profile.host }?.let { "${run.profile.host} → $it" } ?: run.profile.host,
                    style = NetrikTheme.dataTypography.dataSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PhaseChip(run)
            Steps(run)
            run.profile.verifyPort?.let { port -> VerifyRow(port, run) }
            when (val error = run.error) {
                null -> if (run.phase == KnockPhase.Done && run.profile.verifyPort == null) {
                    Text(
                        stringResource(R.string.knock_done_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                KnockRunError.HostNotFound -> ErrorCard(
                    stringResource(R.string.error_host_not_found),
                    stringResource(R.string.error_host_not_found_body),
                    onRetry = null,
                )
                KnockRunError.LocalNetworkPermission -> ErrorCard(
                    stringResource(R.string.error_local_network_title),
                    stringResource(R.string.error_local_network_body),
                    onRetry = requestLocalNetwork,
                    retryLabel = stringResource(R.string.action_allow),
                    retryIcon = R.drawable.ic_lan,
                )
                is KnockRunError.Step -> ErrorCard(
                    stringResource(R.string.knock_step_failed, error.index + 1),
                    when (val cause = error.error) {
                        KnockError.Blocked -> stringResource(R.string.knock_error_blocked)
                        KnockError.Unreachable -> stringResource(R.string.knock_error_unreachable)
                        is KnockError.Other -> cause.detail?.takeIf { it.isNotBlank() } ?: stringResource(R.string.knock_error_other)
                    },
                    onRetry = null,
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                if (run.running) {
                    OutlinedButton(onClick = viewModel::stopKnock) {
                        Icon(painterResource(R.drawable.ic_stop_filled), contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.action_stop), modifier = Modifier.padding(start = 8.dp))
                    }
                } else {
                    TextButton(onClick = viewModel::closeRun) { Text(stringResource(R.string.action_close)) }
                    Button(onClick = viewModel::retryKnock) {
                        Icon(painterResource(R.drawable.ic_refresh), contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.knock_again), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PhaseChip(run: KnockRunState) {
    val (label, tone) = when (run.phase) {
        KnockPhase.Resolving -> stringResource(R.string.knock_phase_resolving) to StatusTone.Running
        KnockPhase.Running -> stringResource(R.string.knock_phase_running) to StatusTone.Running
        KnockPhase.Done -> stringResource(R.string.knock_phase_done) to StatusTone.Success
        KnockPhase.Stopped -> stringResource(R.string.knock_phase_stopped) to StatusTone.Neutral
        KnockPhase.Failed -> stringResource(R.string.knock_phase_failed) to StatusTone.Error
    }
    StatusChip(label, tone)
}

@Composable
private fun Steps(run: KnockRunState) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(16.dp), color = colors.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            run.profile.steps.forEachIndexed { index, step ->
                val status = run.steps.getOrElse(index) { StepStatus.Pending }
                val current = run.currentStep == index
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "${index + 1}",
                        style = NetrikTheme.dataTypography.dataSmall,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.width(24.dp),
                    )
                    Text(
                        stepLabel(step),
                        style = NetrikTheme.dataTypography.dataMedium.copy(fontSize = 15.sp),
                        fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (status == StepStatus.Pending && !current) colors.onSurfaceVariant else colors.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                        when {
                            current -> CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            status == StepStatus.Sent -> StatusIcon(R.drawable.ic_check_circle_filled, NetrikTheme.extendedColors.success, R.string.knock_step_sent)
                            status == StepStatus.Failed -> StatusIcon(R.drawable.ic_error_filled, colors.error, R.string.knock_step_failed_short)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusIcon(icon: Int, tint: Color, description: Int) {
    Icon(painterResource(icon), contentDescription = stringResource(description), tint = tint, modifier = Modifier.size(20.dp))
}

/** "TCP 7000", "UDP 8000", "ICMP" or "ICMP 64 B". */
@Composable
private fun stepLabel(step: KnockStep): String = when (step.protocol) {
    KnockProtocol.Tcp, KnockProtocol.Udp -> "${step.protocol.label} ${step.port}"
    KnockProtocol.Icmp -> step.payloadSize?.let { stringResource(R.string.knock_icmp_size, it) } ?: step.protocol.label
}

@Composable
private fun VerifyRow(port: Int, run: KnockRunState) {
    val colors = MaterialTheme.colorScheme
    val ext = NetrikTheme.extendedColors
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.knock_verify_label, port), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        when {
            run.verifying -> CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            run.verifyResult == PortCheck.Open -> StatusChip(stringResource(R.string.knock_verify_open), StatusTone.Success)
            run.verifyResult == PortCheck.Closed -> StatusChip(stringResource(R.string.knock_verify_closed), StatusTone.Error)
            run.verifyResult == PortCheck.NoReply -> StatusChip(stringResource(R.string.knock_verify_no_reply), StatusTone.Warning)
            else -> Text(stringResource(R.string.knock_verify_waiting), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
    if (run.verifyResult != null && run.verifyResult != PortCheck.Open) {
        Text(
            stringResource(if (run.verifyResult == PortCheck.Closed) R.string.knock_verify_closed_note else R.string.knock_verify_no_reply_note),
            style = MaterialTheme.typography.bodySmall,
            color = if (run.verifyResult == PortCheck.Closed) colors.onSurfaceVariant else ext.warning,
        )
    }
}
