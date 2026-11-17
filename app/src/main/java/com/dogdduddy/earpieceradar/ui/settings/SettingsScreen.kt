package com.dogdduddy.earpieceradar.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dogdduddy.earpieceradar.data.Settings
import com.dogdduddy.earpieceradar.ui.components.SectionCard
import com.dogdduddy.earpieceradar.ui.f

@Composable
fun SettingsScreen(vm: SettingsViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionCard("탐지 임계값") {
            NumField("Δ 임계값 (µT)", s.deltaThresholdUt.f(2), "탐지: Δ ≥ 이 값 그리고 z ≥ z 임계값") { v -> vm.update { it.copy(deltaThresholdUt = v) } }
            NumField("z 임계값", s.zThreshold.f(1), "z = Δ / σ(영점 노이즈)") { v -> vm.update { it.copy(zThreshold = v) } }
            NumField("AC RMS 임계값 (µT)", s.acThresholdUt.f(2), "교류 자기장(넥루프) 탐지. 절반 이상이면 '의심'") { v -> vm.update { it.copy(acThresholdUt = v) } }
            IntField("디바운스 (연속 샘플 수)", s.debounceSamples, "상태가 바뀌려면 같은 판정이 N 샘플 연속되어야 함") { v -> vm.update { it.copy(debounceSamples = v) } }
        }
        SectionCard("시간") {
            IntField("영점 측정 시간 (초)", s.calibrationSeconds, "") { v -> vm.update { it.copy(calibrationSeconds = v) } }
            IntField("카운트다운 (초)", s.countdownSeconds, "자석을 위치에 놓는 시간") { v -> vm.update { it.copy(countdownSeconds = v) } }
            IntField("녹화 시간 (초)", s.recordSeconds, "") { v -> vm.update { it.copy(recordSeconds = v) } }
            IntField("AC 윈도우 (ms)", s.acWindowMs, "high-pass 롤링 윈도우 길이") { v -> vm.update { it.copy(acWindowMs = v) } }
            IntField("그래프 창 (초)", s.chartWindowSeconds, "") { v -> vm.update { it.copy(chartWindowSeconds = v) } }
        }
        OutlinedButton(onClick = vm::reset) { Text("기본값으로") }
        Text(
            "기본값: Δ 3 µT, z 5, AC 1 µT, 디바운스 5, 영점 3초, 카운트다운 3초, 녹화 5초, AC 윈도우 500ms.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NumField(label: String, current: String, hint: String, onCommit: (Double) -> Unit) {
    var text by remember { mutableStateOf(current) }
    LaunchedEffect(current) { if (text.toDoubleOrNull() != current.toDoubleOrNull()) text = current }
    FieldRow(label, hint) {
        OutlinedTextField(
            value = text, singleLine = true, modifier = Modifier.width(110.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            onValueChange = { v -> text = v; v.toDoubleOrNull()?.let { if (it > 0) onCommit(it) } },
        )
    }
}

@Composable
private fun IntField(label: String, current: Int, hint: String, onCommit: (Int) -> Unit) {
    var text by remember { mutableStateOf(current.toString()) }
    LaunchedEffect(current) { if (text.toIntOrNull() != current) text = current.toString() }
    FieldRow(label, hint) {
        OutlinedTextField(
            value = text, singleLine = true, modifier = Modifier.width(110.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            onValueChange = { v -> text = v; v.toIntOrNull()?.let { if (it > 0) onCommit(it) } },
        )
    }
}

@Composable
private fun FieldRow(label: String, hint: String, field: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            if (hint.isNotEmpty()) Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        field()
    }
}
