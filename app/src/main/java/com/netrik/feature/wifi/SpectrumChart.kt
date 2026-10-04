package com.netrik.feature.wifi

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.netrik.core.designsystem.theme.NetrikTheme
import com.netrik.core.network.WifiBand
import com.netrik.core.wifi.SpectrumAxis
import com.netrik.core.wifi.WifiNetwork

private const val TOP_DBM = -20
private const val BOTTOM_DBM = -100
private val CHART_HEIGHT = 290.dp
private val SLOT_WIDTH = 44.dp

/** Curva de cada rede: trapézio suave na largura do canal, com altura = intensidade do sinal. */
@Composable
fun SpectrumChart(
    band: WifiBand,
    networks: List<WifiNetwork>,
    colors: Map<String, Color>,
    selected: String?,
    hiddenLabel: String,
    onSelect: (String?) -> Unit,
) {
    val axis = remember(band) { SpectrumAxis.forBand(band) }
    val scheme = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val mono = NetrikTheme.dataTypography.dataSmall
    val density = LocalDensity.current

    Row(modifier = Modifier.fillMaxWidth()) {
        Canvas(modifier = Modifier.width(36.dp).height(CHART_HEIGHT)) {
            for (dbm in TOP_DBM downTo BOTTOM_DBM step 20) {
                val y = yOf(dbm, size.height)
                val text = measurer.measure(dbm.toString().replace('-', '−'), mono.copy(fontSize = 10.sp, color = scheme.onSurfaceVariant))
                drawText(text, topLeft = Offset(size.width - text.size.width - 4.dp.toPx(), y - text.size.height / 2))
            }
        }
        BoxWithConstraints(modifier = Modifier.weight(1f)) {
            // 2,4 GHz cabe na largura; 5 e 6 GHz usam 44dp por canal de 20 MHz e rolam na horizontal.
            val slotPx = if (band == WifiBand.GHz2_4) constraints.maxWidth / axis.slots else with(density) { SLOT_WIDTH.toPx() }
            val widthDp = with(density) { (slotPx * axis.slots).toDp() }
            val scroll = rememberScrollState()
            Box(modifier = if (band == WifiBand.GHz2_4) Modifier else Modifier.horizontalScroll(scroll)) {
                Canvas(
                    modifier = Modifier
                        .width(widthDp)
                        .height(CHART_HEIGHT)
                        .pointerInput(networks, slotPx) {
                            detectTapGestures { tap ->
                                // A rede mais forte cuja faixa contém o toque.
                                val hit = networks
                                    .mapNotNull { n -> axis.span(n.centerMhz, n.widthMhz)?.let { n to it } }
                                    .filter { (_, s) -> tap.x in s.first * slotPx..s.second * slotPx }
                                    .maxByOrNull { (n, _) -> n.rssiDbm }?.first
                                onSelect(hit?.bssid)
                            }
                        },
                ) {
                    drawGrid(axis, slotPx, scheme.outlineVariant, scheme.outline)
                    drawTicks(axis, slotPx, measurer, mono.copy(fontSize = 11.sp, color = scheme.onSurfaceVariant))
                    // A selecionada por último, por cima; as outras das mais fracas para as mais fortes.
                    networks.sortedWith(compareBy<WifiNetwork>({ it.bssid == selected }, { it.rssiDbm })).forEach { n ->
                        val span = axis.span(n.centerMhz, n.widthMhz) ?: return@forEach
                        val color = colors[n.bssid] ?: scheme.primary
                        val isSelected = n.bssid == selected
                        val dim = selected != null && !isSelected
                        drawCurve(span.first * slotPx, span.second * slotPx, yOf(n.rssiDbm, size.height), color, isSelected, dim)
                        drawLabel(
                            measurer,
                            n.ssid ?: hiddenLabel,
                            (span.first + span.second) / 2 * slotPx,
                            yOf(n.rssiDbm, size.height),
                            TextStyle(
                                color = color.copy(alpha = if (dim) 0.45f else 1f),
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontStyle = if (n.ssid == null) FontStyle.Italic else FontStyle.Normal,
                            ),
                            halo = scheme.surfaceContainerLow,
                        )
                    }
                }
            }
        }
    }
}

