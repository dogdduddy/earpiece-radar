package com.dogdduddy.earpieceradar.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dogdduddy.earpieceradar.data.Profile
import com.dogdduddy.earpieceradar.data.TuningDefaults
import com.dogdduddy.earpieceradar.signal.Baseline
import com.dogdduddy.earpieceradar.signal.DetectionMode
import com.dogdduddy.earpieceradar.ui.components.KeyValue
import com.dogdduddy.earpieceradar.ui.components.SectionCard
import com.dogdduddy.earpieceradar.ui.f
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 장소 프로필 선택/생성/이름변경/삭제 + 영점 저장. 간단 모드 시트와 개발자 설정 화면에서 공용.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileSection(
    profiles: List<Profile>,
    active: Profile,
    currentBaseline: Baseline?,
    onSelect: (String) -> Unit,
    onCreate: (String) -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onSaveBaseline: () -> Unit,
    onClearBaseline: () -> Unit,
    onPreset: (TuningDefaults.Preset) -> Unit,
    onMode: (DetectionMode) -> Unit,
    onResetTuning: () -> Unit,
    showAdvancedSummary: Boolean = true,
) {
    var newName by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    SectionCard("장소 프로필") {
        Text("교실/시험장마다 임계값과 영점을 따로 저장해 두고 골라 쓴다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            profiles.forEach { p ->
                FilterChip(selected = p.id == active.id, onClick = { onSelect(p.id) }, label = { Text(p.name) })
            }
            FilterChip(selected = false, onClick = { newName = "" }, label = { Text("+ 새 장소") })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (!active.isDefault) {
                OutlinedButton(onClick = { renameText = active.name }) { Text("이름 변경") }
                OutlinedButton(onClick = { confirmDelete = true }) { Text("삭제") }
            }
            OutlinedButton(onClick = onResetTuning) { Text("기본값으로 초기화") }
        }

        Text("감도 프리셋", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        val current = TuningDefaults.presetOf(active.tuning)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TuningDefaults.Preset.entries.forEach { p ->
                FilterChip(selected = current == p, onClick = { onPreset(p) }, label = { Text(p.label) })
            }
            if (current == null) FilterChip(selected = true, onClick = {}, label = { Text("사용자 조정") })
        }
        Text(
            current?.description ?: "임계값을 직접 조정한 상태",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text("판정 방식", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = active.tuning.mode == DetectionMode.VECTOR, onClick = { onMode(DetectionMode.VECTOR) }, label = { Text("폰 고정 (벡터 Δ)") })
            FilterChip(selected = active.tuning.mode == DetectionMode.MAGNITUDE, onClick = { onMode(DetectionMode.MAGNITUDE) }, label = { Text("스캔 (크기 Δ|B|)") })
        }
        Text(
            if (active.tuning.mode == DetectionMode.VECTOR)
                "가장 민감. 폰을 책상에 놓고 학생이 지나가거나 손을 가까이 할 때. 폰을 돌리면 오탐."
            else
                "폰을 손에 들고 움직이며 스캔. 회전에 둔감하지만 자석 방향에 따라 변화가 작게 보일 수 있음.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (showAdvancedSummary) {
            KeyValue("Δ / z / AC 임계값", "${active.tuning.deltaThresholdUt.f(2)} µT / ${active.tuning.zThreshold.f(1)} / ${active.tuning.acThresholdUt.f(2)} µT")
            KeyValue("디바운스", "${active.tuning.debounceSamples} 샘플")
        }

        Text("이 장소의 영점", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        val sb = active.savedBaseline
        if (sb != null) {
            val when_ = SimpleDateFormat("MM-dd HH:mm", Locale.US).format(Date(active.savedBaselineAtEpochMs))
            KeyValue("저장됨 ($when_)", "|B|₀ ${sb.mag0.f(1)} µT, σ ${sb.sigma.f(3)}")
        } else {
            Text("저장된 영점 없음. 측정 전 영점을 잡고 저장하면 이 장소를 고를 때 자동 적용됨(스캔 모드에서 유효).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = onSaveBaseline, enabled = currentBaseline != null) { Text("현재 영점 저장") }
            if (sb != null) OutlinedButton(onClick = onClearBaseline) { Text("저장 영점 지우기") }
        }
    }

    newName?.let { name ->
        AlertDialog(
            onDismissRequest = { newName = null },
            title = { Text("새 장소 프로필") },
            text = { OutlinedTextField(value = name, onValueChange = { newName = it }, label = { Text("이름 (예: 3-2 교실)") }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = { onCreate(name); newName = null }) { Text("만들기") } },
            dismissButton = { TextButton(onClick = { newName = null }) { Text("취소") } },
        )
    }
    renameText?.let { name ->
        AlertDialog(
            onDismissRequest = { renameText = null },
            title = { Text("이름 변경") },
            text = { OutlinedTextField(value = name, onValueChange = { renameText = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onRename(name); renameText = null }) { Text("저장") } },
            dismissButton = { TextButton(onClick = { renameText = null }) { Text("취소") } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("프로필 삭제") }, text = { Text("'${active.name}' 을 삭제합니다.") },
            confirmButton = { TextButton(onClick = { onDelete(); confirmDelete = false }) { Text("삭제") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("취소") } },
        )
    }
}
