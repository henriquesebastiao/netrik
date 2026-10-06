package com.netrik.feature.devices

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.netrik.R
import com.netrik.core.designsystem.component.NetrikTopAppBar
import com.netrik.core.designsystem.component.PlaceholderContent
import com.netrik.core.designsystem.component.SectionHeader
import com.netrik.core.designsystem.component.StatusChip
import com.netrik.core.designsystem.component.StatusChipSize
import com.netrik.core.designsystem.component.StatusTone
import com.netrik.core.designsystem.component.groupedItemShape
import com.netrik.core.designsystem.theme.NetrikTheme
import com.netrik.core.lan.Detection
import com.netrik.core.lan.DeviceKind
import com.netrik.core.lan.KindEvidence
import com.netrik.core.lan.InfoSource
import com.netrik.core.lan.LanDevice
import com.netrik.core.oui.MacAddresses
import com.netrik.core.ui.rememberCopyAction
import com.netrik.feature.ping.ms
import com.netrik.navigation.NetrikTool

@Composable
fun DeviceDetailScreen(
    ip: String,
    viewModel: DevicesViewModel,
    onBack: () -> Unit,
    onAction: (NetrikTool, String) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val device = state.devices[ip]
    Scaffold(
        topBar = { NetrikTopAppBar(title = device?.hostname?.value ?: ip, onBack = onBack) },
    ) { padding ->
        if (device == null) {
            PlaceholderContent(R.drawable.ic_devices_other, ip, stringResource(R.string.device_not_found), Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item(key = "header") { Header(device) }
            item(key = "actions") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader(stringResource(R.string.device_quick_actions))
                    val actions = listOf(
                        Action(NetrikTool.Ping, R.drawable.ic_network_ping, stringResource(R.string.tool_ping), ip),
                        Action(NetrikTool.Traceroute, R.drawable.ic_route, stringResource(R.string.tool_traceroute), ip),
                        Action(NetrikTool.PortScanner, R.drawable.ic_radar, stringResource(R.string.device_action_port_scan), stringResource(R.string.device_port_scan_sub, ip)),
                        Action(NetrikTool.Ssh, R.drawable.ic_terminal, stringResource(R.string.device_action_ssh), stringResource(R.string.device_ssh_sub, ip)),
                    )
                    actions.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { ActionTile(it, Modifier.weight(1f)) { onAction(it.tool, ip) } }
                        }
                    }
                }
            }
            item(key = "info") { InfoSection(device, state) }
        }
    }
}

private data class Action(val tool: NetrikTool, @param:DrawableRes val icon: Int, val name: String, val sub: String)

@Composable
private fun Header(device: LanDevice) {
    val (icon, bg, fg) = deviceIcon(device)
    Row(modifier = Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(shape = RoundedCornerShape(20.dp), color = bg, contentColor = fg, modifier = Modifier.size(64.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(32.dp)) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(device.ip, style = NetrikTheme.dataTypography.dataLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val online = stringResource(R.string.device_online)
                StatusChip(device.rttMs?.let { "$online · ${rtt(it)}" } ?: online, StatusTone.Success, size = StatusChipSize.Small)
                badge(device)?.let { DeviceBadge(it) }
            }
        }
    }
}

