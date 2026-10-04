package com.netrik.feature.portscan

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.netrik.R
import com.netrik.core.designsystem.component.ErrorCard
import com.netrik.core.designsystem.component.IconAvatar
import com.netrik.core.designsystem.component.NetrikTopAppBar
import com.netrik.core.designsystem.component.NoConnectionCard
import com.netrik.core.designsystem.component.NumberOptionField
import com.netrik.core.designsystem.component.RecentTargets
import com.netrik.core.designsystem.component.SectionHeader
import com.netrik.core.designsystem.component.StatusChip
import com.netrik.core.designsystem.component.StatusChipSize
import com.netrik.core.designsystem.component.StatusTone
import com.netrik.core.designsystem.theme.NetrikTheme
import com.netrik.core.portscan.Cidr
import com.netrik.core.portscan.PortList
import com.netrik.core.portscan.PortState
import com.netrik.core.portscan.Protocol
import com.netrik.core.portscan.ScanEstimate
import com.netrik.core.ui.ClearHistoryMenu
import com.netrik.core.ui.CopyAction
import com.netrik.core.ui.RunFailure
import com.netrik.core.ui.RunPhase
import com.netrik.core.ui.failureText
import com.netrik.core.ui.rememberCopyAction
import com.netrik.core.ui.rememberLocalNetworkPermissionRequest
import com.netrik.core.ui.targetErrorText
import java.text.NumberFormat
import java.util.Locale

private val ptBr = Locale.forLanguageTag("pt-BR")
private fun nf(n: Long): String = NumberFormat.getIntegerInstance(ptBr).format(n)
private fun nf(n: Int): String = nf(n.toLong())

