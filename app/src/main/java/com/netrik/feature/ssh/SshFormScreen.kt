package com.netrik.feature.ssh

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.netrik.R
import com.netrik.core.designsystem.theme.NetrikTheme
import com.netrik.core.ssh.SshAuth
import com.netrik.core.ssh.SshField
import com.netrik.core.ssh.SshGroup
import com.netrik.core.ui.LocalSnackbarHostState
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SshFormScreen(viewModel: SshViewModel, onClose: () -> Unit) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val list by viewModel.list.collectAsStateWithLifecycle()
    val dialog by viewModel.dialog.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbarHostState.current
    val context = LocalContext.current
    val close = {
        viewModel.onFormClosed()
        onClose()
    }
    BackHandler(onBack = close)

    LaunchedEffect(viewModel) {
        // Para de ouvir ao fechar: o aviso que vem depois ("Autenticado como…") fica para a lista.
        viewModel.events.takeWhile { it != SshEvent.CloseForm }.collect { event ->
            if (event is SshEvent.Message) launch { snackbar.showSnackbar(sshMessageText(context, event.text)) }
        }
        close()
    }

    val pickKey = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::onKeyPicked)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (form.isNew) R.string.ssh_form_new else R.string.ssh_form_edit), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = close) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.action_close))
                    }
                },
                actions = {
                    if (!form.isNew) {
                        TextButton(onClick = { viewModel.submitForm(connect = false) }) { Text(stringResource(R.string.action_save)) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        bottomBar = {
            Column(modifier = Modifier.navigationBarsPadding().imePadding()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Button(
                    onClick = { viewModel.submitForm() },
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp).height(48.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_terminal), contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(
                        stringResource(if (form.save || form.isEditing) R.string.ssh_form_submit_save else R.string.ssh_form_submit),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        },
    ) { padding ->
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
                onValueChange = { v -> viewModel.updateForm { it.copy(name = v) } },
                label = { Text(stringResource(R.string.ssh_form_name)) },
                placeholder = { Text(stringResource(R.string.ssh_form_name_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            DataField(
                value = form.host,
                onValueChange = { v -> viewModel.updateForm { it.copy(host = v) } },
                label = stringResource(R.string.ssh_form_host),
                placeholder = stringResource(R.string.ssh_form_host_hint),
                icon = R.drawable.ic_dns,
                error = form.errors[SshField.Host],
                supporting = stringResource(R.string.ssh_form_host_sup),
                keyboardType = KeyboardType.Uri,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DataField(
                    value = form.port,
                    onValueChange = { v -> viewModel.updateForm { it.copy(port = v.filter(Char::isDigit).take(5)) } },
                    label = stringResource(R.string.ssh_form_port),
                    error = form.errors[SshField.Port],
                    supporting = stringResource(R.string.ssh_form_port_sup),
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.width(104.dp),
                )
                DataField(
                    value = form.user,
                    onValueChange = { v -> viewModel.updateForm { it.copy(user = v) } },
                    label = stringResource(R.string.ssh_form_user),
                    placeholder = stringResource(R.string.ssh_form_user_hint),
                    icon = R.drawable.ic_person,
                    error = form.errors[SshField.User],
                    modifier = Modifier.weight(1f),
                )
            }
            AuthSelector(form.auth) { auth -> viewModel.updateForm { it.copy(auth = auth) } }
            if (form.auth == SshAuth.Password) {
                PasswordField(form, viewModel)
            } else {
                KeySection(form, viewModel, onPick = { pickKey.launch(arrayOf("*/*")) })
            }
            GroupPicker(form.groupId, list.allGroups) { id -> viewModel.updateForm { it.copy(groupId = id) } }
            if (form.isNew) {
                val groupName = list.allGroups.firstOrNull { it.id == form.groupId }?.name ?: stringResource(R.string.ssh_no_group)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clickable { viewModel.updateForm { it.copy(save = !it.save) } }
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.ssh_form_save), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (form.save) stringResource(R.string.ssh_form_save_on, groupName) else stringResource(R.string.ssh_form_save_off),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = form.save,
                        onCheckedChange = { checked -> viewModel.updateForm { it.copy(save = checked) } },
                        thumbContent = if (form.save) {
                            { Icon(painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }

    SshDialogHost(dialog, viewModel)
}

/** Campo de dado técnico (host, porta, usuário) em fonte monoespaçada. */
@Composable
private fun DataField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
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
        modifier = modifier,
    )
}

@Composable
private fun AuthSelector(selected: SshAuth, onSelect: (SshAuth) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.ssh_form_auth),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        val options = listOf(
            Triple(SshAuth.Password, R.string.ssh_form_auth_password, R.drawable.ic_password),
            Triple(SshAuth.Key, R.string.ssh_form_auth_key, R.drawable.ic_vpn_key),
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (auth, label, icon) ->
                SegmentedButton(
                    selected = selected == auth,
                    onClick = { onSelect(auth) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    icon = {
                        SegmentedButtonDefaults.Icon(active = selected == auth) {
                            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    },
                ) { Text(stringResource(label)) }
            }
        }
    }
}

@Composable
private fun PasswordField(form: SshFormState, viewModel: SshViewModel) {
    val error = form.errors[SshField.Password]
    val keep = form.isEditing && form.hasStoredPassword && !form.passwordRejected
    OutlinedTextField(
        value = form.password,
        onValueChange = { v -> viewModel.updateForm { it.copy(password = v) } },
        label = { Text(stringResource(R.string.ssh_form_password)) },
        isError = error != null,
        supportingText = {
            Text(error ?: stringResource(if (keep) R.string.ssh_form_password_keep else R.string.ssh_form_password_sup))
        },
        singleLine = true,
        textStyle = NetrikTheme.dataTypography.dataMedium.copy(fontSize = 15.sp),
        visualTransformation = if (form.showPassword) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        trailingIcon = {
            IconButton(onClick = { viewModel.updateForm { it.copy(showPassword = !it.showPassword) } }) {
                Icon(
                    painterResource(if (form.showPassword) R.drawable.ic_visibility_off else R.drawable.ic_visibility),
                    contentDescription = stringResource(if (form.showPassword) R.string.ssh_form_password_hide else R.string.ssh_form_password_show),
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun KeySection(form: SshFormState, viewModel: SshViewModel, onPick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val keyError = form.errors[SshField.Key] ?: when (form.keyError) {
        KeyFileError.Invalid -> stringResource(R.string.ssh_form_key_invalid)
        KeyFileError.TooLarge -> stringResource(R.string.ssh_form_key_too_large)
        KeyFileError.Unreadable -> stringResource(R.string.ssh_form_key_unreadable)
        null -> null
    }
    val name = form.key?.name ?: form.storedKeyName
    val info = form.key?.let { key ->
        if (key.encrypted) stringResource(R.string.ssh_form_key_encrypted, key.info) else key.info
    } ?: form.storedKeyInfo
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            border = if (keyError != null) BorderStroke(2.dp, colors.error) else BorderStroke(1.dp, colors.outline),
            color = colors.surface,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(painterResource(R.drawable.ic_vpn_key), contentDescription = null, tint = colors.onSurfaceVariant)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.ssh_form_key),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = colors.onSurfaceVariant,
                    )
                    Text(
                        name ?: stringResource(R.string.ssh_form_key_none),
                        style = NetrikTheme.dataTypography.dataMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
                        fontWeight = FontWeight.Medium,
                        color = if (name == null) colors.onSurfaceVariant else colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        info ?: stringResource(R.string.ssh_form_key_formats),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
                FilledTonalButton(onClick = onPick, contentPadding = PaddingValues(start = 12.dp, end = 16.dp)) {
                    Icon(painterResource(R.drawable.ic_folder_open), contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        stringResource(if (name == null) R.string.ssh_form_key_pick else R.string.ssh_form_key_change),
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
        }
        if (keyError != null) {
            Text(keyError, style = MaterialTheme.typography.bodySmall, color = colors.error, modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
    val passphraseError = when (form.passphraseError) {
        PassphraseError.Required -> stringResource(R.string.ssh_form_key_passphrase_required)
        PassphraseError.Wrong -> stringResource(R.string.ssh_form_key_passphrase_wrong)
        null -> null
    }
    val keepPassphrase = form.key == null && form.hasStoredPassphrase
    OutlinedTextField(
        value = form.keyPassphrase,
        onValueChange = { v -> viewModel.updateForm { it.copy(keyPassphrase = v) } },
        label = { Text(stringResource(R.string.ssh_form_key_passphrase)) },
        isError = passphraseError != null,
        supportingText = (passphraseError ?: if (keepPassphrase) stringResource(R.string.ssh_form_key_passphrase_keep) else null)?.let { { Text(it) } },
        singleLine = true,
        textStyle = NetrikTheme.dataTypography.dataMedium.copy(fontSize = 15.sp),
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupPicker(selected: Long?, groups: List<SshGroup>, onSelect: (Long?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val noGroup = stringResource(R.string.ssh_no_group)
    // "Sem grupo" por último, como na lista de hosts.
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
                            androidx.compose.foundation.layout.Spacer(Modifier.size(24.dp))
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
