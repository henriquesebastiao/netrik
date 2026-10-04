package com.netrik.feature.oui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.netrik.R
import com.netrik.core.designsystem.component.NetrikTopAppBar
import com.netrik.core.designsystem.component.SectionHeader
import com.netrik.core.designsystem.component.StatusChip
import com.netrik.core.designsystem.component.StatusChipSize
import com.netrik.core.designsystem.component.StatusTone
import com.netrik.core.designsystem.component.groupedItemShape
import com.netrik.core.designsystem.theme.NetrikTheme
import com.netrik.core.oui.MacAddresses
import com.netrik.core.oui.MacInput
import com.netrik.core.oui.OuiDbStatus
import com.netrik.core.oui.OuiMatch
import com.netrik.core.oui.OuiUpdateState
import com.netrik.core.ui.CopyAction
import com.netrik.core.ui.LocalSnackbarHostState
import com.netrik.core.ui.RelativeTime
import com.netrik.core.ui.rememberCopyAction
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/** Base com mais de 6 meses é marcada como desatualizada (o IEEE publica centenas de prefixos por mês). */
private const val STALE_AFTER_DAYS = 180L

private val EXAMPLES = listOf("3C:22:FB:9A:10:7E", "00-11-32", "240ac45b77e2", "4419.B631.0AD4")

@Composable
fun OuiScreen(
    onBack: () -> Unit,
    onFindInNetwork: () -> Unit,
    viewModel: OuiViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbarHostState.current
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            val text = when (message) {
                is OuiMessage.Updated -> context.getString(R.string.oui_updated, formatCount(message.added))
                OuiMessage.UpdateNetworkError -> context.getString(R.string.oui_update_network_error)
                OuiMessage.UpdateRejected -> context.getString(R.string.oui_update_rejected)
                OuiMessage.UpdateInvalid -> context.getString(R.string.oui_update_invalid)
                OuiMessage.HistoryCleared -> context.getString(R.string.oui_history_cleared)
            }
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(text)
        }
    }
    OuiContent(
        state = state,
        onBack = onBack,
        onInputChange = viewModel::onInputChange,
        onPaste = viewModel::onPaste,
        onSubmit = viewModel::onSubmit,
        onHistorySelected = viewModel::onHistorySelected,
        onHistoryRemove = viewModel::onHistoryRemove,
        onClearHistory = viewModel::onClearHistory,
        onUpdateDatabase = viewModel::onUpdateDatabase,
        onFindInNetwork = onFindInNetwork,
    )
}

@Composable
fun OuiContent(
    state: OuiUiState,
    onBack: () -> Unit,
    onInputChange: (String) -> Unit,
    onPaste: (String) -> Unit,
    onSubmit: () -> Unit,
    onHistorySelected: (String) -> Unit,
    onHistoryRemove: (String) -> Unit,
    onClearHistory: () -> Unit,
    onUpdateDatabase: () -> Unit,
    onFindInNetwork: () -> Unit,
) {
    var showAbout by rememberSaveable { mutableStateOf(false) }
    val copy = rememberCopyAction()
    Scaffold(
        topBar = {
            NetrikTopAppBar(
                title = stringResource(R.string.tool_oui),
                onBack = onBack,
                actions = { AboutMenu(onAbout = { showAbout = true }) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "field") { MacField(state, onInputChange, onPaste, onSubmit) }
            item(key = "octets") { OctetPreview(state.parsed) }
            item(key = "submit") { SubmitButton(enabled = state.parsed.isQueryable, onClick = onSubmit) }
            item(key = "result") {
                ResultSection(state, copy, onInputChange, onUpdateDatabase, onFindInNetwork)
            }
            item(key = "history") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HistoryHeader(hasItems = state.history.isNotEmpty(), onClear = onClearHistory)
                    if (state.history.isEmpty()) {
                        Text(
                            text = stringResource(R.string.oui_history_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            state.history.forEachIndexed { index, item ->
                                HistoryRow(
                                    item = item,
                                    now = state.now,
                                    shapeIndex = index,
                                    shapeCount = state.history.size,
                                    onClick = { onHistorySelected(item.hex) },
                                    onRemove = { onHistoryRemove(item.hex) },
                                )
                            }
                        }
                    }
                }
            }
            item(key = "db") { DatabaseFooter(state.db, state.now, onUpdateDatabase) }
        }
    }
    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            icon = { Icon(painterResource(R.drawable.ic_info), contentDescription = null) },
            title = { Text(stringResource(R.string.oui_about_title)) },
            text = { Text(stringResource(R.string.oui_about_body)) },
            confirmButton = { TextButton(onClick = { showAbout = false }) { Text(stringResource(R.string.action_close)) } },
        )
    }
}