@Composable
fun PortScanScreen(onBack: () -> Unit, viewModel: PortScanViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler(enabled = state.inResults, onBack = viewModel::onBackToConfig)
    Scaffold(
        topBar = {
            NetrikTopAppBar(
                title = stringResource(R.string.tool_port_scanner),
                onBack = if (state.inResults) viewModel::onBackToConfig else onBack,
                actions = { ClearHistoryMenu(onClear = viewModel::onClearHistory) },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            if (state.inResults) Results(state, viewModel) else Config(state, viewModel)
        }
    }
}

@Composable
private fun Config(state: PortScanUiState, viewModel: PortScanViewModel) {
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "target") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(stringResource(R.string.ports_target))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    ScanMode.entries.forEachIndexed { i, mode ->
                        SegmentedButton(
                            selected = state.mode == mode,
                            onClick = { viewModel.onModeChange(mode) },
                            shape = SegmentedButtonDefaults.itemShape(i, ScanMode.entries.size),
                            icon = {
                                SegmentedButtonDefaults.Icon(active = state.mode == mode) {
                                    Icon(painterResource(if (mode == ScanMode.Host) R.drawable.ic_dns else R.drawable.ic_lan), contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                            },
                        ) { Text(stringResource(if (mode == ScanMode.Host) R.string.ports_mode_host else R.string.ports_mode_net)) }
                    }
                }
                if (state.mode == ScanMode.Host) {
                    TargetInput(
                        value = state.hostText,
                        onChange = viewModel::onHostChange,
                        label = stringResource(R.string.ports_host_label),
                        placeholder = stringResource(R.string.ports_host_placeholder),
                        icon = R.drawable.ic_dns,
                        support = targetErrorText(state.targetError) ?: stringResource(R.string.ports_host_support),
                        isError = state.targetError != null,
                        keyboard = KeyboardType.Uri,
                        onDone = viewModel::onStart,
                    )
                    RecentTargets(state.recents, enabled = true, onSelect = viewModel::onHostChange)
                } else {
                    val cidr = state.cidr
                    TargetInput(
                        value = state.cidrText,
                        onChange = viewModel::onCidrChange,
                        label = stringResource(R.string.ports_net_label),
                        placeholder = "192.168.0.0/24",
                        icon = R.drawable.ic_lan,
                        support = when (cidr) {
                            Cidr.Result.Malformed -> stringResource(R.string.ports_net_malformed)
                            Cidr.Result.TooLarge -> stringResource(R.string.ports_net_too_large)
                            is Cidr.Result.Valid -> if (state.cidrText == state.currentCidr) {
                                stringResource(R.string.ports_net_support_current, state.hostCount)
                            } else {
                                stringResource(R.string.ports_net_support, state.hostCount)
                            }
                        },
                        isError = state.cidrText.isNotEmpty() && cidr !is Cidr.Result.Valid,
                        keyboard = KeyboardType.Uri,
                        onDone = viewModel::onStart,
                    )
                }
            }
        }
        item(key = "protocol") {
            Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(stringResource(R.string.ports_protocol))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    Protocol.entries.forEachIndexed { i, p ->
                        SegmentedButton(
                            selected = state.protocol == p,
                            onClick = { viewModel.onProtocolChange(p) },
                            shape = SegmentedButtonDefaults.itemShape(i, Protocol.entries.size),
                        ) { Text(p.name.uppercase()) }
                    }
                }
                Text(
                    stringResource(if (state.protocol == Protocol.Tcp) R.string.ports_tcp_hint else R.string.ports_udp_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                NumberOptionField(
                    label = stringResource(R.string.ports_timeout),
                    value = state.timeoutText,
                    onValueChange = viewModel::onTimeoutChange,
                    suffix = "ms",
                    isError = state.timeoutMs == null,
                    enabled = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                if (state.timeoutMs == null) {
                    Text(stringResource(R.string.ports_timeout_invalid), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
        item(key = "ports") {
            Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(stringResource(R.string.ports_ports))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PortPreset.entries.forEach { p ->
                        val selected = state.preset == p
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.onPresetChange(p) },
                            label = { Text(presetLabel(p)) },
                            leadingIcon = if (selected) {
                                { Icon(painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(18.dp)) }
                            } else {
                                null
                            },
                        )
                    }
                }
                if (state.preset == PortPreset.Custom) {
                    val list = state.portList
                    val error = state.customText.isNotBlank() && list !is PortList.Valid
                    TargetInput(
                        value = state.customText,
                        onChange = viewModel::onCustomChange,
                        label = stringResource(R.string.ports_custom_label),
                        placeholder = stringResource(R.string.ports_custom_placeholder),
                        icon = null,
                        support = when (list) {
                            is PortList.Valid -> stringResource(R.string.ports_custom_summary, nf(list.ports.size), list.singles, list.ranges)
                            is PortList.Malformed -> stringResource(R.string.ports_custom_malformed, list.token)
                            is PortList.OutOfRange -> stringResource(R.string.ports_custom_out_of_range, list.token)
                            PortList.Empty -> stringResource(R.string.ports_custom_help)
                        },
                        isError = error,
                        keyboard = KeyboardType.Number,
                        onDone = viewModel::onStart,
                    )
                }
            }
        }
        item(key = "summary") { Summary(state) }
        if (!state.connected) item(key = "offline") { NoConnectionCard() }
        item(key = "start") {
            val ready = state.portCount > 0 && state.timeoutMs != null && state.connected &&
                (state.mode == ScanMode.Host || state.cidr is Cidr.Result.Valid)
            Button(
                onClick = viewModel::onStart,
                enabled = ready,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Icon(painterResource(R.drawable.ic_play_arrow_filled), contentDescription = null, modifier = Modifier.size(20.dp))
                Text(stringResource(R.string.ports_start), modifier = Modifier.padding(start = 8.dp))
            }
        }
        item(key = "authorized") {
            Text(
                stringResource(R.string.ports_authorized),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun TargetInput(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    placeholder: String,
    icon: Int?,
    support: String,
    isError: Boolean,
    keyboard: KeyboardType,
    onDone: () -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        label = { Text(label) },
        placeholder = { Text(placeholder, style = NetrikTheme.dataTypography.dataMedium.copy(fontSize = 16.sp)) },
        leadingIcon = icon?.let { { Icon(painterResource(it), contentDescription = null) } },
        supportingText = { Text(support) },
        isError = isError,
        singleLine = true,
        textStyle = NetrikTheme.dataTypography.dataMedium.copy(fontSize = 16.sp),
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = keyboard, imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { onDone() }),
    )
}

@Composable
private fun Summary(state: PortScanUiState) {
    val checks = state.hostCount.toLong() * state.portCount
    val timeout = state.timeoutMs
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.ports_checks), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    when {
                        state.portCount == 0 || state.hostCount == 0 -> "—"
                        state.mode == ScanMode.Network -> stringResource(R.string.ports_checks_net, state.hostCount, nf(state.portCount), nf(checks))
                        else -> stringResource(R.string.ports_checks_host, nf(state.portCount))
                    },
                    style = NetrikTheme.dataTypography.dataMedium.copy(fontSize = 15.sp),
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(stringResource(R.string.ports_eta), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    if (checks == 0L || timeout == null) "—" else stringResource(R.string.ports_eta_value, duration(ScanEstimate.seconds(checks, state.protocol, timeout))),
                    style = NetrikTheme.dataTypography.dataMedium.copy(fontSize = 15.sp),
                )
            }
        }
    }
}

