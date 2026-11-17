package com.dogdduddy.earpieceradar.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object RadarColors {
    val axisX = Color(0xFFE63946)
    val axisY = Color(0xFF2A9D8F)
    val axisZ = Color(0xFF457BD9)
    val magnitude = Color(0xFFF4A261)
    val delta = Color(0xFF9B5DE5)
    val levelNone = Color(0xFF2E7D32)
    val levelSuspect = Color(0xFFF9A825)
    val levelDetected = Color(0xFFC62828)
    val seriesPalette = listOf(
        Color(0xFF1F77B4), Color(0xFFFF7F0E), Color(0xFF2CA02C), Color(0xFFD62728),
        Color(0xFF9467BD), Color(0xFF8C564B),
    )
}

private val Light = lightColorScheme(
    primary = Color(0xFF0B5FA5),
    secondary = Color(0xFF2A9D8F),
    tertiary = Color(0xFF9B5DE5),
)
private val Dark = darkColorScheme(
    primary = Color(0xFF8DC6F5),
    secondary = Color(0xFF7FD4C8),
    tertiary = Color(0xFFC9A6F0),
)

@Composable
fun EarpieceRadarTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        content = content,
    )
}
