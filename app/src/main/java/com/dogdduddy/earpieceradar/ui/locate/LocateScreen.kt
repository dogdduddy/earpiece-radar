package com.dogdduddy.earpieceradar.ui.locate

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dogdduddy.earpieceradar.ui.components.KeyValue
import com.dogdduddy.earpieceradar.ui.components.SectionCard
import com.dogdduddy.earpieceradar.ui.components.color
import com.dogdduddy.earpieceradar.ui.components.label
import com.dogdduddy.earpieceradar.ui.f
import com.dogdduddy.earpieceradar.ui.theme.RadarColors
import kotlin.math.abs

@Composable
fun LocateScreen(vm: LocateViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) { vm.start(); onPauseOrDispose { vm.stop() } }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionCard("센서 위치 찾기") {
            Text(
                "1) 자석을 멀리 두고 영점을 잡는다.\n2) 자석을 폰 뒷면/앞면 위에서 천천히 움직인다.\n3) 최대 Δ 가 가장 크게 튀는 지점이 지자기 센서 위치다. 기종마다 다르다.",
                style = MaterialTheme.typography.bodySmall,
            )
            val p = ui.calibrationProgress
            if (p != null) {
                Text("폰을 고정하고 대기…"); LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = vm::calibrate, enabled = ui.current != null) { Text(if (ui.baseline == null) "영점 잡기" else "영점 다시") }
                    OutlinedButton(onClick = vm::resetMax, enabled = ui.baseline != null) { Text("최대값 리셋") }
                }
            }
        }

        if (ui.baseline == null) {
            Text("영점을 먼저 잡아야 Δ 를 계산할 수 있습니다.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        val m = ui.current
        val level = m?.level
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background((level?.color() ?: MaterialTheme.colorScheme.primary).copy(alpha = 0.15f)).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("최대 Δ", style = MaterialTheme.typography.labelLarge)
            Text(ui.maxDelta.f(2), fontSize = 84.sp, fontWeight = FontWeight.Bold, color = RadarColors.delta)
            Text("µT", style = MaterialTheme.typography.labelMedium)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(m?.delta?.f(2) ?: "—", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = level?.color() ?: MaterialTheme.colorScheme.onSurface)
                    Text("현재 Δ" + (level?.let { " · ${it.label()}" } ?: ""), style = MaterialTheme.typography.labelSmall)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(ui.recentPeak.f(2), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text("최근 2초 최대", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        SectionCard("최대 Δ 시점의 축별 변화") {
            val v = ui.maxDeltaVec
            KeyValue("Δx / Δy / Δz", "${v.x.f(2)} / ${v.y.f(2)} / ${v.z.f(2)} µT")
            val dom = listOf("x" to abs(v.x), "y" to abs(v.y), "z" to abs(v.z)).maxByOrNull { it.second }?.first ?: "—"
            KeyValue("지배 축", if (ui.maxDelta > 0) dom else "—")
            Text(
                "z 축이 지배적이면 자석이 폰 면에 수직 방향으로 가장 가까웠다는 뜻. 폰을 뒤집어도 한 번 해보세요.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Start,
            )
        }
    }
}