@Composable
private fun Results(state: PortScanUiState, viewModel: PortScanViewModel) {
    val copy = rememberCopyAction()
    val requestLocalNetwork = rememberLocalNetworkPermissionRequest(onGranted = viewModel::onStart)
    val visible = if (state.onlyOpen) state.hosts.filter { it.open > 0 } else state.hosts
    val hidden = state.hosts.size - visible.size
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") { ResultsHeader(state, onStop = viewModel::onStop, onNew = viewModel::onBackToConfig) }
        val failure = state.failure
        if (failure != null) {
            item(key = "failure") {
                val (title, body) = failureText(failure)
                if (failure == RunFailure.LocalNetworkPermission) {
                    ErrorCard(title, body, onRetry = requestLocalNetwork, retryLabel = stringResource(R.string.action_allow), retryIcon = R.drawable.ic_lan)
                } else {
                    ErrorCard(title, body, onRetry = viewModel::onStart)
                }
            }
            return@LazyColumn
        }
        item(key = "filter") {
            Surface(onClick = viewModel::onToggleOnlyOpen, color = Color.Transparent) {
                Row(modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.ports_only_open), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Switch(checked = state.onlyOpen, onCheckedChange = { viewModel.onToggleOnlyOpen() })
                }
            }
        }
        items(visible, key = { it.ip }) { host ->
            HostCard(host, state, copy, expanded = host.ip in state.expanded, onToggle = { viewModel.onToggleHost(host.ip) })
        }
        if (state.phase == RunPhase.Done && state.hosts.isEmpty()) {
            item(key = "nohosts") {
                Text(stringResource(R.string.ports_no_hosts), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
            }
        }
        // Durante a descoberta os hosts ainda não tiveram as portas varridas: a nota seria prematura.
        if (hidden > 0 && !state.discovering) {
            item(key = "hidden") {
                Text(pluralStringResource(R.plurals.ports_hidden_hosts, hidden, hidden), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
            }
        }
    }
}

@Composable
private fun ResultsHeader(state: PortScanUiState, onStop: () -> Unit, onNew: () -> Unit) {
    val spec = state.spec
    val colors = MaterialTheme.colorScheme
    val mono = NetrikTheme.dataTypography.dataSmall
    Surface(shape = RoundedCornerShape(16.dp), color = colors.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconAvatar(icon = R.drawable.ic_radar)
                Column(modifier = Modifier.weight(1f)) {
                    Text(spec?.target ?: "", style = NetrikTheme.dataTypography.dataMedium.copy(fontSize = 16.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (spec != null) {
                        val ports = presetSpec(spec)
                        Text(
                            if (spec.mode == ScanMode.Network) {
                                stringResource(R.string.ports_spec_net, spec.protocol.name.uppercase(), ports, spec.hostCount)
                            } else {
                                stringResource(R.string.ports_spec, spec.protocol.name.uppercase(), ports)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
                FilledTonalButton(
                    onClick = if (state.running) onStop else onNew,
                    contentPadding = PaddingValues(start = 12.dp, end = 16.dp),
                    colors = if (state.running) {
                        ButtonDefaults.filledTonalButtonColors(containerColor = colors.errorContainer, contentColor = colors.onErrorContainer)
                    } else {
                        ButtonDefaults.filledTonalButtonColors()
                    },
                ) {
                    Icon(painterResource(if (state.running) R.drawable.ic_stop_filled else R.drawable.ic_tune), contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(if (state.running) R.string.action_stop else R.string.ports_new_scan), modifier = Modifier.padding(start = 8.dp))
                }
            }
            when {
                state.phase == RunPhase.Resolving -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                state.running && state.discovering -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LinearProgressIndicator(progress = { fraction(state.probed.toLong(), state.discoveryTotal.toLong()) }, modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.ports_discovering, state.probed, state.discoveryTotal), style = mono, color = colors.onSurfaceVariant)
                }
                state.running -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LinearProgressIndicator(progress = { fraction(state.checksDone, state.checksTotal) }, modifier = Modifier.fillMaxWidth())
                    Text(
                        if (spec?.mode == ScanMode.Network) {
                            stringResource(R.string.ports_progress_net, state.hostsDone, state.hostsTotal, nf(state.checksDone))
                        } else {
                            stringResource(R.string.ports_progress_host, nf(state.checksDone), nf(state.checksTotal))
                        },
                        style = mono,
                        color = colors.onSurfaceVariant,
                    )
                }
                state.phase == RunPhase.Done -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusChip(stringResource(R.string.run_done), StatusTone.Success)
                    Text(stringResource(R.string.ports_done_line, nf(state.checksTotal), duration(state.elapsedMillis / 1000)), style = mono, color = colors.onSurfaceVariant)
                }
                state.phase == RunPhase.Stopped -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusChip(stringResource(R.string.run_stopped), StatusTone.Neutral)
                    Text(stringResource(R.string.ports_stopped_line, nf(state.checksDone), nf(state.checksTotal)), style = mono, color = colors.onSurfaceVariant)
                }
            }
            if (state.failure == null) {
                val ports = state.hosts.flatMap { it.ports.values }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Kpi(stringResource(R.string.ports_kpi_hosts), state.hosts.size.toString(), colors.onSurface, Modifier.weight(1f))
                    Kpi(stringResource(R.string.ports_kpi_open), ports.count { it == PortState.Open }.toString(), NetrikTheme.extendedColors.success, Modifier.weight(1f))
                    Kpi(
                        stringResource(R.string.ports_kpi_filtered),
                        ports.count { it == PortState.Filtered || it == PortState.OpenFiltered }.toString(),
                        NetrikTheme.extendedColors.warning,
                        Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun Kpi(label: String, value: String, color: Color, modifier: Modifier) {
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface, modifier = modifier) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = NetrikTheme.dataTypography.dataMedium.copy(fontSize = 18.sp, lineHeight = 26.sp), color = color)
        }
    }
}

