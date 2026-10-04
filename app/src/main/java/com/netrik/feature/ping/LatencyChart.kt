package com.netrik.feature.ping

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.netrik.R
import com.netrik.core.designsystem.theme.NetrikTheme
import com.netrik.core.network.ping.PingSample

private const val WINDOW = 30
private const val HIGH_LATENCY_MS = 100.0
private val SCALE_STEPS = listOf(20, 50, 100, 200, 500, 1000, 2000, 5000)

/** Escala do eixo Y: o menor degrau que cabe 110% do maior tempo da janela (mínimo de 15 ms). */
internal fun chartTop(samples: List<PingSample>): Int {
    val peak = maxOf(15.0, samples.maxOfOrNull { it.timeMs ?: 0.0 } ?: 0.0) * 1.1
    return SCALE_STEPS.firstOrNull { it >= peak } ?: SCALE_STEPS.last()
}

/** Gráfico das últimas [WINDOW] amostras: linha e área em primary, limite de 100 ms e marcas de timeout. */
@Composable
fun LatencyChart(samples: List<PingSample>, continuousRunning: Boolean) {
    val window = samples.takeLast(WINDOW)
    val top = chartTop(window)
    val colors = MaterialTheme.colorScheme
    val warning = NetrikTheme.extendedColors.warning
    val labelStyle = NetrikTheme.dataTypography.dataSmall.copy(fontSize = 10.sp, color = colors.onSurfaceVariant)
    val measurer = rememberTextMeasurer()
    val hasTimeout = window.any { it.timeMs == null }
    val showLimit = top > HIGH_LATENCY_MS

    Surface(shape = RoundedCornerShape(12.dp), color = colors.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row {
                Text(stringResource(R.string.ping_chart_title), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text(
                    if (continuousRunning) stringResource(R.string.ping_chart_continuous) else stringResource(R.string.ping_chart_samples, window.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                )
            }
            Canvas(modifier = Modifier.fillMaxWidth().height(124.dp)) {
                val left = 32.dp.toPx()
                val topY = 8.dp.toPx()
                val bottomY = size.height - 12.dp.toPx()
                val plotW = size.width - left
                fun x(i: Int) = left + i * plotW / (WINDOW - 1)
                fun y(ms: Double) = (bottomY - (ms / top) * (bottomY - topY)).toFloat()
                val dash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))

                // Grade e eixos
                listOf(top.toDouble() to topY, top / 2.0 to (topY + bottomY) / 2).forEach { (value, yy) ->
                    drawLine(colors.outlineVariant, Offset(left, yy), Offset(size.width, yy), pathEffect = dash)
                    drawText(measurer, value.toInt().toString(), Offset(0f, yy - 6.dp.toPx()), labelStyle)
                }
                drawLine(colors.outline, Offset(left, bottomY), Offset(size.width, bottomY))
                drawText(measurer, "0", Offset(0f, bottomY - 6.dp.toPx()), labelStyle)

                if (showLimit) {
                    val ly = y(HIGH_LATENCY_MS)
                    drawLine(warning, Offset(left, ly), Offset(size.width, ly), strokeWidth = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
                }

                val points = window.mapIndexedNotNull { i, s -> s.timeMs?.let { Offset(x(i), y(it)) } }
                if (points.size > 1) {
                    val area = Path().apply {
                        moveTo(points.first().x, bottomY)
                        points.forEach { lineTo(it.x, it.y) }
                        lineTo(points.last().x, bottomY)
                        close()
                    }
                    drawPath(area, colors.primary.copy(alpha = 0.12f))
                    val line = Path().apply {
                        moveTo(points.first().x, points.first().y)
                        points.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    drawPath(line, colors.primary, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                window.forEachIndexed { i, s ->
                    if (s.timeMs == null) {
                        drawLine(colors.error, Offset(x(i), bottomY), Offset(x(i), bottomY - 14.dp.toPx()), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
                    }
                }
                points.lastOrNull()?.let { drawCircle(colors.primary, radius = 3.5.dp.toPx(), center = it) }
            }
            if (showLimit || hasTimeout) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (showLimit) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Canvas(Modifier.width(14.dp).height(2.dp)) {
                                drawLine(warning, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = size.height, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 3f)))
                            }
                            Text(stringResource(R.string.ping_chart_limit), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                        }
                    }
                    if (hasTimeout) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Canvas(Modifier.size(width = 3.dp, height = 10.dp)) { drawRect(colors.error) }
                            Text(stringResource(R.string.ping_chart_timeout), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