@Composable
private fun ActionTile(action: Action, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier) {
        Row(modifier = Modifier.heightIn(min = 72.dp).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(painterResource(action.icon), contentDescription = null) }
            }
            Column {
                Text(action.name, style = MaterialTheme.typography.titleSmall)
                Text(action.sub, style = NetrikTheme.dataTypography.dataSmall.copy(fontSize = 11.5.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private data class InfoRow(
    val label: String,
    val value: String,
    val sub: String?,
    val mono: Boolean,
    val copied: String?,
    val muted: Boolean = false,
    val copyValue: String = value,
)

@Composable
private fun InfoSection(device: LanDevice, state: DevicesUiState) {
    val copy = rememberCopyAction()
    val rows = buildList {
        add(InfoRow(stringResource(R.string.device_field_ip), device.ip, null, true, stringResource(R.string.devices_ip_copied)))
        val mac = device.mac
        if (mac != null) {
            add(InfoRow(stringResource(R.string.device_field_mac), mac.value, sourceLabel(mac.source), true, stringResource(R.string.devices_mac_copied)))
        } else if (device.isSelf) {
            add(InfoRow(stringResource(R.string.device_field_mac), stringResource(R.string.devices_mac_private), stringResource(R.string.device_mac_self_sub), false, null, muted = true))
        } else {
            add(InfoRow(stringResource(R.string.device_field_mac), stringResource(R.string.devices_mac_unavailable), stringResource(R.string.device_mac_unavailable_sub), false, null, muted = true))
        }
        val vendor = device.vendor
        if (vendor != null) {
            val sub = if (vendor.source == InfoSource.Oui && mac != null) {
                stringResource(R.string.device_source_oui, MacAddresses.format(mac.value.replace(":", "").take(6)))
            } else {
                sourceLabel(vendor.source)
            }
            add(InfoRow(stringResource(R.string.device_field_vendor), vendor.value, sub, false, stringResource(R.string.device_vendor_copied)))
        } else {
            add(InfoRow(stringResource(R.string.device_field_vendor), stringResource(R.string.devices_vendor_unknown), stringResource(R.string.device_vendor_unknown_sub), false, null, muted = true))
        }
        val host = device.hostname
        if (host != null) {
            add(InfoRow(stringResource(R.string.device_field_hostname), host.value, sourceLabel(host.source), true, stringResource(R.string.device_hostname_copied)))
        } else {
            add(InfoRow(stringResource(R.string.device_field_hostname), stringResource(R.string.device_hostname_unresolved), null, false, null, muted = true))
        }
        if (!device.isSelf && !device.isGateway) {
            device.kind?.let { guess ->
                add(InfoRow(stringResource(R.string.device_field_kind), kindLabel(guess.kind), evidenceText(guess.evidence, state.serviceNames), false, null))
            }
        }
        if (state.checkingPorts && !device.isSelf) {
            val open = device.openPorts
            val sub = stringResource(R.string.device_open_ports_sub)
            when {
                open == null && state.running -> add(InfoRow(stringResource(R.string.device_field_open_ports), stringResource(R.string.device_open_ports_checking), sub, false, null, muted = true))
                open == null -> add(InfoRow(stringResource(R.string.device_field_open_ports), stringResource(R.string.device_open_ports_not_checked), null, false, null, muted = true))
                open.isEmpty() -> add(InfoRow(stringResource(R.string.device_field_open_ports), stringResource(R.string.device_open_ports_none), sub, false, null, muted = true))
                else -> {
                    val entries = open.sorted().map { port -> state.serviceNames[port]?.let { "$port $it" } ?: port.toString() }
                    // No-break space inside each entry: lines only break between "8081 sunproxyadmin" and the next.
                    add(
                        InfoRow(
                            stringResource(R.string.device_field_open_ports),
                            entries.joinToString(" · ") { it.replace(' ', '\u00A0') },
                            sub,
                            true,
                            stringResource(R.string.device_open_ports_copied),
                            copyValue = entries.joinToString(", "),
                        ),
                    )
                }
            }
        }
        device.model?.let { add(InfoRow(stringResource(R.string.device_field_model), it, null, false, null)) }
        if (device.services.isNotEmpty()) {
            add(InfoRow(stringResource(R.string.device_field_services), device.services.sorted().joinToString(", "), null, true, null))
        }
        add(InfoRow(stringResource(R.string.device_field_detection), detectionLabel(device.detection), null, false, null))
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(stringResource(R.string.device_info))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            rows.forEachIndexed { i, row ->
                val content: @Composable () -> Unit = {
                    Row(modifier = Modifier.heightIn(min = 64.dp).padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(row.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                row.value,
                                style = if (row.mono) NetrikTheme.dataTypography.dataMedium.copy(fontSize = 15.sp) else MaterialTheme.typography.bodyLarge,
                                color = if (row.muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            )
                            row.sub?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        if (row.copied != null) {
                            Icon(painterResource(R.drawable.ic_content_copy), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        }
                    }
                }
                val shape = groupedItemShape(i, rows.size)
                if (row.copied != null) {
                    Surface(onClick = { copy.copy(row.copyValue, row.copied) }, shape = shape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) { content() }
                } else {
                    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) { content() }
                }
            }
        }
    }
}

@Composable
private fun kindLabel(kind: DeviceKind): String = stringResource(
    when (kind) {
        DeviceKind.Camera -> R.string.device_kind_camera
        DeviceKind.Printer -> R.string.device_kind_printer
        DeviceKind.Speaker -> R.string.device_kind_speaker
        DeviceKind.Tv -> R.string.device_kind_tv
        DeviceKind.Cast -> R.string.device_kind_cast
        DeviceKind.Iphone -> R.string.device_kind_iphone
        DeviceKind.Router -> R.string.device_kind_router
        DeviceKind.VoipPhone -> R.string.device_kind_voip
        DeviceKind.Nas -> R.string.device_kind_nas
        DeviceKind.SmartHome -> R.string.device_kind_smart_home
        DeviceKind.Computer -> R.string.device_kind_computer
    },
)

/** Says what the guess is based on, so the icon isn't taken as something the device declared. */
@Composable
private fun evidenceText(evidence: KindEvidence, serviceNames: Map<Int, String>): String = when (evidence) {
    is KindEvidence.Port -> serviceNames[evidence.port]?.let { stringResource(R.string.device_kind_by_port, evidence.port, it) }
        ?: stringResource(R.string.device_kind_by_port_unnamed, evidence.port)
    is KindEvidence.Service -> stringResource(R.string.device_kind_by_service, evidence.type)
}

@Composable
private fun sourceLabel(source: InfoSource): String = stringResource(
    when (source) {
        InfoSource.Dns -> R.string.device_source_dns
        InfoSource.Mdns -> R.string.device_source_mdns
        InfoSource.Netbios -> R.string.device_source_netbios
        InfoSource.Upnp -> R.string.device_source_upnp
        InfoSource.Oui -> R.string.device_source_oui_generic
        InfoSource.Mndp -> R.string.device_source_mndp
        InfoSource.Ubiquiti -> R.string.device_source_ubiquiti
    },
)

@Composable
private fun detectionLabel(detection: Detection): String = when (detection) {
    Detection.Icmp -> stringResource(R.string.device_detection_icmp)
    is Detection.Tcp -> stringResource(R.string.device_detection_tcp, detection.port)
    Detection.Mdns -> stringResource(R.string.device_detection_mdns)
    Detection.Ssdp -> stringResource(R.string.device_detection_ssdp)
    Detection.Netbios -> stringResource(R.string.device_detection_netbios)
    Detection.Mndp -> stringResource(R.string.device_detection_mndp)
    Detection.Ubiquiti -> stringResource(R.string.device_detection_ubiquiti)
}

/** Below 1 ms the RTT becomes "<1 ms", as in the design. */
private fun rtt(value: Double): String = if (value < 1) "<1 ms" else ms(value).replace(".0 ms", " ms")

