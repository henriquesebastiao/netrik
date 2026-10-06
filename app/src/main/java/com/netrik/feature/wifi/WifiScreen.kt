package com.netrik.feature.wifi

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.netrik.R
import com.netrik.core.designsystem.component.NetrikTopAppBar
import com.netrik.core.designsystem.component.PermissionRationale
import com.netrik.core.designsystem.component.PlaceholderContent
import com.netrik.core.designsystem.component.groupedItemShape
import com.netrik.core.designsystem.theme.NetrikTheme
import com.netrik.core.network.WifiBand
import com.netrik.core.ui.LocalSnackbarHostState
import com.netrik.core.ui.rememberCopyAction
import com.netrik.core.wifi.ChannelAdvisor
import com.netrik.core.wifi.WifiNetwork
import com.netrik.core.wifi.WifiSort
import com.netrik.core.wifi.nearby

@Composable
fun WifiScreen(onOpenMeter: () -> Unit = {}, viewModel: WifiViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val activity = LocalActivity.current
    val snackbar = LocalSnackbarHostState.current

    fun currentPermission(): LocationPermission = when {
        granted(context, Manifest.permission.ACCESS_FINE_LOCATION) -> LocationPermission.Granted
        granted(context, Manifest.permission.ACCESS_COARSE_LOCATION) -> LocationPermission.CoarseOnly
        else -> LocationPermission.NotGranted
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val fine = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarse = result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        val canAskAgain = activity?.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) == true
        viewModel.onPermissionResult(
            when {
                fine -> LocationPermission.Granted
                !canAskAgain -> LocationPermission.PermanentlyDenied
                coarse -> LocationPermission.CoarseOnly
                else -> LocationPermission.NotGranted
            },
        )
    }

    // When coming back from settings, re-check; doesn't undo "denied for good" if nothing changed.
    LifecycleResumeEffect(Unit) {
        val now = currentPermission()
        if (now == LocationPermission.Granted || state.permission != LocationPermission.PermanentlyDenied) {
            viewModel.onPermissionResult(now)
        }
        onPauseOrDispose { }
    }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            val text = when (message) {
                is WifiMessage.Throttled -> resources.getString(R.string.wifi_throttled_toast, message.seconds)
                WifiMessage.Refreshing -> resources.getString(R.string.wifi_refreshing)
                WifiMessage.PermissionLater -> resources.getString(R.string.wifi_perm_later_toast)
            }
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(text)
        }
    }

    Scaffold(
        topBar = {
            NetrikTopAppBar(
                title = stringResource(R.string.tab_wifi),
                actions = {
                    IconButton(onClick = onOpenMeter) {
                        Icon(painterResource(R.drawable.ic_speed), contentDescription = stringResource(R.string.wifi_meter_open))
                    }
                    if (state.ready) {
                        IconButton(onClick = viewModel::onRefresh) {
                            Icon(painterResource(R.drawable.ic_refresh), contentDescription = stringResource(R.string.wifi_refresh))
                        }
                    }
                    WifiMenu(onOpenSettings = { context.startActivity(wifiSettingsIntent()) })
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when {
                state.permission != LocationPermission.Granted -> PermissionContent(
                    permission = state.permission,
                    onContinue = {
                        if (state.permission == LocationPermission.PermanentlyDenied) {
                            context.startActivity(appSettingsIntent(context.packageName))
                        } else {
                            launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                        }
                    },
                    onLater = viewModel::onPermissionLater,
                )
                !state.wifiEnabled -> PlaceholderContent(
                    icon = R.drawable.ic_wifi_off,
                    title = stringResource(R.string.wifi_off_title),
                    text = stringResource(R.string.wifi_off_body),
                    actionLabel = stringResource(R.string.wifi_off_action),
                    onAction = { context.startActivity(wifiSettingsIntent()) },
                )
                !state.locationEnabled -> PlaceholderContent(
                    icon = R.drawable.ic_location_off,
                    title = stringResource(R.string.location_off_title),
                    text = stringResource(R.string.location_off_body),
                    actionLabel = stringResource(R.string.location_off_action),
                    onAction = { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
                )
                else -> ScanContent(state, viewModel, onOpenMeter)
            }
        }
    }
}

@Composable
private fun ScanContent(state: WifiUiState, viewModel: WifiViewModel, onOpenMeter: () -> Unit) {
    val copy = rememberCopyAction()
    state.networks.firstOrNull { it.bssid == state.detailBssid }?.let { network ->
        WifiNetworkSheet(
            network = network,
            rttSupport = state.rttSupport,
            rtt = state.rtt,
            onStartRanging = { viewModel.onStartRanging(network.bssid) },
            onStopRanging = viewModel::onStopRanging,
            onDismiss = viewModel::onCloseDetail,
        )
    }
    val palette = chartPalette()
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "view") {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                WifiView.entries.forEachIndexed { i, view ->
                    SegmentedButton(
                        selected = state.view == view,
                        onClick = { viewModel.onViewChange(view) },
                        shape = SegmentedButtonDefaults.itemShape(i, WifiView.entries.size),
                        icon = {
                            SegmentedButtonDefaults.Icon(active = state.view == view) {
                                Icon(painterResource(viewIcon(view)), contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        },
                    ) { Text(viewLabel(view), maxLines = 1) }
                }
            }
        }
        item(key = "filters") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.bands.forEach { band ->
                    val selected = if (state.view != WifiView.List) state.spectrumBand == band else band in state.listBands
                    FilterChip(
                        selected = selected,
                        onClick = { viewModel.onBandClick(band) },
                        label = {
                            Text(stringResource(R.string.wifi_band, band.label))
                            Text(
                                " " + state.networks.count { it.band == band },
                                style = NetrikTheme.dataTypography.dataSmall,
                                modifier = Modifier.padding(start = 2.dp),
                            )
                        },
                        leadingIcon = if (selected) {
                            { Icon(painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(18.dp)) }
                        } else {
                            null
                        },
                    )
                }
                Spacer(Modifier.weight(1f))
                if (state.view == WifiView.List) {
                    OutlinedButton(
                        onClick = viewModel::onCycleSort,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        contentPadding = PaddingValues(start = 8.dp, end = 12.dp),
                        modifier = Modifier.height(32.dp),
                    ) {
                        Icon(painterResource(R.drawable.ic_sort), contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(sortLabel(state.sort), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
        item(key = "throttle") {
            Row(modifier = Modifier.padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(if (state.throttled) R.drawable.ic_schedule else R.drawable.ic_info),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    if (state.throttled) stringResource(R.string.wifi_throttled, state.secondsToRefresh) else stringResource(R.string.wifi_auto_refresh, state.secondsToRefresh),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (state.view == WifiView.List) {
            val connected = state.connectedNetwork?.takeIf { it.band in state.listBands }
            if (connected != null) {
                item(key = "connected") {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        WifiNetworkRow(connected, RoundedCornerShape(16.dp), copy, highlighted = true, onClick = { viewModel.onOpenDetail(connected.bssid) })
                        TextButton(onClick = onOpenMeter, modifier = Modifier.align(Alignment.End)) {
                            Icon(painterResource(R.drawable.ic_speed), contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(stringResource(R.string.wifi_meter_open), modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
            val nearby = state.networks.nearby(state.listBands, state.sort)
            item(key = "nearby-header") {
                Row(modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 4.dp)) {
                    Text(stringResource(R.string.wifi_nearby), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                    Text(pluralStringResource(R.plurals.wifi_nearby_count, nearby.size, nearby.size), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (nearby.isEmpty()) {
                item(key = "nearby-empty") {
                    Text(stringResource(R.string.wifi_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
                }
            } else {
                item(key = "nearby") {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        nearby.forEachIndexed { i, n -> WifiNetworkRow(n, groupedItemShape(i, nearby.size), copy, onClick = { viewModel.onOpenDetail(n.bssid) }) }
                    }
                }
            }
        } else if (state.view == WifiView.Channels) {
            val inBand = state.networks.filter { it.band == state.spectrumBand }
            val advice = ChannelAdvisor.advise(state.networks, state.spectrumBand)
            item(key = "advice") { ChannelAdviceCard(advice, networksInBand = inBand.count { !it.connected }) }
            item(key = "ranking") { ChannelRanking(advice) }
        } else {
            val inBand = state.networks.filter { it.band == state.spectrumBand }
            val colors = assignColors(inBand, palette)
            val selected = state.selectedBssid?.takeIf { b -> inBand.any { it.bssid == b } }
                ?: inBand.firstOrNull { it.connected }?.bssid
                ?: inBand.maxByOrNull { it.rssiDbm }?.bssid
            item(key = "spectrum") {
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(top = 12.dp, end = 8.dp, bottom = 8.dp)) {
                        Row(modifier = Modifier.padding(start = 12.dp, end = 8.dp)) {
                            Text(stringResource(R.string.wifi_spectrum_signal), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            Text(
                                stringResource(if (state.spectrumBand == WifiBand.GHz2_4) R.string.wifi_spectrum_tap else R.string.wifi_spectrum_scroll),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        SpectrumChart(
                            band = state.spectrumBand,
                            networks = inBand,
                            colors = colors,
                            selected = selected,
                            hiddenLabel = stringResource(R.string.wifi_hidden),
                            onSelect = viewModel::onSelectNetwork,
                        )
                        Text(
                            stringResource(R.string.wifi_spectrum_channel),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            if (inBand.isEmpty()) {
                item(key = "spectrum-empty") {
                    Text(stringResource(R.string.wifi_spectrum_empty, state.spectrumBand.label), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            inBand.firstOrNull { it.bssid == selected }?.let { sel ->
                item(key = "selected") { SelectedNetworkCard(sel, colors[sel.bssid] ?: MaterialTheme.colorScheme.primary, onClick = { viewModel.onOpenDetail(sel.bssid) }) }
            }
            item(key = "legend") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    inBand.sortedByDescending { it.rssiDbm }.forEach { n ->
                        val isSelected = n.bssid == selected
                        Surface(
                            onClick = { viewModel.onSelectNetwork(n.bssid) },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                            border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.height(32.dp),
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(Modifier.size(10.dp).background(colors[n.bssid] ?: Color.Gray, RoundedCornerShape(3.dp)))
                                Text(
                                    n.ssid ?: stringResource(R.string.wifi_hidden),
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                    fontStyle = if (n.ssid == null) FontStyle.Italic else FontStyle.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 160.dp),
                                )
                                Text(formatDbm(n.rssiDbm), style = NetrikTheme.dataTypography.dataSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectedNetworkCard(network: WifiNetwork, color: Color, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = colors.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.padding(top = 6.dp).size(12.dp).background(color, RoundedCornerShape(3.dp)))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        network.ssid ?: stringResource(R.string.wifi_hidden),
                        style = MaterialTheme.typography.titleMedium,
                        fontStyle = if (network.ssid == null) FontStyle.Italic else FontStyle.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (network.connected) {
                        Surface(shape = RoundedCornerShape(4.dp), color = colors.primaryContainer, contentColor = colors.onPrimaryContainer) {
                            Text(stringResource(R.string.wifi_connected), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                }
                Text("${network.bssid} · ${network.security.label}", style = NetrikTheme.dataTypography.dataSmall, color = colors.onSurfaceVariant)
                ChannelLine(network)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(stringResource(R.string.wifi_dbm, formatDbm(network.rssiDbm)), style = NetrikTheme.dataTypography.dataMedium.copy(fontSize = 17.sp))
                Text(qualityLabel(network.quality), style = MaterialTheme.typography.labelSmall, color = qualityColor(network.quality))
            }
        }
    }
}

@Composable
private fun PermissionContent(permission: LocationPermission, onContinue: () -> Unit, onLater: () -> Unit) {
    PermissionRationale(
        icon = R.drawable.ic_location_on,
        title = stringResource(R.string.wifi_perm_title),
        body = stringResource(R.string.wifi_perm_body),
        items = listOf(
            R.drawable.ic_wifi_find to stringResource(R.string.wifi_perm_item_reads),
            R.drawable.ic_location_off to stringResource(R.string.wifi_perm_item_private),
            R.drawable.ic_settings to stringResource(R.string.wifi_perm_item_revoke),
        ),
        note = when (permission) {
            LocationPermission.CoarseOnly -> stringResource(R.string.wifi_perm_coarse)
            LocationPermission.PermanentlyDenied -> stringResource(R.string.wifi_perm_denied)
            else -> null
        },
        primaryLabel = stringResource(if (permission == LocationPermission.PermanentlyDenied) R.string.wifi_perm_open_settings else R.string.wifi_perm_continue),
        onPrimary = onContinue,
        laterLabel = stringResource(R.string.wifi_perm_later),
        onLater = onLater,
    )
}

@Composable
private fun WifiMenu(onOpenSettings: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.action_more))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.wifi_open_settings)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_settings), contentDescription = null) },
                onClick = {
                    expanded = false
                    onOpenSettings()
                },
            )
        }
    }
}

@Composable
private fun sortLabel(sort: WifiSort) = stringResource(
    when (sort) {
        WifiSort.Signal -> R.string.wifi_sort_signal
        WifiSort.Channel -> R.string.wifi_sort_channel
        WifiSort.Name -> R.string.wifi_sort_name
    },
)

/** 8 clearly distinct colors for the curves, adjusted to the theme. */
@Composable
private fun chartPalette(): List<Color> {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return remember(dark) {
        if (dark) {
            listOf(0xFF7FCFE6, 0xFFF2A07B, 0xFF8FD18F, 0xFFC9A6F2, 0xFFE6C66B, 0xFFF29BB7, 0xFFB5D16B, 0xFF9DB1F2)
        } else {
            listOf(0xFF00758F, 0xFFC0532A, 0xFF2E7D32, 0xFF7B4FB8, 0xFF9A7400, 0xFFB83C6A, 0xFF5F7D00, 0xFF3B5BC4)
        }.map { Color(it) }
    }
}

/** Stable color per network: sorts by BSSID so colors don't change on every scan. */
private fun assignColors(networks: List<WifiNetwork>, palette: List<Color>): Map<String, Color> =
    networks.map { it.bssid }.sorted().mapIndexed { i, bssid -> bssid to palette[i % palette.size] }.toMap()

private fun granted(context: Context, permission: String) =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun wifiSettingsIntent(): Intent =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Intent(Settings.Panel.ACTION_WIFI) else Intent(Settings.ACTION_WIFI_SETTINGS)

private fun appSettingsIntent(packageName: String): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))

private fun viewIcon(view: WifiView): Int = when (view) {
    WifiView.List -> R.drawable.ic_list
    WifiView.Spectrum -> R.drawable.ic_ssid_chart
    WifiView.Channels -> R.drawable.ic_bar_chart
}

@Composable
private fun viewLabel(view: WifiView): String = stringResource(
    when (view) {
        WifiView.List -> R.string.wifi_view_list
        WifiView.Spectrum -> R.string.wifi_view_spectrum
        WifiView.Channels -> R.string.wifi_view_channels
    },
)
