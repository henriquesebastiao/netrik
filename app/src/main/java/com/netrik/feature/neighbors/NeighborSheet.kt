package com.netrik.feature.neighbors

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.netrik.R
import com.netrik.core.designsystem.component.IconAvatar
import com.netrik.core.designsystem.component.SectionHeader
import com.netrik.core.designsystem.component.StatusChip
import com.netrik.core.designsystem.component.StatusChipSize
import com.netrik.core.designsystem.component.StatusTone
import com.netrik.core.designsystem.component.groupedItemShape
import com.netrik.core.designsystem.theme.NetrikTheme
import com.netrik.core.neighbor.Neighbor
import com.netrik.core.neighbor.NeighborProtocol
import com.netrik.core.ui.rememberCopyAction
import com.netrik.navigation.NetrikTool

/** Everything a neighbor announced, each value copyable, plus quick actions on its address. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NeighborSheet(neighbor: Neighbor, nowMillis: Long, onDismiss: () -> Unit, onAction: (NetrikTool, String) -> Unit) {
    val uri = LocalUriHandler.current
    val address = neighbor.address
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(modifier = Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                IconAvatar(icon = R.drawable.ic_router)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(neighbor.title(), style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        StatusChip(
                            stringResource(if (neighbor.protocol == NeighborProtocol.Mndp) R.string.neighbors_protocol_mndp else R.string.neighbors_protocol_ubiquiti),
                            StatusTone.Neutral,
                            icon = R.drawable.ic_lan,
                            size = StatusChipSize.Small,
                        )
                        Text(seenText(neighbor.lastSeenMillis, nowMillis), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(stringResource(R.string.device_quick_actions))
                val actions = buildList {
                    add(SheetAction(R.drawable.ic_open_in_new, stringResource(R.string.neighbors_action_browser), "http://$address") { uri.openUri("http://${urlHost(address)}") })
                    add(SheetAction(R.drawable.ic_terminal, stringResource(R.string.device_action_ssh), address) { onAction(NetrikTool.Ssh, address) })
                    add(SheetAction(R.drawable.ic_network_ping, stringResource(R.string.tool_ping), address) { onAction(NetrikTool.Ping, address) })
                    add(SheetAction(R.drawable.ic_radar, stringResource(R.string.device_action_port_scan), address) { onAction(NetrikTool.PortScanner, address) })
                    neighbor.mac?.let { mac -> add(SheetAction(R.drawable.ic_manage_search, stringResource(R.string.tool_oui), mac) { onAction(NetrikTool.Oui, mac) }) }
                }
                actions.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { ActionTile(it, Modifier.weight(1f)) }
                        if (row.size == 1) Box(Modifier.weight(1f))
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(stringResource(R.string.neighbors_announced))
                InfoRows(rows(neighbor))
            }
        }
    }
}

private class SheetAction(@param:DrawableRes val icon: Int, val name: String, val sub: String, val onClick: () -> Unit)

/** IPv6 addresses go in brackets in a URL. */
private fun urlHost(address: String) = if (':' in address) "[$address]" else address

@Composable
private fun ActionTile(action: SheetAction, modifier: Modifier) {
    Surface(onClick = action.onClick, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier) {
        Row(modifier = Modifier.heightIn(min = 64.dp).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) { Icon(painterResource(action.icon), contentDescription = null) }
            }
            Column {
                Text(action.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    action.sub,
                    style = NetrikTheme.dataTypography.dataSmall.copy(fontSize = 11.5.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private data class InfoLine(val label: String, val value: String, val mono: Boolean = true)

@Composable
private fun rows(neighbor: Neighbor): List<InfoLine> = buildList {
    val mikrotik = neighbor.protocol == NeighborProtocol.Mndp
    neighbor.identity?.let { add(InfoLine(stringResource(if (mikrotik) R.string.neighbors_field_identity else R.string.device_field_hostname), it)) }
    add(InfoLine(stringResource(R.string.neighbors_field_ipv4), neighbor.ipv4 ?: neighbor.sourceIp))
    if (neighbor.ipv4 != null && neighbor.ipv4 != neighbor.sourceIp) add(InfoLine(stringResource(R.string.neighbors_field_source), neighbor.sourceIp))
    neighbor.ipv6?.let { add(InfoLine(stringResource(R.string.neighbors_field_ipv6), it)) }
    neighbor.mac?.let { add(InfoLine(stringResource(R.string.device_field_mac), it)) }
    neighbor.model?.let { add(InfoLine(stringResource(if (mikrotik) R.string.neighbors_field_board else R.string.device_field_model), it, mono = false)) }
    neighbor.version?.let { add(InfoLine(stringResource(if (mikrotik) R.string.neighbors_field_routeros else R.string.neighbors_field_version), it)) }
    neighbor.firmware?.let { add(InfoLine(stringResource(R.string.neighbors_field_firmware), it)) }
    neighbor.platform?.let { add(InfoLine(stringResource(R.string.neighbors_field_platform), it, mono = false)) }
    neighbor.softwareId?.let { add(InfoLine(stringResource(R.string.neighbors_field_software_id), it)) }
    neighbor.interfaceName?.let { add(InfoLine(stringResource(R.string.neighbors_field_interface), it)) }
    neighbor.essid?.let { add(InfoLine(stringResource(R.string.neighbors_field_essid), it)) }
    neighbor.uptimeSeconds?.let { add(InfoLine(stringResource(R.string.neighbors_field_uptime), uptimeText(it))) }
}

@Composable
private fun uptimeText(seconds: Long): String = stringResource(
    R.string.neighbors_uptime,
    (seconds / 86_400).toInt(),
    ((seconds / 3_600) % 24).toInt(),
    ((seconds / 60) % 60).toInt(),
    (seconds % 60).toInt(),
)

@Composable
private fun InfoRows(rows: List<InfoLine>) {
    val copy = rememberCopyAction()
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        rows.forEachIndexed { index, row ->
            val copied = stringResource(R.string.copied_value, row.label)
            Surface(
                onClick = { copy.copy(row.value, copied) },
                shape = groupedItemShape(index, rows.size),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(modifier = Modifier.heightIn(min = 56.dp).padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(row.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            row.value,
                            style = if (row.mono) NetrikTheme.dataTypography.dataMedium.copy(fontSize = 15.sp) else MaterialTheme.typography.bodyLarge,
                        )
                    }
                    Icon(painterResource(R.drawable.ic_content_copy), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
