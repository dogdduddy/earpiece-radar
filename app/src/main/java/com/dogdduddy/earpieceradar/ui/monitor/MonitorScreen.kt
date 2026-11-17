package com.dogdduddy.earpieceradar.ui.monitor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dogdduddy.earpieceradar.signal.DetectionLevel
import com.dogdduddy.earpieceradar.ui.components.ChartSeries
import com.dogdduddy.earpieceradar.ui.components.KeyValue
import com.dogdduddy.earpieceradar.ui.components.LineChart
import com.dogdduddy.earpieceradar.ui.components.SectionCard
import com.dogdduddy.earpieceradar.ui.components.color
import com.dogdduddy.earpieceradar.ui.components.label
import com.dogdduddy.earpieceradar.ui.f
import com.dogdduddy.earpieceradar.ui.theme.RadarColors

@Composable
fun MonitorScreen(vm: MonitorViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        vm.start()
        onPauseOrDispose { vm.stop() }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (ui.sensorInfo == null) {
            SectionCard("센서 없음") { Text("이 기기에는 지자기 센서가 없습니다.", color = MaterialTheme.colorScheme.error) }
            return@Column
        }
        ui.error?.let { SectionCard("오류") { Text(it, color = MaterialTheme.colorScheme.error) } }

        DetectionPanel(ui)
        BaselineCard(ui, onCalibrate = vm::calibrate, onClear = vm::clearBaseline)

        SectionCard("실시간 그래프 (최근 ${ui.settings.chartWindowSeconds}초)") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AxisChip("x", ui.showX, RadarColors.axisX) { vm.toggle('x') }
                AxisChip("y", ui.showY, RadarColors.axisY) { vm.toggle('y') }
                AxisChip("z", ui.showZ, RadarColors.axisZ) { vm.toggle('z') }
                AxisChip("|B|", ui.showMag, RadarColors.magnitude) { vm.toggle('m') }
            }
            val c = ui.chart
            val series = buildList {
                if (ui.showX) add(ChartSeries("x", RadarColors.axisX, c.times, c.x))
                if (ui.showY) add(ChartSeries("y", RadarColors.axisY, c.times, c.y))
                if (ui.showZ) add(ChartSeries("z", RadarColors.axisZ, c.times, c.z))
                if (ui.showMag) add(ChartSeries("|B|", RadarColors.magnitude, c.times, c.mag))
            }
            LineChart(
                series = series,
                xRange = -ui.settings.chartWindowSeconds.toFloat()..0f,
                yLabel = "µT", xLabel = "s", showLegend = false,
            )
            if (ui.baseline != null) {
                Text("Δ = |B − B0|", style = MaterialTheme.typography.labelSmall)
                LineChart(
                    series = listOf(ChartSeries("Δ", RadarColors.delta, c.times, c.delta)),
                    xRange = -ui.settings.chartWindowSeconds.toFloat()..0f,
                    yRange = 0f..maxOf(ui.settings.deltaThresholdUt.toFloat() * 1.5f, (c.delta.maxOrNull() ?: 0f) * 1.1f),
                    hLines = listOf(ui.settings.deltaThresholdUt.toFloat() to RadarColors.levelDetected),
                    yLabel = "µT", showLegend = false,
                )
            }
        }

        SensorCard(ui)
    }
}

@Composable
private fun AxisChip(label: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label, color = if (selected) color else Color.Unspecified) })
}

@Composable
fun DetectionPanel(ui: MonitorUiState) {
    val m = ui.current
    val level = if (ui.baseline == null) null else m?.level
    val color = level?.color() ?: MaterialTheme.colorScheme.surfaceVariant
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(color.copy(alpha = 0.18f)).padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            level?.label() ?: "영점 필요",
            style = MaterialTheme.typography.titleMedium,
            color = level?.color() ?: MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = if (ui.baseline != null && m != null) m.delta.f(2) else "—",
            fontSize = 56.sp, fontWeight = FontWeight.Bold,
            color = level?.color() ?: MaterialTheme.colorScheme.onSurface,
        )
        Text("Δ µT", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Metric("z", if (ui.baseline != null && m != null) m.z.f(1) else "—")
            Metric("AC RMS µT", m?.acRms?.f(3) ?: "—")
            Metric("Hz", m?.hz?.f(0) ?: "—")
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun BaselineCard(ui: MonitorUiState, onCalibrate: () -> Unit, onClear: () -> Unit) {
    SectionCard("기준값(영점) B0 / σ") {
        val p = ui.calibrationProgress
        if (p != null) {
            Text("폰을 고정하고 ${ui.settings.calibrationSeconds}초 대기…", fontWeight = FontWeight.Medium)
            LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
        } else {
            val b = ui.baseline
            if (b == null) {
                Text("폰을 책상에 고정하고(자석 없이) 영점을 잡으세요.", style = MaterialTheme.typography.bodySmall)
            } else {
                KeyValue("B0 (x, y, z)", "${b.b0.x.f(1)}, ${b.b0.y.f(1)}, ${b.b0.z.f(1)} µT")
                KeyValue("|B0|", "${b.b0.norm.f(2)} µT")
                KeyValue("σ (벡터 RMS)", "${b.sigma.f(3)} µT")
                KeyValue("σ 축별", "${b.sigmaAxis.x.f(3)} / ${b.sigmaAxis.y.f(3)} / ${b.sigmaAxis.z.f(3)}")
                KeyValue("샘플 수", "${b.sampleCount} (${(b.durationNs / 1e9).f(2)}s)")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onCalibrate, enabled = ui.current != null) { Text("영점 잡기 (${ui.settings.calibrationSeconds}초)") }
                if (b != null) OutlinedButton(onClick = onClear) { Text("지우기") }
            }
        }
    }
}

@Composable
fun SensorCard(ui: MonitorUiState) {
    val info = ui.sensorInfo ?: return
    val m = ui.current
    SectionCard("센서") {
        KeyValue("타입", info.typeLabel + if (info.isUncalibrated) " ✓(보정 전)" else " (폴백: OS 보정됨)")
        KeyValue("이름", "${info.name} / ${info.vendor}")
        KeyValue("센서 최대", "${info.maxHz.f(0)} Hz, 해상도 ${info.resolutionUt.f(3)} µT")
        KeyValue("실측 레이트", if (m != null) "${m.hz.f(1)} Hz" else "—")
        if (m != null) {
            val s = m.sample
            KeyValue("x / y / z", "${s.x.f(2)} / ${s.y.f(2)} / ${s.z.f(2)} µT")
            KeyValue("|B|", "${s.magnitude.f(2)} µT")
            KeyValue("accuracy", accuracyLabel(s.accuracy))
        }
    }
}

fun accuracyLabel(a: Int) = when (a) {
    3 -> "HIGH(3)"; 2 -> "MEDIUM(2)"; 1 -> "LOW(1)"; 0 -> "UNRELIABLE(0)"; else -> "$a"
}