private fun DrawScope.yOf(dbm: Int, height: Float): Float {
    val top = 10.dp.toPx()
    val bottom = height - 28.dp.toPx()
    val clamped = dbm.coerceIn(BOTTOM_DBM, TOP_DBM)
    return top + (TOP_DBM - clamped).toFloat() / (TOP_DBM - BOTTOM_DBM) * (bottom - top)
}

private fun DrawScope.drawGrid(axis: SpectrumAxis, slotPx: Float, grid: Color, base: Color) {
    val dotted = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 4.dp.toPx()))
    for (dbm in -30 downTo -90 step 10) {
        val y = yOf(dbm, size.height)
        drawLine(grid, Offset(0f, y), Offset(size.width, y), pathEffect = dotted)
    }
    val dashed = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
    axis.separators.forEach { slot ->
        drawLine(base.copy(alpha = 0.6f), Offset(slot * slotPx, yOf(-20, size.height)), Offset(slot * slotPx, yOf(-100, size.height)), pathEffect = dashed)
    }
    val bottom = yOf(BOTTOM_DBM, size.height)
    drawLine(base, Offset(0f, bottom), Offset(size.width, bottom))
}

private fun DrawScope.drawTicks(axis: SpectrumAxis, slotPx: Float, measurer: TextMeasurer, style: TextStyle) {
    val y = yOf(BOTTOM_DBM, size.height) + 6.dp.toPx()
    // 6 GHz tem 59 canais: rotula um a cada dois para não sobrepor.
    val step = if (axis.band == WifiBand.GHz6) 2 else 1
    axis.ticks.filterIndexed { i, _ -> i % step == 0 }.forEach { (channel, slot) ->
        val text = measurer.measure(channel.toString(), style)
        drawText(text, topLeft = Offset(slot * slotPx - text.size.width / 2, y))
    }
}

private fun DrawScope.drawCurve(x0: Float, x1: Float, top: Float, color: Color, selected: Boolean, dim: Boolean) {
    val bottom = yOf(BOTTOM_DBM, size.height)
    val s = minOf(9.dp.toPx(), (x1 - x0) / 6)
    val path = Path().apply {
        moveTo(x0, bottom)
        cubicTo(x0 + s, bottom, x0 + s, top, x0 + 2 * s, top)
        lineTo(x1 - 2 * s, top)
        cubicTo(x1 - s, top, x1 - s, bottom, x1, bottom)
        close()
    }
    drawPath(path, color.copy(alpha = if (selected) 0.30f else if (dim) 0.05f else 0.12f))
    drawPath(path, color.copy(alpha = if (dim) 0.35f else 1f), style = Stroke(width = (if (selected) 2.5f else 1.5f).dp.toPx()))
}

private fun DrawScope.drawLabel(measurer: TextMeasurer, text: String, centerX: Float, top: Float, style: TextStyle, halo: Color) {
    val layout = measurer.measure(text, style)
    val pad = 3.dp.toPx()
    val x = (centerX - layout.size.width / 2).coerceIn(pad, (size.width - layout.size.width - pad).coerceAtLeast(pad))
    val y = top - layout.size.height - 3.dp.toPx()
    // Fundo discreto em vez de contorno: mantém o rótulo legível sobre outras curvas.
    drawRoundRect(
        color = halo.copy(alpha = 0.85f),
        topLeft = Offset(x - pad, y),
        size = Size(layout.size.width + 2 * pad, layout.size.height.toFloat()),
        cornerRadius = CornerRadius(4.dp.toPx()),
    )
    drawText(layout, topLeft = Offset(x, y))
}
