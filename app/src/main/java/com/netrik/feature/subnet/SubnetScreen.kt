package com.netrik.feature.subnet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.netrik.R
import com.netrik.core.designsystem.component.NetrikTopAppBar
import com.netrik.core.designsystem.component.RecentTargets
import com.netrik.core.designsystem.component.ToolEmptyState
import com.netrik.core.designsystem.theme.NetrikTheme
import com.netrik.core.subnet.IpVersion
import com.netrik.core.subnet.SubnetCalculator
import com.netrik.core.subnet.SubnetInfo
import com.netrik.core.ui.ClearHistoryMenu
import com.netrik.core.ui.CopyAction
import com.netrik.core.ui.rememberCopyAction

/** Subnet calculator (IPv4 and IPv6): calculate, split (equal parts or VLSM) and aggregate. Works offline. */
@Composable
fun SubnetScreen(onBack: () -> Unit, viewModel: SubnetViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val copy = rememberCopyAction()
    Scaffold(
        topBar = {
            NetrikTopAppBar(
                title = stringResource(R.string.tool_subnet),
                onBack = onBack,
                actions = { if (state.tab == SubnetTab.Calculate && state.recents.isNotEmpty()) ClearHistoryMenu(onClear = viewModel::clearHistory) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "tabs") { Tabs(state.tab, viewModel::selectTab) }
            when (state.tab) {
                SubnetTab.Calculate -> calculateTab(state, viewModel, copy)
                SubnetTab.Split -> splitTab(state, viewModel, copy)
                SubnetTab.Aggregate -> aggregateTab(state, viewModel, copy)
            }
        }
    }
}

@Composable
private fun Tabs(selected: SubnetTab, onSelect: (SubnetTab) -> Unit) {
    val tabs = listOf(
        SubnetTab.Calculate to R.string.subnet_tab_calculate,
        SubnetTab.Split to R.string.subnet_tab_split,
        SubnetTab.Aggregate to R.string.subnet_tab_aggregate,
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        tabs.forEachIndexed { index, (tab, label) ->
            SegmentedButton(
                selected = selected == tab,
                onClick = { onSelect(tab) },
                shape = SegmentedButtonDefaults.itemShape(index, tabs.size),
            ) { Text(stringResource(label), maxLines = 1) }
        }
    }
}

private fun LazyListScope.calculateTab(state: SubnetUiState, viewModel: SubnetViewModel, copy: CopyAction) {
    item(key = "input") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AddressField(state, viewModel)
            RecentTargets(targets = state.recents, enabled = true, onSelect = viewModel::useRecent)
        }
    }
    when (val calc = state.calc) {
        null -> item(key = "empty") { ToolEmptyState(icon = R.drawable.ic_calculate, text = stringResource(R.string.subnet_empty)) }
        is CalcUi.Error -> Unit
        is CalcUi.Result -> {
            item(key = "prefix") { PrefixStepper(calc, viewModel::changePrefix) }
            item(key = "network") { NetworkSection(calc.info, copy) }
            item(key = "address") { AddressSection(calc.info, copy) }
            if (calc.info.version == IpVersion.V4) item(key = "binary") { BinarySection(calc.info, copy) }
        }
    }
}

@Composable
private fun AddressField(state: SubnetUiState, viewModel: SubnetViewModel) {
    val mono = NetrikTheme.dataTypography.dataMedium.copy(fontSize = 16.sp)
    val error = (state.calc as? CalcUi.Error)?.let { inputErrorText(it.error) }
    val bareHost = (state.calc as? CalcUi.Result)?.takeIf { !it.prefixGiven }
    OutlinedTextField(
        value = state.input,
        onValueChange = viewModel::onInputChange,
        label = { Text(stringResource(R.string.subnet_input_label)) },
        placeholder = { Text("192.168.1.10/24", style = mono) },
        leadingIcon = { Icon(painterResource(R.drawable.ic_lan), contentDescription = null) },
        trailingIcon = if (state.input.isNotEmpty()) {
            {
                IconButton(onClick = { viewModel.onInputChange("") }) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.oui_action_clear))
                }
            }
        } else {
            null
        },
        supportingText = {
            Text(
                error ?: if (bareHost != null) {
                    stringResource(R.string.subnet_input_host, bareHost.info.version.bits)
                } else {
                    stringResource(R.string.subnet_input_hint)
                },
            )
        },
        isError = error != null,
        singleLine = true,
        textStyle = mono,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Uri,
            autoCorrectEnabled = false,
            capitalization = KeyboardCapitalization.None,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { viewModel.submit() }),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** "/26 · 255.255.255.192" with − and + to explore neighboring prefixes. */
