package com.netrik.feature.wifi

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.netrik.R
import com.netrik.core.designsystem.component.NetrikTopAppBar
import com.netrik.core.designsystem.component.PlaceholderContent
import com.netrik.core.designsystem.theme.NetrikTheme
import com.netrik.core.network.WifiChannels
import com.netrik.core.wifi.SignalHistory
import com.netrik.core.wifi.SignalMeter
import com.netrik.core.wifi.SignalQuality
import com.netrik.core.wifi.WifiLinkSample
import com.netrik.core.wifi.WifiStandard
import kotlinx.coroutines.delay

/**
 * Live signal meter of the connected network, to walk around looking for good and bad spots. Not in the design:
 * built from the design system. Keeps the screen on while open; the optional beep speeds up as the signal improves.
 */
@Composable
fun WifiMeterScreen(onBack: () -> Unit, viewModel: WifiMeterViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    val latest = state.history.latest
    if (state.sound && latest != null && state.connected) BeepLoop(latest.rssiDbm)

    Scaffold(
        topBar = {
            NetrikTopAppBar(
                title = stringResource(R.string.wifi_meter_title),
                onBack = onBack,
                actions = {
                    IconButton(onClick = viewModel::onToggleSound) {
                        Icon(
                            painterResource(if (state.sound) R.drawable.ic_volume_up else R.drawable.ic_volume_off),
                            contentDescription = stringResource(if (state.sound) R.string.wifi_meter_sound_off else R.string.wifi_meter_sound_on),
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (state.started && !state.connected) {
            PlaceholderContent(
                icon = R.drawable.ic_wifi_off,
                title = stringResource(R.string.wifi_meter_disconnected_title),
                text = stringResource(R.string.wifi_meter_disconnected_body),
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 4.dp)) {
                Text(state.ssid ?: stringResource(R.string.wifi_meter_network), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                state.bssid?.let { Text(it, style = NetrikTheme.dataTypography.dataSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Gauge(latest?.rssiDbm)
            HistoryCard(state.history)
            latest?.let { LinkCard(it) }
            RttSection(
                responder = state.rttResponder,
                support = state.rttSupport,
                rtt = state.rtt,
                onStart = viewModel::onStartRanging,
                onStop = viewModel::onStopRanging,
            )
            Text(stringResource(R.string.wifi_meter_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
        }
    }
}

/** 240° arc from −95 to −30 dBm, filled up to the current signal in its quality color. */
@Composable
private fun Gauge(rssiDbm: Int?) {
    val colors = MaterialTheme.colorScheme
    val quality = rssiDbm?.let(SignalQuality::of)
    val fill = quality?.let { qualityColor(it) } ?: colors.outline
    val track = colors.surfaceContainerHighest
    Surface(shape = RoundedCornerShape(16.dp), color = colors.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp), contentAlignment = Alignment.TopCenter) {
            // The 240° arc leaves the bottom of its circle empty: the box is shorter than wide and the text sits on the circle's center.
            Box(modifier = Modifier.fillMaxWidth(0.72f).aspectRatio(GAUGE_ASPECT)) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val stroke = 18.dp.toPx()
                    val diameter = size.width - stroke
                    val topLeft = Offset(stroke / 2, stroke / 2)
                    val arc = Size(diameter, diameter)
                    drawArc(track, startAngle = 150f, sweepAngle = 240f, useCenter = false, topLeft = topLeft, size = arc, style = Stroke(stroke, cap = StrokeCap.Round))
                    if (rssiDbm != null) {
                        drawArc(fill, startAngle = 150f, sweepAngle = 240f * SignalMeter.fraction(rssiDbm), useCenter = false, topLeft = topLeft, size = arc, style = Stroke(stroke, cap = StrokeCap.Round))
                    }
                }
                Column(modifier = Modifier.align(BiasAlignment(0f, GAUGE_TEXT_BIAS)), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        rssiDbm?.let { formatDbm(it) } ?: "—",
                        style = NetrikTheme.dataTypography.dataLarge.copy(fontSize = 52.sp, lineHeight = 56.sp),
                        color = fill,
                    )
                    Text("dBm", style = NetrikTheme.dataTypography.dataMedium, color = colors.onSurfaceVariant)
                    Text(quality?.let { qualityLabel(it) } ?: stringResource(R.string.wifi_meter_waiting), style = MaterialTheme.typography.titleMedium, color = fill)
                }
            }
        }
    }
}

/** Width/height of the gauge: a circle whose lowest quarter (below the arc ends) is cut off. */
private const val GAUGE_ASPECT = 1.18f

/** Vertical position of the circle's center inside that box (−1 top, 1 bottom). */
private const val GAUGE_TEXT_BIAS = 0.18f

/** Signal over the last two minutes, with the quality thresholds as dashed lines. */
@Composable
private fun HistoryCard(history: SignalHistory) {
    val colors = MaterialTheme.colorScheme
    val line = colors.primary
    val grid = colors.outlineVariant
    Surface(shape = RoundedCornerShape(16.dp), color = colors.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.wifi_meter_history), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            Canvas(modifier = Modifier.fillMaxWidth().height(140.dp)) {
                fun y(dbm: Int) = size.height * (1f - SignalMeter.fraction(dbm))
                listOf(-60, -70, -80).forEach { level ->
                    drawLine(grid, Offset(0f, y(level)), Offset(size.width, y(level)), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
                }
                val samples = history.samples
                val end = samples.lastOrNull()?.timeMillis ?: return@Canvas
                val start = end - history.windowMillis
                fun x(time: Long) = size.width * ((time - start).toFloat() / history.windowMillis)
                val path = Path()
                samples.forEachIndexed { index, s ->
                    if (index == 0) path.moveTo(x(s.timeMillis), y(s.rssiDbm)) else path.lineTo(x(s.timeMillis), y(s.rssiDbm))
                }
                drawPath(path, line, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
                drawCircle(line, radius = 4.dp.toPx(), center = Offset(x(end), y(samples.last().rssiDbm)))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat(stringResource(R.string.wifi_meter_min), history.min, Modifier.weight(1f))
                Stat(stringResource(R.string.wifi_meter_avg), history.average, Modifier.weight(1f))
                Stat(stringResource(R.string.wifi_meter_max), history.max, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Stat(label: String, dbm: Int?, modifier: Modifier) {
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface, modifier = modifier) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(dbm?.let { stringResource(R.string.wifi_dbm, formatDbm(it)) } ?: "—", style = NetrikTheme.dataTypography.dataMedium.copy(fontSize = 16.sp))
        }
    }
}

@Composable
private fun LinkCard(sample: WifiLinkSample) {
    val unknown = stringResource(R.string.field_unavailable)
    val rows = listOf(
        stringResource(R.string.wifi_meter_tx) to (sample.txMbps?.let { "$it Mbps" } ?: unknown),
        stringResource(R.string.wifi_meter_rx) to (sample.rxMbps?.let { "$it Mbps" } ?: unknown),
        stringResource(R.string.wifi_meter_standard) to standardText(sample.standard),
        stringResource(R.string.field_frequency) to (sample.frequencyMhz?.let { mhz ->
            val channel = WifiChannels.frequencyToChannel(mhz)
            if (channel != null) stringResource(R.string.wifi_meter_frequency, mhz, channel) else "$mhz MHz"
        } ?: unknown),
    )
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.wifi_meter_link), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            rows.forEach { (label, value) ->
                Row {
                    Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Text(value, style = NetrikTheme.dataTypography.dataMedium.copy(fontSize = 15.sp))
                }
            }
        }
    }
}

@Composable
private fun standardText(standard: WifiStandard?): String = when {
    standard == null -> stringResource(R.string.field_unavailable)
    standard.generation != null -> stringResource(R.string.wifi_meter_generation, standard.generation)
    standard == WifiStandard.WiGig -> "802.11ad (WiGig)"
    else -> "802.11a/b/g"
}

/** "Geiger counter": a short beep whose spacing follows the latest signal. */
@Composable
private fun BeepLoop(rssiDbm: Int) {
    val current by rememberUpdatedState(rssiDbm)
    LaunchedEffect(Unit) {
        val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 70)
        try {
            while (true) {
                tone.startTone(ToneGenerator.TONE_PROP_BEEP, 60)
                delay(SignalMeter.beepIntervalMillis(current))
            }
        } finally {
            tone.release()
        }
    }
}
