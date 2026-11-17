package com.dogdduddy.earpieceradar.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

data class ChartSeries(
    val label: String,
    val color: Color,
    val xs: FloatArray,
    val ys: FloatArray,
    val drawPoints: Boolean = false,
    val dashed: Boolean = false,
)

/**
 * Compose Canvas 라인 차트. 외부 차트 라이브러리 없이 구현.
 * @param xRange null 이면 데이터 범위로 자동
 * @param yRange null 이면 데이터 범위 + 여백으로 자동
 * @param logY true 면 y 축 log10 (0 이하 값은 바닥값으로 클램프)
 * @param hLines y 값 위치에 점선(임계값 표시용)
 */
@Composable
fun LineChart(
    series: List<ChartSeries>,
    modifier: Modifier = Modifier,
    xRange: ClosedFloatingPointRange<Float>? = null,
    yRange: ClosedFloatingPointRange<Float>? = null,
    logX: Boolean = false,
    logY: Boolean = false,
    xLabel: String = "",
    yLabel: String = "",
    hLines: List<Pair<Float, Color>> = emptyList(),
    showLegend: Boolean = true,
) {
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier) {
        if (showLegend) {
            Row(Modifier.fillMaxWidth().padding(bottom = 2.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                series.forEach { s ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).padding(end = 0.dp)) {
                            Canvas(Modifier.size(10.dp)) { drawRect(s.color) }
                        }
                        Text(" " + s.label, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        Canvas(Modifier.fillMaxWidth().height(180.dp).then(Modifier)) {
            val leftPad = 44.dp.toPx(); val bottomPad = 18.dp.toPx(); val topPad = 6.dp.toPx(); val rightPad = 8.dp.toPx()
            val w = size.width - leftPad - rightPad
            val h = size.height - topPad - bottomPad
            if (w <= 0 || h <= 0) return@Canvas

            fun tx(v: Float): Float = if (logX) log10(max(v, 1e-3f)) else v
            fun ty(v: Float): Float = if (logY) log10(max(v, 1e-3f)) else v

            // 범위 계산
            var xMin = Float.MAX_VALUE; var xMax = -Float.MAX_VALUE
            var yMin = Float.MAX_VALUE; var yMax = -Float.MAX_VALUE
            series.forEach { s ->
                for (i in s.xs.indices) {
                    val xv = tx(s.xs[i]); val yv = ty(s.ys[i])
                    if (xv.isFinite()) { if (xv < xMin) xMin = xv; if (xv > xMax) xMax = xv }
                    if (yv.isFinite()) { if (yv < yMin) yMin = yv; if (yv > yMax) yMax = yv }
                }
            }
            hLines.forEach { (v, _) -> val yv = ty(v); if (yv < yMin) yMin = yv; if (yv > yMax) yMax = yv }
            if (xRange != null) { xMin = tx(xRange.start); xMax = tx(xRange.endInclusive) }
            if (yRange != null) { yMin = ty(yRange.start); yMax = ty(yRange.endInclusive) }
            if (xMin == Float.MAX_VALUE) { xMin = 0f; xMax = 1f }
            if (yMin == Float.MAX_VALUE) { yMin = 0f; yMax = 1f }
            if (yRange == null) {
                val pad = max((yMax - yMin) * 0.1f, if (logY) 0.05f else 0.5f)
                yMin -= pad; yMax += pad
            }
            if (xMax - xMin < 1e-6f) { xMin -= 0.5f; xMax += 0.5f }
            if (yMax - yMin < 1e-6f) { yMin -= 0.5f; yMax += 0.5f }

            fun px(xv: Float) = leftPad + (xv - xMin) / (xMax - xMin) * w
            fun py(yv: Float) = topPad + h - (yv - yMin) / (yMax - yMin) * h

            // 격자 + y 라벨
            val yTicks = niceTicks(yMin, yMax, 4)
            yTicks.forEach { t ->
                val yy = py(t)
                drawLine(gridColor, Offset(leftPad, yy), Offset(leftPad + w, yy), 1f)
                val label = if (logY) fmtNum(10f.pow(t)) else fmtNum(t)
                val tl = measurer.measure(label, labelStyle)
                drawText(tl, topLeft = Offset(leftPad - tl.size.width - 4f, yy - tl.size.height / 2))
            }
            val xTicks = niceTicks(xMin, xMax, 5)
            xTicks.forEach { t ->
                val xx = px(t)
                drawLine(gridColor, Offset(xx, topPad), Offset(xx, topPad + h), 1f)
                val label = if (logX) fmtNum(10f.pow(t)) else fmtNum(t)
                val tl = measurer.measure(label, labelStyle)
                drawText(tl, topLeft = Offset(xx - tl.size.width / 2, topPad + h + 2f))
            }
            // 축
            drawLine(axisColor, Offset(leftPad, topPad), Offset(leftPad, topPad + h), 1.5f)
            drawLine(axisColor, Offset(leftPad, topPad + h), Offset(leftPad + w, topPad + h), 1.5f)

            // 임계선
            hLines.forEach { (v, c) ->
                val yy = py(ty(v))
                drawLine(c, Offset(leftPad, yy), Offset(leftPad + w, yy), 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
            }

            // 시리즈
            series.forEach { s ->
                if (s.xs.isEmpty()) return@forEach
                val path = Path()
                var started = false
                for (i in s.xs.indices) {
                    val xv = tx(s.xs[i]); val yv = ty(s.ys[i])
                    if (!xv.isFinite() || !yv.isFinite()) continue
                    val p = Offset(px(xv), py(yv))
                    if (!started) { path.moveTo(p.x, p.y); started = true } else path.lineTo(p.x, p.y)
                    if (s.drawPoints) drawCircle(s.color, 5f, p)
                }
                drawPath(
                    path, s.color,
                    style = Stroke(
                        width = 2.2f,
                        pathEffect = if (s.dashed) PathEffect.dashPathEffect(floatArrayOf(8f, 8f)) else null,
                    ),
                )
            }

            if (yLabel.isNotEmpty()) {
                val tl = measurer.measure(yLabel, labelStyle)
                drawText(tl, topLeft = Offset(leftPad + 4f, topPad))
            }
            if (xLabel.isNotEmpty()) {
                val tl = measurer.measure(xLabel, labelStyle)
                drawText(tl, topLeft = Offset(leftPad + w - tl.size.width, topPad + h - tl.size.height - 2f))
            }
        }
    }
}

private fun niceTicks(min: Float, max: Float, target: Int): List<Float> {
    val range = max - min
    if (range <= 0f || !range.isFinite()) return listOf(min)
    val rough = range / target
    val mag = 10.0.pow(floor(log10(rough.toDouble()))).toFloat()
    val norm = rough / mag
    val step = when {
        norm < 1.5f -> 1f
        norm < 3f -> 2f
        norm < 7f -> 5f
        else -> 10f
    } * mag
    val first = ceil(min / step) * step
    val out = ArrayList<Float>()
    var v = first
    while (v <= max + step * 1e-3f && out.size < 20) { out.add(v); v += step }
    return out
}

private fun fmtNum(v: Float): String = when {
    abs(v) >= 100f -> String.format(Locale.US, "%.0f", v)
    abs(v) >= 10f -> String.format(Locale.US, "%.1f", v)
    abs(v) >= 1f -> String.format(Locale.US, "%.1f", v)
    else -> String.format(Locale.US, "%.2f", v)
}