@Composable
private fun AboutMenu(onAbout: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.action_more))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.oui_about_title)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_info), contentDescription = null) },
                onClick = {
                    expanded = false
                    onAbout()
                },
            )
        }
    }
}

@Composable
private fun MacField(
    state: OuiUiState,
    onInputChange: (String) -> Unit,
    onPaste: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val parsed = state.parsed
    val isError = parsed is MacInput.InvalidChar || parsed is MacInput.TooLong ||
        (parsed is MacInput.Partial && state.showShortError)
    val ok = parsed.isQueryable

    val (supportIcon, supportText) = when (parsed) {
        MacInput.Empty -> null to stringResource(R.string.oui_support_empty)
        is MacInput.Partial -> (if (isError) R.drawable.ic_error_filled else null) to
            stringResource(R.string.oui_support_short, parsed.hex.length)
        is MacInput.Prefix -> R.drawable.ic_check_circle_filled to stringResource(R.string.oui_support_prefix, parsed.hex.length)
        is MacInput.Full -> R.drawable.ic_check_circle_filled to stringResource(R.string.oui_support_full)
        is MacInput.InvalidChar -> R.drawable.ic_error_filled to stringResource(R.string.oui_support_invalid_char, parsed.char.toString())
        is MacInput.TooLong -> R.drawable.ic_error_filled to stringResource(R.string.oui_support_too_long)
    }
    val supportColor = when {
        isError -> MaterialTheme.colorScheme.error
        ok -> NetrikTheme.extendedColors.success
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    OutlinedTextField(
        value = state.input,
        onValueChange = onInputChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.oui_field_label)) },
        placeholder = { Text(stringResource(R.string.oui_field_placeholder), style = NetrikTheme.dataTypography.dataMedium.copy(fontSize = 16.sp)) },
        leadingIcon = { Icon(painterResource(R.drawable.ic_memory), contentDescription = null) },
        trailingIcon = {
            Row {
                if (state.input.isNotEmpty()) {
                    IconButton(onClick = { onInputChange("") }) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.oui_action_clear))
                    }
                }
                IconButton(
                    onClick = {
                        scope.launch {
                            val text = clipboard.getClipEntry()?.clipData?.takeIf { it.itemCount > 0 }
                                ?.getItemAt(0)?.text?.toString()
                            if (!text.isNullOrBlank()) onPaste(text)
                        }
                    },
                ) {
                    Icon(painterResource(R.drawable.ic_content_paste), contentDescription = stringResource(R.string.oui_action_paste))
                }
            }
        },
        supportingText = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {
                if (supportIcon != null) {
                    Icon(painterResource(supportIcon), contentDescription = null, tint = supportColor, modifier = Modifier.size(14.dp).padding(top = 1.dp))
                }
                Text(supportText, color = supportColor)
            }
        },
        isError = isError,
        singleLine = true,
        textStyle = NetrikTheme.dataTypography.dataMedium.copy(fontSize = 16.sp),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters,
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Ascii,
            imeAction = ImeAction.Search,
        ),
        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
    )
}

