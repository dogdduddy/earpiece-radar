package com.dogdduddy.earpieceradar.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dogdduddy.earpieceradar.signal.DetectionLevel
import com.dogdduddy.earpieceradar.ui.theme.RadarColors

@Composable
fun SectionCard(title: String? = null, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
fun KeyValue(key: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(key, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

fun DetectionLevel.color(): Color = when (this) {
    DetectionLevel.NONE -> RadarColors.levelNone
    DetectionLevel.SUSPECT -> RadarColors.levelSuspect
    DetectionLevel.DETECTED -> RadarColors.levelDetected
}

fun DetectionLevel.label(): String = when (this) {
    DetectionLevel.NONE -> "없음"
    DetectionLevel.SUSPECT -> "의심"
    DetectionLevel.DETECTED -> "탐지"
}
