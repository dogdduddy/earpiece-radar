package com.dogdduddy.earpieceradar.ui.results

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dogdduddy.earpieceradar.data.Trial
import com.dogdduddy.earpieceradar.ui.components.ChartSeries
import com.dogdduddy.earpieceradar.ui.components.LineChart
import com.dogdduddy.earpieceradar.ui.components.SectionCard
import com.dogdduddy.earpieceradar.ui.f
import com.dogdduddy.earpieceradar.ui.theme.RadarColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.pow

@Composable
fun ResultsScreen(vm: ResultsViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var confirmDeleteAll by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Trial?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionCard("내보내기") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = vm::exportSummary, enabled = !ui.busy && ui.trials.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("요약 CSV") }
                Button(onClick = vm::exportRaw, enabled = !ui.busy && ui.trials.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("raw CSV") }
            }
            ui.message?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = vm::clearMessage) { Text("닫기") }
            }
        }

        SectionCard("거리 vs 평균 Δ (자석 종류별)") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = ui.logY, onClick = vm::toggleLogY, label = { Text("log Y") })
                FilterChip(selected = ui.logX, onClick = vm::toggleLogX, label = { Text("log X") })
                FilterChip(selected = ui.showCubicRef, onClick = vm::toggleCubic, label = { Text("r⁻³ 참고선") })
            }
            if (ui.series.isEmpty()) {
                Text("트라이얼을 기록하면 여기 그래프가 그려집니다.", style = MaterialTheme.typography.bodySmall)
            } else {
                val series = buildList {
                    ui.series.forEachIndexed { i, s ->
                        val color = RadarColors.seriesPalette[i % RadarColors.seriesPalette.size]
                        add(ChartSeries(
                            label = s.magnetLabel, color = color,
                            xs = s.points.map { it.distanceCm.toFloat() }.toFloatArray(),
                            ys = s.points.map { it.meanDelta.toFloat() }.toFloatArray(),
                            drawPoints = true,
                        ))
                        // 가장 가까운 점에 맞춘 1/r³ 기준선: Δ(d) = Δ1 · (d1/d)³
                        if (ui.showCubicRef && s.points.size >= 2) {
                            val p1 = s.points.first()
                            val dMin = p1.distanceCm; val dMax = s.points.last().distanceCm
                            val n = 40
                            val xs = FloatArray(n) { k -> (dMin + (dMax - dMin) * k / (n - 1)).toFloat() }
                            val ys = FloatArray(n) { k -> (p1.meanDelta * (dMin / xs[k]).pow(3)).toFloat() }
                            add(ChartSeries(label = "${s.magnetLabel} r⁻³", color = color.copy(alpha = 0.5f), xs = xs, ys = ys, dashed = true))
                        }
                    }
                }
                LineChart(series = series, logX = ui.logX, logY = ui.logY, xLabel = "cm", yLabel = "Δ µT")
                Text(
                    "로그 Y 에서 직선이면 거듭제곱 감쇠, 기울기가 r⁻³ 참고선과 평행하면 쌍극자(자석)로 설명됨.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ui.series.forEach { s ->
                    Text(
                        s.magnetLabel + ": " + s.points.joinToString("  ") { "${it.distanceCm.f(1)}cm→${it.meanDelta.f(2)}(n=${it.n})" },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        SectionCard("트라이얼 목록 (${ui.trials.size})") {
            if (ui.trials.isEmpty()) Text("아직 없음", style = MaterialTheme.typography.bodySmall)
            else {
                TrialTable(ui.trials, onDelete = { pendingDelete = it })
                OutlinedButton(onClick = { confirmDeleteAll = true }) { Text("전체 삭제") }
            }
        }
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text("전체 삭제") }, text = { Text("모든 트라이얼과 raw 파일을 삭제합니다. 내보내기 먼저 했나요?") },
            confirmButton = { TextButton(onClick = { vm.deleteAll(); confirmDeleteAll = false }) { Text("삭제") } },
            dismissButton = { TextButton(onClick = { confirmDeleteAll = false }) { Text("취소") } },
        )
    }
    pendingDelete?.let { t ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("트라이얼 #${t.id} 삭제") },
            text = { Text("${t.config.magnetLabel} ${t.config.distanceCm.f(1)}cm, 평균Δ ${t.stats.meanDelta.f(2)} µT") },
            confirmButton = { TextButton(onClick = { vm.delete(t.id); pendingDelete = null }) { Text("삭제") } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("취소") } },
        )
    }
}

@Composable
private fun TrialTable(trials: List<Trial>, onDelete: (Trial) -> Unit) {
    val fmt = remember { SimpleDateFormat("MM-dd HH:mm:ss", Locale.US) }
    val cols = listOf("시각" to 96, "자석" to 80, "cm" to 40, "방향" to 64, "위치" to 44, "평균Δ" to 60, "최대Δ" to 60, "Δ|B|" to 56, "σΔ" to 52, "z̄" to 48, "AC" to 56, "σ0" to 52, "Hz" to 44, "메모" to 120)
    Column(Modifier.horizontalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("", Modifier.width(36.dp))
            cols.forEach { (h, w) -> Text(h, Modifier.width(w.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
        }
        HorizontalDivider()
        trials.forEach { t ->
            val s = t.stats; val c = t.config
            val cells = listOf(
                fmt.format(Date(t.createdAtEpochMs)), c.magnetLabel, c.distanceCm.f(1), c.orientation.label, c.placement.label,
                s.meanDelta.f(2), s.maxDelta.f(2), s.meanDeltaMag.f(2), s.stdDelta.f(2), s.meanZ.f(1), s.acRms.f(3), t.baseline.sigma.f(3), s.sampleRateHz.f(0), c.note,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onDelete(t) }, modifier = Modifier.width(36.dp)) {
                    Icon(Icons.Filled.Delete, contentDescription = "삭제", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                cells.forEachIndexed { i, v -> Text(v, Modifier.width(cols[i].second.dp), style = MaterialTheme.typography.bodySmall, maxLines = 1) }
            }
        }
    }
}
