package com.netrik.feature.hub

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import com.netrik.R
import com.netrik.core.designsystem.component.IconAvatar
import com.netrik.core.designsystem.component.StatusChip
import com.netrik.core.designsystem.component.StatusTone
import com.netrik.core.designsystem.theme.NetrikTheme
import com.netrik.core.network.CurrentNetwork.Transport
import com.netrik.core.ui.CopyAction
import com.netrik.core.ui.rememberCopyAction

/** Card "Rede atual" do hub: tocar num valor copia; "Copiar tudo" copia o resumo. */
@Composable
fun NetworkCard(
    network: NetworkCardState,
    publicIp: PublicIpUi,
    onShowPublicIp: () -> Unit,
    modifier: Modifier = Modifier,
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
                is NetworkCardState.Connected -> ConnectedContent(network, publicIp, onShowPublicIp)
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
private fun ConnectedContent(network: NetworkCardState.Connected, publicIp: PublicIpUi, onShowPublicIp: () -> Unit) {
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

    val fields = listOf(
        Field(stringResource(R.string.field_local_ip), network.localIp, stringResource(R.string.copied_local_ip)),
        Field(stringResource(R.string.field_gateway), network.gateway, stringResource(R.string.copied_gateway)),
        Field(stringResource(R.string.field_mask_cidr), network.maskCidr, stringResource(R.string.copied_mask)),
        Field(stringResource(R.string.field_dns), network.dns, stringResource(R.string.copied_dns)),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        fields.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { FieldTile(it, unavailable, copy, Modifier.weight(1f)) }
            }
        }
        if (network.ipv6 != null) {
            FieldTile(Field(stringResource(R.string.field_ipv6), network.ipv6, stringResource(R.string.copied_ipv6)), unavailable, copy)
        }
        PublicIpTile(publicIp, onShowPublicIp, copy)
    }

    val ssidLabel = stringResource(R.string.field_ssid)
    val publicIpLabel = stringResource(R.string.field_public_ip)
    val ipv6Label = stringResource(R.string.field_ipv6)
    val copiedAll = stringResource(R.string.copied_network)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(
            onClick = {
                val lines = buildList {
                    if (network.transport == Transport.Wifi && network.name != null) add("$ssidLabel: ${network.name}")
                    fields.forEach { add("${it.label}: ${it.value ?: unavailable}") }
                    network.ipv6?.let { add("$ipv6Label: $it") }
                    (publicIp as? PublicIpUi.Loaded)?.let { add("$publicIpLabel: ${it.ip}") }
                }
                copy.copy(lines.joinToString("\n"), copiedAll)
            },
        ) {
            Icon(painterResource(R.drawable.ic_copy_all), contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.action_copy_all), modifier = Modifier.padding(start = 8.dp))
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

@Composable
private fun SignalLine(signal: WifiSignal) {
    val rssi = signal.rssiDbm?.let(::formatDbm)
    val text = when {
        rssi != null && signal.band != null && signal.channel != null && signal.widthMhz != null ->
            stringResource(R.string.network_signal_wifi_width, rssi, signal.band.label, signal.channel, signal.widthMhz)
        rssi != null && signal.band != null && signal.channel != null ->
            stringResource(R.string.network_signal_wifi, rssi, signal.band.label, signal.channel)
        rssi != null && signal.band != null -> stringResource(R.string.network_signal_wifi_no_channel, rssi, signal.band.label)
        rssi != null -> stringResource(R.string.network_signal_rssi_only, rssi)
        else -> return
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
            painter = painterResource(signalIcon(signal.rssiDbm)),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Text(text, style = NetrikTheme.dataTypography.dataSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

/** Bloco de valor do card: fundo surface, cantos de 8dp, toque mínimo de 60dp. */
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

/** Usa o sinal de menos tipográfico, como no design (−54 dBm). */
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
