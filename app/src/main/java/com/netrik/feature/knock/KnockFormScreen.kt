package com.netrik.feature.knock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.netrik.R
import com.netrik.core.designsystem.component.SectionHeader
import com.netrik.core.designsystem.theme.NetrikTheme
import com.netrik.core.knock.KnockField
import com.netrik.core.knock.KnockFieldError
import com.netrik.core.knock.KnockForm
import com.netrik.core.knock.KnockGroup
import com.netrik.core.knock.KnockProtocol
import com.netrik.core.knock.KnockStepError

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnockFormScreen(viewModel: KnockFormViewModel, onClose: () -> Unit) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.closed.collect { onClose() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (form.isNew) R.string.knock_form_new else R.string.knock_form_edit), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.action_close))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        bottomBar = {
            Column(modifier = Modifier.navigationBarsPadding().imePadding()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Button(
                    onClick = viewModel::save,
                    enabled = form.loaded,
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp).height(48.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(stringResource(R.string.action_save), modifier = Modifier.padding(start = 8.dp))
                }
            }
        },
    ) { padding ->
        if (!form.loaded) return@Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = form.name,
                onValueChange = { v -> viewModel.update { it.copy(name = v) } },
                label = { Text(stringResource(R.string.ssh_form_name)) },
                placeholder = { Text(stringResource(R.string.knock_form_name_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            DataField(
                value = form.host,
                onValueChange = { v -> viewModel.update { it.copy(host = v, errors = it.errors - KnockField.Host) } },
                label = stringResource(R.string.ssh_form_host),
                placeholder = stringResource(R.string.ssh_form_host_hint),
                icon = R.drawable.ic_dns,
                error = fieldErrorText(form.errors[KnockField.Host]),
                keyboardType = KeyboardType.Uri,
            )
            GroupPicker(form.groupId, groups) { id -> viewModel.update { it.copy(groupId = id) } }

            SectionHeader(stringResource(R.string.knock_form_sequence), modifier = Modifier.padding(top = 8.dp))
            form.steps.forEachIndexed { index, step ->
                StepCard(
                    index = index,
                    step = step,
                    count = form.steps.size,
                    error = form.stepErrors[index],
                    onProtocol = { viewModel.setStepProtocol(index, it) },
                    onValue = { viewModel.setStepValue(index, it) },
                    onMoveUp = { viewModel.moveStep(index, -1) },
                    onMoveDown = { viewModel.moveStep(index, 1) },
                    onRemove = { viewModel.removeStep(index) },
                )
            }
            fieldErrorText(form.errors[KnockField.Steps])?.let { error ->
                Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
            }
            if (form.steps.size < KnockForm.MAX_STEPS) {
                FilledTonalButton(onClick = viewModel::addStep, contentPadding = PaddingValues(start = 12.dp, end = 16.dp)) {
                    Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.knock_form_add_step), modifier = Modifier.padding(start = 6.dp))
                }
            }

            SectionHeader(stringResource(R.string.knock_form_options), modifier = Modifier.padding(top = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DataField(
                    value = form.delayMs,
                    onValueChange = { v -> viewModel.update { it.copy(delayMs = v.filter(Char::isDigit).take(5), errors = it.errors - KnockField.Delay) } },
                    label = stringResource(R.string.knock_form_delay),
                    error = fieldErrorText(form.errors[KnockField.Delay]),
                    supporting = stringResource(R.string.knock_form_delay_sup),
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f),
                )
                DataField(
                    value = form.verifyPort,
                    onValueChange = { v -> viewModel.update { it.copy(verifyPort = v.filter(Char::isDigit).take(5), errors = it.errors - KnockField.VerifyPort) } },
                    label = stringResource(R.string.knock_form_verify),
                    placeholder = "22",
                    error = fieldErrorText(form.errors[KnockField.VerifyPort]),
                    supporting = stringResource(R.string.knock_form_verify_sup),
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun StepCard(
    index: Int,
    step: StepDraft,
    count: Int,
    error: KnockStepError?,
    onProtocol: (KnockProtocol) -> Unit,
    onValue: (String) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(16.dp), color = colors.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.knock_form_step, index + 1),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onMoveUp, enabled = index > 0) {
                    Icon(painterResource(R.drawable.ic_arrow_upward), contentDescription = stringResource(R.string.knock_form_step_up))
                }
                IconButton(onClick = onMoveDown, enabled = index < count - 1) {
                    Icon(painterResource(R.drawable.ic_arrow_downward), contentDescription = stringResource(R.string.knock_form_step_down))
                }
                IconButton(onClick = onRemove, enabled = count > 1) {
                    Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.knock_form_step_remove))
                }
            }
            Column(modifier = Modifier.padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    KnockProtocol.entries.forEachIndexed { i, protocol ->
                        SegmentedButton(
                            selected = step.protocol == protocol,
                            onClick = { onProtocol(protocol) },
                            shape = SegmentedButtonDefaults.itemShape(i, KnockProtocol.entries.size),
                        ) { Text(protocol.label) }
                    }
                }
                val icmp = step.protocol == KnockProtocol.Icmp
                DataField(
                    value = step.value,
                    onValueChange = onValue,
                    label = stringResource(if (icmp) R.string.knock_form_payload else R.string.ssh_form_port),
                    placeholder = if (icmp) "56" else "7000",
                    error = when (error) {
                        KnockStepError.PortInvalid -> stringResource(R.string.knock_form_port_invalid)
                        KnockStepError.PayloadInvalid -> stringResource(R.string.knock_form_payload_invalid, KnockForm.MAX_PAYLOAD)
                        null -> null
                    },
                    supporting = if (icmp) stringResource(R.string.knock_form_payload_sup) else null,
                    keyboardType = KeyboardType.Number,
                )
            }
        }
    }
}