@Composable
private fun PrefixStepper(calc: CalcUi.Result, onChange: (Int) -> Unit) {
    val info = calc.info
    val prefix = info.cidr.prefix
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = { onChange(-1) }, enabled = prefix > 0) {
                Icon(painterResource(R.drawable.ic_remove), contentDescription = stringResource(R.string.subnet_prefix_shorter))
            }
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("/$prefix", style = NetrikTheme.dataTypography.dataLarge)
                Text(
                    info.netmask?.format() ?: stringResource(R.string.subnet_prefix_addresses, formatSizeShort(info.totalAddresses)),
                    style = NetrikTheme.dataTypography.dataSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalIconButton(onClick = { onChange(1) }, enabled = prefix < info.version.bits) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.subnet_prefix_longer))
            }
        }
    }
}

@Composable
private fun NetworkSection(info: SubnetInfo, copy: CopyAction) {
    val v4 = info.version == IpVersion.V4
    val rows = buildList {
        add(ResultRow(stringResource(R.string.subnet_field_network), info.cidr.toString()))
        info.netmask?.let { add(ResultRow(stringResource(R.string.subnet_field_netmask), it.format())) }
        info.wildcard?.let { add(ResultRow(stringResource(R.string.subnet_field_wildcard), it.format())) }
        if (v4) {
            add(
                info.broadcast?.let { ResultRow(stringResource(R.string.subnet_field_broadcast), it.format()) }
                    ?: ResultRow(stringResource(R.string.subnet_field_broadcast), stringResource(R.string.subnet_no_broadcast), mono = false, copyValue = null),
            )
        }
        add(ResultRow(stringResource(if (v4) R.string.subnet_field_first_host else R.string.subnet_field_first_address), info.firstHost.format()))
        add(ResultRow(stringResource(if (v4) R.string.subnet_field_last_host else R.string.subnet_field_last_address), info.lastHost.format()))
        if (v4) add(ResultRow(stringResource(R.string.subnet_field_usable), formatCount(info.usableHosts)))
        add(ResultRow(stringResource(R.string.subnet_field_total), formatSize(info.totalAddresses)))
        info.subnets64?.let { add(ResultRow(stringResource(R.string.subnet_field_subnets64), formatSize(it))) }
    }
    val title = stringResource(if (v4) R.string.subnet_section_network_v4 else R.string.subnet_section_network_v6)
    ResultSection(title, rows, copy, copyAllText = rows.filter { it.copyValue != null }.joinToString("\n") { "${it.label}: ${it.value}" })
}

@Composable
private fun AddressSection(info: SubnetInfo, copy: CopyAction) {
    val rows = buildList {
        add(ResultRow(stringResource(R.string.subnet_field_address), info.address.format()))
        if (info.version == IpVersion.V6) add(ResultRow(stringResource(R.string.subnet_field_expanded), info.address.expanded()))
        add(ResultRow(stringResource(R.string.subnet_field_type), addressTypeText(info.type), mono = false))
        info.ipv4Class?.let { add(ResultRow(stringResource(R.string.subnet_field_class), stringResource(R.string.subnet_class, it.toString()), mono = false, copyValue = it.toString())) }
        add(ResultRow(stringResource(R.string.subnet_field_reverse), info.reverseZone, sub = stringResource(R.string.subnet_field_reverse_sub)))
        add(ResultRow(stringResource(R.string.subnet_field_hex), SubnetCalculator.hex(info.address)))
        if (info.version == IpVersion.V4) add(ResultRow(stringResource(R.string.subnet_field_decimal), info.address.value.toString()))
    }
    ResultSection(stringResource(R.string.subnet_section_address), rows, copy)
}

@Composable
private fun BinarySection(info: SubnetInfo, copy: CopyAction) {
    val rows = buildList {
        add(ResultRow(stringResource(R.string.subnet_field_address), SubnetCalculator.binary(info.address)))
        info.netmask?.let { add(ResultRow(stringResource(R.string.subnet_field_netmask), SubnetCalculator.binary(it))) }
        add(ResultRow(stringResource(R.string.subnet_field_network), SubnetCalculator.binary(info.cidr.network)))
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ResultSection(stringResource(R.string.subnet_section_binary), rows, copy)
        Text(
            stringResource(R.string.subnet_binary_note, info.cidr.prefix),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

