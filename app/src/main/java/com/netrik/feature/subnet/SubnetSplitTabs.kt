package com.netrik.feature.subnet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.netrik.R
import com.netrik.core.designsystem.component.ToolEmptyState
import com.netrik.core.designsystem.theme.NetrikTheme
import com.netrik.core.subnet.AggregateSummary
import com.netrik.core.subnet.Cidr
import com.netrik.core.subnet.IpAddress
import com.netrik.core.subnet.IpVersion
import com.netrik.core.subnet.SplitError
import com.netrik.core.subnet.SplitResult
import com.netrik.core.subnet.SubnetSplitter
import com.netrik.core.subnet.VlsmResult
import com.netrik.core.ui.CopyAction
import java.math.BigInteger

// Split

internal fun LazyListScope.splitTab(state: SubnetUiState, viewModel: SubnetViewModel, copy: CopyAction) {
    item(key = "split-input") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            NetworkField(state, viewModel)
            ModeSelector(state.splitMode, viewModel::onSplitModeChange)
            if (state.splitMode == SplitMode.Equal) EqualControls(state, viewModel) else VlsmControls(state, viewModel)
        }
    }
    when (val split = state.split) {
        null, is SplitUi.NetworkError -> item(key = "split-empty") {
            ToolEmptyState(
                icon = R.drawable.ic_call_split,
                text = stringResource(if (state.splitMode == SplitMode.Equal) R.string.subnet_split_empty else R.string.subnet_vlsm_empty),
            )
        }
        is SplitUi.Equal -> when (val result = split.result) {
            is SplitResult.Error -> item(key = "split-error") { Notice(splitErrorText(result.error), error = true) }
            is SplitResult.Ok -> item(key = "split-result") { EqualResult(result, copy) }
        }
        is SplitUi.Vlsm -> when {
            split.invalidEntry != null -> item(key = "vlsm-error") { Notice(stringResource(R.string.subnet_vlsm_invalid, split.invalidEntry), error = true) }
            split.result != null -> item(key = "vlsm-result") { VlsmResultView(split.result, copy) }
        }
    }
}

