package com.dogdduddy.earpieceradar.ui.record

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dogdduddy.earpieceradar.data.MagnetType
import com.dogdduddy.earpieceradar.data.Orientation
import com.dogdduddy.earpieceradar.data.Placement
import com.dogdduddy.earpieceradar.data.TrialConfig
import com.dogdduddy.earpieceradar.signal.TrialStats
import com.dogdduddy.earpieceradar.ui.components.KeyValue
import com.dogdduddy.earpieceradar.ui.components.SectionCard
import com.dogdduddy.earpieceradar.ui.components.color
import com.dogdduddy.earpieceradar.ui.components.label
import com.dogdduddy.earpieceradar.ui.f
import com.dogdduddy.earpieceradar.ui.theme.RadarColors

private val DISTANCE_PRESETS = listOf(1.0, 2.0, 3.0, 5.0, 10.0)

@Composable
fun RecordScreen(vm: RecordViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (!ui.sensorAvailable) { Text("지자기 센서가 없어 기록할 수 없습니다.", color = MaterialTheme.colorScheme.error); return@Column }
        when (val p = ui.phase) {
            RecordPhase.Idle -> {
                ConfigForm(ui, vm)
                StartCard(ui, onStart = vm::start)
            }
            is RecordPhase.Calibrating -> PhaseCard(
                title = "영점 측정 중", big = "${(p.progress * 100).toInt()}%",
                hint = "자석을 멀리 두고 폰을 고정하세요 (${ui.settings.calibrationSeconds}초)",
                progress = p.progress, onCancel = vm::cancel,
            )
            is RecordPhase.Countdown -> PhaseCard(
                title = "자석을 위치에 놓으세요", big = "${p.secondsLeft}",
                hint = "${ui.config.magnetLabel} · ${ui.config.distanceCm.f(1)}cm · ${ui.config.orientation.label} · ${ui.config.placement.label}",
                progress = null, onCancel = vm::cancel,
            )
            is RecordPhase.Recording -> {
                val live = p.live
                PhaseCard(
                    title = "녹화 중 (${ui.settings.recordSeconds}초)",
                    big = live?.delta?.f(2) ?: "—",
                    bigColor = live?.level?.color(),
                    hint = "Δ µT  ·  z ${live?.z?.f(1) ?: "—"}  ·  AC ${live?.acRms?.f(3) ?: "—"}  ·  ${p.sampleCount} 샘플" +
                        (live?.level?.let { "  ·  ${it.label()}" } ?: ""),
                    progress = p.progress, onCancel = vm::cancel,
                )
            }
            RecordPhase.Saving -> PhaseCard("저장 중", "…", "", null, null)
            is RecordPhase.Done -> DoneCard(ui, p, onRepeat = vm::repeat, onEdit = vm::backToIdle)
            is RecordPhase.Error -> SectionCard("오류") {
                Text(p.message, color = MaterialTheme.colorScheme.error)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = vm::repeat) { Text("다시 시도") }
                    OutlinedButton(onClick = vm::backToIdle) { Text("설정으로") }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConfigForm(ui: RecordUiState, vm: RecordViewModel) {
    val c = ui.config
    SectionCard("트라이얼 설정") {
        Text("자석", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MagnetType.entries.forEach { m ->
                FilterChip(selected = c.magnet == m, onClick = { vm.updateConfig { it.copy(magnet = m) } }, label = { Text(m.label) })
            }
        }
        if (c.magnet == MagnetType.OTHER) {
            OutlinedTextField(
                value = c.magnetOther, onValueChange = { v -> vm.updateConfig { it.copy(magnetOther = v) } },
                label = { Text("자석 설명") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
        }

        Text("거리 (cm)", style = MaterialTheme.typography.labelMedium)
        var customDistance by remember { mutableStateOf(if (c.distanceCm in DISTANCE_PRESETS) "" else c.distanceCm.f(1)) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            DISTANCE_PRESETS.forEach { d ->
                FilterChip(
                    selected = c.distanceCm == d && customDistance.isEmpty(),
                    onClick = { customDistance = ""; vm.updateConfig { it.copy(distanceCm = d) } },
                    label = { Text("${d.toInt()}") },
                )
            }
            OutlinedTextField(
                value = customDistance,
                onValueChange = { v ->
                    customDistance = v
                    v.toDoubleOrNull()?.let { d -> if (d > 0) vm.updateConfig { it.copy(distanceCm = d) } }
                },
                label = { Text("직접") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.width(96.dp),
            )
        }

        Text("방향", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Orientation.entries.forEach { o ->
                FilterChip(selected = c.orientation == o, onClick = { vm.updateConfig { it.copy(orientation = o) } }, label = { Text(o.label) })
            }
        }
        Text("위치", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Placement.entries.forEach { p ->
                FilterChip(selected = c.placement == p, onClick = { vm.updateConfig { it.copy(placement = p) } }, label = { Text(p.label) })
            }
        }
        OutlinedTextField(
            value = c.note, onValueChange = { v -> vm.updateConfig { it.copy(note = v) } },
            label = { Text("메모") }, modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("녹화 전에 기준값 새로 잡기", style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (ui.baseline == null) "기존 기준값 없음 → 항상 새로 잡습니다"
                    else "끄면 현재 B0(σ ${ui.baseline.sigma.f(3)} µT) 를 재사용",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = c.freshBaseline || ui.baseline == null, enabled = ui.baseline != null,
                onCheckedChange = { v -> vm.updateConfig { it.copy(freshBaseline = v) } })
        }
    }
}

@Composable
private fun StartCard(ui: RecordUiState, onStart: () -> Unit) {
    SectionCard {
        val s = ui.settings
        Text(
            buildString {
                if (ui.config.freshBaseline || ui.baseline == null) append("영점 ${s.calibrationSeconds}초 → ")
                append("카운트다운 ${s.countdownSeconds}초 → 녹화 ${s.recordSeconds}초")
            },
            style = MaterialTheme.typography.bodySmall,
        )
        Text("같은 조건 기록 ${ui.sameConfigCount}회 / 전체 ${ui.totalTrials}회", style = MaterialTheme.typography.bodySmall)
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text("녹화 시작", fontSize = 18.sp)
        }
    }
}

@Composable
private fun PhaseCard(
    title: String, big: String, hint: String, progress: Float?, onCancel: (() -> Unit)?,
    bigColor: androidx.compose.ui.graphics.Color? = null,
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background((bigColor ?: MaterialTheme.colorScheme.primary).copy(alpha = 0.12f)).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(big, fontSize = 72.sp, fontWeight = FontWeight.Bold, color = bigColor ?: MaterialTheme.colorScheme.onSurface)
        if (hint.isNotEmpty()) Text(hint, style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (progress != null) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        if (onCancel != null) OutlinedButton(onClick = onCancel) { Text("취소") }
    }
}

@Composable
private fun DoneCard(ui: RecordUiState, p: RecordPhase.Done, onRepeat: () -> Unit, onEdit: () -> Unit) {
    val t = p.trial; val s = p.stats
    SectionCard("저장됨  #${t.id}") {
        Text(
            "${t.config.magnetLabel} · ${t.config.distanceCm.f(1)}cm · ${t.config.orientation.label} · ${t.config.placement.label}",
            fontWeight = FontWeight.Medium,
        )
        StatsTable(s, sigma = t.baseline.sigma)
        Spacer(Modifier.height(4.dp))
        Text("같은 조건 기록 ${ui.sameConfigCount}회", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onRepeat, modifier = Modifier.weight(1f).height(52.dp)) { Text("같은 설정으로 다시") }
            OutlinedButton(onClick = onEdit, modifier = Modifier.height(52.dp)) { Text("설정 수정") }
        }
    }
}

@Composable
fun StatsTable(s: TrialStats, sigma: Double) {
    KeyValue("평균 Δ", "${s.meanDelta.f(3)} µT")
    KeyValue("최대 Δ", "${s.maxDelta.f(3)} µT")
    KeyValue("Δ 표준편차", "${s.stdDelta.f(3)} µT")
    KeyValue("평균 Δ|B| / 최대", "${s.meanDeltaMag.f(3)} / ${s.maxDeltaMag.f(3)} µT")
    KeyValue("평균 z", s.meanZ.f(2))
    KeyValue("AC RMS", "${s.acRms.f(4)} µT")
    KeyValue("기준 σ", "${sigma.f(4)} µT")
    KeyValue("샘플 / 레이트", "${s.sampleCount} / ${s.sampleRateHz.f(1)} Hz")
}
