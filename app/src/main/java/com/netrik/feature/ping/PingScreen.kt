package com.netrik.feature.ping

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import com.netrik.core.network.ping.PingSample
import com.netrik.core.network.ping.PingStats
import com.netrik.core.ui.ClearHistoryMenu
import com.netrik.core.ui.RunFailure
import com.netrik.core.ui.RunPhase
import com.netrik.core.ui.failureText
import com.netrik.core.ui.rememberCopyAction
import com.netrik.core.ui.rememberLocalNetworkPermissionRequest
import com.netrik.core.ui.targetErrorText
import java.util.Locale

private const val MAX_ROWS = 40

@Composable
fun PingScreen(onBack: () -> Unit, viewModel: PingViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val copy = rememberCopyAction()
    val requestLocalNetwork = rememberLocalNetworkPermissionRequest(onGranted = viewModel::onStart)
    val copiedMessage = stringResource(R.string.ping_copied)
    val statLabels = statLabels()
    val runLine = runLine(state)

    Scaffold(
        topBar = {
            NetrikTopAppBar(
                title = stringResource(R.string.tool_ping),
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
            item(key = "options") { PingOptionsPanel(state, viewModel) }
            item(key = "run") {
                RunButton(
                    running = state.running,
                    enabled = state.connected,
                    onStart = viewModel::onStart,
                    onStop = viewModel::onStop,
                )
            }
            if (!state.connected && !state.running) item(key = "offline") { NoConnectionCard() }

            when {
                state.phase == RunPhase.Idle -> item(key = "empty") {
                    ToolEmptyState(icon = R.drawable.ic_network_ping, text = stringResource(R.string.ping_empty))
                }
                state.failure == RunFailure.HostNotFound -> item(key = "error") {
                    ErrorCard(stringResource(R.string.error_host_not_found), stringResource(R.string.error_host_not_found_body), onRetry = viewModel::onStart)
                }
                else -> {
                    item(key = "header") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            RunHeader(
                                chipLabel = chipLabel(state.phase),
                                chipTone = chipTone(state.phase),
                                chipIcon = if (state.phase == RunPhase.Stopped || state.phase == RunPhase.Failed) R.drawable.ic_do_not_disturb_on_filled else null,
                                running = state.running,
                                runLine = runLine,
                                onCopy = if (!state.running && state.samples.isNotEmpty()) {
                                    { copy.copy(copyText(runLine, statLabels, state.stats), copiedMessage) }
                                } else {
                                    null
                                },
                            )
                            RunProgress(state)
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
                    if (state.samples.isNotEmpty() || state.phase == RunPhase.Running) {
                        item(key = "stats") { StatsGrid(state.stats, statLabels) }
                        item(key = "chart") { LatencyChart(state.samples, continuousRunning = state.running && state.plannedCount == null) }
                        item(key = "table") { SamplesTable(state.samples) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PingOptionsPanel(state: PingUiState, viewModel: PingViewModel) {
    val enabled = !state.running
    val summary = if (state.mode == PingMode.Count) {
        stringResource(R.string.ping_summary_count, state.countText, state.intervalText, state.sizeText, state.timeoutText)
    } else {
        stringResource(R.string.ping_summary_continuous, state.intervalText, state.sizeText, state.timeoutText)
    }
    AdvancedOptionsPanel(summary = summary, expanded = state.advancedOpen, onToggle = viewModel::onToggleAdvanced) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                PingMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = state.mode == mode,
                        onClick = { viewModel.onModeChange(mode) },
                        enabled = enabled,
                        shape = SegmentedButtonDefaults.itemShape(index, PingMode.entries.size),
                    ) {
                        Text(stringResource(if (mode == PingMode.Count) R.string.ping_mode_count else R.string.ping_mode_continuous))
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberOptionField(
                    label = stringResource(R.string.ping_field_count),
                    value = state.countText,
                    onValueChange = viewModel::onCountChange,
                    suffix = null,
                    isError = OptionField.Count in state.optionErrors,
                    enabled = enabled && state.mode == PingMode.Count,
                    modifier = Modifier.weight(1f),
                )
                NumberOptionField(
                    label = stringResource(R.string.ping_field_interval),
                    value = state.intervalText,
                    onValueChange = viewModel::onIntervalChange,
                    suffix = stringResource(R.string.unit_seconds),
                    isError = OptionField.Interval in state.optionErrors,
                    enabled = enabled,
                    decimal = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberOptionField(
                    label = stringResource(R.string.ping_field_size),
                    value = state.sizeText,
                    onValueChange = viewModel::onSizeChange,
                    suffix = stringResource(R.string.unit_bytes),
                    isError = OptionField.Size in state.optionErrors,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                )
                NumberOptionField(
                    label = stringResource(R.string.ping_field_timeout),
                    value = state.timeoutText,
                    onValueChange = viewModel::onTimeoutChange,
                    suffix = stringResource(R.string.unit_seconds),
                    isError = OptionField.Timeout in state.optionErrors,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                )
            }
            if (OptionField.Interval in state.optionErrors) {
                Text(stringResource(R.string.ping_interval_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun RunProgress(state: PingUiState) {
    val planned = state.plannedCount
    when {
        state.phase == RunPhase.Resolving || (state.phase == RunPhase.Running && planned == null) ->
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        state.phase == RunPhase.Running && planned != null -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val sent = state.samples.size
            LinearProgressIndicator(progress = { (sent.toFloat() / planned).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            Text(stringResource(R.string.ping_progress, sent, planned), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private data class StatCell(val label: String, val value: String, val tone: Tone = Tone.Normal)
private enum class Tone { Normal, Warning, Error }

private data class StatLabels(
    val sent: String, val received: String, val loss: String, val jitter: String,
    val min: String, val avg: String, val max: String,
)

@Composable
private fun statLabels() = StatLabels(
    stringResource(R.string.ping_stat_sent), stringResource(R.string.ping_stat_received), stringResource(R.string.ping_stat_loss),
    stringResource(R.string.ping_stat_jitter), stringResource(R.string.ping_stat_min), stringResource(R.string.ping_stat_avg),
    stringResource(R.string.ping_stat_max),
)

private fun statCells(stats: PingStats, l: StatLabels) = listOf(
    StatCell(l.sent, stats.sent.toString()),
    StatCell(l.received, stats.received.toString()),
    StatCell(l.loss, "${stats.lossPercent}%", if (stats.lossPercent >= 50) Tone.Error else if (stats.lossPercent > 0) Tone.Warning else Tone.Normal),
    StatCell(l.jitter, ms(stats.jitterMs)),
    StatCell(l.min, ms(stats.minMs)),
    StatCell(l.avg, ms(stats.avgMs)),
    StatCell(l.max, ms(stats.maxMs), if ((stats.maxMs ?: 0.0) > 100) Tone.Warning else Tone.Normal),
)

@Composable
private fun StatsGrid(stats: PingStats, labels: StatLabels) {
    val cells = statCells(stats, labels)
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            cells.chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { cell -> StatItem(cell, Modifier.weight(1f)) }
                    repeat(4 - row.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun StatItem(cell: StatCell, modifier: Modifier) {
    val color = when (cell.tone) {
        Tone.Normal -> MaterialTheme.colorScheme.onSurface
        Tone.Warning -> NetrikTheme.extendedColors.warning
        Tone.Error -> MaterialTheme.colorScheme.error
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(cell.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            if (cell.tone != Tone.Normal) {
                Icon(painterResource(R.drawable.ic_warning_filled), contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            }
            Text(cell.value, style = NetrikTheme.dataTypography.dataMedium.copy(fontSize = 15.sp), color = color, maxLines = 1)
        }
    }
}

@Composable
private fun SamplesTable(samples: List<PingSample>) {
    val colors = MaterialTheme.colorScheme
    val warning = NetrikTheme.extendedColors.warning
    Surface(shape = RoundedCornerShape(12.dp), color = colors.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                listOf(R.string.ping_table_seq, R.string.ping_table_ttl).forEach {
                    Text(stringResource(it).uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = colors.onSurfaceVariant, modifier = Modifier.width(56.dp))
                }
                Text(stringResource(R.string.ping_table_time).uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
            }
            samples.takeLast(MAX_ROWS).asReversed().forEach { s ->
                HorizontalDivider(color = colors.outlineVariant)
                val timeout = s.timeMs == null
                val high = !timeout && s.timeMs > 100
                val color = if (timeout) colors.error else if (high) warning else colors.onSurface
                Row(modifier = Modifier.heightIn(min = 40.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    val mono = NetrikTheme.dataTypography.dataMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Normal)
                    Text(s.seq.toString(), style = mono, modifier = Modifier.width(56.dp))
                    Text(s.ttl?.toString() ?: "—", style = mono, color = colors.onSurfaceVariant, modifier = Modifier.width(56.dp))
                    Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                        if (timeout || high) {
                            Icon(
                                painterResource(if (timeout) R.drawable.ic_error_filled else R.drawable.ic_warning_filled),
                                contentDescription = null,
                                tint = color,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        Text(
                            if (timeout) stringResource(R.string.ping_timeout) else ms(s.timeMs),
                            style = mono.copy(fontWeight = FontWeight.Medium),
                            color = color,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun chipLabel(phase: RunPhase): String = stringResource(
    when (phase) {
        RunPhase.Running, RunPhase.Resolving, RunPhase.Idle -> R.string.run_running
        RunPhase.Done -> R.string.run_done
        RunPhase.Stopped, RunPhase.Failed -> R.string.run_stopped
    },
)

private fun chipTone(phase: RunPhase): StatusTone = when (phase) {
    RunPhase.Done -> StatusTone.Success
    RunPhase.Failed -> StatusTone.Error
    else -> StatusTone.Neutral
}

@Composable
private fun runLine(state: PingUiState): String {
    val target = state.runTarget ?: state.target.trim()
    val address = state.runAddress
    return if (address != null && address != target) {
        stringResource(R.string.ping_run_line_resolved, target, address, state.runPayload)
    } else {
        stringResource(R.string.ping_run_line, target, state.runPayload)
    }
}

private fun copyText(runLine: String, labels: StatLabels, stats: PingStats): String =
    (listOf(runLine) + statCells(stats, labels).map { "${it.label}: ${it.value}" }).joinToString("\n")

/** Times with one decimal place and a dot, like ping's own output. */
internal fun ms(value: Double?): String = value?.let { String.format(Locale.ROOT, "%.1f ms", it) } ?: "—"

