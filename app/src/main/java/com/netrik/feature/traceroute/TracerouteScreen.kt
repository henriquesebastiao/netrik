package com.netrik.feature.traceroute

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.netrik.R
import com.netrik.core.designsystem.component.AdvancedOptionsPanel
import com.netrik.core.designsystem.component.ErrorCard
import com.netrik.core.designsystem.component.NetrikTopAppBar
import com.netrik.core.designsystem.component.NoConnectionCard
import com.netrik.core.designsystem.component.NumberOptionField
import com.netrik.core.designsystem.component.RecentTargets
import com.netrik.core.designsystem.component.RunButton
import com.netrik.core.designsystem.component.RunHeader
import com.netrik.core.designsystem.component.StatusTone
import com.netrik.core.designsystem.component.TargetField
import com.netrik.core.designsystem.component.ToolEmptyState
import com.netrik.core.designsystem.theme.NetrikTheme
import com.netrik.core.network.ping.OptionField
import com.netrik.core.ui.ClearHistoryMenu
import com.netrik.core.ui.CopyAction
import com.netrik.core.ui.RunFailure
import com.netrik.core.ui.RunPhase
import com.netrik.core.ui.failureText
import com.netrik.core.ui.rememberCopyAction
import com.netrik.core.ui.rememberLocalNetworkPermissionRequest
import com.netrik.core.ui.targetErrorText
import com.netrik.feature.ping.ms

