package com.netrik.feature.subnet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.netrik.R
import com.netrik.core.designsystem.component.SectionHeader
import com.netrik.core.designsystem.component.groupedItemShape
import com.netrik.core.designsystem.theme.NetrikTheme
import com.netrik.core.subnet.AddressType
import com.netrik.core.subnet.SubnetInputError
import com.netrik.core.ui.CopyAction
import java.math.BigInteger
import java.text.NumberFormat
import java.util.Locale

/** One label/value line of a result; tapping copies [value] (or [copyValue]). */
data class ResultRow(
    val label: String,
    val value: String,
    val mono: Boolean = true,
    val sub: String? = null,
    val copyValue: String? = value,
    val tint: Color? = null,
)

/** Section header with an optional "Copy all", then the rows as a grouped list. */
@Composable
fun ResultSection(title: String, rows: List<ResultRow>, copy: CopyAction, copyAllText: String? = null) {
    val copiedAll = stringResource(R.string.subnet_copied_all)
    val copiedOne = stringResource(R.string.copied_value)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionHeader(title, modifier = Modifier.weight(1f))
            if (copyAllText != null) {
                TextButton(onClick = { copy.copy(copyAllText, copiedAll) }) {
                    Icon(painterResource(R.drawable.ic_copy_all), contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.action_copy_all), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            rows.forEachIndexed { index, row -> ResultLine(row, groupedItemShape(index, rows.size), copy, copiedOne.format(row.label)) }
        }
    }
}

@Composable
private fun ResultLine(row: ResultRow, shape: RoundedCornerShape, copy: CopyAction, copied: String) {
    val colors = MaterialTheme.colorScheme
    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier.heightIn(min = 56.dp).padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(row.label, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                Text(
                    row.value,
                    style = if (row.mono) NetrikTheme.dataTypography.dataMedium.copy(fontSize = 15.sp) else MaterialTheme.typography.bodyLarge,
                    color = row.tint ?: colors.onSurface,
                )
                row.sub?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
            }
            if (row.copyValue != null) {
                Icon(painterResource(R.drawable.ic_content_copy), contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
        }
    }
    val copyValue = row.copyValue
    if (copyValue != null) {
        Surface(onClick = { copy.copy(copyValue, copied) }, shape = shape, color = colors.surfaceContainer, modifier = Modifier.fillMaxWidth()) { content() }
    } else {
        Surface(shape = shape, color = colors.surfaceContainer, modifier = Modifier.fillMaxWidth()) { content() }
    }
}

/** Multi-line field for networks/plans, in the data font. */
@Composable
fun MonoTextArea(value: String, onValueChange: (String) -> Unit, label: String, placeholder: String, supporting: String?, isError: Boolean = false) {
    val mono = NetrikTheme.dataTypography.dataMedium.copy(fontSize = 15.sp)
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder, style = mono) },
        supportingText = supporting?.let { { Text(it) } },
        isError = isError,
        minLines = 4,
        maxLines = 12,
        textStyle = mono,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false, capitalization = KeyboardCapitalization.None),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Count in the current language ("65,536" / "65.536"). */
fun formatCount(value: BigInteger): String = NumberFormat.getIntegerInstance(Locale.getDefault()).format(value)

/** Block sizes are powers of two: "65,536 (2^16)"; small ones without the power. */
fun formatSize(value: BigInteger): String {
    val exponent = value.bitLength() - 1
    val power = value.signum() > 0 && value.bitCount() == 1
    return if (power && exponent >= 16) "${formatCount(value)} (2^$exponent)" else formatCount(value)
}

/** Compact size for tight spots: "2^80" from 2^32 up, the full count below. */
fun formatSizeShort(value: BigInteger): String {
    val power = value.signum() > 0 && value.bitCount() == 1
    return if (power && value.bitLength() - 1 >= 32) "2^${value.bitLength() - 1}" else formatCount(value)
}

@Composable
fun inputErrorText(error: SubnetInputError): String = stringResource(
    when (error) {
        SubnetInputError.Empty, SubnetInputError.InvalidAddress -> R.string.subnet_error_address
        SubnetInputError.InvalidPrefix -> R.string.subnet_error_prefix
        SubnetInputError.InvalidMask -> R.string.subnet_error_mask
    },
)

@Composable
fun addressTypeText(type: AddressType): String = stringResource(
    when (type) {
        AddressType.ThisNetwork -> R.string.subnet_type_this_network
        AddressType.Private -> R.string.subnet_type_private
        AddressType.SharedCgnat -> R.string.subnet_type_cgnat
        AddressType.Loopback -> R.string.subnet_type_loopback
        AddressType.LinkLocal -> R.string.subnet_type_link_local
        AddressType.IetfProtocol -> R.string.subnet_type_ietf
        AddressType.Documentation -> R.string.subnet_type_documentation
        AddressType.Benchmarking -> R.string.subnet_type_benchmarking
        AddressType.Multicast -> R.string.subnet_type_multicast
        AddressType.Reserved -> R.string.subnet_type_reserved
        AddressType.LimitedBroadcast -> R.string.subnet_type_broadcast
        AddressType.Public -> R.string.subnet_type_public
        AddressType.Unspecified -> R.string.subnet_type_unspecified
        AddressType.Ipv4Mapped -> R.string.subnet_type_ipv4_mapped
        AddressType.Nat64 -> R.string.subnet_type_nat64
        AddressType.Teredo -> R.string.subnet_type_teredo
        AddressType.SixToFour -> R.string.subnet_type_6to4
        AddressType.UniqueLocal -> R.string.subnet_type_ula
        AddressType.GlobalUnicast -> R.string.subnet_type_global
    },
)