/** Technical data field in a monospaced font. */
@Composable
private fun DataField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    icon: Int? = null,
    error: String? = null,
    supporting: String? = null,
    keyboardType: KeyboardType = KeyboardType.Ascii,
) {
    val mono = NetrikTheme.dataTypography.dataMedium.copy(fontSize = 15.sp)
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, style = mono) } },
        leadingIcon = icon?.let { { Icon(painterResource(it), contentDescription = null) } },
        isError = error != null,
        supportingText = (error ?: supporting)?.let { { Text(it) } },
        singleLine = true,
        textStyle = mono,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, autoCorrectEnabled = false, capitalization = KeyboardCapitalization.None),
        modifier = modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupPicker(selected: Long?, groups: List<KnockGroup>, onSelect: (Long?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val noGroup = stringResource(R.string.ssh_no_group)
    // "No group" last, as in the list.
    val options = groups.map { it.id as Long? to it.name } + (null to noGroup)
    val current = options.firstOrNull { it.first == selected }?.second ?: noGroup
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = current,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.ssh_form_group)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_folder), contentDescription = null) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (id, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    leadingIcon = {
                        if (id == selected) {
                            Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        } else {
                            Spacer(Modifier.width(24.dp))
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelect(id)
                    },
                )
            }
        }
    }
}

@Composable
private fun fieldErrorText(error: KnockFieldError?): String? = when (error) {
    null -> null
    KnockFieldError.HostRequired -> stringResource(R.string.ssh_form_host_required)
    KnockFieldError.HostInvalid -> stringResource(R.string.ssh_form_host_invalid)
    KnockFieldError.DelayInvalid -> stringResource(R.string.knock_form_delay_invalid, KnockForm.MAX_DELAY_MS)
    KnockFieldError.VerifyPortInvalid -> stringResource(R.string.knock_form_port_invalid)
    KnockFieldError.NoSteps -> stringResource(R.string.knock_form_no_steps)
    KnockFieldError.TooManySteps -> stringResource(R.string.knock_form_too_many_steps, KnockForm.MAX_STEPS)
}
