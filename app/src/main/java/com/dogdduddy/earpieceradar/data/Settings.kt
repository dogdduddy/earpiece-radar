package com.dogdduddy.earpieceradar.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 장소와 무관한 앱 설정. 임계값/판정 모드는 [Profile.tuning] 에 있다. */
data class Settings(
    val developerMode: Boolean = false,
    val calibrationSeconds: Int = 3,
    val countdownSeconds: Int = 3,
    val recordSeconds: Int = 5,
    val acWindowMs: Int = 500,
    val chartWindowSeconds: Int = 10,
) {
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
            .putBoolean("devMode", next.developerMode)
            .putInt("calSec", next.calibrationSeconds)
            .putInt("cdSec", next.countdownSeconds)
            .putInt("recSec", next.recordSeconds)
            .putInt("acWinMs", next.acWindowMs)
            .putInt("chartSec", next.chartWindowSeconds)
            .apply()
    }

    fun setDeveloperMode(on: Boolean) = update { it.copy(developerMode = on) }

    fun resetDefaults() = update { Settings(developerMode = it.developerMode) }

    private fun load(): Settings {
        val d = Settings()
        return Settings(
            developerMode = prefs.getBoolean("devMode", d.developerMode),
            calibrationSeconds = prefs.getInt("calSec", d.calibrationSeconds),
            countdownSeconds = prefs.getInt("cdSec", d.countdownSeconds),
            recordSeconds = prefs.getInt("recSec", d.recordSeconds),
            acWindowMs = prefs.getInt("acWinMs", d.acWindowMs),
            chartWindowSeconds = prefs.getInt("chartSec", d.chartWindowSeconds),
        )
    }
}
