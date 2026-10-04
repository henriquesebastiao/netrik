package com.netrik.feature.devices

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.netrik.R
import com.netrik.core.designsystem.component.IconAvatar
import com.netrik.core.designsystem.component.NetrikTopAppBar
import com.netrik.core.designsystem.component.PermissionRationale
import com.netrik.core.designsystem.component.PlaceholderContent
import com.netrik.core.designsystem.component.StatusChip
import com.netrik.core.designsystem.component.SearchTopBar
import com.netrik.core.designsystem.component.StatusTone
import com.netrik.core.designsystem.component.groupedItemShape
import com.netrik.core.designsystem.theme.NetrikTheme
import com.netrik.core.lan.DeviceSort
import com.netrik.core.lan.LanDevice
import com.netrik.core.network.LocalNetworkAccess
import com.netrik.core.ui.CopyAction
import com.netrik.core.ui.LocalSnackbarHostState
import com.netrik.core.ui.RunPhase
import com.netrik.core.ui.rememberCopyAction
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun DevicesScreen(viewModel: DevicesViewModel, onOpenDevice: (String) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val copy = rememberCopyAction()
    val context = LocalContext.current
    val resources = LocalResources.current
    val activity = LocalActivity.current
    val snackbar = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    BackHandler(enabled = state.searchOpen, onBack = viewModel::onSearchClose)

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val canAskAgain = activity?.shouldShowRequestPermissionRationale(LOCAL_NETWORK) == true
        viewModel.onPermissionResult(
            when {
                granted -> LocalNetworkPermission.Granted
                canAskAgain -> LocalNetworkPermission.NotGranted
                else -> LocalNetworkPermission.PermanentlyDenied
            },
        )
    }
    // Re-checks when coming back from settings; doesn't undo "denied for good" if nothing changed.
    LifecycleResumeEffect(Unit) {
        val now = currentLocalNetworkPermission(context)
        if (now == LocalNetworkPermission.Granted || state.permission != LocalNetworkPermission.PermanentlyDenied) {
            viewModel.onPermissionResult(now)
        }
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            if (state.searchOpen) {
                SearchTopBar(
                    query = state.query,
                    hint = stringResource(R.string.devices_search_hint),
                    closeLabel = stringResource(R.string.devices_search_close),
                    onQueryChange = viewModel::onQueryChange,
                    onClose = viewModel::onSearchClose,
                )
            } else {
                NetrikTopAppBar(
                    title = stringResource(R.string.tab_devices),
                    actions = {
                        if (state.network != null) {
                            IconButton(onClick = viewModel::onSearchOpen) {
                                Icon(painterResource(R.drawable.ic_search), contentDescription = stringResource(R.string.devices_search))
                            }
                        }
                    },
                )
            }
        },
    ) { padding ->
        val network = state.network
        if (network == null) {
            if (state.networkChecked) {
                PlaceholderContent(
                    icon = R.drawable.ic_signal_disconnected,
                    title = stringResource(R.string.devices_no_lan_title),
                    text = stringResource(R.string.devices_no_lan_body),
                    modifier = Modifier.padding(padding),
                )
            }
            return@Scaffold
        }
        if (state.permission != LocalNetworkPermission.Granted) {
            if (state.permission != LocalNetworkPermission.Unknown) {
                Box(modifier = Modifier.padding(padding)) {
                    val denied = state.permission == LocalNetworkPermission.PermanentlyDenied
                    PermissionRationale(
                        icon = R.drawable.ic_lan,
                        title = stringResource(R.string.devices_perm_title),
                        body = stringResource(R.string.devices_perm_body),
                        items = listOf(
                            R.drawable.ic_radar to stringResource(R.string.devices_perm_item_probe),
                            R.drawable.ic_lan to stringResource(R.string.devices_perm_item_local),
                            R.drawable.ic_settings to stringResource(R.string.devices_perm_item_revoke),
                        ),
                        note = if (denied) stringResource(R.string.devices_perm_denied) else null,
                        primaryLabel = stringResource(if (denied) R.string.wifi_perm_open_settings else R.string.wifi_perm_continue),
                        onPrimary = {
                            if (denied) {
                                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                            } else {
                                launcher.launch(LOCAL_NETWORK)
                            }
                        },
                        laterLabel = stringResource(R.string.wifi_perm_later),
                        onLater = {
                            val text = resources.getString(R.string.devices_perm_later_toast)
                            scope.launch {
                                snackbar.currentSnackbarData?.dismiss()
                                snackbar.showSnackbar(text)
                            }
                        },
                    )
                }
            }
            return@Scaffold
        }
        val visible = state.visible
        val showProbe = state.running && !state.sweepDone && state.query.isBlank()
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "subnet") { SubnetCard(state, network, viewModel::onScanClick) }
            item(key = "sort") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DeviceSort.entries.forEach { sort ->
                        val selected = state.sort == sort
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.onSortChange(sort) },
                            label = { Text(stringResource(if (sort == DeviceSort.Ip) R.string.devices_sort_ip else R.string.devices_sort_vendor)) },
                            leadingIcon = if (selected) {
                                { Icon(painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(18.dp)) }
                            } else {
                                null
                            },
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (state.query.isNotBlank()) {
                            stringResource(R.string.devices_count_filtered, visible.size, state.devices.size)
                        } else {
                            pluralStringResource(R.plurals.devices_count, state.devices.size, state.devices.size)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item(key = "list") {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    val count = visible.size + if (showProbe) 1 else 0
                    visible.forEachIndexed { i, device ->
                        DeviceRow(device, groupedItemShape(i, count), copy, onClick = { onOpenDevice(device.ip) })
                    }
                    if (showProbe) {
                        val next = network.range.hosts.getOrNull(state.scanned) ?: network.range.hosts.last()
                        ProbeRow(next, groupedItemShape(count - 1, count))
                    }
                }
            }
            if (state.query.isNotBlank() && visible.isEmpty()) {
                item(key = "nomatch") {
                    PlaceholderContent(
                        icon = R.drawable.ic_search_off,
                        title = "",
                        text = stringResource(R.string.devices_no_match, state.query),
                        modifier = Modifier.heightIn(max = 240.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SubnetCard(state: DevicesUiState, network: LocalNetwork, onScan: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(16.dp), color = colors.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconAvatar(icon = R.drawable.ic_lan)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        listOfNotNull(stringResource(R.string.devices_subnet), network.interfaceName, network.ssid).joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(network.range.cidr, style = NetrikTheme.dataTypography.dataMedium.copy(fontSize = 17.sp, lineHeight = 24.sp))
                }
                FilledTonalButton(
                    onClick = onScan,
                    contentPadding = PaddingValues(start = 12.dp, end = 16.dp),
                    colors = if (state.running) {
                        ButtonDefaults.filledTonalButtonColors(containerColor = colors.errorContainer, contentColor = colors.onErrorContainer)
                    } else {
                        ButtonDefaults.filledTonalButtonColors()
                    },
                ) {
                    Icon(
                        painterResource(if (state.running) R.drawable.ic_stop_filled else R.drawable.ic_refresh),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(stringResource(if (state.running) R.string.action_stop else R.string.devices_scan), modifier = Modifier.padding(start = 8.dp))
                }
            }
            val mono = NetrikTheme.dataTypography.dataSmall
            when {
                state.running && !state.sweepDone -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LinearProgressIndicator(progress = { if (state.total == 0) 0f else state.scanned.toFloat() / state.total }, modifier = Modifier.fillMaxWidth())
                    Row {
                        Text(stringResource(R.string.devices_progress, state.scanned, state.total, state.devices.size), style = mono, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
                        etaSeconds(state)?.let { Text(stringResource(R.string.devices_eta, it), style = mono, color = colors.onSurfaceVariant) }
                    }
                }
                state.running -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.devices_naming), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
                state.phase == RunPhase.Done -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusChip(stringResource(R.string.run_done), StatusTone.Success)
                    Text(stringResource(R.string.devices_done_line, state.total, seconds(state.elapsedMillis)), style = mono, color = colors.onSurfaceVariant)
                }
                state.phase == RunPhase.Stopped -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusChip(stringResource(R.string.run_stopped), StatusTone.Neutral)
                    Text(stringResource(R.string.devices_stopped_line, state.scanned, state.total), style = mono, color = colors.onSurfaceVariant)
                }
                else -> Text(stringResource(R.string.devices_idle), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            if (network.range.truncated) {
                Text(stringResource(R.string.devices_truncated), style = MaterialTheme.typography.bodySmall, color = NetrikTheme.extendedColors.warning)
            }
        }
    }
}

