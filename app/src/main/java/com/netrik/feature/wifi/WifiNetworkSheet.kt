package com.netrik.feature.wifi

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.netrik.R
import com.netrik.core.designsystem.component.SectionHeader
import com.netrik.core.designsystem.component.groupedItemShape
import com.netrik.core.designsystem.theme.NetrikTheme
import com.netrik.core.ui.CopyAction
import com.netrik.core.ui.rememberCopyAction
import com.netrik.core.wifi.RttReading
import com.netrik.core.wifi.RttSupport
import com.netrik.core.wifi.WifiNetwork
import java.text.NumberFormat
import java.util.Locale

/** Everything about one network, each value copyable, plus the RTT distance when both sides support it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiNetworkSheet(
    network: WifiNetwork,
    rttSupport: RttSupport,
    rtt: RttUi?,
    onStartRanging: () -> Unit,
    onStopRanging: () -> Unit,
    onDismiss: () -> Unit,
) {
    val copy = rememberCopyAction()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 4.dp)) {
                Text(
                    network.ssid ?: stringResource(R.string.wifi_hidden),
                    style = MaterialTheme.typography.titleLarge,
                    fontStyle = if (network.ssid == null) FontStyle.Italic else FontStyle.Normal,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(R.string.wifi_dbm, formatDbm(network.rssiDbm)) + " · " + qualityLabel(network.quality),
                    style = NetrikTheme.dataTypography.dataSmall,
                    color = qualityColor(network.quality),
                )
            }
            RttSection(
                responder = network.rttResponder,
                support = rttSupport,
                rtt = rtt?.takeIf { it.bssid == network.bssid },
                onStart = onStartRanging,
                onStop = onStopRanging,
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(stringResource(R.string.wifi_detail_section))
                val rows = buildList {
                    network.ssid?.let { add(stringResource(R.string.field_ssid) to it) }
                    add(stringResource(R.string.field_bssid) to network.bssid)
                    add(stringResource(R.string.wifi_detail_vendor) to vendorLabel(network))
                    add(stringResource(R.string.wifi_detail_security) to network.security.label)
                    add(stringResource(R.string.wifi_detail_wps) to stringResource(if (network.wps) R.string.wifi_detail_wps_yes else R.string.wifi_detail_wps_no))
                    network.band?.let { add(stringResource(R.string.field_band) to stringResource(R.string.wifi_band, it.label)) }
                    network.channel?.let { add(stringResource(R.string.field_channel) to it.toString()) }
                    add(stringResource(R.string.field_frequency) to "${network.frequencyMhz} MHz")
                    add(stringResource(R.string.field_channel_width) to "${network.widthMhz} MHz")
                    if (network.centerMhz != network.frequencyMhz) add(stringResource(R.string.wifi_detail_center) to "${network.centerMhz} MHz")
                    add(stringResource(R.string.wifi_detail_rtt) to stringResource(if (network.rttResponder) R.string.wifi_detail_rtt_yes else R.string.wifi_detail_rtt_no))
                }
                DetailRows(rows, copy)
            }
        }
    }
}

@Composable
private fun DetailRows(rows: List<Pair<String, String>>, copy: CopyAction) {
    val copied = stringResource(R.string.copied_value)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        rows.forEachIndexed { index, (label, value) ->
            Surface(
                onClick = { copy.copy(value, copied.format(label)) },
                shape = groupedItemShape(index, rows.size),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(value, style = NetrikTheme.dataTypography.dataMedium.copy(fontSize = 15.sp))
                }
            }
        }
    }
}

/**
 * Distance to the access point via Wi-Fi RTT. Explains when the phone or the AP can't measure, asks for the
 * Nearby devices permission (Android 13+) on the first tap and shows each reading as it arrives.
 */
@Composable
fun RttSection(responder: Boolean, support: RttSupport, rtt: RttUi?, onStart: () -> Unit, onStop: () -> Unit) {
    val context = LocalContext.current
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) onStart() }
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(16.dp), color = colors.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(painterResource(R.drawable.ic_straighten), contentDescription = null, tint = colors.primary)
                Text(stringResource(R.string.wifi_rtt_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            }
            when {
                support == RttSupport.Unsupported -> RttNote(stringResource(R.string.wifi_rtt_phone_unsupported))
                !responder -> RttNote(stringResource(R.string.wifi_rtt_ap_unsupported))
                support == RttSupport.Unavailable -> RttNote(stringResource(R.string.wifi_rtt_unavailable))
                rtt == null -> Button(onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !granted(context, Manifest.permission.NEARBY_WIFI_DEVICES)) {
                        request.launch(Manifest.permission.NEARBY_WIFI_DEVICES)
                    } else {
                        onStart()
                    }
                }) {
                    Icon(painterResource(R.drawable.ic_play_arrow_filled), contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.wifi_rtt_start), modifier = Modifier.padding(start = 8.dp))
                }
                else -> {
                    RttReadingView(rtt.reading)
                    OutlinedButton(onClick = onStop) {
                        Icon(painterResource(R.drawable.ic_stop_filled), contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.action_stop), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun RttReadingView(reading: RttReading?) {
    when (reading) {
        null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Text(stringResource(R.string.wifi_rtt_measuring), style = MaterialTheme.typography.bodyMedium)
        }
        is RttReading.Measured -> Column {
            Text(
                stringResource(R.string.wifi_rtt_distance, meters(reading.distanceMm), meters(reading.stdDevMm.coerceAtLeast(0))),
                style = NetrikTheme.dataTypography.dataLarge,
            )
            Text(
                stringResource(R.string.wifi_rtt_measurements, reading.successful, reading.attempted),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        RttReading.Failed -> RttNote(stringResource(R.string.wifi_rtt_failed))
        RttReading.NotResponder -> RttNote(stringResource(R.string.wifi_rtt_ap_unsupported))
        RttReading.NotInScan -> RttNote(stringResource(R.string.wifi_rtt_not_in_scan))
        RttReading.MissingPermission -> RttNote(stringResource(R.string.wifi_rtt_permission))
    }
}

@Composable
private fun RttNote(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Millimeters as meters with one decimal in the current language ("3.2" / "3,2"); negative readings near the AP count as 0. */
private fun meters(mm: Int): String {
    val format = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
        minimumFractionDigits = 1
        maximumFractionDigits = 1
    }
    return format.format(mm.coerceAtLeast(0) / 1000.0)
}

private fun granted(context: Context, permission: String) =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
