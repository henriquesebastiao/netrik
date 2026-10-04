package com.netrik.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.netrik.R
import com.netrik.core.designsystem.theme.NetrikTheme

/**
 * Tela de explicação antes de uma permissão do sistema (padrão da aba Wi-Fi no design):
 * ícone, título, motivo, itens do que é e não é feito, aviso opcional e ações.
 */
@Composable
fun PermissionRationale(
    @DrawableRes icon: Int,
    title: String,
    body: String,
    items: List<Pair<Int, String>>,
    note: String?,
    primaryLabel: String,
    onPrimary: () -> Unit,
    laterLabel: String,
    onLater: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 40.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Surface(shape = RoundedCornerShape(24.dp), color = colors.primaryContainer, contentColor = colors.onPrimaryContainer, modifier = Modifier.size(72.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(36.dp)) }
            }
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items.forEachIndexed { i, (itemIcon, text) ->
                Surface(shape = groupedItemShape(i, items.size), color = colors.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Icon(painterResource(itemIcon), contentDescription = null, tint = colors.onSurfaceVariant)
                        Text(text, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        if (note != null) {
            val ext = NetrikTheme.extendedColors
            Surface(shape = RoundedCornerShape(12.dp), color = ext.warningContainer, contentColor = ext.onWarningContainer, modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(painterResource(R.drawable.ic_warning_filled), contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(note, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onPrimary, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text(primaryLabel) }
            TextButton(onClick = onLater, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text(laterLabel) }
        }
    }
}
