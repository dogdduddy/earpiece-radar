package com.dogdduddy.earpieceradar.data

import android.content.Context
import android.content.SharedPreferences
import com.dogdduddy.earpieceradar.signal.DetectorConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Settings(
    val deltaThresholdUt: Double = 3.0,
    val zThreshold: Double = 5.0,
    val acThresholdUt: Double = 1.0,
    val debounceSamples: Int = 5,
    val calibrationSeconds: Int = 3,
    val countdownSeconds: Int = 3,
    val recordSeconds: Int = 5,
    val acWindowMs: Int = 500,
    val chartWindowSeconds: Int = 10,
) {
    fun detectorConfig() = DetectorConfig(deltaThresholdUt, zThreshold, acThresholdUt, debounceSamples)
    val acWindowNs: Long get() = acWindowMs * 1_000_000L
}

/** SharedPreferences 기반 설정 저장. 단순함을 위해 DataStore 대신 사용. */
class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    fun update(transform: (Settings) -> Settings) {
        val next = transform(_settings.value)
        _settings.value = next
        prefs.edit()
            .putFloat("deltaThr", next.deltaThresholdUt.toFloat())
            .putFloat("zThr", next.zThreshold.toFloat())
            .putFloat("acThr", next.acThresholdUt.toFloat())
            .putInt("debounce", next.debounceSamples)
            .putInt("calSec", next.calibrationSeconds)
            .putInt("cdSec", next.countdownSeconds)
            .putInt("recSec", next.recordSeconds)
            .putInt("acWinMs", next.acWindowMs)
            .putInt("chartSec", next.chartWindowSeconds)
            .apply()
    }

    fun resetDefaults() = update { Settings() }

    private fun load(): Settings {
        val d = Settings()
        return Settings(
            deltaThresholdUt = prefs.getFloat("deltaThr", d.deltaThresholdUt.toFloat()).toDouble(),
            zThreshold = prefs.getFloat("zThr", d.zThreshold.toFloat()).toDouble(),
            acThresholdUt = prefs.getFloat("acThr", d.acThresholdUt.toFloat()).toDouble(),
            debounceSamples = prefs.getInt("debounce", d.debounceSamples),
            calibrationSeconds = prefs.getInt("calSec", d.calibrationSeconds),
            countdownSeconds = prefs.getInt("cdSec", d.countdownSeconds),
            recordSeconds = prefs.getInt("recSec", d.recordSeconds),
            acWindowMs = prefs.getInt("acWinMs", d.acWindowMs),
            chartWindowSeconds = prefs.getInt("chartSec", d.chartWindowSeconds),
        )
    }
}