@Composable
fun TracerouteScreen(onBack: () -> Unit, viewModel: TracerouteViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val copy = rememberCopyAction()
    val requestLocalNetwork = rememberLocalNetworkPermissionRequest(onGranted = viewModel::onStart)
    val routeCopied = stringResource(R.string.trace_copied)
    val noReply = stringResource(R.string.trace_no_reply, state.runHopTimeout)

    Scaffold(
        topBar = {
            NetrikTopAppBar(
                title = stringResource(R.string.tool_traceroute),
                onBack = onBack,
                actions = { ClearHistoryMenu(onClear = viewModel::onClearHistory) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "target") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TargetField(
                        value = state.target,
                        onValueChange = viewModel::onTargetChange,
                        onSubmit = viewModel::onStart,
                        error = targetErrorText(state.targetError),
                        enabled = !state.running,
                    )
                    RecentTargets(state.recents, enabled = !state.running, onSelect = viewModel::onTargetChange)
                }
            }
            item(key = "options") {
                AdvancedOptionsPanel(
                    summary = stringResource(R.string.trace_summary, state.maxHopsText, state.hopTimeoutText),
                    expanded = state.advancedOpen,
                    onToggle = viewModel::onToggleAdvanced,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NumberOptionField(
                            label = stringResource(R.string.trace_field_max_hops),
                            value = state.maxHopsText,
                            onValueChange = viewModel::onMaxHopsChange,
                            suffix = null,
                            isError = OptionField.MaxHops in state.optionErrors,
                            enabled = !state.running,
                            modifier = Modifier.weight(1f),
                        )
                        NumberOptionField(
                            label = stringResource(R.string.trace_field_hop_timeout),
                            value = state.hopTimeoutText,
                            onValueChange = viewModel::onHopTimeoutChange,
                            suffix = stringResource(R.string.unit_seconds),
                            isError = OptionField.HopTimeout in state.optionErrors,
                            enabled = !state.running,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            item(key = "run") { RunButton(state.running, state.connected, viewModel::onStart, viewModel::onStop) }
            if (!state.connected && !state.running) item(key = "offline") { NoConnectionCard() }

            when {
                state.phase == RunPhase.Idle -> item(key = "empty") {
                    ToolEmptyState(icon = R.drawable.ic_route, text = stringResource(R.string.trace_empty))
                }
                state.failure == RunFailure.HostNotFound -> item(key = "error") {
                    ErrorCard(stringResource(R.string.error_host_not_found), stringResource(R.string.error_host_not_found_body), onRetry = viewModel::onStart)
                }
                else -> {
                    item(key = "header") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val (label, tone) = chip(state)
                            RunHeader(
                                chipLabel = label,
                                chipTone = tone,
                                chipIcon = when {
                                    state.phase == RunPhase.Failed -> R.drawable.ic_error_filled
                                    state.phase == RunPhase.Done && state.reachedDestination -> R.drawable.ic_check_circle_filled
                                    else -> R.drawable.ic_do_not_disturb_on_filled
                                },
                                running = state.running,
                                runLine = runLine(state),
                                onCopy = if (!state.running && state.hops.isNotEmpty()) {
                                    { copy.copy(routeText(state.hops), routeCopied) }
                                } else {
                                    null
                                },
                            )
                            if (state.running) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                    if (state.hops.isNotEmpty() || state.probingHop != null) {
                        item(key = "hops") { HopTimeline(state, noReply, copy) }
                        // A nota só faz sentido quando há roteadores intermediários (RTT medido à parte).
                        if (state.hops.any { it.address != null && !it.isDestination }) item(key = "note") {
                            Text(
                                stringResource(R.string.trace_rtt_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                        }
                    }
                    val failure = state.failure
                    if (failure != null) {
                        item(key = "failure") {
                            val (title, body) = failureText(failure)
                            if (failure == RunFailure.LocalNetworkPermission) {
                                ErrorCard(title, body, onRetry = requestLocalNetwork, retryLabel = stringResource(R.string.action_allow), retryIcon = R.drawable.ic_lan)
                            } else {
                                ErrorCard(title, body, onRetry = if (state.connected) viewModel::onStart else null)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HopTimeline(state: TracerouteUiState, noReply: String, copy: CopyAction) {
    val hops = state.hops.sortedBy { it.number }
    val pending = state.probingHop?.takeIf { state.running && hops.none { h -> h.number == it } }
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            hops.forEachIndexed { index, hop ->
                HopRow(
                    hop = hop,
                    noReply = noReply,
                    lineAbove = index > 0,
                    lineBelow = index < hops.lastIndex || pending != null,
                    copy = copy,
                )
            }
            if (pending != null) PendingRow(pending, lineAbove = hops.isNotEmpty())
        }
    }
}

@Composable
private fun HopRow(hop: HopUi, noReply: String, lineAbove: Boolean, lineBelow: Boolean, copy: CopyAction) {
    val colors = MaterialTheme.colorScheme
    val copied = hop.address?.let { stringResource(R.string.trace_ip_copied, it) }
    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).heightIn(min = 60.dp).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TimelineBadge(
                lineAbove = lineAbove,
                lineBelow = lineBelow,
                container = if (hop.isDestination) colors.primary else colors.surfaceContainerHigh,
                contentColor = if (hop.isDestination) colors.onPrimary else colors.onSurfaceVariant,
            ) {
                Text(hop.number.toString(), style = NetrikTheme.dataTypography.dataMedium.copy(fontSize = 13.sp))
            }
            Column(modifier = Modifier.weight(1f).align(Alignment.CenterVertically).padding(vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = hop.address ?: "* * *",
                        style = NetrikTheme.dataTypography.dataMedium,
                        color = if (hop.address != null) colors.onSurface else colors.onSurfaceVariant,
                    )
                    if (hop.address != null) {
                        Icon(painterResource(R.drawable.ic_content_copy), contentDescription = null, tint = colors.outline, modifier = Modifier.size(14.dp))
                    }
                }
                Text(
                    text = when {
                        hop.address == null -> noReply
                        hop.hostname != null -> hop.hostname
                        else -> stringResource(R.string.trace_hostname_unresolved)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = if (hop.address == null) "—" else ms(hop.rttMs),
                style = NetrikTheme.dataTypography.dataMedium.copy(fontSize = 13.sp),
                color = if (hop.rttMs != null) colors.onSurface else colors.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }
    }
    if (hop.address != null && copied != null) {
        Surface(onClick = { copy.copy(hop.address, copied) }, color = Color.Transparent) { content() }
    } else {
        content()
    }
}

@Composable
private fun PendingRow(hop: Int, lineAbove: Boolean) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).heightIn(min = 60.dp).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TimelineBadge(lineAbove = lineAbove, lineBelow = false, container = colors.primaryContainer, contentColor = colors.onPrimaryContainer) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = colors.onPrimaryContainer)
        }
        Text(
            stringResource(R.string.trace_waiting, hop),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterVertically),
        )
    }
}

/** Círculo numerado de 32dp com a linha vertical que liga os saltos. */
@Composable
private fun TimelineBadge(
    lineAbove: Boolean,
    lineBelow: Boolean,
    container: Color,
    contentColor: Color,
    content: @Composable () -> Unit,
) {
    val line = MaterialTheme.colorScheme.outlineVariant
    Box(modifier = Modifier.width(32.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val x = size.width / 2
            val stroke = 2.dp.toPx()
            if (lineAbove) drawLine(line, Offset(x, 0f), Offset(x, size.height / 2), strokeWidth = stroke)
            if (lineBelow) drawLine(line, Offset(x, size.height / 2), Offset(x, size.height), strokeWidth = stroke)
        }
        Surface(shape = CircleShape, color = container, contentColor = contentColor, modifier = Modifier.size(32.dp)) {
            Box(contentAlignment = Alignment.Center) { content() }
        }
    }
}

@Composable
private fun chip(state: TracerouteUiState): Pair<String, StatusTone> {
    val hop = state.probingHop ?: state.hops.maxOfOrNull { it.number } ?: 1
    val count = state.hops.maxOfOrNull { it.number } ?: 0
    return when (state.phase) {
        RunPhase.Resolving, RunPhase.Running, RunPhase.Idle -> stringResource(R.string.trace_running, hop) to StatusTone.Running
        RunPhase.Done -> if (state.reachedDestination) {
            pluralStringResource(R.plurals.trace_reached, count, count) to StatusTone.Success
        } else {
            pluralStringResource(R.plurals.trace_not_reached, count, count) to StatusTone.Neutral
        }
        RunPhase.Stopped -> stringResource(R.string.trace_stopped, count) to StatusTone.Neutral
        RunPhase.Failed -> stringResource(R.string.run_stopped) to StatusTone.Error
    }
}

@Composable
private fun runLine(state: TracerouteUiState): String {
    val target = state.runTarget ?: state.target.trim()
    val address = state.runAddress
    return if (address != null && address != target) {
        stringResource(R.string.trace_run_line_resolved, target, address, state.runMaxHops)
    } else {
        stringResource(R.string.trace_run_line, target, state.runMaxHops)
    }
}

private fun routeText(hops: List<HopUi>): String = hops.sortedBy { it.number }.joinToString("\n") { h ->
    if (h.address == null) "${h.number}  * * *" else listOfNotNull("${h.number}", h.address, h.hostname, h.rttMs?.let { ms(it) }).joinToString("  ")
}
