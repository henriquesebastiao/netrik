package com.netrik.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.netrik.R
import com.netrik.core.designsystem.theme.NetrikTheme

/** Campo "IP ou domínio" com limpar, validação e ação de teclado. */
@Composable
fun TargetField(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    error: String?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    @DrawableRes leadingIcon: Int = R.drawable.ic_public,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        label = { Text(stringResource(R.string.target_label)) },
        placeholder = { Text(stringResource(R.string.target_placeholder), style = NetrikTheme.dataTypography.dataMedium.copy(fontSize = 16.sp)) },
        leadingIcon = { Icon(painterResource(leadingIcon), contentDescription = null) },
        trailingIcon = if (value.isNotEmpty() && enabled) {
            {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.oui_action_clear))
                }
            }
        } else {
            null
        },
        supportingText = error?.let {
            {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_error_filled), contentDescription = null, modifier = Modifier.size(14.dp))
                    Text(it)
                }
            }
        },
        isError = error != null,
        singleLine = true,
        textStyle = NetrikTheme.dataTypography.dataMedium.copy(fontSize = 16.sp),
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { onSubmit() }),
    )
}

/** Chips de alvos recentes, roláveis na horizontal, encostando nas bordas da tela. */
@Composable
fun RecentTargets(targets: List<String>, enabled: Boolean, onSelect: (String) -> Unit) {
    if (targets.isEmpty()) return
    LazyRow(
        modifier = Modifier.bleedHorizontal(16.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(targets, key = { it }) { target ->
            OutlinedButton(
                onClick = { onSelect(target) },
                enabled = enabled,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                contentPadding = PaddingValues(start = 8.dp, end = 12.dp),
                modifier = Modifier.height(32.dp),
            ) {
                Icon(
                    painterResource(R.drawable.ic_history),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = target,
                    style = NetrikTheme.dataTypography.dataSmall.copy(fontSize = 13.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
    }
}

/** Painel recolhível "Opções avançadas" com resumo das opções atuais. */
@Composable
fun AdvancedOptionsPanel(
    summary: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Surface(onClick = onToggle, color = Color.Transparent) {
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_tune), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.advanced_options), style = MaterialTheme.typography.titleSmall)
                        Text(summary, style = NetrikTheme.dataTypography.dataSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(
                        painterResource(if (expanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (expanded) {
                Box(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp)) { content() }
            }
        }
    }
}

/** Campo numérico compacto das opções avançadas. */
@Composable
fun NumberOptionField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    suffix: String?,
    isError: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    decimal: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        label = { Text(label) },
        suffix = suffix?.let { { Text(it) } },
        isError = isError,
        singleLine = true,
        textStyle = NetrikTheme.dataTypography.dataMedium.copy(fontSize = 15.sp),
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number, imeAction = ImeAction.Done),
    )
}

/** Ação principal: Iniciar (primary) ou Parar (errorContainer), 48dp e largura total. */
@Composable
fun RunButton(running: Boolean, enabled: Boolean, onStart: () -> Unit, onStop: () -> Unit) {
    Button(
        onClick = if (running) onStop else onStart,
        enabled = enabled || running,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        colors = if (running) {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            )
        } else {
            ButtonDefaults.buttonColors()
        },
    ) {
        Icon(
            painterResource(if (running) R.drawable.ic_stop_filled else R.drawable.ic_play_arrow_filled),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Text(stringResource(if (running) R.string.action_stop else R.string.action_start), modifier = Modifier.padding(start = 8.dp))
    }
}

/** Estado vazio de uma ferramenta: ícone em círculo e orientação. */
@Composable
fun ToolEmptyState(@DrawableRes icon: Int, text: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.size(56.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
            }
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

/** Cabeçalho da execução: chip de estado, botão Copiar e linha com alvo e parâmetros. */
@Composable
fun RunHeader(
    chipLabel: String,
    chipTone: StatusTone,
    running: Boolean,
    runLine: String,
    onCopy: (() -> Unit)?,
    @DrawableRes chipIcon: Int? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
            if (running) {
                RunningChip(chipLabel)
            } else if (chipIcon != null) {
                StatusChip(chipLabel, chipTone, icon = chipIcon)
            } else {
                StatusChip(chipLabel, chipTone)
            }
            Box(modifier = Modifier.weight(1f))
            if (onCopy != null) {
                TextButton(onClick = onCopy) {
                    Icon(painterResource(R.drawable.ic_content_copy), contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.action_copy), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        Text(runLine, style = NetrikTheme.dataTypography.dataSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
    }
}

@Composable
private fun RunningChip(label: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.height(32.dp),
    ) {
        Row(modifier = Modifier.padding(start = 8.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Card de erro com ação "Tentar novamente" (errorContainer). */
@Composable
fun ErrorCard(title: String, body: String, onRetry: (() -> Unit)?) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(painterResource(R.drawable.ic_error_filled), contentDescription = null)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(body, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (onRetry != null) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                        contentPadding = PaddingValues(start = 12.dp, end = 16.dp),
                    ) {
                        Icon(painterResource(R.drawable.ic_refresh), contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.action_retry), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}

/** Aviso de rede ausente: as ferramentas de rede ficam indisponíveis. */
@Composable
fun NoConnectionCard() {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(painterResource(R.drawable.ic_signal_disconnected), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.no_connection_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.no_connection_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Estende o conteúdo [margin] para fora dos dois lados (ex.: lista horizontal até a borda da tela). */
fun Modifier.bleedHorizontal(margin: Dp): Modifier = layout { measurable, constraints ->
    val extra = (margin * 2).roundToPx()
    val placeable = measurable.measure(constraints.copy(minWidth = constraints.maxWidth + extra, maxWidth = constraints.maxWidth + extra))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-extra / 2, 0) }
}