@Composable
private fun DeviceRow(device: LanDevice, shape: Shape, copy: CopyAction, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val ipCopied = stringResource(R.string.devices_ip_copied)
    val macCopied = stringResource(R.string.devices_mac_copied)
    val (icon, bg, fg) = deviceIcon(device)
    Surface(onClick = onClick, shape = shape, color = colors.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            IconAvatar(icon = icon, containerColor = bg, contentColor = fg, modifier = Modifier.padding(top = 2.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            device.hostname?.value ?: stringResource(R.string.devices_no_hostname),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (device.hostname != null) colors.onSurface else colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        badge(device)?.let { DeviceBadge(it) }
                    }
                    Text(
                        device.vendor?.value ?: stringResource(R.string.devices_vendor_unknown),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // Chips wrap to the next line when they don't fit (e.g. "MAC unavailable").
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CopyChip("IP", device.ip, onClick = { copy.copy(device.ip, ipCopied) })
                    val mac = device.mac?.value
                    if (mac != null) {
                        CopyChip("MAC", mac, onClick = { copy.copy(mac, macCopied) })
                    } else {
                        CopyChip("MAC", stringResource(if (device.isSelf) R.string.devices_mac_private else R.string.devices_mac_unavailable), onClick = null)
                    }
                }
            }
            Icon(
                painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }
    }
}

