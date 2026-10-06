package com.netrik.feature.wifi

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.netrik.R
import com.netrik.core.designsystem.theme.NetrikTheme
import com.netrik.core.network.WifiBand
import com.netrik.core.wifi.ChannelAdvice
import com.netrik.core.wifi.ChannelScore

/** "Best channel" card: the least congested channel and how the connected network's channel compares. */
@Composable
fun ChannelAdviceCard(advice: ChannelAdvice, networksInBand: Int) {
    val colors = MaterialTheme.colorScheme
    val best = advice.best ?: return
    Surface(shape = RoundedCornerShape(16.dp), color = colors.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.wifi_channels_best, advice.band.label), style = MaterialTheme.typography.labelLarge, color = colors.primary)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(best.channel.toString(), style = NetrikTheme.dataTypography.dataLarge.copy(fontSize = 40.sp, lineHeight = 44.sp))
                Text(
                    networksText(best) + if (best.dfs) " · " + stringResource(R.string.wifi_channels_dfs) else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            if (networksInBand == 0) {
                Text(stringResource(R.string.wifi_channels_empty_band, advice.band.label), style = MaterialTheme.typography.bodyMedium)
            }
            val current = advice.current
            if (current != null) {
                val good = advice.currentIsGood
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (good) NetrikTheme.extendedColors.successContainer else NetrikTheme.extendedColors.warningContainer,
                    contentColor = if (good) NetrikTheme.extendedColors.onSuccessContainer else NetrikTheme.extendedColors.onWarningContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(
                            painterResource(if (good) R.drawable.ic_check_circle_filled else R.drawable.ic_warning_filled),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            if (good) {
                                stringResource(R.string.wifi_channels_current_good, current.channel, networksText(current))
                            } else {
                                stringResource(R.string.wifi_channels_current_change, current.channel, networksText(current), best.channel)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            Text(
                stringResource(if (advice.band == WifiBand.GHz2_4) R.string.wifi_channels_note_24 else R.string.wifi_channels_note),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

/** One bar per candidate channel, least congested on top; the best and the current one are marked. */
@Composable
fun ChannelRanking(advice: ChannelAdvice) {
    val colors = MaterialTheme.colorScheme
    val maxScore = advice.ranking.maxOfOrNull { it.score }?.takeIf { it > 0 } ?: 1.0
    val best = advice.best?.channel
    val current = advice.current?.channel
    Surface(shape = RoundedCornerShape(16.dp), color = colors.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.wifi_channels_ranking), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            advice.ranking.forEach { score ->
                val fraction = (score.score / maxScore).toFloat().coerceIn(0f, 1f)
                val barColor = when {
                    score.channel == best -> NetrikTheme.extendedColors.success
                    fraction > 0.66f -> colors.error
                    fraction > 0.33f -> NetrikTheme.extendedColors.warning
                    else -> colors.primary
                }
                Row(modifier = Modifier.fillMaxWidth().heightIn(min = 28.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        score.channel.toString(),
                        style = NetrikTheme.dataTypography.dataMedium.copy(fontSize = 14.sp),
                        fontWeight = if (score.channel == best || score.channel == current) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.width(36.dp),
                    )
                    Box(modifier = Modifier.weight(1f).height(10.dp).background(colors.surfaceContainerHighest, RoundedCornerShape(5.dp))) {
                        Box(modifier = Modifier.fillMaxWidth(fraction.coerceAtLeast(0.02f)).fillMaxHeight().background(barColor, RoundedCornerShape(5.dp)))
                    }
                    Text(
                        listOfNotNull(
                            stringResource(R.string.wifi_channels_mark_best).takeIf { score.channel == best },
                            stringResource(R.string.wifi_channels_mark_current).takeIf { score.channel == current },
                            "DFS".takeIf { score.dfs },
                            score.networks.toString(),
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (score.channel == best) NetrikTheme.extendedColors.success else colors.onSurfaceVariant,
                        modifier = Modifier.width(112.dp),
                        maxLines = 1,
                    )
                }
            }
            Text(stringResource(R.string.wifi_channels_ranking_note), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun networksText(score: ChannelScore): String =
    if (score.networks == 0) stringResource(R.string.wifi_channels_free) else pluralStringResource(R.plurals.wifi_channels_networks, score.networks, score.networks)