@Composable
private fun NetworkField(state: SubnetUiState, viewModel: SubnetViewModel) {
    val mono = NetrikTheme.dataTypography.dataMedium.copy(fontSize = 16.sp)
    val error = (state.split as? SplitUi.NetworkError)?.let { inputErrorText(it.error) }
    OutlinedTextField(
        value = state.splitNetwork,
        onValueChange = viewModel::onSplitNetworkChange,
        label = { Text(stringResource(R.string.subnet_split_network)) },
        placeholder = { Text("192.168.0.0/24", style = mono) },
        leadingIcon = { Icon(painterResource(R.drawable.ic_lan), contentDescription = null) },
        supportingText = error?.let { { Text(it) } },
        isError = error != null,
        singleLine = true,
        textStyle = mono,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ModeSelector(selected: SplitMode, onSelect: (SplitMode) -> Unit) {
    val modes = listOf(SplitMode.Equal to R.string.subnet_mode_equal, SplitMode.Vlsm to R.string.subnet_mode_vlsm)
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        modes.forEachIndexed { index, (mode, label) ->
            SegmentedButton(selected = selected == mode, onClick = { onSelect(mode) }, shape = SegmentedButtonDefaults.itemShape(index, modes.size)) {
                Text(stringResource(label))
            }
        }
    }
}

@Composable
private fun EqualControls(state: SubnetUiState, viewModel: SubnetViewModel) {
    val kinds = listOf(
        SplitKind.Prefix to R.string.subnet_split_by_prefix,
        SplitKind.Count to R.string.subnet_split_by_count,
        SplitKind.Hosts to R.string.subnet_split_by_hosts,
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            kinds.forEachIndexed { index, (kind, label) ->
                SegmentedButton(selected = state.splitKind == kind, onClick = { viewModel.onSplitKindChange(kind) }, shape = SegmentedButtonDefaults.itemShape(index, kinds.size)) {
                    Text(stringResource(label), maxLines = 1)
                }
            }
        }
        OutlinedTextField(
            value = state.splitValue,
            onValueChange = viewModel::onSplitValueChange,
            label = {
                Text(
                    stringResource(
                        when (state.splitKind) {
                            SplitKind.Prefix -> R.string.subnet_split_value_prefix
                            SplitKind.Count -> R.string.subnet_split_value_count
                            SplitKind.Hosts -> R.string.subnet_split_value_hosts
                        },
                    ),
                )
            },
            prefix = if (state.splitKind == SplitKind.Prefix) {
                { Text("/", style = NetrikTheme.dataTypography.dataMedium.copy(fontSize = 16.sp)) }
            } else {
                null
            },
            singleLine = true,
            textStyle = NetrikTheme.dataTypography.dataMedium.copy(fontSize = 16.sp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun VlsmControls(state: SubnetUiState, viewModel: SubnetViewModel) {
    MonoTextArea(
        value = state.vlsmText,
        onValueChange = viewModel::onVlsmTextChange,
        label = stringResource(R.string.subnet_vlsm_label),
        placeholder = "LAN A: 100\nLAN B: 50\nWi-Fi: 20\nLink: 2",
        supporting = stringResource(R.string.subnet_vlsm_hint),
    )
}

@Composable
private fun EqualResult(result: SplitResult.Ok, copy: CopyAction) {
    val v4 = result.parent.version == IpVersion.V4
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Notice(
            stringResource(
                if (v4) R.string.subnet_split_summary_v4 else R.string.subnet_split_summary_v6,
                formatCount(result.count),
                result.prefix,
                if (v4) formatCount(result.usablePerSubnet) else formatSizeShort(result.usablePerSubnet),
            ),
        )
        if (result.count > BigInteger.valueOf(result.subnets.size.toLong())) {
            Notice(stringResource(R.string.subnet_split_truncated, result.subnets.size, formatCount(result.count)))
        }
        val rows = result.subnets.mapIndexed { index, cidr ->
            ResultRow(
                label = stringResource(R.string.subnet_split_item, index + 1),
                value = cidr.toString(),
                sub = blockRange(cidr),
                copyValue = cidr.toString(),
            )
        }
        ResultSection(stringResource(R.string.subnet_section_subnets), rows, copy, copyAllText = result.subnets.joinToString("\n"))
    }
}

@Composable
private fun VlsmResultView(result: VlsmResult, copy: CopyAction) {
    val failed = result.allocations.count { it.cidr == null }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Notice(stringResource(R.string.subnet_vlsm_summary, formatCount(result.used), formatCount(result.parent.size), formatCount(result.free)))
        if (failed > 0) Notice(pluralStringResource(R.plurals.subnet_vlsm_failed, failed, failed), error = true)
        val rows = result.allocations.mapIndexed { index, allocation ->
            val name = allocation.request.label ?: stringResource(R.string.subnet_vlsm_item, index + 1)
            val needs = pluralStringResource(R.plurals.subnet_vlsm_needs, allocation.request.hosts.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), formatCount(BigInteger.valueOf(allocation.request.hosts)))
            val cidr = allocation.cidr
            if (cidr == null) {
                ResultRow(label = "$name · $needs", value = stringResource(R.string.subnet_vlsm_no_fit), mono = false, copyValue = null, tint = MaterialTheme.colorScheme.error)
            } else {
                val usable = SubnetSplitter.usableHosts(cidr.version, cidr.prefix)
                ResultRow(
                    label = "$name · $needs",
                    value = cidr.toString(),
                    sub = blockRange(cidr) + " · " + pluralStringResource(R.plurals.subnet_vlsm_usable, usable.coerceAtMost(BigInteger.valueOf(Int.MAX_VALUE.toLong())).toInt(), formatCount(usable)),
                )
            }
        }
        val copyAll = result.allocations.mapIndexedNotNull { index, allocation ->
            allocation.cidr?.let { "${allocation.request.label ?: "#${index + 1}"}: $it" }
        }.joinToString("\n")
        ResultSection(stringResource(R.string.subnet_section_plan), rows, copy, copyAllText = copyAll.ifEmpty { null })
    }
}

/** Usable range of a block: "192.168.0.1 – 192.168.0.62 · bcast 192.168.0.63" (IPv4) or first – last (IPv6). */
@Composable
private fun blockRange(cidr: Cidr): String {
    if (cidr.version == IpVersion.V6 || cidr.prefix >= 31) return "${cidr.first} – ${cidr.last}"
    val first = IpAddress(cidr.version, cidr.first.value + BigInteger.ONE)
    val last = IpAddress(cidr.version, cidr.last.value - BigInteger.ONE)
    return stringResource(R.string.subnet_range_with_broadcast, first.format(), last.format(), cidr.last.format())
}

@Composable
private fun splitErrorText(error: SplitError): String = stringResource(
    when (error) {
        SplitError.PrefixTooShort -> R.string.subnet_split_error_short
        SplitError.PrefixTooLong -> R.string.subnet_split_error_long
        SplitError.NotPositive -> R.string.subnet_split_error_zero
        SplitError.DoesNotFit -> R.string.subnet_split_error_fit
    },
)

// Aggregate

internal fun LazyListScope.aggregateTab(state: SubnetUiState, viewModel: SubnetViewModel, copy: CopyAction) {
    item(key = "agg-input") {
        MonoTextArea(
            value = state.aggregateText,
            onValueChange = viewModel::onAggregateTextChange,
            label = stringResource(R.string.subnet_aggregate_label),
            placeholder = "10.0.0.0/25\n10.0.0.128/25\n10.0.1.0/24\n10.0.2.5-10.0.2.20",
            supporting = stringResource(R.string.subnet_aggregate_hint),
        )
    }
    val result = state.aggregate
    if (result == null) {
        item(key = "agg-empty") { ToolEmptyState(icon = R.drawable.ic_call_merge, text = stringResource(R.string.subnet_aggregate_empty)) }
        return
    }
    if (result.invalidLines.isNotEmpty()) {
        item(key = "agg-invalid") {
            Notice(
                pluralStringResource(R.plurals.subnet_aggregate_invalid, result.invalidLines.size, result.invalidLines.joinToString(", ")),
                error = true,
            )
        }
    }
    if (result.normalized.isNotEmpty()) {
        item(key = "agg-normalized") {
            Notice(result.normalized.map { stringResource(R.string.subnet_aggregate_normalized, it.line, it.typed, it.cidr.toString()) }.joinToString("\n"))
        }
    }
    result.v4?.let { summary -> item(key = "agg-v4") { SummaryView(summary, R.string.subnet_section_summary_v4, copy) } }
    result.v6?.let { summary -> item(key = "agg-v6") { SummaryView(summary, R.string.subnet_section_summary_v6, copy) } }
}

@Composable
private fun SummaryView(summary: AggregateSummary, title: Int, copy: CopyAction) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val rows = summary.cidrs.map { ResultRow(label = blockRangeLabel(it), value = it.toString()) }
        ResultSection(stringResource(title), rows, copy, copyAllText = summary.cidrs.joinToString("\n"))
        val supernet = ResultRow(
            label = stringResource(R.string.subnet_field_supernet),
            value = summary.supernet.toString(),
            mono = true,
            sub = if (summary.extra.signum() == 0) {
                stringResource(R.string.subnet_supernet_exact)
            } else {
                stringResource(R.string.subnet_supernet_extra, formatCount(summary.extra))
            },
        )
        ResultSection(stringResource(R.string.subnet_section_supernet), listOf(supernet), copy)
    }
}

@Composable
private fun blockRangeLabel(cidr: Cidr): String =
    pluralStringResource(R.plurals.subnet_addresses, cidr.size.coerceAtMost(BigInteger.valueOf(Int.MAX_VALUE.toLong())).toInt(), formatSize(cidr.size))

@Composable
private fun Notice(text: String, error: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (error) colors.errorContainer else colors.surfaceContainerHigh,
        contentColor = if (error) colors.onErrorContainer else colors.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Icon(painterResource(if (error) R.drawable.ic_error_filled else R.drawable.ic_info), contentDescription = null, modifier = Modifier.size(20.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = Color.Unspecified)
        }
    }
}