@Composable
private fun CopyChip(label: String, value: String, onClick: (() -> Unit)?) {
    val colors = MaterialTheme.colorScheme
    val content: @Composable () -> Unit = {
        Row(modifier = Modifier.height(30.dp).padding(start = 8.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            Text(
                value,
                style = if (onClick != null) NetrikTheme.dataTypography.dataMedium.copy(fontSize = 13.sp) else MaterialTheme.typography.bodySmall,
                color = if (onClick != null) colors.onSurface else colors.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
            if (onClick != null) {
                Icon(painterResource(R.drawable.ic_content_copy), contentDescription = null, tint = colors.outline, modifier = Modifier.size(14.dp))
            }
        }
    }
    if (onClick != null) {
        Surface(onClick = onClick, shape = RoundedCornerShape(6.dp), color = colors.surface) { content() }
    } else {
        Surface(shape = RoundedCornerShape(6.dp), color = colors.surface) { content() }
    }
}

@Composable
fun DeviceBadge(text: String) {
    Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
        Text(text, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

@Composable
private fun ProbeRow(ip: String, shape: Shape) {
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.heightIn(min = 56.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            }
            Text(stringResource(R.string.devices_checking, ip), style = NetrikTheme.dataTypography.dataSmall.copy(fontSize = 13.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun badge(device: LanDevice): String? = when {
    device.isGateway -> stringResource(R.string.devices_badge_gateway)
    device.isSelf -> stringResource(R.string.devices_badge_self)
    else -> null
}

/** Icon from the role on the network or from the services the device itself announces. */
@Composable
fun deviceIcon(device: LanDevice): Triple<Int, Color, Color> {
    val c = MaterialTheme.colorScheme
    return when {
        device.isGateway -> Triple(R.drawable.ic_router, c.tertiaryContainer, c.onTertiaryContainer)
        device.isSelf -> Triple(R.drawable.ic_smartphone, c.primaryContainer, c.onPrimaryContainer)
        device.services.any { it.startsWith("_ipp") || it.startsWith("_printer") || it.startsWith("_pdl") } ->
            Triple(R.drawable.ic_print, c.surfaceContainerHighest, c.onSurfaceVariant)
        device.services.any { it.startsWith("_googlecast") || it.startsWith("_airplay") || it.startsWith("_raop") } ->
            Triple(R.drawable.ic_tv, c.surfaceContainerHighest, c.onSurfaceVariant)
        device.services.any { it.startsWith("_workstation") || it.startsWith("_smb") || it.startsWith("_companion") } ->
            Triple(R.drawable.ic_computer, c.surfaceContainerHighest, c.onSurfaceVariant)
        else -> Triple(R.drawable.ic_devices_other, c.surfaceContainerHighest, c.onSurfaceVariant)
    }
}

/** ETA from the average speed so far. */
private fun etaSeconds(state: DevicesUiState): Int? {
    if (state.scanned < 8 || state.startedAt == 0L) return null
    val elapsed = System.currentTimeMillis() - state.startedAt
    val rate = state.scanned / (elapsed / 1000.0)
    return ((state.total - state.scanned) / rate).toInt().coerceAtLeast(1)
}

private const val LOCAL_NETWORK = LocalNetworkAccess.PERMISSION

private fun currentLocalNetworkPermission(context: Context): LocalNetworkPermission =
    if (LocalNetworkAccess.isGranted(context)) LocalNetworkPermission.Granted else LocalNetworkPermission.NotGranted

private fun seconds(millis: Long): String = String.format(Locale.getDefault(), "%.1f", millis / 1000.0)
