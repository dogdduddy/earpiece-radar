package com.dogdduddy.earpieceradar.ui.simple

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dogdduddy.earpieceradar.signal.DetectionLevel
import com.dogdduddy.earpieceradar.signal.DetectionMode
import com.dogdduddy.earpieceradar.ui.components.SectionCard
import com.dogdduddy.earpieceradar.ui.components.color
import com.dogdduddy.earpieceradar.ui.components.label
import com.dogdduddy.earpieceradar.ui.f
import com.dogdduddy.earpieceradar.ui.settings.ProfileSection

/** 선생님용 단일 화면. 상태·큰 Δ·영점 버튼·장소 선택만. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleScreen(vm: SimpleViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var showSheet by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) { vm.start(); onPauseOrDispose { vm.stop() } }

    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        TopAppBar(
            title = { Text("이어피스 레이더") },
            actions = { IconButton(onClick = { showSheet = true }) { Icon(Icons.Filled.Settings, contentDescription = "설정") } },
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!ui.sensorAvailable) {
                Text("이 기기에는 지자기 센서가 없어 사용할 수 없습니다.", color = MaterialTheme.colorScheme.error)
                return@Column
            }
            val m = ui.current
            val hasBaseline = ui.baseline != null
            val level = if (hasBaseline) ui.recentLevel else null
            // 영점 전: 테마의 불투명 쌍(surfaceVariant / onSurfaceVariant)으로 대비 보장. 레벨 상태: 레벨 색 틴트 + 레벨 색 글자.
            val cardBg = level?.color()?.copy(alpha = 0.22f) ?: MaterialTheme.colorScheme.surfaceVariant
            val textColor = level?.color() ?: MaterialTheme.colorScheme.onSurfaceVariant

            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(cardBg).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    when {
                        !hasBaseline -> "영점 필요"
                        level == DetectionLevel.DETECTED -> "자석 반응 탐지"
                        level == DetectionLevel.SUSPECT -> "의심"
                        else -> "이상 없음"
                    },
                    fontSize = 30.sp, fontWeight = FontWeight.Bold,
                    color = textColor,
                )
                Text(
                    if (hasBaseline && m != null) m.deltaUsed.f(1) else "—",
                    fontSize = 88.sp, fontWeight = FontWeight.Bold,
                    color = textColor,
                )
                Text("자기장 변화 (µT)", style = MaterialTheme.typography.labelLarge, color = textColor)
                if (hasBaseline) {
                    Spacer(Modifier.height(6.dp))
                    Text("최근 3초 최대 ${ui.recentMaxDelta.f(1)} µT · 기준 ${ui.active.tuning.deltaThresholdUt.f(1)} µT", style = MaterialTheme.typography.bodySmall)
                }
            }

            val p = ui.calibrationProgress
            SectionCard("1. 영점 잡기") {
                if (p != null) {
                    Text("폰을 움직이지 말고 ${ui.calibrationSeconds}초만 기다려 주세요…", fontWeight = FontWeight.Medium)
                    LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                } else {
                    Text(
                        "학생과 1m 이상 떨어진 곳에서, 폰을 책상 위나 손에 고정한 채 버튼을 누르세요. 장소를 옮기면 다시 잡아 주세요.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(onClick = vm::calibrate, enabled = m != null, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Text(if (hasBaseline) "영점 다시 잡기" else "영점 잡기", fontSize = 17.sp)
                    }
                    ui.baseline?.let { b ->
                        Text("영점 노이즈 σ ${b.sigma.f(2)} µT" + if (b.sigma > 1.0) "  ← 주변이 시끄럽습니다. 다른 자리에서 다시 잡아보세요." else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            SectionCard("2. 확인하기") {
                Text(
                    if (ui.active.tuning.mode == DetectionMode.MAGNITUDE)
                        "폰 뒷면 윗부분(센서 위치)을 학생의 귀·목 근처 3~5cm 까지 천천히 가져갑니다. 숫자가 크게 뛰고 색이 바뀌면 자석 반응입니다."
                    else
                        "폰을 돌리지 말고 평행하게만 움직여 학생의 귀·목 근처 3~5cm 까지 천천히 가져갑니다. 폰을 기울이면 지구 자기장만으로도 숫자가 뛰니 주의하세요.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "주의: 이 앱은 영구자석에만 반응합니다. 금속 액세서리, 안경, 자석 단추, 다른 스마트폰도 반응하니 '탐지'는 확인의 계기일 뿐 증거가 아닙니다.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Start,
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("장소: ${ui.active.name}", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { showSheet = true }) { Text("장소/감도 설정") }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showSheet) {
        ModalBottomSheet(onDismissRequest = { showSheet = false }) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ProfileSection(
                    profiles = ui.profiles, active = ui.active, currentBaseline = ui.baseline,
                    onSelect = vm::selectProfile, onCreate = vm::createProfile, onRename = vm::renameProfile, onDelete = vm::deleteProfile,
                    onSaveBaseline = vm::saveBaselineToProfile, onClearBaseline = vm::clearProfileBaseline,
                    onPreset = vm::applyPreset, onMode = vm::setMode, onResetTuning = vm::resetTuning,
                )
                SectionCard("개발자 모드") {
                    Text("그래프, 실험 기록, CSV 내보내기, 임계값 숫자 조정 등 전체 기능을 엽니다.", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { vm.enableDeveloperMode(); showSheet = false }) { Text("개발자 모드 켜기") }
                }
            }
        }
    }
}