@Composable
private fun HostCard(host: HostResult, state: PortScanUiState, copy: CopyAction, expanded: Boolean, onToggle: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val proto = state.spec?.protocol ?: Protocol.Tcp
    Surface(shape = RoundedCornerShape(16.dp), color = colors.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column {
            Surface(onClick = onToggle, color = Color.Transparent) {
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    IconAvatar(icon = R.drawable.ic_devices_other, containerColor = colors.surfaceContainerHighest, contentColor = colors.onSurfaceVariant)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(host.ip, style = NetrikTheme.dataTypography.dataMedium.copy(fontSize = 15.sp))
                        Text(
                            state.hostnames[host.ip] ?: stringResource(R.string.ports_no_hostname),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    StatusChip(
                        pluralStringResource(R.plurals.ports_open_count, host.open, host.open),
                        if (host.open > 0) StatusTone.Success else StatusTone.Neutral,
                        size = StatusChipSize.Small,
                    )
                    Icon(painterResource(if (expanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more), contentDescription = null, tint = colors.onSurfaceVariant)
                }
            }
            if (expanded) {
                val ports = host.ports.entries
                    .filter { !state.onlyOpen || it.value == PortState.Open }
                    .sortedWith(compareBy({ it.value != PortState.Open }, { it.key }))
                ports.forEach { (port, portState) ->
                    val label = "${host.ip}:$port"
                    val copied = stringResource(R.string.ports_copied, label)
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        HorizontalDivider(color = colors.outlineVariant)
                        Surface(onClick = { copy.copy(label, copied) }, color = Color.Transparent) {
                            Row(modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(port.toString(), style = NetrikTheme.dataTypography.dataMedium.copy(fontSize = 15.sp), modifier = Modifier.width(56.dp))
                                Text(proto.name.lowercase(), style = NetrikTheme.dataTypography.dataSmall, color = colors.onSurfaceVariant, modifier = Modifier.width(32.dp))
                                Text(
                                    state.serviceNames[port] ?: stringResource(R.string.ports_service_unknown),
                                    style = NetrikTheme.dataTypography.dataSmall.copy(fontSize = 13.sp),
                                    color = colors.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                PortStateChip(portState)
                            }
                        }
                    }
                }
                if (!state.onlyOpen && host.closed > 0) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        HorizontalDivider(color = colors.outlineVariant)
                        Text(
                            stringResource(R.string.ports_closed_hidden, nf(host.closed)),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                        )
                    }
                }
                Box(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun PortStateChip(state: PortState) {
    when (state) {
        PortState.Open -> StatusChip(stringResource(R.string.ports_state_open), StatusTone.Success, size = StatusChipSize.Small)
        PortState.Filtered -> StatusChip(stringResource(R.string.ports_state_filtered), StatusTone.Warning, size = StatusChipSize.Small)
        PortState.OpenFiltered -> StatusChip(stringResource(R.string.ports_state_open_filtered), StatusTone.Warning, size = StatusChipSize.Small)
        PortState.Closed -> StatusChip(stringResource(R.string.ports_state_closed), StatusTone.Neutral, size = StatusChipSize.Small)
    }
}

@Composable
private fun presetLabel(p: PortPreset): String = stringResource(
    when (p) {
        PortPreset.Top100 -> R.string.ports_preset_top100
        PortPreset.Top1000 -> R.string.ports_preset_top1000
        PortPreset.All -> R.string.ports_preset_all
        PortPreset.Custom -> R.string.ports_preset_custom
    },
)

@Composable
private fun presetSpec(spec: ScanSpec): String =
    if (spec.preset == PortPreset.Custom) stringResource(R.string.ports_n_ports, nf(spec.portCount)) else presetLabel(spec.preset)

private fun fraction(done: Long, total: Long): Float = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)

/** "~45 s", "~3 min", "~1,5 h". */
internal fun duration(seconds: Long): String = when {
    seconds < 60 -> "~${seconds.coerceAtLeast(1)} s"
    seconds < 3600 -> "~${(seconds + 30) / 60} min"
    else -> "~" + String.format(ptBr, "%.1f", seconds / 3600.0) + " h"
}