/** Normalização ao vivo em 6 octetos; os 3 primeiros (OUI) em destaque. */
@Composable
private fun OctetPreview(parsed: MacInput) {
    val hex = parsed.hex
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = if (hex.isEmpty()) stringResource(R.string.oui_normalized_empty) else stringResource(R.string.oui_normalized, MacAddresses.format(hex)),
            style = NetrikTheme.dataTypography.dataSmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(6) { i ->
                val value = hex.drop(i * 2).take(2)
                val filled = value.isNotEmpty()
                val oui = i < 3
                val outline = MaterialTheme.colorScheme.outlineVariant
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .then(
                            if (filled) {
                                Modifier.background(
                                    if (oui) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                    RoundedCornerShape(8.dp),
                                )
                            } else {
                                Modifier.drawBehind {
                                    drawRoundRect(
                                        color = outline,
                                        cornerRadius = CornerRadius(8.dp.toPx()),
                                        style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))),
                                    )
                                }
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (filled) value.padEnd(2, '·') else "··",
                        style = NetrikTheme.dataTypography.dataMedium.copy(fontSize = 16.sp),
                        color = when {
                            !filled -> MaterialTheme.colorScheme.outline
                            oui -> MaterialTheme.colorScheme.onPrimaryContainer
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(R.string.oui_octets_prefix, R.string.oui_octets_device).forEach {
                Text(
                    text = stringResource(it).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SubmitButton(enabled: Boolean, onClick: () -> Unit) {
    // Sempre clicável: sem dígitos suficientes, o toque mostra o erro no campo.
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        colors = if (enabled) ButtonDefaults.buttonColors() else ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Icon(painterResource(R.drawable.ic_manage_search), contentDescription = null, modifier = Modifier.size(20.dp))
        Text(stringResource(R.string.oui_action_lookup), modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun ResultSection(
    state: OuiUiState,
    copy: CopyAction,
    onInputChange: (String) -> Unit,
    onUpdateDatabase: () -> Unit,
    onFindInNetwork: () -> Unit,
) {
    val result = state.result
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        when {
            result is OuiResult.Loading -> LoadingCard()
            result is OuiResult.Done -> {
                result.match?.let { MatchCard(it, result.multicast, copy, onFindInNetwork) }
                if (result.locallyAdministered) RandomCard(result.hex)
                if (result.match == null && !result.locallyAdministered) NotFoundCard(result.hex, onUpdateDatabase)
            }
            state.input.isEmpty() -> EmptyHelp(onInputChange)
        }
    }
}

@Composable
private fun LoadingCard() {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Text(stringResource(R.string.oui_loading), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MatchCard(match: OuiMatch, multicast: Boolean, copy: CopyAction, onFindInNetwork: () -> Unit) {
    val record = match.record
    val prefix = MacAddresses.format(record.prefix)
    val queried = paddedMac(match.queriedHex)
    val copiedVendor = stringResource(R.string.oui_copied_vendor)
    val copiedResult = stringResource(R.string.oui_copied_result)
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 16.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(48.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) { Icon(painterResource(R.drawable.ic_factory), contentDescription = null) }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.oui_vendor), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(record.organization, style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 26.sp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusChip(
                            label = stringResource(R.string.oui_registry_bits, record.registry.label, record.registry.bits),
                            tone = StatusTone.Success,
                            icon = R.drawable.ic_verified_filled,
                            size = StatusChipSize.Small,
                        )
                        StatusChip(
                            label = stringResource(if (multicast) R.string.oui_multicast else R.string.oui_unicast),
                            tone = StatusTone.Neutral,
                            icon = if (multicast) R.drawable.ic_hub else R.drawable.ic_radio_button_checked,
                            size = StatusChipSize.Small,
                        )
                    }
                }
                IconButton(onClick = { copy.copy(record.organization, copiedVendor) }) {
                    Icon(painterResource(R.drawable.ic_content_copy), contentDescription = stringResource(R.string.oui_copy_vendor), modifier = Modifier.size(20.dp))
                }
            }
            InfoRow(stringResource(R.string.oui_row_prefix), prefix, mono = true, copyMessage = stringResource(R.string.oui_copied_prefix), copy = copy)
            InfoRow(stringResource(R.string.oui_row_queried), queried, mono = true, copyMessage = stringResource(R.string.oui_copied_mac), copy = copy)
            if (record.address != null) {
                InfoRow(stringResource(R.string.oui_row_vendor_address), record.address, mono = false, copyMessage = stringResource(R.string.oui_copied_address), copy = copy)
            } else {
                InfoRow(stringResource(R.string.oui_row_vendor_address), stringResource(R.string.oui_private_registration), mono = false, copyMessage = null, copy = copy, muted = true)
            }
            FlowRow(
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(
                    onClick = {
                        val text = listOfNotNull(queried, record.organization, "OUI $prefix (${record.registry.label})", record.address).joinToString("\n")
                        copy.copy(text, copiedResult)
                    },
                ) {
                    Icon(painterResource(R.drawable.ic_copy_all), contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.oui_copy_result), modifier = Modifier.padding(start = 8.dp))
                }
                OutlinedButton(onClick = onFindInNetwork) {
                    Icon(painterResource(R.drawable.ic_lan), contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.oui_find_in_network), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, mono: Boolean, copyMessage: String?, copy: CopyAction, muted: Boolean = false) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        val content: @Composable () -> Unit = {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = value,
                        style = if (mono) NetrikTheme.dataTypography.dataMedium else MaterialTheme.typography.bodyMedium,
                        color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (copyMessage != null) {
                    Icon(painterResource(R.drawable.ic_content_copy), contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp))
                }
            }
        }
        if (copyMessage != null) {
            Surface(onClick = { copy.copy(value, copyMessage) }, color = Color.Transparent) { content() }
        } else {
            content()
        }
    }
}

@Composable
private fun RandomCard(hex: String) {
    val ext = NetrikTheme.extendedColors
    MessageCard(
        icon = R.drawable.ic_shuffle_filled,
        title = stringResource(R.string.oui_random_title),
        body = stringResource(R.string.oui_random_body, hex.getOrElse(1) { ' ' }.toString()),
        container = ext.warningContainer,
        content = ext.onWarningContainer,
    )
}

@Composable
private fun NotFoundCard(hex: String, onUpdateDatabase: () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(painterResource(R.drawable.ic_help), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.oui_not_found_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.oui_not_found_body, MacAddresses.format(hex.take(6))),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onUpdateDatabase) {
                    Icon(painterResource(R.drawable.ic_sync), contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.oui_update_database), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun MessageCard(@DrawableRes icon: Int, title: String, body: String, container: Color, content: Color) {
    Surface(shape = RoundedCornerShape(16.dp), color = container, contentColor = content, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(painterResource(icon), contentDescription = null)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun EmptyHelp(onExample: (String) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.oui_empty_help), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            EXAMPLES.forEach { example ->
                OutlinedButton(
                    onClick = { onExample(example) },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier.height(32.dp),
                ) {
                    Text(example, style = NetrikTheme.dataTypography.dataSmall.copy(fontSize = 13.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun HistoryHeader(hasItems: Boolean, onClear: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
        SectionHeader(stringResource(R.string.oui_history_title), modifier = Modifier.weight(1f))
        if (hasItems) TextButton(onClick = onClear) { Text(stringResource(R.string.oui_history_clear)) }
    }
}

@Composable
private fun HistoryRow(item: HistoryItem, now: Long, shapeIndex: Int, shapeCount: Int, onClick: () -> Unit, onRemove: () -> Unit) {
    val vendor = when {
        item.organization != null -> item.organization
        item.locallyAdministered -> stringResource(R.string.oui_history_random)
        else -> stringResource(R.string.oui_history_not_found)
    }
    Surface(
        shape = groupedItemShape(shapeIndex, shapeCount),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(onClick = onClick, color = Color.Transparent, modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.heightIn(min = 64.dp).padding(start = 16.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_history), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(MacAddresses.format(item.hex), style = NetrikTheme.dataTypography.dataMedium)
                        Text(
                            text = vendor,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                            color = if (item.organization != null) MaterialTheme.colorScheme.onSurfaceVariant else NetrikTheme.extendedColors.warning,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        RelativeTime.format(item.queriedAt, now, ZoneId.systemDefault()),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onRemove) {
                Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.oui_history_remove), modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun DatabaseFooter(db: OuiDbStatus?, now: Long, onUpdate: () -> Unit) {
    val update = db?.update ?: OuiUpdateState.Preparing
    val busy = update != OuiUpdateState.Idle
    val today = if (now > 0) java.time.Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate() else LocalDate.now()
    val stale = db?.dataDate?.let { it.plusDays(STALE_AFTER_DAYS) < today } == true
    val warning = NetrikTheme.extendedColors.warning

    val (icon, iconTint) = when {
        busy -> R.drawable.ic_cloud_sync to MaterialTheme.colorScheme.onSurfaceVariant
        stale -> R.drawable.ic_history to warning
        else -> R.drawable.ic_offline_pin to MaterialTheme.colorScheme.onSurfaceVariant
    }
    val title = when {
        update == OuiUpdateState.Preparing -> stringResource(R.string.oui_db_title_preparing)
        busy -> stringResource(R.string.oui_db_title_updating)
        stale -> stringResource(R.string.oui_db_title_old)
        else -> stringResource(R.string.oui_db_title)
    }
    val subtitle = when (update) {
        OuiUpdateState.Preparing -> stringResource(R.string.oui_db_subtitle_preparing)
        is OuiUpdateState.Downloading -> if (update.fraction != null) {
            stringResource(R.string.oui_db_subtitle_downloading_pct, update.registry.label, update.step, update.steps, (update.fraction * 100).toInt())
        } else {
            stringResource(R.string.oui_db_subtitle_downloading, update.registry.label, update.step, update.steps)
        }
        OuiUpdateState.Installing -> stringResource(R.string.oui_db_subtitle_installing)
        OuiUpdateState.Idle -> if (db?.prefixCount != null && db.dataDate != null) {
            stringResource(R.string.oui_db_subtitle, formatCount(db.prefixCount), RelativeTime.date(db.dataDate))
        } else {
            ""
        }
    }

    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(painterResource(icon), contentDescription = null, tint = iconTint)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                if (subtitle.isNotEmpty()) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            TextButton(onClick = onUpdate, enabled = !busy) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.oui_db_updating), modifier = Modifier.padding(start = 6.dp))
                } else {
                    Text(stringResource(R.string.oui_db_update))
                }
            }
        }
    }
}

/** MAC completo formatado, ou o prefixo completado com "··" até 6 octetos. */
private fun paddedMac(hex: String): String =
    (0 until 6).joinToString(":") { i -> hex.drop(i * 2).take(2).padEnd(2, '·') }

private fun formatCount(n: Int): String = NumberFormat.getIntegerInstance(Locale.forLanguageTag("pt-BR")).format(n)
