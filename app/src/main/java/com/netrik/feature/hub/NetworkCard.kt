package com.netrik.feature.hub

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.netrik.R
import com.netrik.core.designsystem.component.IconAvatar
import com.netrik.core.designsystem.component.StatusChip
import com.netrik.core.designsystem.component.StatusTone
import com.netrik.core.designsystem.theme.NetrikTheme
import com.netrik.core.network.CurrentNetwork.Transport
import com.netrik.core.ui.CopyAction
import com.netrik.core.ui.rememberCopyAction

/** Hub "Current network" card: the main addresses (tap to copy) and a "Network details" sheet with everything else. */
@Composable
fun NetworkCard(
    network: NetworkCardState,
    publicIp: PublicIpUi,
    onShowPublicIp: () -> Unit,
    modifier: Modifier = Modifier,
    onCalculateSubnet: (String) -> Unit = {},
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (network) {
                NetworkCardState.Loading -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                NetworkCardState.Disconnected -> DisconnectedContent()
                is NetworkCardState.Connected -> ConnectedContent(network, publicIp, onShowPublicIp, onCalculateSubnet)
            }
        }
    }
}

@Composable
private fun DisconnectedContent() {
    CardHeader(
        icon = R.drawable.ic_signal_disconnected,
        label = stringResource(R.string.network_current_none),
        title = stringResource(R.string.network_title_none),
        titleMuted = true,
        status = { StatusChip(stringResource(R.string.network_status_offline), StatusTone.Neutral) },
        avatarContainer = MaterialTheme.colorScheme.surfaceContainerHighest,
        avatarContent = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        text = stringResource(R.string.network_none_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ConnectedContent(network: NetworkCardState.Connected, publicIp: PublicIpUi, onShowPublicIp: () -> Unit, onCalculateSubnet: (String) -> Unit) {
    val copy = rememberCopyAction()
    val unavailable = stringResource(R.string.field_unavailable)

    CardHeader(
        icon = network.transport.icon,
        label = stringResource(network.transport.label),
        title = network.name ?: stringResource(network.transport.fallbackTitle),
        titleMuted = network.name == null,
        status = {
            if (network.hasInternet) {
                StatusChip(stringResource(R.string.network_status_connected), StatusTone.Success)
            } else {
                StatusChip(stringResource(R.string.network_status_no_internet), StatusTone.Warning)
            }
        },
    )
    if (network.ssidHidden) {
        Text(
            text = stringResource(R.string.network_ssid_requires_location),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    network.signal?.let { SignalLine(it) }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldTile(Field(stringResource(R.string.field_local_ip), network.localIp, stringResource(R.string.copied_local_ip)), unavailable, copy, Modifier.weight(1f))
        FieldTile(Field(stringResource(R.string.field_gateway), network.gateway, stringResource(R.string.copied_gateway)), unavailable, copy, Modifier.weight(1f))
    }
    PublicIpTile(publicIp, onShowPublicIp, copy)

    var showDetails by rememberSaveable { mutableStateOf(false) }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = { showDetails = true }) {
            Icon(painterResource(R.drawable.ic_info), contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.network_details), modifier = Modifier.padding(start = 8.dp))
        }
    }
    if (showDetails) {
        NetworkDetailsSheet(
            network = network,
            publicIp = publicIp,
            onShowPublicIp = onShowPublicIp,
            onCalculateSubnet = { cidr ->
                showDetails = false
                onCalculateSubnet(cidr)
            },
            onDismiss = { showDetails = false },
        )
    }
}

/** Every detail Netrik knows about the current network, each one copyable. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NetworkDetailsSheet(
    network: NetworkCardState.Connected,
    publicIp: PublicIpUi,
    onShowPublicIp: () -> Unit,
    onCalculateSubnet: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val copy = rememberCopyAction()
    val unavailable = stringResource(R.string.field_unavailable)
    val signal = network.signal
    val wifi = network.transport == Transport.Wifi
    val rows = buildList {
        if (wifi) add(stringResource(R.string.field_ssid) to network.name)
        if (network.transport == Transport.Cellular) add(stringResource(R.string.field_carrier) to network.name)
        add(stringResource(R.string.field_connection) to stringResource(network.transport.typeName))
        add(
            stringResource(R.string.field_internet) to stringResource(
                if (network.hasInternet) R.string.network_status_connected else R.string.network_status_no_internet,
            ),
        )
        if (wifi) {
            add(stringResource(R.string.field_signal) to signal?.rssiDbm?.let { stringResource(R.string.wifi_dbm, formatDbm(it)) })
            add(stringResource(R.string.field_frequency) to network.frequencyMhz?.let { "$it MHz" })
            add(stringResource(R.string.field_band) to signal?.band?.let { stringResource(R.string.wifi_band, it.label) })
            add(stringResource(R.string.field_channel) to signal?.channel?.toString())
            add(stringResource(R.string.field_channel_width) to signal?.widthMhz?.let { "$it MHz" })
            add(stringResource(R.string.field_bssid) to network.bssid)
        }
        add(stringResource(R.string.field_interface) to network.interfaceName)
        add(stringResource(R.string.field_local_ip) to network.localIp)
        add(stringResource(R.string.field_mask_cidr) to network.maskCidr)
        add(stringResource(R.string.field_gateway) to network.gateway)
        add(stringResource(R.string.field_dns) to network.dns)
        add(stringResource(R.string.field_ipv6) to network.ipv6)
    }
    val publicIpLabel = stringResource(R.string.field_public_ip)
    val copiedAll = stringResource(R.string.copied_network)
    val copiedOne = stringResource(R.string.copied_value)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.network_details), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(
                    onClick = {
                        val lines = rows.map { (label, value) -> "$label: ${value ?: unavailable}" } +
                            listOfNotNull((publicIp as? PublicIpUi.Loaded)?.let { "$publicIpLabel: ${it.ip}" })
                        copy.copy(lines.joinToString("\n"), copiedAll)
                    },
                ) {
                    Icon(painterResource(R.drawable.ic_copy_all), contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.action_copy_all), modifier = Modifier.padding(start = 8.dp))
                }
            }
            rows.forEach { (label, value) -> FieldTile(Field(label, value, copiedOne.format(label)), unavailable, copy) }
            PublicIpTile(publicIp, onShowPublicIp, copy)
            network.localCidr?.let { cidr ->
                FilledTonalButton(onClick = { onCalculateSubnet(cidr) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Icon(painterResource(R.drawable.ic_calculate), contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.network_open_subnet), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun CardHeader(
    @DrawableRes icon: Int,
    label: String,
    title: String,
    titleMuted: Boolean,
    status: @Composable () -> Unit,
    avatarContainer: Color = MaterialTheme.colorScheme.primaryContainer,
    avatarContent: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IconAvatar(icon = icon, containerColor = avatarContainer, contentColor = avatarContent)
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (titleMuted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        status()
    }
}

/**
 * Signal, band, channel and width side by side, each with its label: one line of
 * "−54 dBm · 5 GHz · Channel 149 · 80 MHz" wrapped on a phone.
 */
@Composable
private fun SignalLine(signal: WifiSignal) {
    val rssi = signal.rssiDbm ?: return
    val items = listOfNotNull(
        stringResource(R.string.field_signal) to stringResource(R.string.wifi_dbm, formatDbm(rssi)),
        signal.band?.let { stringResource(R.string.field_band) to stringResource(R.string.wifi_band, it.label) },
        signal.channel?.let { stringResource(R.string.field_channel) to it.toString() },
        signal.widthMhz?.let { stringResource(R.string.network_signal_width) to "$it MHz" },
    )
    // Each column as wide as its content, spread over the card; on a very narrow screen whole columns wrap.
    FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEachIndexed { i, (label, value) ->
            Column(modifier = Modifier.padding(end = if (i < items.lastIndex) 12.dp else 0.dp)) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (i == 0) {
                        Icon(
                            painter = painterResource(signalIcon(rssi)),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Text(value, style = NetrikTheme.dataTypography.dataSmall.copy(fontSize = 13.sp), maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

private data class Field(val label: String, val value: String?, val copiedMessage: String)

@Composable
private fun FieldTile(field: Field, unavailable: String, copy: CopyAction, modifier: Modifier = Modifier) {
    val value = field.value
    Tile(
        label = field.label,
        showCopyIcon = value != null,
        onClick = value?.let { { copy.copy(it, field.copiedMessage) } },
        modifier = modifier,
    ) {
        Text(
            text = value ?: unavailable,
            style = if (value != null) NetrikTheme.dataTypography.dataMedium else MaterialTheme.typography.bodyMedium,
            color = if (value != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PublicIpTile(state: PublicIpUi, onShow: () -> Unit, copy: CopyAction) {
    val label = stringResource(R.string.field_public_ip)
    val copied = stringResource(R.string.copied_public_ip)
    when (state) {
        is PublicIpUi.Loaded -> Tile(label = label, showCopyIcon = true, onClick = { copy.copy(state.ip, copied) }) {
            Text(state.ip, style = NetrikTheme.dataTypography.dataMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        PublicIpUi.Loading -> Tile(label = label, showCopyIcon = false, onClick = null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(
                    stringResource(R.string.public_ip_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        PublicIpUi.Failed -> Tile(label = label, showCopyIcon = false, onClick = onShow) {
            Text(
                stringResource(R.string.public_ip_error),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        PublicIpUi.Hidden -> Tile(label = label, showCopyIcon = false, onClick = onShow) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        painterResource(R.drawable.ic_public),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        stringResource(R.string.public_ip_show),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    stringResource(R.string.public_ip_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Value block of the card: surface background, 8dp corners, 60dp minimum touch target. */
@Composable
private fun Tile(
    label: String,
    showCopyIcon: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    val color = MaterialTheme.colorScheme.surface
    val inner: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .heightIn(min = 60.dp)
                .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (showCopyIcon) {
                    Icon(
                        painterResource(R.drawable.ic_content_copy),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            content()
        }
    }
    if (onClick != null) {
        Surface(onClick = onClick, shape = shape, color = color, modifier = modifier.fillMaxWidth()) { inner() }
    } else {
        Surface(shape = shape, color = color, modifier = modifier.fillMaxWidth()) { inner() }
    }
}

/** Uses the typographic minus sign, as in the design (−54 dBm). */
private fun formatDbm(dbm: Int): String = dbm.toString().replace('-', '−')

@DrawableRes
private fun signalIcon(rssi: Int?): Int = when {
    rssi == null -> R.drawable.ic_network_wifi_3_bar
    rssi >= -60 -> R.drawable.ic_signal_wifi_4_bar
    rssi >= -70 -> R.drawable.ic_network_wifi_3_bar
    rssi >= -80 -> R.drawable.ic_network_wifi_2_bar
    else -> R.drawable.ic_network_wifi_1_bar
}

private val Transport.icon: Int
    get() = when (this) {
        Transport.Wifi -> R.drawable.ic_wifi_filled
        Transport.Cellular -> R.drawable.ic_signal_cellular_alt_filled
        Transport.Ethernet -> R.drawable.ic_lan
        Transport.Vpn, Transport.Other -> R.drawable.ic_public
    }

private val Transport.typeName: Int
    get() = when (this) {
        Transport.Wifi -> R.string.transport_wifi
        Transport.Cellular -> R.string.transport_cellular
        Transport.Ethernet -> R.string.transport_ethernet
        Transport.Vpn -> R.string.transport_vpn
        Transport.Other -> R.string.transport_other
    }

private val Transport.label: Int
    get() = when (this) {
        Transport.Wifi -> R.string.network_current_wifi
        Transport.Cellular -> R.string.network_current_cellular
        Transport.Ethernet -> R.string.network_current_ethernet
        Transport.Vpn -> R.string.network_current_vpn
        Transport.Other -> R.string.network_current_other
    }

private val Transport.fallbackTitle: Int
    get() = when (this) {
        Transport.Wifi -> R.string.network_title_wifi_unknown
        Transport.Cellular -> R.string.network_title_cellular_unknown
        Transport.Ethernet -> R.string.network_title_ethernet
        Transport.Vpn -> R.string.network_title_vpn
        Transport.Other -> R.string.network_title_other
    }
