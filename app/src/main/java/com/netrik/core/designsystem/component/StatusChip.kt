package com.netrik.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.netrik.R
import com.netrik.core.designsystem.theme.NetrikTheme

enum class StatusTone { Success, Warning, Error, Neutral, Running }

/** Tamanho 32dp: estado de execução. 24dp: atributo de um item. */
enum class StatusChipSize { Large, Small }

/** Chip de status: sempre cor + ícone + rótulo, iguais nos dois temas. */
@Composable
fun StatusChip(
    label: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int = tone.defaultIcon,
    size: StatusChipSize = StatusChipSize.Large,
) {
    val (container, content) = tone.colors()
    val large = size == StatusChipSize.Large
    Surface(
        modifier = modifier.height(if (large) 32.dp else 24.dp),
        shape = RoundedCornerShape(if (large) 8.dp else 6.dp),
        color = container,
        contentColor = content,
    ) {
        Row(
            modifier = Modifier.padding(start = if (large) 8.dp else 6.dp, end = if (large) 12.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (large) 6.dp else 4.dp),
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(if (large) 18.dp else 14.dp),
            )
            Text(
                text = label,
                style = if (large) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium,
            )
        }
    }
}

private val StatusTone.defaultIcon: Int
    get() = when (this) {
        StatusTone.Success, StatusTone.Running -> R.drawable.ic_check_circle_filled
        StatusTone.Warning -> R.drawable.ic_warning_filled
        StatusTone.Error -> R.drawable.ic_error_filled
        StatusTone.Neutral -> R.drawable.ic_do_not_disturb_on_filled
    }

@Composable
private fun StatusTone.colors(): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    val ext = NetrikTheme.extendedColors
    return when (this) {
        StatusTone.Success -> ext.successContainer to ext.onSuccessContainer
        StatusTone.Warning -> ext.warningContainer to ext.onWarningContainer
        StatusTone.Error -> scheme.errorContainer to scheme.onErrorContainer
        StatusTone.Neutral -> scheme.surfaceContainerHighest to scheme.onSurfaceVariant
        StatusTone.Running -> scheme.primaryContainer to scheme.onPrimaryContainer
    }
}
