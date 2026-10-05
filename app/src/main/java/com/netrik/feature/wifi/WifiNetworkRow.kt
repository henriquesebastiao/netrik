package com.netrik.feature.wifi

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.netrik.R
import com.netrik.core.designsystem.theme.NetrikTheme
import com.netrik.core.wifi.SignalQuality
import com.netrik.core.wifi.WifiNetwork
import com.netrik.core.wifi.WifiSecurity
import com.netrik.core.ui.CopyAction

/** Network row from the design: signal icon, SSID, vendor, BSSID, chips, channel and dBm. */
@Composable
fun WifiNetworkRow(
    network: WifiNetwork,
    shape: Shape,
    copy: CopyAction,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val qualityColor = qualityColor(network.quality)
    val bssidCopied = stringResource(R.string.wifi_bssid_copied)
    Surface(
        shape = shape,
        color = colors.surfaceContainer,
        border = if (highlighted) BorderStroke(2.dp, colors.primary) else null,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(start = 14.dp, end = 12.dp, top = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (highlighted) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(painterResource(R.drawable.ic_check_circle_filled), contentDescription = null, tint = colors.primary, modifier = Modifier.size(16.dp))
                    Text(stringResource(R.string.wifi_connected), style = MaterialTheme.typography.labelMedium, color = colors.primary)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
                Surface(
                    shape = CircleShape,
                    color = if (highlighted) colors.primaryContainer else colors.surfaceContainerHighest,
                    contentColor = if (highlighted) colors.onPrimaryContainer else qualityColor,
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) { Icon(painterResource(signalIcon(network.quality)), contentDescription = null) }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Column {
                        Text(
                            text = network.ssid ?: stringResource(R.string.wifi_hidden),
                            style = MaterialTheme.typography.bodyLarge,
                            fontStyle = if (network.ssid == null) FontStyle.Italic else FontStyle.Normal,
                            color = if (network.ssid == null) colors.onSurfaceVariant else colors.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        // Vendor on its own line above the BSSID, so a long name isn't cut off by the address.
                        if (network.vendor != null || network.bssidLocal) {
                            Text(
                                vendorLabel(network),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            network.bssid,
                            style = NetrikTheme.dataTypography.dataSmall,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier
                                .clickable { copy.copy(network.bssid, bssidCopied) }
                                .padding(vertical = 2.dp),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SecurityChip(network.security)
                        network.band?.let { AttributeChip(stringResource(R.string.wifi_band, it.label)) }
                    }
                    Text(channelLine(network), style = NetrikTheme.dataTypography.dataSmall, color = colors.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.wifi_dbm, formatDbm(network.rssiDbm)), style = NetrikTheme.dataTypography.dataMedium.copy(fontSize = 17.sp))
                    Text(qualityLabel(network.quality), style = MaterialTheme.typography.labelSmall, color = qualityColor)
                }
            }
        }
    }
}

@Composable
private fun SecurityChip(security: WifiSecurity) {
    val ext = NetrikTheme.extendedColors
    val colors = MaterialTheme.colorScheme
    val (bg, fg) = if (security.weak) ext.warningContainer to ext.onWarningContainer else colors.surfaceContainerHighest to colors.onSurfaceVariant
    val icon = when (security) {
        WifiSecurity.Open -> R.drawable.ic_lock_open_filled
        WifiSecurity.Wep, WifiSecurity.Wpa -> R.drawable.ic_warning_filled
        else -> R.drawable.ic_lock_filled
    }
    Surface(shape = RoundedCornerShape(6.dp), color = bg, contentColor = fg, modifier = Modifier.height(24.dp)) {
        Row(modifier = Modifier.padding(start = 6.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(14.dp))
            Text(
                if (security == WifiSecurity.Open) stringResource(R.string.wifi_security_open) else security.label,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun AttributeChip(text: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.height(24.dp),
    ) {
        Box(modifier = Modifier.padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun vendorLabel(network: WifiNetwork): String = when {
    network.vendor != null -> network.vendor
    network.bssidLocal -> stringResource(R.string.wifi_vendor_local)
    else -> stringResource(R.string.wifi_vendor_unknown)
}

@Composable
fun channelLine(network: WifiNetwork): String = network.channel?.let {
    stringResource(R.string.wifi_channel_line, it, network.frequencyMhz, network.widthMhz)
} ?: stringResource(R.string.wifi_channel_line_unknown, network.frequencyMhz, network.widthMhz)

@Composable
fun qualityColor(quality: SignalQuality): Color = when (quality) {
    SignalQuality.Excellent -> NetrikTheme.extendedColors.success
    SignalQuality.Good -> MaterialTheme.colorScheme.onSurface
    SignalQuality.Weak -> NetrikTheme.extendedColors.warning
    SignalQuality.VeryWeak -> MaterialTheme.colorScheme.error
}

@Composable
fun qualityLabel(quality: SignalQuality): String = stringResource(
    when (quality) {
        SignalQuality.Excellent -> R.string.wifi_quality_excellent
        SignalQuality.Good -> R.string.wifi_quality_good
        SignalQuality.Weak -> R.string.wifi_quality_weak
        SignalQuality.VeryWeak -> R.string.wifi_quality_very_weak
    },
)

private fun signalIcon(quality: SignalQuality): Int = when (quality) {
    SignalQuality.Excellent -> R.drawable.ic_signal_wifi_4_bar
    SignalQuality.Good -> R.drawable.ic_network_wifi_3_bar
    SignalQuality.Weak -> R.drawable.ic_network_wifi_2_bar
    SignalQuality.VeryWeak -> R.drawable.ic_network_wifi_1_bar
}

/** Typographic minus sign, as in the design (−54 dBm). */
fun formatDbm(dbm: Int): String = dbm.toString().replace('-', '−')
